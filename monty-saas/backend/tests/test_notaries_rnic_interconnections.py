from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
from pathlib import Path
import sys
import time
import unittest

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.config import load_settings  # noqa: E402
from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.domain.auth_and_billing import AuthService  # noqa: E402
from app.domain.country_packs import CountryCode  # noqa: E402
from app.domain.crm_service import ContactRole, CrmService  # noqa: E402
from app.domain.interconnections_service import InterconnectionsService  # noqa: E402
from app.domain.notaries_service import (  # noqa: E402
    NotaryBookService,
    build_notary_transmission_eml,
    parse_dinum_notary_results,
    search_french_notary_offices,
)
from app.domain.portfolio_and_leads_service import (  # noqa: E402
    LeadKind,
    PortfolioAndLeadsService,
    TransactionStage,
)
from app.domain.rnic_copro_service import (  # noqa: E402
    RnicAccessService,
    is_rnic_free_for_role,
    normalize_rnic_row,
    redact_rnic_record_for_unpaid_buyer,
    search_rnic_coproprietes,
)


class _FakeHttpResp:
    def __init__(self, status_code: int, payload: dict) -> None:
        self.status_code = status_code
        self._payload = payload

    def json(self) -> dict:
        return self._payload


class _FakeDinumClient:
    def __init__(self, status_code: int = 200, payload: dict | None = None, raise_exc: Exception | None = None) -> None:
        self._status = status_code
        self._payload = payload or {}
        self._exc = raise_exc

    def get(self, url: str, params=None, timeout: float = 10.0):
        if self._exc:
            raise self._exc
        return _FakeHttpResp(self._status, self._payload)


class NotariesRnicAndInterconnectionsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.settings = load_settings()
        self.director = TenantContext(user_id="usr-dir-1", agency_id="agency-alpha", role=MontyRole.DIRECTOR)
        self.other_director = TenantContext(user_id="usr-dir-2", agency_id="agency-beta", role=MontyRole.DIRECTOR)
        self.agent = TenantContext(user_id="usr-ag-1", agency_id="agency-alpha", role=MontyRole.AGENT)
        self.buyer = TenantContext(user_id="usr-buy-1", agency_id="monty-public-clients", role=MontyRole.BUYER)
        self.seller = TenantContext(user_id="usr-sel-1", agency_id="monty-public-clients", role=MontyRole.SELLER)

    def test_dinum_notary_parsing_and_error_resilience(self) -> None:
        sample_payload = {
            "results": [
                {
                    "siren": "345678901",
                    "nom_complet": "SCP ALCAIX & ASSOCIES NOTAIRES",
                    "activite_principale": "69.10Z",
                    "matching_etablissements": [
                        {
                            "siret": "34567890100024",
                            "activite_principale": "69.10Z",
                            "adresse": "119 AVENUE MARECHAL DE SAXE",
                            "code_postal": "69003",
                            "libelle_commune": "LYON",
                            "latitude": "45.756",
                            "longitude": "4.847",
                        }
                    ],
                },
                {
                    "siren": "999999999",
                    "nom_complet": "BOULANGERIE DUPONT",
                    "activite_principale": "10.71C",
                    "matching_etablissements": [
                        {"siret": "99999999900011", "activite_principale": "10.71C", "code_postal": "69003"}
                    ],
                },
            ]
        }
        offices = parse_dinum_notary_results(sample_payload)
        self.assertEqual(len(offices), 1)
        self.assertEqual(offices[0].siret, "34567890100024")
        self.assertEqual(offices[0].department_code, "69")
        self.assertIn("notaires.fr", offices[0].csn_directory_url)

        res_live = search_french_notary_offices(
            query="Alcaix",
            postal_code="69003",
            http_client=_FakeDinumClient(200, sample_payload),
        )
        self.assertEqual(res_live["status"], "live")
        self.assertEqual(res_live["count"], 1)

        res_err = search_french_notary_offices(
            query="Lyon",
            http_client=_FakeDinumClient(503, {}),
        )
        self.assertEqual(res_err["status"], "api_error")
        self.assertEqual(res_err["offices"], [])

        res_net = search_french_notary_offices(
            query="Lyon",
            http_client=_FakeDinumClient(raise_exc=RuntimeError("DNS offline")),
        )
        self.assertEqual(res_net["status"], "network_unavailable")
        self.assertEqual(res_net["offices"], [])

    def test_notary_book_and_eml_crlf_sanitization_with_attachment(self) -> None:
        book = NotaryBookService()
        saved = book.save_notary(
            self.director,
            office_name="Étude Notariale Bellecour",
            notary_name="Me Claire Martin",
            email="claire.martin@notaires.fr",
            street_address="12 Place Bellecour",
            postal_code="69002",
            city="Lyon",
        )
        self.assertTrue(saved.is_official_notaire_domain)
        self.assertEqual(saved.department_code, "69")

        subj, eml = build_notary_transmission_eml(
            from_email="agence@monty.fr",
            recipient_email="claire.martin@notaires.fr",
            recipient_office="Étude Bellecour\r\nBcc: evil@attacker.tld",
            recipient_address="12 Place Bellecour, 69002 Lyon",
            dossier_ref="COMP-2026-09\r\nSubject: Hijacked",
            transmission_type="SIGNATURE_COMPROMIS",
            property_title="T3 Terrasse\r\nLyon",
            property_address="45 Cours Gambetta",
            seller_name="M. Durand",
            buyer_name="Mme Moreau",
            agreed_price_euro=415000,
            target_date="2026-12-18",
            checklist_items=["Titre de propriété", "Pré-État Daté L.721-2"],
            pre_etat_date_html="<html><body><h1>Pre-Etat Date</h1></body></html>",
        )
        self.assertNotIn("\r", subj)
        self.assertNotIn("\n", subj)
        self.assertIn("pre-etat-date-COMP-2026-09", eml)

        tx = book.create_transmission(
            self.director,
            notary_contact_id=saved.id,
            dossier_ref="COMP-2026-09",
            transmission_type="SIGNATURE_COMPROMIS",
            recipient_email=saved.email,
            recipient_office=saved.office_name,
            recipient_address="12 Place Bellecour",
            property_title="T3 Terrasse Lyon",
            agreed_price_euro=415000,
            send_now=True,
            settings=self.settings,
        )
        self.assertEqual(tx.delivery_status, "READY_EML_DOWNLOAD")
        self.assertEqual(len(book.list_transmissions(self.other_director)), 0)

    def test_rnic_tiered_pricing_and_redaction(self) -> None:
        self.assertTrue(is_rnic_free_for_role(MontyRole.DIRECTOR))
        self.assertTrue(is_rnic_free_for_role(MontyRole.AGENT))
        self.assertTrue(is_rnic_free_for_role(MontyRole.SELLER))
        self.assertFalse(is_rnic_free_for_role(MontyRole.BUYER))
        self.assertFalse(is_rnic_free_for_role(None))

        raw_row = {
            "numero_immatriculation": "AA0-123-456",
            "nom_usage_copropriete": "Résidence Les Tilleuls",
            "adresse_reference": "18 Rue Garibaldi",
            "code_postal": "69003",
            "commune": "LYON",
            "nombre_total_lots": "34",
            "nombre_lots_habitation": "22",
            "nombre_lots_stationnement": "12",
            "periode_construction": "DE_1994_A_2000",
            "type_syndic": "Professionnel",
            "syndic_en_exercice": "Foncia Lyon Centre",
            "copro_en_difficulte": "non",
        }
        rec = normalize_rnic_row(raw_row)
        self.assertEqual(rec.nombre_total_lots, 34)
        self.assertFalse(rec.locked)

        redacted = redact_rnic_record_for_unpaid_buyer(rec)
        self.assertTrue(redacted["locked"])
        self.assertEqual(redacted["syndic_en_exercice"], "Masqué")
        self.assertIsNone(redacted["nombre_total_lots"])
        self.assertEqual(redacted["lot_count_band"], "Copropriété moyenne (16–50 lots)")

        class _RnicHttpClient:
            def get(self, url: str, params=None, timeout: float = 10.0):
                if "datasets/registre-national" in url:
                    return _FakeHttpResp(
                        200,
                        {"resources": [{"id": "rnic-res-2026", "format": "csv", "title": "rnic_copro.csv", "filesize": 999999}]},
                    )
                return _FakeHttpResp(200, {"data": [raw_row]})

        paid_preview = search_rnic_coproprietes(
            settings=self.settings,
            postal_code="69003",
            has_full_access=False,
            http_client=_RnicHttpClient(),
        )
        self.assertEqual(paid_preview["access_tier"], "PAID_REQUIRED")
        self.assertTrue(paid_preview["records"][0]["locked"])

        pro_full = search_rnic_coproprietes(
            settings=self.settings,
            postal_code="69003",
            has_full_access=True,
            http_client=_RnicHttpClient(),
        )
        self.assertEqual(pro_full["access_tier"], "FULL")
        self.assertFalse(pro_full["records"][0]["locked"])
        self.assertEqual(pro_full["records"][0]["syndic_en_exercice"], "Foncia Lyon Centre")

        rnic_passes = RnicAccessService()
        p1 = rnic_passes.issue_active_pass(
            user_id=self.buyer.user_id,
            buyer_email="acheteur@exemple.fr",
            stripe_session_id="cs_test_rnic_1",
            pass_token="rnic_pass_tok_1",
        )
        p1_dup = rnic_passes.issue_active_pass(
            user_id=self.buyer.user_id,
            buyer_email="acheteur@exemple.fr",
            stripe_session_id="cs_test_rnic_1",
            pass_token="rnic_pass_tok_1",
        )
        self.assertEqual(p1.id, p1_dup.id)
        self.assertTrue(rnic_passes.has_active_pass(pass_token="rnic_pass_tok_1"))
        self.assertTrue(rnic_passes.has_active_pass(user_id=self.buyer.user_id))

    def test_interconnections_property_match_compromis_and_visit_request(self) -> None:
        crm = CrmService()
        inter = InterconnectionsService()
        portfolio = PortfolioAndLeadsService(crm_service=crm, interconnections=inter)

        crm.create_contact(
            self.director,
            full_name="Sophie Acquéreur",
            email="sophie@exemple.fr",
            phone="0600000001",
            role=ContactRole.BUYER,
            country_code=CountryCode.FR,
            city="Lyon",
            budget_euro=450000,
        )
        prop = portfolio.create_property(
            self.director,
            title="T3 Balcon Lyon 6",
            sector="Lyon 6e",
            property_type="Appartement",
            price=410000,
            surface=76,
            bedrooms=2,
            dpe="B",
            mandate="EXCLUSIF",
        )
        tx = portfolio.create_transaction(
            self.director,
            property_id=prop.id,
            buyer_name="Sophie Acquéreur",
            offer_euro=405000,
            notary_target_date="2026-12-30",
        )
        portfolio.update_transaction_stage(self.director, tx_id=tx.id, stage=TransactionStage.COMPROMIS)

        lead = portfolio.submit_public_lead(
            agency_id=self.director.agency_id,
            lead_kind=LeadKind.VISIT_REQUEST,
            country_code=CountryCode.FR,
            full_name="Lucas Visiteur",
            email="lucas@exemple.fr",
            phone="0611223344",
            city="Lyon",
            budget_euro=410000,
            property_title="T3 Balcon Lyon 6",
            rgpd_consent_granted=True,
        )
        self.assertEqual(lead.lead_kind, LeadKind.VISIT_REQUEST)
        self.assertIsNotNone(lead.crm_contact_id)

        notifs = inter.list_notifications(self.director)
        event_types = {n.event_type for n in notifs}
        self.assertIn("BUYER_MATCH_FOUND", event_types)
        self.assertIn("TRANSACTION_COMPROMIS", event_types)
        self.assertIn("PUBLIC_LEAD_RECEIVED", event_types)

        auto_tasks = inter.list_auto_tasks(self.director)
        self.assertGreaterEqual(len(auto_tasks), 2)

        with self.assertRaises(PermissionError):
            inter.mark_notification_read(self.other_director, notifs[0].id)

        read_n = inter.mark_notification_read(self.director, notifs[0].id)
        self.assertTrue(read_n.is_read)

        # Feature flags RBAC
        with self.assertRaises(PermissionError):
            inter.update_feature_flags(
                self.agent,
                auto_matching_enabled=False,
                ai_copywriting_enabled=True,
                rnic_free_for_sellers=True,
                public_appointment_booking=True,
                notary_auto_task_on_compromis=True,
            )

        updated_flags = inter.update_feature_flags(
            self.director,
            auto_matching_enabled=False,
            ai_copywriting_enabled=True,
            rnic_free_for_sellers=True,
            public_appointment_booking=True,
            notary_auto_task_on_compromis=True,
        )
        self.assertFalse(updated_flags.auto_matching_enabled)

        # Favoris & Coffre documentaire particulier
        fav = inter.upsert_favorite(self.buyer, listing_id="lst-1", listing_snapshot={"title": "Appartement Lyon"})
        self.assertEqual(fav.listing_id, "lst-1")
        self.assertEqual(len(inter.list_favorites(self.buyer)), 1)
        self.assertTrue(inter.remove_favorite(self.buyer, listing_id="lst-1"))

        doc = inter.add_client_document(
            self.buyer,
            title="Accord de prêt",
            doc_category="FINANCEMENT",
            file_name="accord.pdf",
            file_size_bytes=42000,
        )
        self.assertEqual(doc.title, "Accord de prêt")

    def test_client_registration_and_router_wiring(self) -> None:
        auth = AuthService()
        buyer_sess = auth.register_client_account(
            email="acheteur.portal@exemple.fr",
            password="Password123!",
            display_name="Camille Acheteur",
            role=MontyRole.BUYER,
            country_code=CountryCode.FR,
            settings=self.settings,
        )
        self.assertEqual(buyer_sess.role, MontyRole.BUYER)
        self.assertEqual(buyer_sess.agency_id, "agency-france-reseau")

        seller_sess = auth.register_client_account(
            email="vendeur.rnic@exemple.fr",
            password="Password123!",
            display_name="Marc Vendeur",
            role=MontyRole.SELLER,
            country_code=CountryCode.FR,
            settings=self.settings,
        )
        self.assertEqual(seller_sess.role, MontyRole.SELLER)
        self.assertTrue(is_rnic_free_for_role(seller_sess.role))
        self.assertFalse(is_rnic_free_for_role(buyer_sess.role))

        with self.assertRaises(ValueError):
            auth.register_client_account(
                email="hack@exemple.fr",
                password="Password123!",
                display_name="Hack",
                role=MontyRole.DIRECTOR,
                country_code=CountryCode.FR,
                settings=self.settings,
            )

        router_src = (BACKEND_DIR / "app" / "api" / "v1" / "router.py").read_text(encoding="utf-8")
        self.assertIn("/auth/register-client", router_src)
        self.assertIn("notaries_rnic_router", router_src)
        self.assertIn("interconnect_router", router_src)

        billing_src = (BACKEND_DIR / "app" / "api" / "v1" / "billing_router.py").read_text(encoding="utf-8")
        self.assertIn("/rnic-pass", billing_src)
        self.assertIn("RNIC_PARTICULIER", billing_src)
        self.assertIn("issue_active_rnic_pass", billing_src)

        notaries_src = (BACKEND_DIR / "app" / "api" / "v1" / "notaries_rnic_router.py").read_text(encoding="utf-8")
        self.assertIn("/notaries/search", notaries_src)
        self.assertIn("/notaries/transmissions", notaries_src)
        self.assertIn("/rnic/search", notaries_src)

        interconnect_src = (BACKEND_DIR / "app" / "api" / "v1" / "interconnect_router.py").read_text(encoding="utf-8")
        self.assertIn("/notifications", interconnect_src)
        self.assertIn("/agenda", interconnect_src)
        self.assertIn("/portal/favorites", interconnect_src)
        self.assertIn("/portal/documents", interconnect_src)
        self.assertIn("/admin/feature-flags", interconnect_src)

    def test_migration_0009_enforces_rls_and_visit_request_constraint(self) -> None:
        sql_path = BACKEND_DIR / "alembic" / "versions" / "0009_notaries_rnic_interconnections.sql"
        sql = sql_path.read_text(encoding="utf-8")
        for table in [
            "agency_notary_contacts",
            "notary_transmissions",
            "agency_notifications",
            "agency_feature_flags",
            "client_portal_favorites",
            "client_documents",
        ]:
            self.assertIn(f"ALTER TABLE {table} ENABLE ROW LEVEL SECURITY", sql)
            self.assertIn(f"ALTER TABLE {table} FORCE ROW LEVEL SECURITY", sql)
        self.assertIn("'VISIT_REQUEST'", sql)
        self.assertIn("monty_auth_register_client", sql)


if __name__ == "__main__":
    unittest.main()
