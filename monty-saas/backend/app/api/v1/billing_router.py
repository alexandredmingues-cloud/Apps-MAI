from __future__ import annotations

import secrets
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, status
import httpx
from pydantic import BaseModel, Field

from app.api.v1.deps import get_db_session_factory, get_settings, require_permission
from app.api.v1.notaries_rnic_router import _resolve_optional_tenant, _rnic_access
from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.core.security import InputSanitizer, verify_stripe_webhook_signature
from app.db.postgres_notaries_rnic_repository import PostgresNotariesRnicRepository
from app.db.postgres_repositories import PostgresBillingRepository
from app.domain.auth_and_billing import BillingService, SAAS_PLANS

billing_router = APIRouter(prefix="/billing", tags=["Facturation & Abonnements Stripe"])
_billing = BillingService()


@billing_router.get("/plans")
async def list_saas_plans() -> dict:
    return {
        "plans": [
            {
                "code": p.code,
                "name": p.name,
                "monthly_price_euro": p.monthly_price_euro,
                "seat_limit": p.seat_limit,
                "ai_monthly_quota_tokens": p.ai_monthly_quota_tokens,
                "target_audience": p.target_audience,
                "features": list(p.features),
            }
            for p in _billing.list_plans()
        ]
    }


@billing_router.get("/subscription")
async def get_agency_subscription(
    ctx: TenantContext = Depends(require_permission(Permission.BILLING_MANAGE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        state = await PostgresBillingRepository(sf).get_agency_billing(ctx)
    else:
        state = _billing.get_agency_billing(ctx)
    return {
        "agency_id": state.agency_id,
        "plan_code": state.plan_code,
        "subscription_status": state.subscription_status,
        "seat_limit": state.seat_limit,
    }


class CheckoutSessionRequest(BaseModel):
    plan_code: str = Field(pattern="^(INDIVIDUAL|AGENCY|NETWORK)$")
    success_url: str
    cancel_url: str


@billing_router.post("/checkout-session")
async def create_stripe_checkout_session(
    body: CheckoutSessionRequest,
    ctx: TenantContext = Depends(require_permission(Permission.BILLING_MANAGE)),
    settings: Settings = Depends(get_settings),
) -> dict:
    plan = SAAS_PLANS.get(body.plan_code)
    if plan is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Forfait inconnu.")

    safe_success = InputSanitizer.sanitize_public_http_url(body.success_url)
    safe_cancel = InputSanitizer.sanitize_public_http_url(body.cancel_url)
    if not safe_success or not safe_cancel:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="URLs success_url et cancel_url publiques HTTPS/HTTP valides obligatoires.",
        )

    if not settings.stripe_secret_key:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="STRIPE_SECRET_KEY non configurée sur le serveur.",
        )

    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.post(
            "https://api.stripe.com/v1/checkout/sessions",
            headers={"Authorization": f"Bearer {settings.stripe_secret_key}"},
            data={
                "mode": "subscription",
                "success_url": safe_success,
                "cancel_url": safe_cancel,
                "line_items[0][price_data][currency]": "eur",
                "line_items[0][price_data][unit_amount]": str(plan.monthly_price_euro * 100),
                "line_items[0][price_data][recurring][interval]": "month",
                "line_items[0][price_data][product_data][name]": plan.name,
                "line_items[0][quantity]": "1",
                "metadata[agency_id]": ctx.agency_id,
                "metadata[plan_code]": plan.code,
                "metadata[actor_user_id]": ctx.user_id,
            },
        )
    if resp.status_code >= 400:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Erreur Stripe Checkout ({resp.status_code}).",
        )
    data = resp.json()
    return {
        "checkout_session_id": data.get("id", ""),
        "checkout_url": data.get("url", ""),
        "plan_code": plan.code,
    }


class RnicPassCheckoutRequest(BaseModel):
    buyer_email: str = Field(min_length=5, max_length=160)
    success_url: str
    cancel_url: str


@billing_router.post("/rnic-pass")
async def create_rnic_particulier_checkout(
    body: RnicPassCheckoutRequest,
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
) -> dict:
    """
    Crée une session Stripe Checkout (paiement unique) pour débloquer l'accès Particulier
    à la Base Nationale des Copropriétés (RNIC ANAH) pendant 30 jours.
    Gratuit pour les agences et vendeurs (vérifié côté serveur).
    """
    ctx = _resolve_optional_tenant(authorization, settings)
    clean_email = InputSanitizer.sanitize_text(body.buyer_email, 160).lower()
    if "@" not in clean_email or "." not in clean_email.split("@")[-1]:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Adresse e-mail valide obligatoire.")

    safe_success = InputSanitizer.sanitize_public_http_url(body.success_url)
    safe_cancel = InputSanitizer.sanitize_public_http_url(body.cancel_url)
    if not safe_success or not safe_cancel:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="URLs success_url et cancel_url publiques HTTPS/HTTP valides obligatoires.",
        )

    if not settings.stripe_secret_key:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="STRIPE_SECRET_KEY non configurée sur le serveur pour le paiement Pass Copropriétés Particulier.",
        )

    pass_token = f"rnic_pass_{secrets.token_urlsafe(24)}"
    sep = "&" if "?" in safe_success else "?"
    success_with_token = (
        f"{safe_success}{sep}"
        + urlencode({"rnic_pass_token": pass_token})
        + "&rnic_session_id={CHECKOUT_SESSION_ID}"
    )

    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.post(
            "https://api.stripe.com/v1/checkout/sessions",
            headers={"Authorization": f"Bearer {settings.stripe_secret_key}"},
            data={
                "mode": "payment",
                "customer_email": clean_email,
                "success_url": success_with_token,
                "cancel_url": safe_cancel,
                "line_items[0][price_data][currency]": "eur",
                "line_items[0][price_data][unit_amount]": str(settings.rnic_particulier_price_euro * 100),
                "line_items[0][price_data][product_data][name]": "Monty RNIC — Accès Base Nationale Copropriétés (30 jours)",
                "line_items[0][quantity]": "1",
                "metadata[pass_type]": "RNIC_PARTICULIER",
                "metadata[pass_token]": pass_token,
                "metadata[buyer_email]": clean_email,
                "metadata[user_id]": ctx.user_id if ctx else "",
            },
        )
    if resp.status_code >= 400:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Erreur Stripe Checkout RNIC ({resp.status_code}).",
        )
    data = resp.json()
    return {
        "checkout_session_id": data.get("id", ""),
        "checkout_url": data.get("url", ""),
        "pass_token": pass_token,
        "amount_euro": settings.rnic_particulier_price_euro,
    }


@billing_router.get("/rnic-pass/verify")
async def verify_rnic_pass_status(
    pass_token: str = Query(default="", max_length=128),
    session_id: str = Query(default="", max_length=160),
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict:
    ctx = _resolve_optional_tenant(authorization, settings)
    uid = ctx.user_id if ctx else None
    active = (
        await PostgresNotariesRnicRepository(sf).has_active_rnic_pass(pass_token=pass_token, user_id=uid)
        if sf is not None
        else _rnic_access.has_active_pass(pass_token=pass_token, user_id=uid)
    )
    if not active and session_id and settings.stripe_secret_key:
        try:
            async with httpx.AsyncClient(timeout=12.0) as client:
                resp = await client.get(
                    f"https://api.stripe.com/v1/checkout/sessions/{session_id}",
                    headers={"Authorization": f"Bearer {settings.stripe_secret_key}"},
                )
            if resp.status_code == 200:
                sess = resp.json()
                meta = sess.get("metadata", {}) or {}
                if (
                    sess.get("payment_status") == "paid"
                    and meta.get("pass_type") == "RNIC_PARTICULIER"
                ):
                    tok = str(meta.get("pass_token") or pass_token)
                    buyer_email = str(meta.get("buyer_email") or sess.get("customer_email") or "particulier@monty.fr")
                    meta_uid = str(meta.get("user_id") or "") or uid
                    if sf is not None:
                        rec = await PostgresNotariesRnicRepository(sf).issue_active_rnic_pass(
                            user_id=meta_uid,
                            buyer_email=buyer_email,
                            stripe_session_id=session_id,
                            pass_token=tok,
                            amount_euro=settings.rnic_particulier_price_euro,
                        )
                    else:
                        rec = _rnic_access.issue_active_pass(
                            user_id=meta_uid,
                            buyer_email=buyer_email,
                            stripe_session_id=session_id,
                            pass_token=tok,
                            amount_euro=settings.rnic_particulier_price_euro,
                        )
                    return {"active": True, "pass_token": rec.pass_token, "expires_at": rec.expires_at}
        except Exception:
            pass
    return {"active": active, "pass_token": pass_token}


@billing_router.post("/webhooks/stripe")
async def stripe_webhook_handler(
    request: Request,
    stripe_signature: str = Header(default="", alias="Stripe-Signature"),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict:
    payload_bytes = await request.body()
    try:
        event = verify_stripe_webhook_signature(
            payload_bytes,
            stripe_signature,
            settings.stripe_webhook_secret,
        )
        event_id = str(event.get("id", "")).strip()
        event_type = str(event.get("type", "")).strip()
        obj = event.get("data", {}).get("object", {})
        metadata = obj.get("metadata", {}) or {}

        if metadata.get("pass_type") == "RNIC_PARTICULIER":
            if sf is not None:
                processed = await PostgresBillingRepository(sf).record_webhook_and_update_agency(
                    event_id=event_id,
                    event_type=event_type,
                    agency_id="",
                    plan_code="AGENCY",
                    subscription_status="ACTIVE",
                    customer_id=str(obj.get("customer", "") or ""),
                    subscription_id=str(obj.get("id", "") or ""),
                )
                if not processed:
                    return {"status": "duplicate_ignored", "event_id": event_id, "event_type": event_type}
                if event_type == "checkout.session.completed":
                    rec = await PostgresNotariesRnicRepository(sf).issue_active_rnic_pass(
                        user_id=str(metadata.get("user_id") or "") or None,
                        buyer_email=str(metadata.get("buyer_email") or obj.get("customer_email") or "particulier@monty.fr"),
                        stripe_session_id=str(obj.get("id") or event_id),
                        pass_token=str(metadata.get("pass_token") or ""),
                        amount_euro=settings.rnic_particulier_price_euro,
                    )
                    return {
                        "status": "processed",
                        "event_id": event_id,
                        "event_type": event_type,
                        "rnic_pass_token": rec.pass_token,
                    }
                return {"status": "processed", "event_id": event_id, "event_type": event_type}

            if event_id in _billing._processed_events:
                return {"status": "duplicate_ignored", "event_id": event_id, "event_type": event_type}
            _billing._processed_events.add(event_id)
            if event_type == "checkout.session.completed":
                rec = _rnic_access.issue_active_pass(
                    user_id=str(metadata.get("user_id") or "") or None,
                    buyer_email=str(metadata.get("buyer_email") or obj.get("customer_email") or "particulier@monty.fr"),
                    stripe_session_id=str(obj.get("id") or event_id),
                    pass_token=str(metadata.get("pass_token") or ""),
                    amount_euro=settings.rnic_particulier_price_euro,
                )
                return {
                    "status": "processed",
                    "event_id": event_id,
                    "event_type": event_type,
                    "rnic_pass_token": rec.pass_token,
                }
            return {"status": "processed", "event_id": event_id, "event_type": event_type}

        if sf is not None:
            agency_id = str(metadata.get("agency_id", "")).strip()
            plan_code = str(metadata.get("plan_code", "AGENCY")).strip().upper()
            if event_type in ("checkout.session.completed", "invoice.payment_succeeded"):
                sub_status = "ACTIVE"
            elif event_type == "invoice.payment_failed":
                sub_status = "PAST_DUE"
            elif event_type == "customer.subscription.deleted":
                sub_status = "CANCELED"
            else:
                sub_status = "ACTIVE"

            repo = PostgresBillingRepository(sf)
            processed = await repo.record_webhook_and_update_agency(
                event_id=event_id,
                event_type=event_type,
                agency_id=agency_id,
                plan_code=plan_code,
                subscription_status=sub_status,
                customer_id=str(obj.get("customer", "") or ""),
                subscription_id=str(obj.get("subscription", obj.get("id", "")) or ""),
            )
            return {
                "status": "processed" if processed else "duplicate_ignored",
                "event_id": event_id,
                "event_type": event_type,
            }

        return _billing.process_stripe_webhook(
            payload_bytes=payload_bytes,
            signature_header=stripe_signature,
            webhook_secret=settings.stripe_webhook_secret,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
