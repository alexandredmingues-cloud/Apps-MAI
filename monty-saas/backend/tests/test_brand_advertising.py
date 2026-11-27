# Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code.
from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.api.v1.ads_router import AdRequest


def valid_request(**overrides):
    payload = {
        "advertiser_name": "Entreprise Exemple",
        "brand_name": "Marque Habitat",
        "contact_email": "contact@example.fr",
        "website_url": "https://example.fr",
        "creative_image_url": "https://cdn.example.fr/banner.jpg",
        "target_url": "https://example.fr/offre",
        "placement": "HOME",
        "country_code": "FR",
        "city": "Montpellier",
        "headline": "Votre projet immobilier",
        "description": "Découvrez les solutions proposées par notre marque partenaire.",
        "campaign_budget_euro": 500,
        "consent": True,
    }
    payload.update(overrides)
    return payload


def test_ad_request_accepts_https_campaign_and_explicit_consent():
    request = AdRequest(**valid_request())
    assert request.brand_name == "Marque Habitat"
    assert request.placement == "HOME"
    assert request.consent is True


@pytest.mark.parametrize("field", ["target_url", "website_url", "creative_image_url"])
def test_ad_request_rejects_non_https_links(field: str):
    with pytest.raises(ValidationError):
        AdRequest(**valid_request(**{field: "http://insecure.example"}))


def test_ad_request_rejects_missing_consent():
    with pytest.raises(ValidationError):
        AdRequest(**valid_request(consent=False))


def test_ad_request_rejects_unknown_placement_and_negative_budget():
    with pytest.raises(ValidationError):
        AdRequest(**valid_request(placement="UNRELATED"))
    with pytest.raises(ValidationError):
        AdRequest(**valid_request(campaign_budget_euro=-1))
