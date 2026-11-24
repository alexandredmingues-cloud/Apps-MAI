from __future__ import annotations

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status
import httpx
from pydantic import BaseModel, Field

from app.api.v1.deps import get_db_session_factory, get_settings, require_permission
from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.core.security import InputSanitizer, verify_stripe_webhook_signature
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
) -> dict:
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


@billing_router.post("/webhooks/stripe")
async def stripe_webhook_handler(
    request: Request,
    stripe_signature: str = Header(default="", alias="Stripe-Signature"),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict:
    payload_bytes = await request.body()
    try:
        if sf is not None:
            event = verify_stripe_webhook_signature(
                payload_bytes,
                stripe_signature,
                settings.stripe_webhook_secret,
            )
            event_id = str(event.get("id", "")).strip()
            event_type = str(event.get("type", "")).strip()
            obj = event.get("data", {}).get("object", {})
            metadata = obj.get("metadata", {}) or {}
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
