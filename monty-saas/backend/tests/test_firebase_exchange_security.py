from __future__ import annotations

from dataclasses import replace
import sys
from pathlib import Path
import unittest
from unittest.mock import patch
import time

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from fastapi import HTTPException  # noqa: E402
from app.api.v1 import router as auth_router  # noqa: E402
from app.core.config import load_settings  # noqa: E402
from app.core.rbac import MontyRole  # noqa: E402


def _claims(**overrides):
    data = {
        "sub": "firebase-user-123",
        "uid": "firebase-user-123",
        "aud": "demo-project",
        "iss": "https://securetoken.google.com/demo-project",
        "exp": int(time.time()) + 600,
        "iat": int(time.time()) - 30,
        "auth_time": int(time.time()) - 60,
        "email": "agent@example.test",
        "email_verified": True,
        "name": "Agent Exemple",
        # Ces champs ne doivent jamais attribuer un rôle.
        "staff": True,
        "advisor": True,
    }
    data.update(overrides)
    return data


class FirebaseTokenVerificationTests(unittest.TestCase):
    def setUp(self):
        self.settings = replace(load_settings(), firebase_project_id="demo-project")

    def verify(self, claims):
        return auth_router._verify_firebase_token_via_google(
            "signed.firebase.token",
            settings=self.settings,
            verifier=lambda _token, _project: claims,
        )

    def test_accepts_verified_token_for_exact_project(self):
        self.assertEqual(self.verify(_claims())["uid"], "firebase-user-123")

    def test_rejects_wrong_audience_or_issuer(self):
        with self.assertRaises(ValueError):
            self.verify(_claims(aud="attacker-project"))
        with self.assertRaises(ValueError):
            self.verify(_claims(iss="https://attacker.example/demo-project"))

    def test_rejects_unverified_or_missing_email(self):
        with self.assertRaises(ValueError):
            self.verify(_claims(email_verified=False))
        with self.assertRaises(ValueError):
            self.verify(_claims(email=""))

    def test_rejects_expired_or_missing_expiration(self):
        with self.assertRaises(ValueError):
            self.verify(_claims(exp=int(time.time()) - 1))
        with self.assertRaises(ValueError):
            self.verify(_claims(exp=None))

    def test_rejects_when_project_is_not_configured(self):
        settings = replace(self.settings, firebase_project_id="")
        with self.assertRaises(ValueError):
            auth_router._verify_firebase_token_via_google(
                "signed.firebase.token",
                settings=settings,
                verifier=lambda _token, _project: _claims(),
            )


class _Mappings:
    def __init__(self, row):
        self.row = row

    def mappings(self):
        return self

    def first(self):
        return self.row


class _FakeSession:
    def __init__(self, row):
        self.row = row
        self.sql = ""
        self.params = {}

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return False

    async def execute(self, statement, params):
        self.sql = str(statement)
        self.params = params
        return _Mappings(self.row)


class FirebaseExchangeMembershipTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.settings = replace(
            load_settings(),
            firebase_project_id="demo-project",
            jwt_secret="test-secret-for-firebase-exchange-32-bytes",
        )
        self.row = {
            "user_id": "11111111-1111-1111-1111-111111111111",
            "email": "agent@example.test",
            "display_name": "Agent Persisté",
            "is_active": True,
            "is_super_admin": False,
            "agency_id": "22222222-2222-2222-2222-222222222222",
            "agency_name": "Agence Exemple",
            "role": "AGENT",
        }

    async def test_role_and_identity_come_from_active_database_membership(self):
        session = _FakeSession(self.row)

        class SessionFactory:
            def __call__(self):
                return session

        with patch.object(auth_router, "_verify_firebase_token_via_google", return_value=_claims()):
            result = await auth_router.exchange_firebase_session(
                auth_router.FirebaseExchangeRequest(
                    id_token="x" * 30,
                    agency_id=self.row["agency_id"],
                    preferred_role=MontyRole.DIRECTOR,
                ),
                settings=self.settings,
                sf=SessionFactory(),
            )

        self.assertEqual(result.user_id, self.row["user_id"])
        self.assertEqual(result.agency_id, self.row["agency_id"])
        self.assertEqual(result.role, MontyRole.AGENT)
        self.assertEqual(result.display_name, "Agent Persisté")
        self.assertEqual(session.params["email"], "agent@example.test")
        self.assertIn("m.status = 'ACTIVE'", session.sql)
        self.assertNotIn("fb:", result.user_id)

    async def test_rejects_missing_or_inactive_membership(self):
        session = _FakeSession(None)

        class SessionFactory:
            def __call__(self):
                return session

        with patch.object(auth_router, "_verify_firebase_token_via_google", return_value=_claims()):
            with self.assertRaises(HTTPException) as raised:
                await auth_router.exchange_firebase_session(
                    auth_router.FirebaseExchangeRequest(
                        id_token="x" * 30,
                        agency_id=self.row["agency_id"],
                        preferred_role=MontyRole.DIRECTOR,
                    ),
                    settings=self.settings,
                    sf=SessionFactory(),
                )
        self.assertEqual(raised.exception.status_code, 403)

    async def test_fails_closed_without_persistent_database(self):
        with patch.object(auth_router, "_verify_firebase_token_via_google", return_value=_claims()):
            with self.assertRaises(HTTPException) as raised:
                await auth_router.exchange_firebase_session(
                    auth_router.FirebaseExchangeRequest(id_token="x" * 30),
                    settings=self.settings,
                    sf=None,
                )
        self.assertEqual(raised.exception.status_code, 503)


if __name__ == "__main__":
    unittest.main()
