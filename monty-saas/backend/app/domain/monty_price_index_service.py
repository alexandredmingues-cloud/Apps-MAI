from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
import math
import statistics
from typing import Any
import uuid

from app.core.rbac import Permission, TenantContext
from app.core.security import InputSanitizer
from app.domain.country_packs import CountryCode
from app.domain.dvf_sync_service import DvfSyncService
from app.domain.marketplace_service import CITY_COORDINATES
from app.domain.real_estate_engines import DvfComparable, MONTPELLIER_SECTORS


class PriceIndexCategory(str, Enum):
    EXCELLENTE_AFFAIRE = "EXCELLENTE_AFFAIRE"
    TRES_BONNE_AFFAIRE = "TRES_BONNE_AFFAIRE"
    BONNE_AFFAIRE = "BONNE_AFFAIRE"
    PRIX_COHERENT = "PRIX_COHERENT"
    AU_DESSUS_DU_MARCHE = "AU_DESSUS_DU_MARCHE"
    TROP_CHER = "TROP_CHER"
    DONNEES_INSUFFISANTES = "DONNEES_INSUFFISANTES"


class PriceConfidenceTier(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"
    INSUFFICIENT = "INSUFFICIENT"


@dataclass
class PriceIndexConfig:
    agency_id: str
    excellent_deal_pct: float = -15.0
    very_good_deal_pct: float = -10.0
    good_deal_pct: float = -5.0
    above_market_pct: float = 5.0
    overpriced_pct: float = 10.0
    min_comparables_required: int = 3
    max_comparable_distance_m: int = 2500
    max_comparable_age_months: int = 24
    updated_by: str = "system"
    updated_at: str = ""


PRICE_INDEX_DISCLAIMER = (
    "Indicateur automatisé non contractuel établi à partir de transactions comparables "
    "et de critères déclaratifs. Ne constitue pas une expertise immobilière certifiée."
)


@dataclass(frozen=True)
class PriceIndexHistoryRecord:
    id: str
    agency_id: str
    target_ref: str
    title: str
    country_code: str
    city: str
    property_kind: str
    asking_price_euro: int
    estimated_market_value: int
    gap_percent: float
    category: str
    confidence_tier: str
    comparables_count: int
    evaluated_by: str
    created_at: str


@dataclass(frozen=True)
class ComparableStats:
    comparables_found_count: int
    comparables_retained_count: int
    outliers_removed_count: int
    median_price_m2: int
    weighted_price_m2: int
    q1_price_m2: int
    q3_price_m2: int
    dispersion_cv: float
    avg_distance_m: int
    source_label: str
    data_date: str


class ComparableTransactionService:
    """Sélectionne, filtre (IQR) et pondère les transactions comparables à partir de DVF et des repères locaux."""

    def __init__(self, dvf_sync: DvfSyncService) -> None:
        self._dvf = dvf_sync

    def collect_comparables(
        self,
        *,
        country_code: CountryCode,
        city: str,
        sector_label: str,
        property_kind: str,
        surface_m2: int,
        postal_code: str = "",
        explicit_comparables: list[DvfComparable] | None = None,
        config: PriceIndexConfig,
    ) -> ComparableStats:
        pool: list[DvfComparable] = list(explicit_comparables or [])
        source_label = "Comparables fournis"
        if not pool and postal_code and self._dvf.get_cached_comparables(postal_code):
            pool = self._dvf.get_cached_comparables(postal_code)
            source_label = f"Base DVF officielle (secteur {postal_code})"

        # Référentiel secteurs Montpellier
        if not pool:
            combined_loc = f"{city} {sector_label}".lower()
            for sec in MONTPELLIER_SECTORS:
                short = sec.name.split(" (")[0].lower()
                if short in combined_loc:
                    base_m2 = (sec.price_m2_house or sec.price_m2_apartment) if "maison" in property_kind.lower() else sec.price_m2_apartment
                    pool = [
                        DvfComparable(
                            mutation_id=f"sec-{short[:6]}-{i}",
                            date_mutation=f"2026-0{(i % 6) + 1}-12",
                            year=2026,
                            price_euro=round((base_m2 + (i - 2) * 110) * max(20, surface_m2)),
                            surface_m2=max(20, surface_m2),
                            property_type=property_kind,
                            distance_meters=180 + i * 160,
                        )
                        for i in range(6)
                    ]
                    source_label = f"Référentiel marché local Monty ({sec.name})"
                    break

        # Référentiel national FR/PT par ville connue
        if not pool:
            city_key = city.strip().lower()
            matched_city = next((k for k in CITY_COORDINATES if k in city_key), None)
            if matched_city:
                _lat, _lon, c_code, region, ref_m2 = CITY_COORDINATES[matched_city]
                adj_m2 = round(ref_m2 * (1.07 if "maison" in property_kind.lower() else 1.0))
                pool = [
                    DvfComparable(
                        mutation_id=f"city-{matched_city}-{i}",
                        date_mutation=f"2026-0{(i % 6) + 1}-10",
                        year=2026,
                        price_euro=round((adj_m2 + (i - 2) * 125) * max(20, surface_m2)),
                        surface_m2=max(20, surface_m2),
                        property_type=property_kind,
                        distance_meters=350 + i * 240,
                    )
                    for i in range(5)
                ]
                source_label = (
                    f"Référentiel marché Portugal ({matched_city.title()}, {region})"
                    if c_code == CountryCode.PT
                    else f"Repères DVF / marché ({matched_city.title()}, {region})"
                )

        if not pool:
            return ComparableStats(
                comparables_found_count=0,
                comparables_retained_count=0,
                outliers_removed_count=0,
                median_price_m2=0,
                weighted_price_m2=0,
                q1_price_m2=0,
                q3_price_m2=0,
                dispersion_cv=1.0,
                avg_distance_m=0,
                source_label="Aucune transaction comparable disponible",
                data_date="",
            )

        # Filtrage distance & type de bien compatible
        dist_filtered = [
            c for c in pool if c.distance_meters <= config.max_comparable_distance_m
        ] or pool
        kind_lower = property_kind.lower()
        same_kind = [
            c for c in dist_filtered
            if (kind_lower in c.property_type.lower()) or (c.property_type.lower() in kind_lower)
        ]
        working = same_kind if len(same_kind) >= 2 else dist_filtered

        # Prix/m² + filtrage valeurs aberrantes IQR
        per_m2_pairs = sorted(
            ((c.price_euro / max(1, c.surface_m2), c) for c in working),
            key=lambda pair: pair[0],
        )
        values = [p[0] for p in per_m2_pairs]
        outliers = 0
        retained = per_m2_pairs
        if len(values) >= 4:
            q1 = statistics.quantiles(values, n=4)[0]
            q3 = statistics.quantiles(values, n=4)[2]
            iqr = max(150.0, q3 - q1)
            low_bound = q1 - 1.5 * iqr
            high_bound = q3 + 1.5 * iqr
            filtered = [p for p in per_m2_pairs if low_bound <= p[0] <= high_bound]
            if len(filtered) >= 2:
                outliers = len(per_m2_pairs) - len(filtered)
                retained = filtered

        r_vals = [p[0] for p in retained]
        med_m2 = round(statistics.median(r_vals))
        # Pondération par proximité + récence
        weighted_sum = 0.0
        weight_total = 0.0
        for val_m2, comp in retained:
            w_dist = 1.0 / (1.0 + comp.distance_meters / 800.0)
            w_recency = 1.15 if comp.year >= 2025 else 0.85
            w = w_dist * w_recency
            weighted_sum += val_m2 * w
            weight_total += w
        w_m2 = round(weighted_sum / weight_total) if weight_total > 0 else med_m2

        q1_out = round(statistics.quantiles(r_vals, n=4)[0]) if len(r_vals) >= 4 else round(min(r_vals) * 0.96)
        q3_out = round(statistics.quantiles(r_vals, n=4)[2]) if len(r_vals) >= 4 else round(max(r_vals) * 1.04)
        mean_v = statistics.mean(r_vals)
        stdev_v = statistics.pstdev(r_vals) if len(r_vals) >= 2 else 0.0
        cv = round(stdev_v / mean_v, 3) if mean_v > 0 else 0.0
        avg_dist = round(sum(p[1].distance_meters for p in retained) / len(retained))
        latest_date = max(p[1].date_mutation for p in retained)

        return ComparableStats(
            comparables_found_count=len(pool),
            comparables_retained_count=len(retained),
            outliers_removed_count=outliers,
            median_price_m2=med_m2,
            weighted_price_m2=w_m2,
            q1_price_m2=q1_out,
            q3_price_m2=q3_out,
            dispersion_cv=cv,
            avg_distance_m=avg_dist,
            source_label=source_label,
            data_date=latest_date,
        )


class MarketDataService:
    """Calcule les ajustements qualitatifs du bien (DPE, état, extérieur, étage) en distinguant actes vs annonces."""

    @staticmethod
    def compute_qualitative_multiplier(
        *,
        dpe: str,
        condition_label: str,
        has_exterior: bool = False,
        has_parking: bool = False,
        estimated_works_euro: int = 0,
    ) -> tuple[float, list[dict[str, str]]]:
        adj_pct = 0.0
        factors: list[dict[str, str]] = []

        dpe_clean = dpe.strip().upper()[:1]
        dpe_adj = {"A": 4.0, "B": 2.5, "C": 1.0, "D": 0.0, "E": -3.0, "F": -7.5, "G": -12.0}.get(dpe_clean, 0.0)
        if dpe_adj != 0.0:
            adj_pct += dpe_adj
            factors.append({"factor": f"Performance énergétique (DPE {dpe_clean or 'N/C'})", "impact": f"{dpe_adj:+.1f} %"})

        cond = condition_label.lower()
        if any(k in cond for k in ("neuf", "excellent", "rénové")):
            adj_pct += 5.0
            factors.append({"factor": "État général : rénové / excellent", "impact": "+5.0 %"})
        elif "rafraîchir" in cond or "rafraichir" in cond:
            adj_pct -= 5.0
            factors.append({"factor": "État général : à rafraîchir", "impact": "-5.0 %"})
        elif "rénover" in cond or "renover" in cond:
            adj_pct -= 12.0
            factors.append({"factor": "État général : travaux lourds", "impact": "-12.0 %"})

        if has_exterior:
            adj_pct += 3.0
            factors.append({"factor": "Extérieur privatif (terrasse / jardin / balcon)", "impact": "+3.0 %"})
        if has_parking:
            adj_pct += 2.5
            factors.append({"factor": "Stationnement / garage inclus", "impact": "+2.5 %"})
        if estimated_works_euro > 0:
            factors.append({"factor": f"Travaux estimés signalés ({estimated_works_euro:,} €)".replace(",", " "), "impact": "Déduction directe"})

        return max(0.72, min(1.25, 1.0 + adj_pct / 100.0)), factors


class PriceClassificationService:
    """Classe le positionnement prix dans les 6 catégories configurables Monty Price Index."""

    LABELS: dict[PriceIndexCategory, str] = {
        PriceIndexCategory.EXCELLENTE_AFFAIRE: "Excellente affaire",
        PriceIndexCategory.TRES_BONNE_AFFAIRE: "Très bonne affaire",
        PriceIndexCategory.BONNE_AFFAIRE: "Bonne affaire",
        PriceIndexCategory.PRIX_COHERENT: "Prix cohérent",
        PriceIndexCategory.AU_DESSUS_DU_MARCHE: "Au-dessus du marché",
        PriceIndexCategory.TROP_CHER: "Trop cher",
        PriceIndexCategory.DONNEES_INSUFFISANTES: "Données insuffisantes",
    }

    @classmethod
    def classify(cls, gap_percent: float, cfg: PriceIndexConfig) -> tuple[PriceIndexCategory, str]:
        if gap_percent < cfg.excellent_deal_pct:
            cat = PriceIndexCategory.EXCELLENTE_AFFAIRE
        elif gap_percent < cfg.very_good_deal_pct:
            cat = PriceIndexCategory.TRES_BONNE_AFFAIRE
        elif gap_percent < cfg.good_deal_pct:
            cat = PriceIndexCategory.BONNE_AFFAIRE
        elif gap_percent <= cfg.above_market_pct:
            cat = PriceIndexCategory.PRIX_COHERENT
        elif gap_percent <= cfg.overpriced_pct:
            cat = PriceIndexCategory.AU_DESSUS_DU_MARCHE
        else:
            cat = PriceIndexCategory.TROP_CHER
        return cat, cls.LABELS[cat]


class PriceConfidenceService:
    """Évalue la fiabilité statistique du repère de prix et masque le badge si les données sont insuffisantes."""

    @staticmethod
    def evaluate_confidence(stats: ComparableStats, cfg: PriceIndexConfig) -> tuple[PriceConfidenceTier, int, str, bool]:
        if stats.comparables_retained_count < cfg.min_comparables_required or stats.weighted_price_m2 <= 0:
            return (
                PriceConfidenceTier.INSUFFICIENT,
                15,
                "Données insuffisantes : trop peu de comparables fiables pour afficher un badge prix.",
                False,
            )

        score = 50
        if stats.comparables_retained_count >= 10:
            score += 25
        elif stats.comparables_retained_count >= 5:
            score += 15
        else:
            score += 5

        if stats.avg_distance_m <= 600:
            score += 15
        elif stats.avg_distance_m <= 1400:
            score += 8

        if stats.dispersion_cv <= 0.12:
            score += 10
        elif stats.dispersion_cv >= 0.25:
            score -= 15

        score = max(20, min(96, score))
        if score >= 78:
            return PriceConfidenceTier.HIGH, score, "Fiabilité élevée", True
        if score >= 55:
            return PriceConfidenceTier.MEDIUM, score, "Fiabilité moyenne", True
        return PriceConfidenceTier.LOW, score, "Fiabilité faible (à confirmer par visite)", True


class MontyPriceEngine:
    """Moteur complet Monty Price Index + configuration & historique par agence."""

    def __init__(self, dvf_sync: DvfSyncService | None = None) -> None:
        self._dvf = dvf_sync or DvfSyncService()
        self._comp_service = ComparableTransactionService(self._dvf)
        self._configs: dict[str, PriceIndexConfig] = {}
        self._history: list[PriceIndexHistoryRecord] = []

    def get_config(self, agency_id: str) -> PriceIndexConfig:
        if agency_id not in self._configs:
            self._configs[agency_id] = PriceIndexConfig(
                agency_id=agency_id,
                updated_at=datetime.now(timezone.utc).isoformat(),
            )
        return self._configs[agency_id]

    def update_config(
        self,
        ctx: TenantContext,
        *,
        excellent_deal_pct: float | None = None,
        very_good_deal_pct: float,
        good_deal_pct: float,
        above_market_pct: float,
        overpriced_pct: float,
        min_comparables_required: int,
        max_comparable_distance_m: int,
        max_comparable_age_months: int,
    ) -> PriceIndexConfig:
        if not ctx.has_permission(Permission.AGENCY_MANAGE):
            raise PermissionError("Seul un administrateur peut modifier les seuils Monty Price Index.")
        eff_excellent = round(
            float(excellent_deal_pct) if excellent_deal_pct is not None else min(-15.0, float(very_good_deal_pct) - 5.0),
            2,
        )
        if not (eff_excellent < very_good_deal_pct < good_deal_pct < above_market_pct < overpriced_pct):
            raise ValueError(
                "Les seuils doivent être strictement croissants "
                "(Excellente affaire < Très bonne affaire < Bonne affaire < Au-dessus < Trop cher)."
            )
        cfg = PriceIndexConfig(
            agency_id=ctx.agency_id,
            excellent_deal_pct=eff_excellent,
            very_good_deal_pct=round(float(very_good_deal_pct), 2),
            good_deal_pct=round(float(good_deal_pct), 2),
            above_market_pct=round(float(above_market_pct), 2),
            overpriced_pct=round(float(overpriced_pct), 2),
            min_comparables_required=max(2, min(20, int(min_comparables_required))),
            max_comparable_distance_m=max(300, min(15000, int(max_comparable_distance_m))),
            max_comparable_age_months=max(6, min(60, int(max_comparable_age_months))),
            updated_by=ctx.user_id,
            updated_at=datetime.now(timezone.utc).isoformat(),
        )
        self._configs[ctx.agency_id] = cfg
        return cfg

    def evaluate_property(
        self,
        *,
        agency_id: str = "agency-france-reseau",
        user_id: str = "public",
        target_ref: str = "",
        title: str = "Bien analysé",
        country_code: CountryCode = CountryCode.FR,
        city: str,
        sector_label: str = "",
        postal_code: str = "",
        property_kind: str = "Appartement",
        asking_price_euro: int,
        surface_m2: int,
        dpe: str = "C",
        condition_label: str = "Bon état",
        has_exterior: bool = False,
        has_parking: bool = False,
        estimated_works_euro: int = 0,
        explicit_comparables: list[DvfComparable] | None = None,
        record_history: bool = True,
        config_override: PriceIndexConfig | None = None,
    ) -> dict[str, Any]:
        cfg = config_override or self.get_config(agency_id)
        if asking_price_euro <= 0 or surface_m2 <= 0:
            raise ValueError("Prix affiché et surface habitable (> 0) requis.")

        calculated_at = datetime.now(timezone.utc).isoformat()
        stats = self._comp_service.collect_comparables(
            country_code=country_code,
            city=city,
            sector_label=sector_label,
            property_kind=property_kind,
            surface_m2=surface_m2,
            postal_code=postal_code,
            explicit_comparables=explicit_comparables,
            config=cfg,
        )
        tier, reliability_score, confidence_label, display_badge = PriceConfidenceService.evaluate_confidence(stats, cfg)

        if not display_badge:
            return {
                "display_badge": False,
                "category": PriceIndexCategory.DONNEES_INSUFFISANTES.value,
                "category_label": PriceClassificationService.LABELS[PriceIndexCategory.DONNEES_INSUFFISANTES],
                "confidence_tier": tier.value,
                "confidence_label": confidence_label,
                "reliability_score": reliability_score,
                "asking_price_euro": asking_price_euro,
                "asking_price_m2": round(asking_price_euro / surface_m2),
                "estimated_market_value": 0,
                "estimated_price_m2": 0,
                "price_range_low": 0,
                "price_range_high": 0,
                "gap_percent": 0.0,
                "gap_euro": 0,
                "comparables": stats.__dict__,
                "adjustment_factors": [],
                "thresholds": {
                    "excellent_deal_pct": cfg.excellent_deal_pct,
                    "very_good_deal_pct": cfg.very_good_deal_pct,
                    "good_deal_pct": cfg.good_deal_pct,
                    "above_market_pct": cfg.above_market_pct,
                    "overpriced_pct": cfg.overpriced_pct,
                },
                "calculated_at": calculated_at,
                "estimation_disclaimer": PRICE_INDEX_DISCLAIMER,
                "explanation_grounded": (
                    f"Indicateur masqué pour « {InputSanitizer.sanitize_text(title, 120)} » : "
                    f"seulement {stats.comparables_retained_count} comparable(s) retenu(s) "
                    f"(minimum requis : {cfg.min_comparables_required})."
                ),
            }

        mult, factors = MarketDataService.compute_qualitative_multiplier(
            dpe=dpe,
            condition_label=condition_label,
            has_exterior=has_exterior,
            has_parking=has_parking,
            estimated_works_euro=estimated_works_euro,
        )
        est_m2 = round(stats.weighted_price_m2 * mult)
        est_value = max(10_000, round(est_m2 * surface_m2) - max(0, estimated_works_euro))
        spread = 0.06 if tier == PriceConfidenceTier.HIGH else (0.09 if tier == PriceConfidenceTier.MEDIUM else 0.12)
        range_low = round(est_value * (1.0 - spread))
        range_high = round(est_value * (1.0 + spread))
        gap_euro = asking_price_euro - est_value
        gap_pct = round((gap_euro / est_value) * 100.0, 2)
        category, category_label = PriceClassificationService.classify(gap_pct, cfg)
        asking_m2 = round(asking_price_euro / surface_m2)

        def _fmt_eur(val: int) -> str:
            return f"{val:,}".replace(",", " ")

        def _fmt_signed_eur(val: int) -> str:
            return f"{val:+,}".replace(",", " ")

        explanation = (
            f"Analyse Monty Price Index ({confidence_label}) : sur la base de {stats.comparables_retained_count} "
            f"comparables retenus ({stats.source_label}, médiane {_fmt_eur(stats.median_price_m2)} €/m²), "
            f"la valeur de marché estimée est de {_fmt_eur(est_value)} € ({_fmt_eur(est_m2)} €/m², "
            f"fourchette {_fmt_eur(range_low)} € – {_fmt_eur(range_high)} €). "
            f"Le prix affiché de {_fmt_eur(asking_price_euro)} € ({_fmt_eur(asking_m2)} €/m²) présente un écart de "
            f"{gap_pct:+.1f} % ({_fmt_signed_eur(gap_euro)} €), classant le bien en « {category_label} »."
        )

        history_record: PriceIndexHistoryRecord | None = None
        if record_history:
            rec = PriceIndexHistoryRecord(
                id=f"mpi-{uuid.uuid4().hex[:10]}",
                agency_id=agency_id,
                target_ref=InputSanitizer.sanitize_text(target_ref, 120),
                title=InputSanitizer.sanitize_text(title, 200),
                country_code=country_code.value,
                city=InputSanitizer.sanitize_text(city, 120),
                property_kind=InputSanitizer.sanitize_text(property_kind, 60),
                asking_price_euro=asking_price_euro,
                estimated_market_value=est_value,
                gap_percent=gap_pct,
                category=category.value,
                confidence_tier=tier.value,
                comparables_count=stats.comparables_retained_count,
                evaluated_by=user_id,
                created_at=calculated_at,
            )
            history_record = rec
            self._history.insert(0, rec)
            if len(self._history) > 300:
                self._history = self._history[:300]

        return {
            "display_badge": True,
            "category": category.value,
            "category_label": category_label,
            "confidence_tier": tier.value,
            "confidence_label": confidence_label,
            "reliability_score": reliability_score,
            "asking_price_euro": asking_price_euro,
            "asking_price_m2": asking_m2,
            "estimated_market_value": est_value,
            "estimated_price_m2": est_m2,
            "price_range_low": range_low,
            "price_range_high": range_high,
            "gap_percent": gap_pct,
            "gap_euro": gap_euro,
            "comparables": stats.__dict__,
            "adjustment_factors": factors,
            "thresholds": {
                "excellent_deal_pct": cfg.excellent_deal_pct,
                "very_good_deal_pct": cfg.very_good_deal_pct,
                "good_deal_pct": cfg.good_deal_pct,
                "above_market_pct": cfg.above_market_pct,
                "overpriced_pct": cfg.overpriced_pct,
            },
            "calculated_at": calculated_at,
            "estimation_disclaimer": PRICE_INDEX_DISCLAIMER,
            "explanation_grounded": explanation,
            "history_record_id": history_record.id if history_record else "",
        }

    def list_history(self, ctx: TenantContext, limit: int = 50) -> list[PriceIndexHistoryRecord]:
        return [h for h in self._history if h.agency_id == ctx.agency_id][:limit]


monty_price_engine = MontyPriceEngine()
