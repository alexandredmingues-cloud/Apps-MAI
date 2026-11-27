from __future__ import annotations

import asyncio
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field

from app.api.v1.deps import get_current_tenant, get_db_session_factory, get_settings, require_permission
from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.db.postgres_v3_orchestrator_repository import PostgresV3OrchestratorRepository, is_valid_uuid
from app.domain.monty_ai_core import MontyAITaskType, monty_ai_core

monty_ai_router = APIRouter(prefix="/monty-ai", tags=["Phase 27 — Monty AI Core"])


def _use_pg(sf: Any, ctx: TenantContext) -> bool:
    return sf is not None and is_valid_uuid(ctx.agency_id)


class MontyAIChatRequest(BaseModel):
    prompt: str = Field(min_length=2, max_length=4000)
    task_type: MontyAITaskType = MontyAITaskType.CONVERSATION
    confidential_mode: bool = False
    context_payload: dict[str, Any] = Field(default_factory=dict)


@monty_ai_router.post("/chat")
async def monty_ai_chat(
    body: MontyAIChatRequest,
    ctx: TenantContext = Depends(get_current_tenant),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        res = await asyncio.to_thread(
            monty_ai_core.chat,
            ctx,
            prompt=body.prompt,
            task_type=body.task_type,
            confidential_mode=body.confidential_mode,
            context_payload=body.context_payload,
            settings=settings,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    engine_used = str(res.pop("_engine_used", "monty-tools-offline"))
    est_cost = float(res.pop("_estimated_cost_euro", 0.0))
    sanitized_prompt = str(res.pop("_sanitized_prompt", ""))

    if _use_pg(sf, ctx):
        audit_status = "CACHE_HIT" if res.get("cache_hit") else (
            "OFFLINE_TOOLS" if res.get("execution_mode") == "TOOL_ASSISTED_OFFLINE" else "SUCCESS"
        )
        ids = await PostgresV3OrchestratorRepository(sf).record_chat_turn(
            ctx,
            task_type=body.task_type.value,
            engine_used=engine_used,
            execution_mode=str(res.get("execution_mode") or "TOOL_ASSISTED_OFFLINE"),
            sanitized_prompt=sanitized_prompt,
            response_text=str(res.get("response_text") or ""),
            sources=list(res.get("sources") or []),
            latency_ms=int(res.get("latency_ms") or 1),
            estimated_cost_euro=est_cost,
            audit_status=audit_status,
            pending_action=res.get("pending_action"),
        )
        if ids.get("conversation_id"):
            res["conversation_id"] = ids["conversation_id"]
        if ids.get("pending_action_id") and isinstance(res.get("pending_action"), dict):
            res["pending_action"]["id"] = ids["pending_action_id"]
    return res


@monty_ai_router.get("/conversations")
async def list_monty_ai_conversations(
    limit: int = Query(default=25, ge=1, le=100),
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if _use_pg(sf, ctx):
        items = await PostgresV3OrchestratorRepository(sf).list_conversations(ctx, limit=limit)
    else:
        items = monty_ai_core.list_conversations(ctx, limit=limit)
    return {
        "count": len(items),
        "conversations": [
            {
                "id": c.id,
                "task_type": c.task_type,
                "execution_mode": c.execution_mode,
                "sanitized_prompt": c.sanitized_prompt,
                "response_text": c.response_text,
                "sources": c.sources,
                "tools_invoked": c.tools_invoked,
                "latency_ms": c.latency_ms,
                "created_at": c.created_at,
            }
            for c in items
        ],
    }


@monty_ai_router.get("/memories")
async def list_monty_ai_memories(
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if _use_pg(sf, ctx):
        items = await PostgresV3OrchestratorRepository(sf).list_memories(ctx)
    else:
        items = monty_ai_core.memory_manager.list_memories(ctx)
    return {"count": len(items), "memories": [m.__dict__ for m in items]}


class UpsertMemoryRequest(BaseModel):
    memory_id: str | None = Field(default=None, max_length=64)
    scope: str = Field(default="USER", max_length=24)
    title: str = Field(min_length=2, max_length=180)
    content: str = Field(min_length=2, max_length=2000)
    source_ref: str = Field(default="manual", max_length=120)


@monty_ai_router.post("/memories", status_code=status.HTTP_201_CREATED)
async def upsert_monty_ai_memory(
    body: UpsertMemoryRequest,
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if _use_pg(sf, ctx):
            m = await PostgresV3OrchestratorRepository(sf).upsert_memory(
                ctx,
                memory_id=body.memory_id,
                scope=body.scope,
                title=body.title,
                content=body.content,
                source_ref=body.source_ref,
            )
        else:
            m = monty_ai_core.memory_manager.upsert_memory(
                ctx,
                memory_id=body.memory_id,
                scope=body.scope,
                title=body.title,
                content=body.content,
                source_ref=body.source_ref,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return m.__dict__


@monty_ai_router.delete("/memories/{memory_id}")
async def delete_monty_ai_memory(
    memory_id: str,
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if _use_pg(sf, ctx):
            removed = await PostgresV3OrchestratorRepository(sf).delete_memory(ctx, memory_id)
        else:
            removed = monty_ai_core.memory_manager.delete_memory(ctx, memory_id)
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    return {"removed": removed, "memory_id": memory_id}


@monty_ai_router.get("/actions")
async def list_monty_ai_pending_actions(
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if _use_pg(sf, ctx):
        items = await PostgresV3OrchestratorRepository(sf).list_pending_actions(ctx)
    else:
        items = monty_ai_core.list_pending_actions(ctx)
    return {
        "count": len(items),
        "actions": [
            {
                "id": a.id,
                "action_type": a.action_type.value,
                "title": a.title,
                "summary": a.summary,
                "payload": a.payload,
                "status": a.status,
                "requested_by_user_id": a.requested_by_user_id,
                "reviewed_by_user_id": a.reviewed_by_user_id,
                "created_at": a.created_at,
                "reviewed_at": a.reviewed_at,
            }
            for a in items
        ],
    }


class DecidePendingActionRequest(BaseModel):
    approve: bool


@monty_ai_router.post("/actions/{action_id}/decide")
async def decide_monty_ai_pending_action(
    action_id: str,
    body: DecidePendingActionRequest,
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if _use_pg(sf, ctx):
            a = await PostgresV3OrchestratorRepository(sf).decide_pending_action(ctx, action_id, body.approve)
        else:
            a = monty_ai_core.decide_pending_action(ctx, action_id, body.approve)
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return {
        "id": a.id,
        "action_type": a.action_type.value,
        "title": a.title,
        "status": a.status,
        "reviewed_by_user_id": a.reviewed_by_user_id,
        "reviewed_at": a.reviewed_at,
    }


@monty_ai_router.get("/admin/engines")
async def admin_list_ai_engines(
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    engines = monty_ai_core.admin_list_engines(ctx, settings)
    if sf is not None:
        persisted = {r["engine_id"]: r for r in await PostgresV3OrchestratorRepository(sf).list_engine_configs()}
        for e in engines:
            row = persisted.get(e["engine_id"])
            if row:
                e["enabled"] = bool(row["enabled"])
                e["is_default"] = bool(row["is_default"])
                e["is_fallback"] = bool(row["is_fallback"])
                e["priority"] = int(row["priority"])
                if row.get("model_name"):
                    e["model_name"] = str(row["model_name"])
    return {"count": len(engines), "engines": engines}


class UpdateEngineRequest(BaseModel):
    enabled: bool | None = None
    is_default: bool | None = None
    is_fallback: bool | None = None
    priority: int | None = Field(default=None, ge=1, le=999)
    model_name: str | None = Field(default=None, max_length=120)


@monty_ai_router.put("/admin/engines/{engine_id}")
async def admin_update_ai_engine(
    engine_id: str,
    body: UpdateEngineRequest,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        updated = monty_ai_core.admin_update_engine(
            ctx,
            engine_id=engine_id,
            enabled=body.enabled,
            is_default=body.is_default,
            is_fallback=body.is_fallback,
            priority=body.priority,
            model_name=body.model_name,
            settings=settings,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc

    if sf is not None:
        await PostgresV3OrchestratorRepository(sf).upsert_engine_config(
            ctx,
            engine_id=updated["engine_id"],
            provider=updated["provider"],
            model_name=updated["model_name"],
            tier=updated["tier"],
            is_local=bool(updated["is_local"]),
            confidential_safe=bool(updated["confidential_safe"]),
            enabled=bool(updated["enabled"]),
            is_default=bool(updated["is_default"]),
            is_fallback=bool(updated["is_fallback"]),
            priority=int(updated["priority"]),
            min_ram_gb=int(updated["min_ram_gb"]),
            masked_api_key=str(updated["masked_api_key"]),
        )
    return updated


class ConfigureProviderKeyRequest(BaseModel):
    provider: str = Field(min_length=2, max_length=32)
    api_key: str = Field(default="", max_length=300)
    local_base_url: str = Field(default="", max_length=300)


@monty_ai_router.post("/admin/providers/key")
async def admin_configure_provider_key(
    body: ConfigureProviderKeyRequest,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    settings: Settings = Depends(get_settings),
) -> dict[str, Any]:
    try:
        return monty_ai_core.admin_configure_provider_key(
            ctx,
            provider=body.provider,
            api_key=body.api_key,
            local_base_url=body.local_base_url,
            settings=settings,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc


@monty_ai_router.get("/admin/metrics")
async def admin_monty_ai_metrics(
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if _use_pg(sf, ctx):
        summary = await PostgresV3OrchestratorRepository(sf).get_ai_metrics_summary(ctx)
        if summary["daily_spend_euro"] == 0.0:
            summary["daily_spend_euro"] = monty_ai_core.cost_manager.get_daily_spend(ctx.agency_id)
        return summary
    summary = monty_ai_core.evaluation_manager.metrics_summary(ctx.agency_id)
    summary["daily_spend_euro"] = monty_ai_core.cost_manager.get_daily_spend(ctx.agency_id)
    return summary
