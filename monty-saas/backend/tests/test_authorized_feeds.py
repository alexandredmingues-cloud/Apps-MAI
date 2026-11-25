from __future__ import annotations

from pathlib import Path
import sys
import unittest

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.domain.authorized_feeds_service import (  # noqa: E402
    AuthorizedListingFeedsService,
    FeedFormat,
    check_robots_txt_allowed,
)
from app.domain.country_packs import CountryCode  # noqa: E402
from app.domain.marketplace_service import MarketplaceService, PublisherType  # noqa: E402


class AuthorizedPartnerFeedsTests(unittest.TestCase):
    def test_ssrf_and_agreement_and_robots_txt_guards(self) -> None:
        market = MarketplaceService()
        feeds = AuthorizedListingFeedsService(market)
        director = TenantContext(user_id="usr-dir", agency_id="ag-feed", role=MontyRole.DIRECTOR)

        # Refus sans accord explicite de diffusion
        with self.assertRaises(ValueError):
            feeds.register_feed(
                director,
                name="Flux sans accord",
                country_code=CountryCode.FR,
                publisher_type=PublisherType.AGENCE,
                feed_format=FeedFormat.XML,
                feed_url="https://partner.example.com/feed.xml",
                agreement_confirmed=False,
            )

        # Blocage SSRF URLs privées / metadata
        for bad_url in ("http://127.0.0.1:8080/feed.xml", "http://169.254.169.254/latest/meta-data"):
            with self.assertRaises(ValueError):
                feeds.register_feed(
                    director,
                    name="Flux SSRF",
                    country_code=CountryCode.FR,
                    publisher_type=PublisherType.AGENCE,
                    feed_format=FeedFormat.XML,
                    feed_url=bad_url,
                    agreement_confirmed=True,
                )

        # Respect strict de robots.txt
        allowed = check_robots_txt_allowed(
            "https://partner.example.com/feeds/biens.xml",
            robots_fetcher=lambda _u: "User-agent: *\nDisallow: /feeds/\n",
        )
        self.assertFalse(allowed)

        feed = feeds.register_feed(
            director,
            name="Portail Partenaire Bloqué Robots",
            country_code=CountryCode.FR,
            publisher_type=PublisherType.AGENCE,
            feed_format=FeedFormat.XML,
            feed_url="https://partner.example.com/feeds/biens.xml",
            agreement_confirmed=True,
        )
        blocked_res = feeds.sync_feed(
            director,
            feed_id=feed.id,
            robots_fetcher=lambda _u: "User-agent: *\nDisallow: /\n",
        )
        self.assertEqual(blocked_res.status, "ROBOTS_BLOCKED")
        self.assertEqual(blocked_res.accepted_count, 0)

    def test_xml_and_json_partner_ingestion_dedup_and_pii_redaction(self) -> None:
        market = MarketplaceService()
        feeds = AuthorizedListingFeedsService(market)
        director = TenantContext(user_id="usr-dir", agency_id="ag-sync", role=MontyRole.DIRECTOR)

        xml_feed = feeds.register_feed(
            director,
            name="Passerelle Apimo Lyon",
            country_code=CountryCode.FR,
            publisher_type=PublisherType.AGENCE,
            feed_format=FeedFormat.XML,
            feed_url="https://syndication.apimo-partner.example/export.xml",
            agreement_confirmed=True,
            auto_publish_verified_agency=True,
        )
        sample_xml = """<?xml version="1.0" encoding="utf-8"?>
        <biens>
          <bien>
            <id>REF-101</id>
            <titre>Appartement T3 Haussmannien Lyon 6e</titre>
            <type_bien>Appartement</type_bien>
            <ville>Lyon</ville>
            <quartier>Foch</quartier>
            <prix>485000</prix>
            <surface_m2>86</surface_m2>
            <pieces>3</pieces>
            <dpe>C</dpe>
            <description>Bel appartement lumineux. Contact direct vendeur 0612345678 ou jean@example.com pour visiter.</description>
            <url>https://syndication.apimo-partner.example/biens/101</url>
          </bien>
          <bien>
            <id>REF-INVALID</id>
            <titre>Trop court</titre>
            <prix>500</prix>
            <surface_m2>3</surface_m2>
          </bien>
        </biens>"""

        res1 = feeds.sync_feed(
            director,
            feed_id=xml_feed.id,
            fetcher=lambda _u: sample_xml,
            robots_fetcher=lambda _u: "User-agent: *\nAllow: /\n",
        )
        self.assertEqual(res1.status, "SYNCED")
        self.assertEqual(res1.accepted_count, 1)
        self.assertEqual(res1.rejected_count, 1)

        # Le numéro et l'e-mail ont été masqués avant mise en catalogue
        hits = market.search_listings(country_code=CountryCode.FR, origin_address="Lyon", only_within_perimeter=False)
        ingested = [h.listing for h in hits if h.listing.id == res1.created_listing_ids[0]][0]
        self.assertNotIn("0612345678", ingested.description)
        self.assertNotIn("jean@example.com", ingested.description)
        self.assertIn("Passerelle Apimo Lyon", ingested.description)

        # Re-synchronisation => déduplication exacte
        res2 = feeds.sync_feed(
            director,
            feed_id=xml_feed.id,
            inline_payload=sample_xml,
        )
        self.assertEqual(res2.accepted_count, 0)
        self.assertEqual(res2.skipped_duplicates, 1)

        # Flux JSON Particuliers consentants (Portugal)
        json_feed = feeds.register_feed(
            director,
            name="Rede Particulares Autorizados Lisboa",
            country_code=CountryCode.PT,
            publisher_type=PublisherType.PARTICULIER,
            feed_format=FeedFormat.JSON,
            feed_url="https://parceiros.imovel-pt.example/feed.json",
            agreement_confirmed=True,
        )
        sample_json = """{
          "listings": [
            {
              "external_id": "PT-88",
              "title": "Apartamento T2 Renovado Campo de Ourique",
              "property_kind": "Appartement",
              "city": "Lisboa",
              "sector_label": "Campo de Ourique",
              "price_euro": 410000,
              "surface_m2": 78,
              "rooms": 3,
              "dpe": "B",
              "description": "Apartamento luminoso com varanda e vista aberta, pronto a habitar.",
              "canonical_url": "https://parceiros.imovel-pt.example/pt-88"
            }
          ]
        }"""
        pt_res = feeds.sync_feed(director, feed_id=json_feed.id, inline_payload=sample_json)
        self.assertEqual(pt_res.status, "SYNCED")
        self.assertEqual(pt_res.accepted_count, 1)

        router_src = (BACKEND_DIR / "app" / "api" / "v1" / "marketplace_router.py").read_text(encoding="utf-8")
        self.assertIn("/feeds", router_src)
        self.assertIn("/feeds/{feed_id}/sync", router_src)


if __name__ == "__main__":
    unittest.main()
