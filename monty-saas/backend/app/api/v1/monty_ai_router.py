from __future__ import annotations

import asyncio
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field

from app.api.v1.deps import get_current_tenant, get_settings, require_permission
from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.domain.monty_ai_core import MontyAITaskType, monty_ai_core

monty_ai_router = APIRouter(prefix="/monty-ai", tags=["Phase 27 — Monty AI Core"])


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
) -> dict[str, Any]:
    try:
        return await asyncio.to_thread(
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


@monty_ai_router.get("/conversations")
async def list_monty_ai_conversations(
    limit: int = Query(default=25, ge=1, le=100),
    ctx: TenantContext = Depends(get_current_tenant),
) -> dict[str, Any]:
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
) -> dict[str, Any]:
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
) -> dict[str, Any]:
    try:
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
) -> dict[str, Any]:
    try:
        removed = monty_ai_core.memory_manager.delete_memory(ctx, memory_id)
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    return {"removed": removed, "memory_id": memory_id}


@monty_ai_router.get("/actions")
async def list_monty_ai_pending_actions(
    ctx: TenantContext = Depends(get_current_tenant),
) -> dict[str, Any]:
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
) -> dict[str, Any]:
    try:
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
) -> dict[str, Any]:
    engines = monty_ai_core.admin_list_engines(ctx, settings)
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
) -> dict[str, Any]:
    try:
        return monty_ai_core.admin_update_engine(
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


class ConfigureProviderKeyRequest(BaseModel):
    provider: str = Field(min_length=2, max_length=32)
    api_key: str = Field(default="", max_length=300)
    local_base_url: str = Field(default="", max_length=300)


@monty_ai_router.post("/admin/providers/key")
async def admin_configure_provider_key(
    body: ConfigureProviderKeyRequest,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
) -> dict[str, Any]:
    try:
        return monty_ai_core.admin_configure_provider_key(
            ctx,
            provider=body.provider,
            api_key=body.api_key,
            local_base_url=body.local_base_url,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc


@monty_ai_router.get("/admin/metrics")
async def admin_monty_ai_metrics(
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
) -> dict[str, Any]:
    summary = monty_ai_core.evaluation_manager.metrics_summary(ctx.agency_id)
    summary["daily_spend_euro"] = monty_ai_core.cost_manager.get_daily_spend(ctx.agency_id)
    return summary
