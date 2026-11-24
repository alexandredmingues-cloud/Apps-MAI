from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class CountryCode(str, Enum):
    FR = "FR"
    PT = "PT"


@dataclass(frozen=True)
class CountryJurisdictionPack:
    country_code: CountryCode
    name_fr: str
    name_native: str
    default_locale: str
    supported_locales: tuple[str, ...]
    currency_code: str
    rollout_priority: str
    regulation_jurisdiction: str
    credit_regulator: str
    max_debt_ratio_guideline_percent: float
    tax_system_label: str
    energy_certificate_label: str
    regions: tuple[str, ...]
    major_cities: tuple[str, ...]
    official_data_sources: tuple[str, ...]


COUNTRY_PACKS: dict[CountryCode, CountryJurisdictionPack] = {
    CountryCode.FR: CountryJurisdictionPack(
        country_code=CountryCode.FR,
        name_fr="France",
        name_native="France",
        default_locale="fr-FR",
        supported_locales=("fr-FR", "en-GB"),
        currency_code="EUR",
        rollout_priority="Priorité 1 & 2 — Active (France entière)",
        regulation_jurisdiction="FR_CODE_CIVIL_CGI_ALUR",
        credit_regulator="HCSF (Haut Conseil de Stabilité Financière)",
        max_debt_ratio_guideline_percent=35.0,
        tax_system_label="Frais de notaire (DMTO + émoluments) & Plus-value CGI",
        energy_certificate_label="DPE (A à G) — Loi Climat et Résilience",
        regions=(
            "National",
            "Île-de-France",
            "Auvergne-Rhône-Alpes",
            "Occitanie",
            "Nouvelle-Aquitaine",
            "Provence-Alpes-Côte d'Azur",
            "Bretagne",
            "Pays de la Loire",
            "Hauts-de-France",
            "Grand Est",
            "Normandie",
            "Bourgogne-Franche-Comté",
            "Centre-Val de Loire",
            "Corse",
        ),
        major_cities=(
            "Paris",
            "Lyon",
            "Marseille",
            "Bordeaux",
            "Toulouse",
            "Nantes",
            "Lille",
            "Nice",
            "Montpellier",
            "Strasbourg",
            "Rennes",
        ),
        official_data_sources=(
            "Banque de France",
            "Etalab / DGFiP (DVF)",
            "Notaires de France",
            "Légifrance",
            "Service-Public.fr",
            "ADEME",
        ),
    ),
    CountryCode.PT: CountryJurisdictionPack(
        country_code=CountryCode.PT,
        name_fr="Portugal",
        name_native="Portugal",
        default_locale="pt-PT",
        supported_locales=("pt-PT", "fr-FR", "en-GB"),
        currency_code="EUR",
        rollout_priority="Priorité 4 — Module national isolé prêt",
        regulation_jurisdiction="PT_CODIGO_CIVIL_CIRS_CIMT",
        credit_regulator="Banco de Portugal (Recomendação Macroprudencial DSTI)",
        max_debt_ratio_guideline_percent=35.0,
        tax_system_label="IMT + Imposto do Selo (0,8 %) & Mais-Valias IRS",
        energy_certificate_label="Certificado Energético ADENE (A+ a F)",
        regions=(
            "Nacional",
            "Área Metropolitana de Lisboa",
            "Norte (Porto / Braga)",
            "Centro",
            "Algarve",
            "Alentejo",
            "Região Autónoma dos Açores",
            "Região Autónoma da Madeira",
        ),
        major_cities=(
            "Lisboa",
            "Porto",
            "Faro",
            "Braga",
            "Coimbra",
            "Cascais",
            "Setúbal",
            "Funchal",
        ),
        official_data_sources=(
            "Banco de Portugal",
            "INE Portugal (Instituto Nacional de Estatística)",
            "Portal das Finanças / Diário da República",
            "ADENE — Agência para a Energia",
        ),
    ),
}


def get_country_pack(code: str | CountryCode) -> CountryJurisdictionPack:
    if isinstance(code, CountryCode):
        return COUNTRY_PACKS[code]
    cleaned = code.strip().upper()
    try:
        return COUNTRY_PACKS[CountryCode(cleaned)]
    except ValueError as exc:
        raise ValueError(f"Juridiction nationale non supportée : {code}") from exc


# ---------------------------------------------------------------------------
# Module National Portugal Isolé (CIMT / Imposto do Selo / Banco de Portugal)
# Strictement séparé du moteur fiscal français (CGI / DMTO / HCSF)
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class PortugalAcquisitionCostResult:
    country_code: str
    property_price_euro: int
    is_primary_residence_hpp: bool
    is_imt_jovem_eligible: bool
    imt_euro: int
    imposto_selo_acquisition_euro: int
    imposto_selo_mortgage_euro: int
    notary_registry_estimate_euro: int
    total_taxes_and_costs_euro: int
    total_budget_euro: int
    disclaimer: str


def compute_portugal_acquisition_costs(
    *,
    property_price_euro: int,
    loan_amount_euro: int = 0,
    is_primary_residence_hpp: bool = True,
    is_imt_jovem_eligible: bool = False,
) -> PortugalAcquisitionCostResult:
    """
    Calcul indicatif de l'IMT et de l'Imposto do Selo au Portugal continental (CIMT / CIS).
    Ne réutilise jamais les barèmes français (DMTO/HCSF).
    """
    price = max(0, property_price_euro)
    loan = max(0, loan_amount_euro)

    if price == 0:
        return PortugalAcquisitionCostResult(
            country_code="PT",
            property_price_euro=0,
            is_primary_residence_hpp=is_primary_residence_hpp,
            is_imt_jovem_eligible=is_imt_jovem_eligible,
            imt_euro=0,
            imposto_selo_acquisition_euro=0,
            imposto_selo_mortgage_euro=0,
            notary_registry_estimate_euro=0,
            total_taxes_and_costs_euro=0,
            total_budget_euro=0,
            disclaimer="Simulação indicativa (Portugal continental) — validar com solicitador / notário.",
        )

    # Tranches simplifiées Portugal Continental (OE 2025/2026) — HPP vs Habitação Secundária
    if is_primary_residence_hpp and is_imt_jovem_eligible and price <= 316_772:
        imt = 0
        selo_acq = 0
    else:
        if is_primary_residence_hpp:
            if price <= 101_917:
                imt = 0
            elif price <= 139_412:
                imt = round(price * 0.02 - 2_038.34)
            elif price <= 190_086:
                imt = round(price * 0.05 - 6_220.70)
            elif price <= 316_772:
                imt = round(price * 0.07 - 10_022.42)
            elif price <= 633_453:
                imt = round(price * 0.08 - 13_190.14)
            elif price <= 1_102_920:
                imt = round(price * 0.06)
            else:
                imt = round(price * 0.075)
        else:
            # Habitação secundária ou arrendamento
            if price <= 101_917:
                imt = round(price * 0.01)
            elif price <= 139_412:
                imt = round(price * 0.02 - 1_019.17)
            elif price <= 190_086:
                imt = round(price * 0.05 - 5_201.53)
            elif price <= 316_772:
                imt = round(price * 0.07 - 9_003.25)
            elif price <= 607_528:
                imt = round(price * 0.08 - 12_170.97)
            elif price <= 1_102_920:
                imt = round(price * 0.06)
            else:
                imt = round(price * 0.075)

        if is_primary_residence_hpp and is_imt_jovem_eligible and price > 316_772:
            # Exonération partielle jusqu'à 316 772 € en HPP jeune <= 35 ans
            imt = max(0, round((price - 316_772) * 0.08))
            selo_acq = round(max(0, price - 316_772) * 0.008)
        else:
            selo_acq = round(price * 0.008)

    selo_loan = round(loan * 0.006)
    notary_reg = 950  # Escritura Casa Pronta / Registo Predial indicatif
    total_taxes = max(0, imt) + selo_acq + selo_loan + notary_reg

    return PortugalAcquisitionCostResult(
        country_code="PT",
        property_price_euro=price,
        is_primary_residence_hpp=is_primary_residence_hpp,
        is_imt_jovem_eligible=is_imt_jovem_eligible,
        imt_euro=max(0, imt),
        imposto_selo_acquisition_euro=selo_acq,
        imposto_selo_mortgage_euro=selo_loan,
        notary_registry_estimate_euro=notary_reg,
        total_taxes_and_costs_euro=total_taxes,
        total_budget_euro=price + total_taxes,
        disclaimer=(
            "Simulação indicativa baseada nas regras fiscais do Portugal continental (CIMT & Imposto do Selo). "
            "Não substitui cálculo oficial no Portal das Finanças ou parecer de solicitador."
        ),
    )
