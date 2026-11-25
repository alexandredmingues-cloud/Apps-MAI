from __future__ import annotations

from datetime import date
import hashlib
import hmac
import json
from pathlib import Path
import sys
import unittest

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.config import load_settings  # noqa: E402
from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.domain.ai_agents_service import AGENTS_CATALOG, MontyAgentsGovernanceService  # noqa: E402
from app.domain.pre_etat_date import (  # noqa: E402
    CoproLotInput,
    PRE_ETAT_DOC_VERSION,
    PreEtatDateInput,
    compute_pre_etat_date,
    render_pre_etat_date_html,
    serialize_pre_etat_result,
)


class PreEtatDateL7212Tests(unittest.TestCase):
    def test_ten_anonymized_dossiers_and_blocking_rules(self) -> None:
        dossiers = [
            # 1: Appartement + Cave + Parking Lyon
            PreEtatDateInput(
                dossier_ref="DOS-2026-001",
                seller_name="Vendeur A.",
                property_address="14 Rue Garibaldi, 69006 Lyon",
                syndic_name="Syndic Rhône & Saône",
                total_tantiemes_copro=10000,
                lots=[
                    CoproLotInput("12", "Appartement T3", 410),
                    CoproLotInput("45", "Cave", 12),
                    CoproLotInput("88", "Parking sous-sol", 28),
                ],
                annual_budget_copro_euro=64000.0,
                charges_n_minus_1_lot_euro=2840.0,
                charges_n_minus_2_lot_euro=2710.0,
                works_fund_alur_balance_euro=940.0,
                current_quarter_called_euro=720.0,
            ),
            # 2: Studio étudiant Montpellier avec impayés vendeur
            PreEtatDateInput(
                dossier_ref="DOS-2026-002",
                seller_name="Vendeur B.",
                property_address="8 Place de la Comédie, 34000 Montpellier",
                syndic_name="Syndic Languedoc",
                total_tantiemes_copro=1000,
                lots=[CoproLotInput("4", "Studio", 48)],
                annual_budget_copro_euro=28000.0,
                charges_n_minus_1_lot_euro=1310.0,
                charges_n_minus_2_lot_euro=1260.0,
                seller_unpaid_charges_euro=390.0,
                works_fund_alur_balance_euro=215.0,
            ),
            # 3: Grand T4 familial Bordeaux + 2 parkings + travaux votés non appelés
            PreEtatDateInput(
                dossier_ref="DOS-2026-003",
                seller_name="Indivision C. & D.",
                property_address="22 Cours de l'Intendance, 33000 Bordeaux",
                syndic_name="Cabinet Aquitaine",
                total_tantiemes_copro=100000,
                lots=[
                    CoproLotInput("101", "Appartement T4", 3150),
                    CoproLotInput("204", "Parking", 120),
                    CoproLotInput("205", "Parking", 120),
                ],
                annual_budget_copro_euro=145000.0,
                charges_n_minus_1_lot_euro=4915.0,
                charges_n_minus_2_lot_euro=4680.0,
                non_budget_works_voted_euro=1850.0,
                works_fund_alur_balance_euro=1620.0,
                sale_date=date(2026, 5, 1),
            ),
            # 4: Duplex Toulouse caractères spéciaux (échappement HTML)
            PreEtatDateInput(
                dossier_ref="DOS-2026-004",
                seller_name="SCI « Soleil & Garonne » <Test>",
                property_address="3 Allées Jean Jaurès, 31000 Toulouse",
                syndic_name="Syndic Midi",
                total_tantiemes_copro=5000,
                lots=[CoproLotInput("18", "Duplex", 380)],
                annual_budget_copro_euro=42000.0,
                charges_n_minus_1_lot_euro=3192.0,
                charges_n_minus_2_lot_euro=3050.0,
                works_fund_alur_balance_euro=510.0,
            ),
            # 5: Copro 2 lots Nantes (petite copro)
            PreEtatDateInput(
                dossier_ref="DOS-2026-005",
                seller_name="Vendeur E.",
                property_address="19 Rue Crébillon, 44000 Nantes",
                syndic_name="Syndic Bénévole",
                total_tantiemes_copro=1000,
                lots=[CoproLotInput("1", "Maison de ville en copro", 540)],
                annual_budget_copro_euro=3200.0,
                charges_n_minus_1_lot_euro=1728.0,
                charges_n_minus_2_lot_euro=1640.0,
                works_fund_alur_balance_euro=420.0,
            ),
            # 6: T2 Paris 15e + cave
            PreEtatDateInput(
                dossier_ref="DOS-2026-006",
                seller_name="Vendeur F.",
                property_address="78 Rue de la Convention, 75015 Paris",
                syndic_name="Cabinet Seine",
                total_tantiemes_copro=10000,
                lots=[CoproLotInput("32", "Appartement T2", 210), CoproLotInput("91", "Cave", 9)],
                annual_budget_copro_euro=112000.0,
                charges_n_minus_1_lot_euro=2452.8,
                charges_n_minus_2_lot_euro=2310.0,
                works_fund_alur_balance_euro=880.0,
            ),
            # 7: Local commercial en copro Lille
            PreEtatDateInput(
                dossier_ref="DOS-2026-007",
                seller_name="SAS Nord Immo",
                property_address="5 Rue Nationale, 59000 Lille",
                syndic_name="Syndic Flandres",
                total_tantiemes_copro=10000,
                lots=[CoproLotInput("RDC-1", "Local commercial", 690)],
                annual_budget_copro_euro=58000.0,
                charges_n_minus_1_lot_euro=4002.0,
                charges_n_minus_2_lot_euro=3890.0,
                works_fund_alur_balance_euro=1150.0,
            ),
            # 8: Lot avec champ douteux (< 0.80) NON validé => export doit être bloqué
            PreEtatDateInput(
                dossier_ref="DOS-2026-008",
                seller_name="Vendeur G.",
                property_address="11 Avenue Jean Médecin, 06000 Nice",
                syndic_name="Syndic Azur",
                total_tantiemes_copro=10000,
                lots=[CoproLotInput("19", "Appartement T3", 330, confidence=0.68, validated=False)],
                annual_budget_copro_euro=72000.0,
                charges_n_minus_1_lot_euro=2376.0,
                charges_n_minus_2_lot_euro=2280.0,
                confidence_map={"annual_budget_copro_euro": 0.72},
            ),
            # 9: Dossier avec pièce obligatoire L.721-2 manquante => export bloqué
            PreEtatDateInput(
                dossier_ref="DOS-2026-009",
                seller_name="Vendeur H.",
                property_address="40 Avenue Foch, 67000 Strasbourg",
                syndic_name="Syndic Alsace",
                total_tantiemes_copro=10000,
                lots=[CoproLotInput("7", "Appartement", 295)],
                annual_budget_copro_euro=52000.0,
                charges_n_minus_1_lot_euro=1534.0,
                charges_n_minus_2_lot_euro=1490.0,
                has_pv_ag_3_years=False,
            ),
            # 10: Vente début de trimestre (prorata quasi total remboursé par acquéreur)
            PreEtatDateInput(
                dossier_ref="DOS-2026-010",
                seller_name="Vendeur I.",
                property_address="12 Cours Mirabeau, 13100 Aix-en-Provence",
                syndic_name="Syndic Provence",
                total_tantiemes_copro=10000,
                lots=[CoproLotInput("22", "Appartement T3", 450)],
                annual_budget_copro_euro=68000.0,
                charges_n_minus_1_lot_euro=3060.0,
                charges_n_minus_2_lot_euro=2940.0,
                current_quarter_called_euro=765.0,
                sale_date=date(2026, 4, 4),
            ),
        ]
        self.assertEqual(len(dossiers), 10)

        for idx, dossier in enumerate(dossiers, start=1):
            res = compute_pre_etat_date(dossier)
            serialized = serialize_pre_etat_result(res)
            self.assertEqual(serialized["document_version"], PRE_ETAT_DOC_VERSION)
            self.assertIn("2019", res.savings_source_note)
            self.assertEqual(len(res.calculation_steps), 6)
            self.assertEqual(len(res.l721_2_checklist), 8)

            if idx in (8, 9):
                self.assertFalse(res.export_allowed)
                self.assertGreater(len(res.blocking_reasons), 0)
                with self.assertRaises(ValueError):
                    render_pre_etat_date_html(dossier, res)
            else:
                self.assertTrue(res.export_allowed, f"Dossier {dossier.dossier_ref} bloqué à tort : {res.blocking_reasons}")
                html_doc = render_pre_etat_date_html(dossier, res)
                self.assertIn(PRE_ETAT_DOC_VERSION, html_doc)
                self.assertIn("Art. L. 721-2 CCH", html_doc)
                if idx == 4:
                    self.assertNotIn("<Test>", html_doc)
                    self.assertIn("&lt;Test&gt;", html_doc)

        # Validation humaine des champs douteux débloque l'export sur le dossier 8
        dossier_8_validated = PreEtatDateInput(
            dossier_ref="DOS-2026-008",
            seller_name="Vendeur G.",
            property_address="11 Avenue Jean Médecin, 06000 Nice",
            syndic_name="Syndic Azur",
            total_tantiemes_copro=10000,
            lots=[CoproLotInput("19", "Appartement T3", 330, confidence=0.68, validated=True)],
            annual_budget_copro_euro=72000.0,
            charges_n_minus_1_lot_euro=2376.0,
            charges_n_minus_2_lot_euro=2280.0,
            confidence_map={"annual_budget_copro_euro": 0.72},
            validated_fields={"annual_budget_copro_euro"},
        )
        res_8_ok = compute_pre_etat_date(dossier_8_validated)
        self.assertTrue(res_8_ok.export_allowed)
        self.assertIn(PRE_ETAT_DOC_VERSION, render_pre_etat_date_html(dossier_8_validated, res_8_ok))


class AgentsGovernanceScoutWhatsAppTests(unittest.TestCase):
    def test_eleven_agents_non_regression_cache_pii_budget_and_kill_switch(self) -> None:
        self.assertEqual(len(AGENTS_CATALOG), 11)
        svc = MontyAgentsGovernanceService()
        settings = load_settings()
        director = TenantContext(user_id="usr-dir", agency_id="ag-test", role=MontyRole.DIRECTOR)
        agent_user = TenantContext(user_id="usr-ag", agency_id="ag-test", role=MontyRole.AGENT)

        # Non-régression sur les 11 agents autonomes
        for agent_def in AGENTS_CATALOG:
            log = svc.run_agent(
                agent_user,
                agent_id=agent_def.agent_id,
                prompt_text=f"Dossier client Jean@example.com 0612345678 pour {agent_def.name}",
                settings=settings,
            )
            self.assertEqual(log.agent_id, agent_def.agent_id)
            self.assertFalse(log.cache_hit)
            self.assertNotIn("Jean@example.com", log.sanitized_input)
            self.assertNotIn("0612345678", log.sanitized_input)

        # 2e appel identique => cache_hit=True et cost_euro=0.0
        cached_log = svc.run_agent(
            agent_user,
            agent_id="dvf_analyst",
            prompt_text="Dossier client Jean@example.com 0612345678 pour Analyste DVF & Score Monty",
            settings=settings,
        )
        self.assertTrue(cached_log.cache_hit)
        self.assertEqual(cached_log.cost_euro, 0.0)

        # Seul un Directeur/Admin peut activer le kill-switch
        with self.assertRaises(PermissionError):
            svc.set_kill_switch(agent_user, enabled=True, reason="Test non autorisé")

        ks = svc.set_kill_switch(director, enabled=True, reason="Maintenance sécurité")
        self.assertTrue(ks["kill_switch_enabled"])

        with self.assertRaises(RuntimeError):
            svc.run_agent(agent_user, agent_id="hcsf_coach", prompt_text="Calcul HCSF client", settings=settings)

        svc.set_kill_switch(director, enabled=False)
        resumed = svc.run_agent(agent_user, agent_id="hcsf_coach", prompt_text="Calcul HCSF client", settings=settings)
        self.assertEqual(resumed.agent_id, "hcsf_coach")

    def test_monty_scout_one_click_and_whatsapp_hmac_opt_out(self) -> None:
        svc = MontyAgentsGovernanceService()
        agent_user = TenantContext(user_id="usr-ag", agency_id="ag-scout", role=MontyRole.AGENT)

        prop = svc.create_scout_proposal(
            agent_user,
            target_contact_name="Sophie Martin",
            channel="WHATSAPP",
            legal_basis="MESURES_PRECONTRACTUELLES",
            message_draft="Bonjour Sophie, un appartement T3 correspond à vos critères à Montpellier.",
        )
        self.assertEqual(prop.status, "A_VALIDER")
        self.assertIn("STOP", prop.message_draft)

        approved = svc.review_scout_proposal(agent_user, proposal_id=prop.id, approve=True)
        self.assertEqual(approved.status, "VALIDEE")
        self.assertEqual(approved.reviewed_by_user_id, "usr-ag")

        # Vérification signature WhatsApp HMAC-SHA256 + désinscription STOP
        app_secret = "monty-whatsapp-secret-test"
        event = {
            "entry": [
                {
                    "changes": [
                        {
                            "value": {
                                "messages": [
                                    {"from": "33612345678", "text": {"body": "STOP"}},
                                ]
                            }
                        }
                    ]
                }
            ]
        }
        payload_bytes = json.dumps(event).encode("utf-8")
        good_sig = "sha256=" + hmac.new(app_secret.encode("utf-8"), payload_bytes, hashlib.sha256).hexdigest()

        with self.assertRaises(ValueError):
            svc.handle_whatsapp_payload(
                payload_bytes=payload_bytes,
                signature_header="sha256=deadbeef",
                app_secret=app_secret,
                event=event,
            )

        res = svc.handle_whatsapp_payload(
            payload_bytes=payload_bytes,
            signature_header=good_sig,
            app_secret=app_secret,
            event=event,
        )
        self.assertEqual(res["messages_processed"], 1)
        self.assertEqual(res["opt_outs_recorded"], 1)
        self.assertTrue(svc.is_phone_opted_out("33612345678"))

        legal_router_src = (BACKEND_DIR / "app" / "api" / "v1" / "legal_agents_router.py").read_text(encoding="utf-8")
        self.assertIn("/pre-etat-date/compute", legal_router_src)
        self.assertIn("/pre-etat-date/export", legal_router_src)
        self.assertIn("/agents/kill-switch", legal_router_src)
        self.assertIn("/webhooks/whatsapp", legal_router_src)

        main_router_src = (BACKEND_DIR / "app" / "api" / "v1" / "router.py").read_text(encoding="utf-8")
        self.assertIn("legal_agents_router", main_router_src)


if __name__ == "__main__":
    unittest.main()
