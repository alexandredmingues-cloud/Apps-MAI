from __future__ import annotations

import base64
import hashlib
import hmac
import ipaddress
import json
import re
import secrets
import time
from collections import deque
from dataclasses import dataclass
from threading import Lock
from typing import Callable
from urllib.parse import urlparse


def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _b64url_decode(raw: str) -> bytes:
    padding = "=" * (-len(raw) % 4)
    return base64.urlsafe_b64decode(raw + padding)


def hash_password(password: str) -> str:
    if len(password) < 8:
        raise ValueError("Le mot de passe doit contenir au moins 8 caractères.")
    salt = secrets.token_bytes(16)
    derived = hashlib.scrypt(password.encode("utf-8"), salt=salt, n=2**14, r=8, p=1, dklen=32)
    return f"scrypt$16384$8$1${_b64url_encode(salt)}${_b64url_encode(derived)}"


def verify_password(password: str, stored_hash: str) -> bool:
    try:
        algo, n_str, r_str, p_str, salt_b64, dk_b64 = stored_hash.split("$", 5)
        if algo != "scrypt":
            return False
        salt = _b64url_decode(salt_b64)
        expected = _b64url_decode(dk_b64)
        actual = hashlib.scrypt(
            password.encode("utf-8"),
            salt=salt,
            n=int(n_str),
            r=int(r_str),
            p=int(p_str),
            dklen=len(expected),
        )
        return hmac.compare_digest(actual, expected)
    except Exception:
        return False


def create_access_token(
    subject_user_id: str,
    agency_id: str,
    role: str,
    secret: str,
    ttl_seconds: int = 3600,
    now_fn: Callable[[], int] = lambda: int(time.time()),
) -> str:
    now = now_fn()
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": subject_user_id,
        "aid": agency_id,
        "role": role,
        "iat": now,
        "exp": now + ttl_seconds,
        "jti": secrets.token_hex(12),
    }
    h_part = _b64url_encode(json.dumps(header, separators=(",", ":")).encode("utf-8"))
    p_part = _b64url_encode(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signing_input = f"{h_part}.{p_part}".encode("ascii")
    sig = hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
    return f"{h_part}.{p_part}.{_b64url_encode(sig)}"


def decode_access_token(
    token: str,
    secret: str,
    now_fn: Callable[[], int] = lambda: int(time.time()),
) -> dict:
    parts = token.split(".")
    if len(parts) != 3:
        raise ValueError("Jeton JWT invalide.")
    h_part, p_part, s_part = parts
    signing_input = f"{h_part}.{p_part}".encode("ascii")
    expected_sig = hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
    actual_sig = _b64url_decode(s_part)
    if not hmac.compare_digest(actual_sig, expected_sig):
        raise ValueError("Signature JWT invalide.")
    header = json.loads(_b64url_decode(h_part).decode("utf-8"))
    if header.get("alg") != "HS256":
        raise ValueError("Algorithme JWT non supporté.")
    payload = json.loads(_b64url_decode(p_part).decode("utf-8"))
    exp = int(payload.get("exp", 0))
    if now_fn() >= exp:
        raise ValueError("Session expirée.")
    return payload


class InputSanitizer:
    VALID_ID_REGEX = re.compile(r"^[a-zA-Z0-9_\-]{1,128}$")
    CONTROL_CHARS_REGEX = re.compile(r"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F\u200B-\u200F\uFEFF]")
    HTML_SCRIPT_REGEX = re.compile(r"(?is)<\s*/?\s*(script|iframe|object|embed|style)[^>]*>|javascript\s*:|on\w+\s*=")
    PROMPT_INJECTION_REGEX = re.compile(
        r"(?im)^\s*(system|developer|assistant)\s*:|ignore\s+(all\s+)?previous\s+instructions|```\s*(system|json)"
    )
    FORMULA_PREFIXES = ("=", "+", "-", "@", "\t", "\r")

    @classmethod
    def sanitize_text(cls, value: str, max_length: int = 500) -> str:
        if not value or max_length <= 0:
            return ""
        cleaned = cls.CONTROL_CHARS_REGEX.sub("", value)
        cleaned = cls.HTML_SCRIPT_REGEX.sub("", cleaned)
        return cleaned.strip()[:max_length]

    @classmethod
    def sanitize_ai_prompt_input(cls, value: str, max_length: int = 1200) -> str:
        cleaned = cls.sanitize_text(value, max_length)
        return cls.PROMPT_INJECTION_REGEX.sub("[filtre-sécurité]", cleaned).strip()[:max_length]

    @classmethod
    def sanitize_spreadsheet_cell(cls, value: str) -> str:
        cleaned = cls.sanitize_text(value, 500)
        if cleaned and cleaned[0] in cls.FORMULA_PREFIXES:
            return f"'{cleaned}"
        return cleaned

    @classmethod
    def sanitize_public_http_url(
        cls,
        raw_url: str,
        allowed_hosts: set[str] | None = None,
        max_length: int = 500,
    ) -> str:
        cleaned = cls.CONTROL_CHARS_REGEX.sub("", raw_url).strip()
        if not cleaned or len(cleaned) > max_length:
            return ""
        parsed = urlparse(cleaned)
        if parsed.scheme not in ("http", "https") or not parsed.hostname:
            return ""
        host = parsed.hostname.lower()
        if host in ("localhost", "127.0.0.1", "::1", "0.0.0.0"):
            return ""
        try:
            ip = ipaddress.ip_address(host)
            if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved:
                return ""
        except ValueError:
            pass
        if allowed_hosts is not None and host not in allowed_hosts:
            return ""
        return cleaned


@dataclass(frozen=True)
class RateLimitDecision:
    allowed: bool
    remaining: int
    retry_after_seconds: int = 0


class SlidingWindowRateLimiter:
    """
    Rate limiter à fenêtre glissante distribué sur Redis (ZREMRANGEBYSCORE/ZCARD/ZADD/EXPIRE)
    lorsqu'un `redis_url` ou `redis_client` est configuré, avec bascule transparente en mémoire
    lorsque Redis est absent ou temporairement injoignable.
    """

    def __init__(
        self,
        clock: Callable[[], float] = time.monotonic,
        *,
        redis_url: str = "",
        redis_client: object | None = None,
        wall_clock: Callable[[], float] = time.time,
        key_prefix: str = "monty:rl:",
    ) -> None:
        self._clock = clock
        self._wall_clock = wall_clock
        self._redis_url = redis_url.strip()
        self._redis_client = redis_client
        self._redis_init_attempted = redis_client is not None
        self._key_prefix = key_prefix
        self._buckets: dict[str, deque[float]] = {}
        self._lock = Lock()

    def _resolve_redis_client(self) -> object | None:
        if self._redis_client is not None:
            return self._redis_client
        if self._redis_init_attempted:
            return None
        url = self._redis_url or __import__("os").environ.get("REDIS_URL", "").strip()
        self._redis_init_attempted = True
        if not url:
            return None
        try:
            import redis  # type: ignore

            self._redis_client = redis.Redis.from_url(
                url,
                socket_connect_timeout=1.5,
                socket_timeout=1.5,
                decode_responses=True,
            )
            return self._redis_client
        except Exception:
            return None

    def _check_redis(self, client: object, key: str, max_requests: int, window_seconds: int) -> RateLimitDecision | None:
        now = float(self._wall_clock())
        cutoff = now - float(window_seconds)
        rkey = f"{self._key_prefix}{key}"
        member = f"{now:.6f}:{secrets.token_hex(4)}"
        try:
            pipe = client.pipeline()  # type: ignore[attr-defined]
            pipe.zremrangebyscore(rkey, "-inf", cutoff)
            pipe.zcard(rkey)
            pipe.zadd(rkey, {member: now})
            pipe.expire(rkey, max(1, int(window_seconds) + 1))
            results = pipe.execute()
            prior_count = int(results[1])
            if prior_count >= max_requests:
                # Retire le jeton ajouté à tort et calcule le retry_after depuis la plus ancienne entrée
                try:
                    client.zrem(rkey, member)  # type: ignore[attr-defined]
                    oldest = client.zrange(rkey, 0, 0, withscores=True)  # type: ignore[attr-defined]
                    oldest_ts = float(oldest[0][1]) if oldest else now
                except Exception:
                    oldest_ts = now
                retry_after = max(1, int((oldest_ts + window_seconds) - now))
                return RateLimitDecision(allowed=False, remaining=0, retry_after_seconds=retry_after)
            return RateLimitDecision(allowed=True, remaining=max(0, max_requests - (prior_count + 1)))
        except Exception:
            return None

    def check_and_consume(self, key: str, max_requests: int, window_seconds: int) -> RateLimitDecision:
        client = self._resolve_redis_client()
        if client is not None:
            decision = self._check_redis(client, key, max_requests, window_seconds)
            if decision is not None:
                return decision

        now = self._clock()
        cutoff = now - window_seconds
        with self._lock:
            bucket = self._buckets.setdefault(key, deque())
            while bucket and bucket[0] < cutoff:
                bucket.popleft()
            if len(bucket) >= max_requests:
                retry_after = max(1, int((bucket[0] + window_seconds) - now))
                return RateLimitDecision(allowed=False, remaining=0, retry_after_seconds=retry_after)
            bucket.append(now)
            return RateLimitDecision(allowed=True, remaining=max(0, max_requests - len(bucket)))


def verify_stripe_webhook_signature(
    payload: bytes,
    signature_header: str,
    secret: str,
    *,
    tolerance_seconds: int = 300,
    now_fn: Callable[[], int] = lambda: int(time.time()),
) -> dict:
    """Vérifie la signature Stripe (`t=...,v1=...`) via HMAC-SHA256 en temps constant."""
    if not secret:
        raise ValueError("STRIPE_WEBHOOK_SECRET non configuré.")
    if not signature_header:
        raise ValueError("En-tête Stripe-Signature manquant.")

    timestamp_str = ""
    v1_signatures: list[str] = []
    for part in signature_header.split(","):
        if "=" not in part:
            continue
        k, v = part.strip().split("=", 1)
        if k == "t":
            timestamp_str = v
        elif k == "v1":
            v1_signatures.append(v)

    if not timestamp_str or not v1_signatures:
        raise ValueError("Format Stripe-Signature invalide.")
    try:
        ts = int(timestamp_str)
    except ValueError as exc:
        raise ValueError("Horodatage Stripe-Signature invalide.") from exc

    if abs(now_fn() - ts) > tolerance_seconds:
        raise ValueError("Webhook Stripe expiré (fenêtre anti-rejeu dépassée).")

    signed_payload = f"{timestamp_str}.".encode("ascii") + payload
    expected_mac = hmac.new(secret.encode("utf-8"), signed_payload, hashlib.sha256).hexdigest()
    if not any(hmac.compare_digest(expected_mac, candidate) for candidate in v1_signatures):
        raise ValueError("Signature webhook Stripe invalide.")

    try:
        return json.loads(payload.decode("utf-8"))
    except Exception as exc:
        raise ValueError("Payload JSON Stripe invalide.") from exc

