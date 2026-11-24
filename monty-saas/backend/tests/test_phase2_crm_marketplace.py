from __future__ import annotations

import sys
import unittest
from datetime import date, timedelta
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.domain.country_packs import CountryCode  # noqa: E402
from app.domain.crm_service import (  # noqa: E402
    ContactRole,
    CrmService,
    ExchangeChannel,
)
from app.domain.csv_import_export import (  # noqa: E402
    export_contacts_to_csv,
    import_contacts_from_csv,
)
from app.domain.marketplace_service import (  # noqa: E402
    MarketplaceService,
    PublisherType,
    generate_listing_copy,
)
from app.domain.real_estate_engines import SearchPerimeterMode, TransportMode  # noqa: E402


class Phase2CrmAndRgpdTests(unittest.TestCase):
    def setUp(self) -> None:
        self.crm = CrmService()
        self.director = TenantContext(
            user_id="usr-director",
            agency_id="agency-france-reseau",
            role=MontyRole.DIRECTOR,
        )
        self.agent = TenantContext(
            user_id="usr-agent",
            agency_id="agency-france-reseau",
            role=MontyRole.AGENT,
        )
        self.other_agent = TenantContext(
            user_id="usr-agent-2",
            agency_id="agency-france-reseau",
            role=MontyRole.AGENT,
        )
        self.other_agency = TenantContext(
            user_id="usr-ext",
            agency_id="agency-concurrent",
            role=MontyRole.DIRECTOR,
        )
        self.buyer = TenantContext(
            user_id="usr-buyer",
            agency_id="agency-france-reseau",
            role=MontyRole.BUYER,
        )

    def test_cross_agency_isolation(self) -> None:
        contacts = self.crm.list_contacts(self.other_agency)
        self.assertEqual(contacts, [])
        with self.assertRaises(PermissionError):
            self.crm.list_private_notes(self.other_agency, contact_id="cnt-fr-1")

    def test_private_notes_strict_isolation_and_rgpd_export_exclusion(self) -> None:
        note = self.crm.add_private_note(
            self.agent,
            contact_id="cnt-fr-1",
            content="Note confidentielle : client prêt à monter de 10 k€ si cuisine refaite.",
        )
        self.assertIn("confidentielle", note.content)

        # Même agence mais autre conseiller -> invisible
        self.assertEqual(self.crm.list_private_notes(self.other_agent, contact_id="cnt-fr-1"), [])

        # Auteur & Directeur -> visible
        self.assertEqual(len(self.crm.list_private_notes(self.agent, contact_id="cnt-fr-1")), 1)
        self.assertEqual(len(self.crm.list_private_notes(self.director, contact_id="cnt-fr-1")), 1)

        # Acquéreur -> bloqué RBAC
        with self.assertRaises(PermissionError):
            self.crm.list_private_notes(self.buyer, contact_id="cnt-fr-1")

        # Export portabilité RGPD (Art. 15/20) : NE DOIT JAMAIS contenir les notes privées
        self.crm.log_exchange(
            self.agent,
            contact_id="cnt-fr-1",
            channel=ExchangeChannel.VISIT,
            summary="Visite T3 Lyon Croix-Rousse.",
        )
        rgpd_dump = self.crm.export_contact_rgpd(self.director, contact_id="cnt-fr-1")
        serialized = str(rgpd_dump)
        self.assertNotIn("Note confidentielle", serialized)
        self.assertNotIn("monter de 10", serialized)
        self.assertEqual(len(rgpd_dump["exchanges"]), 1)

    def test_automatic_followups_and_rgpd_purge(self) -> None:
        expired_contact = self.crm.create_contact(
            self.director,
            full_name="Ancien Prospect Expiré",
            email="old@exemple.fr",
            role=ContactRole.PROSPECT,
            country_code=CountryCode.FR,
            city="Nantes",
            retention_until=(date.today() - timedelta(days=5)).isoformat(),
        )
        self.crm.add_private_note(
            self.director,
            contact_id=expired_contact.id,
            content="Note à purger lors de l'anonymisation",
        )

        tasks = self.crm.generate_automatic_followups(self.director)
        titles = " | ".join(t.title for t in tasks)
        self.assertIn("Débriefing visite J+4", titles)
        self.assertIn("Relance offre / décision J+8", titles)
        self.assertIn("Pièces réglementaires", titles)
        self.assertIn("Purge RGPD requise", titles)

        purged = self.crm.execute_rgpd_purge(self.director)
        self.assertEqual(purged, 1)
        reloaded = [c for c in self.crm.list_contacts(self.director) if c.id == expired_contact.id][0]
        self.assertTrue(reloaded.is_anonymized)
        self.assertEqual(reloaded.email, "")
        self.assertEqual(self.crm.list_private_notes(self.director, contact_id=expired_contact.id), [])


class Phase2CsvSecurityTests(unittest.TestCase):
    def setUp(self) -> None:
        self.crm = CrmService()
        self.director = TenantContext(
            user_id="usr-director",
            agency_id="agency-france-reseau",
            role=MontyRole.DIRECTOR,
        )

    def test_csv_import_deduplication_and_export_formula_injection_protection(self) -> None:
        raw_csv = (
            "nom;email;telephone;ville;budget;role;pays\n"
            "=CMD|' /C calc'!A0;malicious@exemple.fr;0601020304;Paris;550000;ACQUEREUR;FR\n"
            "+SUM(1,2);plus@exemple.fr;0601020305;Lyon;320000;VENDEUR;FR\n"
            "Camille Laurent;camille.laurent@exemple.fr;0642189011;Lyon;420000;BUYER;FR\n"
            ";sansnom@exemple.fr;0600000000;Nice;200000;LEAD;FR\n"
            "Diogo Silva;diogo@exemplo.pt;+351910000000;Porto;290000;BUYER;PT\n"
        )
        summary = import_contacts_from_csv(self.director, self.crm, csv_text=raw_csv)
        self.assertEqual(summary.imported_count, 3)
        self.assertEqual(summary.skipped_duplicates, 1)
        self.assertEqual(summary.rejected_rows, 1)

        exported_csv = export_contacts_to_csv(self.director, self.crm)
        self.assertTrue(exported_csv.startswith("\ufeff"))
        # Neutralisation stricte des formules Excel dangereuses
        self.assertIn("'=CMD", exported_csv)
        self.assertIn("'+SUM(1,2)", exported_csv)


class Phase2MarketplaceAndMobilityTests(unittest.TestCase):
    def setUp(self) -> None:
        self.market = MarketplaceService()

    def test_six_transport_modes_and_country_isolation(self) -> None:
        for mode in (
            TransportMode.VOITURE,
            TransportMode.TRAM,
            TransportMode.BUS,
            TransportMode.TRAIN,
            TransportMode.VELO,
            TransportMode.PIED,
        ):
            hits = self.market.search_listings(
                country_code=CountryCode.FR,
                origin_address="Lyon",
                search_mode=SearchPerimeterMode.TRAVEL_TIME,
                transport_mode=mode,
                max_travel_minutes=60,
                only_within_perimeter=True,
            )
            self.assertGreaterEqual(len(hits), 1, f"Mode {mode} should match Lyon listings")
            for hit in hits:
                self.assertEqual(hit.listing.country_code, CountryCode.FR)

        pt_hits = self.market.search_listings(
            country_code=CountryCode.PT,
            origin_address="Lisboa",
            search_mode=SearchPerimeterMode.RADIUS_KM,
            max_radius_km=400,
        )
        self.assertEqual(len(pt_hits), 2)
        self.assertTrue(all(h.listing.country_code == CountryCode.PT for h in pt_hits))

    def test_seller_publish_listing_and_pii_redacted_copywriting(self) -> None:
        copy = generate_listing_copy(
            country_code=CountryCode.FR,
            property_kind="Appartement",
            city="Nantes",
            sector_label="Procé / Monselet — contact vendeur jean@mail.fr",
            surface_m2=68,
            rooms=3,
            price_euro=289000,
            dpe="B",
            condition_label="Bon état",
            highlights=["Balcon Sud", "Tram 4 min"],
            photo_count=6,
            estimated_sale_days=42,
        )
        self.assertNotIn("jean@mail.fr", copy["suggested_description"])
        self.assertIn("[email-masqué-rgpd]", copy["suggested_description"])
        self.assertIn("~42 jours", copy["suggested_description"])

        created = self.market.publish_listing(
            country_code=CountryCode.FR,
            publisher_type=PublisherType.PARTICULIER,
            publisher_label="Propriétaire Nantais",
            title="Appartement T3 lumineux parc de Procé 06 12 34 56 78",
            property_kind="Appartement",
            city="Nantes",
            sector_label="Procé",
            price_euro=289000,
            surface_m2=68,
            rooms=3,
            dpe="B",
            condition_label="Bon état",
            highlights=["Balcon Sud", "Parking"],
            description="Bel appartement traversant sans vis-à-vis proche tramway.",
            photo_count=6,
        )
        self.assertNotIn("06 12 34 56 78", created.title)
        self.assertEqual(created.sale_timeline.status, "READY")
        self.assertGreater(created.sale_timeline.likely_days, 0)


if __name__ == "__main__":
    unittest.main()
