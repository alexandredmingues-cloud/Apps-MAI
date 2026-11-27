"""Server-side Cloudflare Turnstile verification. Fail closed in production."""
from __future__ import annotations

import httpx
from fastapi import HTTPException, Request, status

from app.core.config import Settings

VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"


async def verify_turnstile(token: str, request: Request, settings: Settings) -> None:
    if not settings.turnstile_secret_key:
        if settings.is_production:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="La protection anti-robot n'est pas configurée. Réessayez ultérieurement.",
            )
        return
    clean_token = token.strip()
    if not clean_token or len(clean_token) > 2048:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Veuillez valider le contrôle anti-robot.")
    payload = {"secret": settings.turnstile_secret_key, "response": clean_token}
    if request.client and request.client.host:
        payload["remoteip"] = request.client.host
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(5.0, connect=2.0)) as client:
            response = await client.post(VERIFY_URL, data=payload)
            response.raise_for_status()
            result = response.json()
    except (httpx.HTTPError, ValueError):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Vérification anti-robot indisponible. Réessayez.",
        )
    if not isinstance(result, dict) or result.get("success") is not True:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Contrôle anti-robot invalide ou expiré. Réessayez.")
