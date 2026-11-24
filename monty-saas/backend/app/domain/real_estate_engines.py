from __future__ import annotations

import math
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum


# ---------------------------------------------------------------------------
# 1. Référentiel Secteurs Montpellier (porté depuis SeedData.kt)
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class SectorBenchmark:
    name: str
    price_m2_apartment: int
    price_m2_house: int | None
    evolution: str
    tension: str
    lat: float
    lon: float


MONTPELLIER_SECTORS: tuple[SectorBenchmark, ...] = (
    SectorBenchmark("Port Marianne (Jacques Cœur)", 5150, None, "+3.8%", "Extrême", 43.6003, 3.9041),
    SectorBenchmark("Les Arceaux", 4650, 5400, "+2.9%", "Très forte", 43.6119, 3.8645),
    SectorBenchmark("Antigone", 4200, None, "+2.1%", "Forte", 43.6087, 3.8902),
    SectorBenchmark("Ovalie", 3950, 4450, "+3.2%", "Forte", 43.5936, 3.8467),
    SectorBenchmark("Mas Drevon / Montcalm", 3850, 4300, "+2.5%", "Forte", 43.5974, 3.8648),
    SectorBenchmark("Croix d'Argent", 3450, 3900, "+1.8%", "Modérée", 43.5908, 3.8614),
    SectorBenchmark("Hôpitaux-Facultés", 4100, 4750, "+3.1%", "Très forte", 43.6335, 3.8618),
    SectorBenchmark("Castelnau-le-Lez", 4400, 4950, "+3.5%", "Très forte", 43.6365, 3.9012),
    SectorBenchmark("Baillargues (Gare TER)", 3650, 4150, "+4.2%", "Très forte", 43.6606, 4.0132),
    SectorBenchmark("Juvignac", 3350, 3850, "+1.9%", "Modérée", 43.6132, 3.8104),
    SectorBenchmark("Lattes / Pérols", 4300, 4850, "+3.0%", "Forte", 43.5681, 3.9022),
)


def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6_371_000.0
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (
        math.sin(d_lat / 2) ** 2
        + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(d_lon / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return abs(r * c)


# ---------------------------------------------------------------------------
# 2. Simulateur Financier & Capacité d'Emprunt HCSF 35% (porté depuis SeedData.kt)
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class FinanceMetrics:
    notary_fees: int
    total_project_cost: int
    borrowed_amount: int
    monthly_excl_insurance: int
    monthly_insurance: int
    monthly_incl_insurance: int
    total_credit_cost: int
    debt_ratio_percent: float
    remainder_to_live: int
    real_monthly_housing_cost: int
    scenario_20_years: int
    scenario_25_years: int


def compute_finance_metrics(
    *,
    price: int,
    down_payment: int,
    notary_rate_percent: float,
    duration_years: int,
    interest_rate_percent: float,
    insurance_rate_percent: float,
    monthly_income: int,
    existing_credits: int,
    monthly_charges: int,
) -> FinanceMetrics:
    notary_fees = round(price * (notary_rate_percent / 100.0))
    total_project_cost = price + notary_fees
    borrowed_amount = max(0, total_project_cost - down_payment)

    monthly_rate = interest_rate_percent / 100.0 / 12.0
    months = duration_years * 12

    if borrowed_amount <= 0:
        monthly_excl_ins = 0.0
    elif monthly_rate == 0.0:
        monthly_excl_ins = borrowed_amount / months
    else:
        monthly_excl_ins = (borrowed_amount * monthly_rate) / (1.0 - (1.0 + monthly_rate) ** (-months))

    monthly_ins = (borrowed_amount * (insurance_rate_percent / 100.0)) / 12.0
    monthly_incl_ins = monthly_excl_ins + monthly_ins
    total_credit_cost = max(0.0, monthly_incl_ins * months - borrowed_amount)
    total_monthly_debt = monthly_incl_ins + existing_credits
    debt_ratio_percent = round((total_monthly_debt / monthly_income) * 1000.0) / 10.0 if monthly_income > 0 else 0.0
    remainder_to_live = round(monthly_income - total_monthly_debt - monthly_charges)
    real_monthly_cost = round(monthly_incl_ins + monthly_charges)

    def _scenario(years: int) -> int:
        if borrowed_amount <= 0:
            return 0
        m = years * 12
        base = borrowed_amount / m if monthly_rate == 0.0 else (borrowed_amount * monthly_rate) / (1.0 - (1.0 + monthly_rate) ** (-m))
        return round(base + monthly_ins)

    return FinanceMetrics(
        notary_fees=notary_fees,
        total_project_cost=total_project_cost,
        borrowed_amount=borrowed_amount,
        monthly_excl_insurance=round(monthly_excl_ins),
        monthly_insurance=round(monthly_ins),
        monthly_incl_insurance=round(monthly_incl_ins),
        total_credit_cost=round(total_credit_cost),
        debt_ratio_percent=debt_ratio_percent,
        remainder_to_live=remainder_to_live,
        real_monthly_housing_cost=real_monthly_cost,
        scenario_20_years=_scenario(20),
        scenario_25_years=_scenario(25),
    )


@dataclass(frozen=True)
class HcsfDurationScenario:
    duration_years: int
    max_borrowed_capital: int
    max_property_price_net: int
    is_hcsf_compliant: bool


@dataclass(frozen=True)
class HcsfCapacityInput:
    monthly_net_income: int = 6800
    existing_monthly_credits: int = 0
    down_payment: int = 95000
    duration_years: int = 25
    interest_rate_percent: float = 3.35
    insurance_rate_percent: float = 0.32
    max_debt_ratio_percent: float = 35.0
    notary_rate_percent: float = 7.8
    is_vefa_neuf: bool = False


@dataclass(frozen=True)
class HcsfCapacityResult:
    max_monthly_debt_total: int
    max_monthly_loan_payment: int
    max_borrowed_capital: int
    max_total_project_budget: int
    max_property_price_net: int
    estimated_notary_fees: int
    total_credit_cost: int
    effective_debt_ratio_percent: float
    remainder_to_live: int
    is_hcsf_ratio_compliant: bool
    is_hcsf_duration_compliant: bool
    duration_scenarios: list[HcsfDurationScenario]


def compute_hcsf_borrowing_capacity(inp: HcsfCapacityInput) -> HcsfCapacityResult:
    income = max(0, inp.monthly_net_income)
    existing = max(0, inp.existing_monthly_credits)
    down = max(0, inp.down_payment)
    years = min(30, max(5, inp.duration_years))
    target_ratio = min(45.0, max(10.0, inp.max_debt_ratio_percent))
    annual_interest = min(15.0, max(0.0, inp.interest_rate_percent))
    annual_insurance = min(3.0, max(0.0, inp.insurance_rate_percent))
    notary_rate = min(15.0, max(0.0, inp.notary_rate_percent))

    max_monthly_debt_total_d = income * (target_ratio / 100.0)
    max_monthly_loan_payment_d = max(0.0, max_monthly_debt_total_d - existing)

    m_int = annual_interest / 100.0 / 12.0
    m_ins = annual_insurance / 100.0 / 12.0

    def _cap_for_duration(d_years: int) -> tuple[int, int]:
        months = d_years * 12
        credit_factor = (1.0 / months) if m_int == 0.0 else m_int / (1.0 - (1.0 + m_int) ** (-months))
        total_factor = credit_factor + m_ins
        borrowed = 0 if (max_monthly_loan_payment_d <= 0.0 or total_factor <= 0.0) else round(max_monthly_loan_payment_d / total_factor)
        total_budget = borrowed + down
        net_price = 0 if total_budget <= 0 else round(total_budget / (1.0 + notary_rate / 100.0))
        return borrowed, net_price

    max_borrowed, max_net_price = _cap_for_duration(years)
    max_total_budget = max_borrowed + down
    est_notary = max(0, max_total_budget - max_net_price)
    months = years * 12
    total_credit_cost = 0 if max_borrowed <= 0 else round(max(0.0, max_monthly_loan_payment_d * months - max_borrowed))
    actual_loan_pmt = round(max_monthly_loan_payment_d)
    eff_ratio = round(((actual_loan_pmt + existing) / income) * 1000.0) / 10.0 if income > 0 else 0.0
    rem_to_live = income - existing - actual_loan_pmt
    ratio_ok = target_ratio <= 35.0 and (income == 0 or eff_ratio <= 35.5)
    duration_ok = years <= 27 if inp.is_vefa_neuf else years <= 25

    scenarios = []
    for d in (15, 20, 25):
        b, n = _cap_for_duration(d)
        scenarios.append(HcsfDurationScenario(d, b, n, d <= 25))

    return HcsfCapacityResult(
        max_monthly_debt_total=round(max_monthly_debt_total_d),
        max_monthly_loan_payment=actual_loan_pmt,
        max_borrowed_capital=max_borrowed,
        max_total_project_budget=max_total_budget,
        max_property_price_net=max_net_price,
        estimated_notary_fees=est_notary,
        total_credit_cost=total_credit_cost,
        effective_debt_ratio_percent=eff_ratio,
        remainder_to_live=rem_to_live,
        is_hcsf_ratio_compliant=ratio_ok,
        is_hcsf_duration_compliant=duration_ok,
        duration_scenarios=scenarios,
    )


# ---------------------------------------------------------------------------
# 3. GeoCommuteService (porté depuis GeoCommuteService.kt)
# ---------------------------------------------------------------------------
class SearchPerimeterMode(str, Enum):
    SECTOR_LIST = "SECTOR_LIST"
    RADIUS_KM = "RADIUS_KM"
    TRAVEL_TIME = "TRAVEL_TIME"


class TransportMode(str, Enum):
    VOITURE = "VOITURE"
    TRAM = "TRAM"
    BUS = "BUS"
    TRAIN = "TRAIN"
    VELO = "VELO"
    PIED = "PIED"


TRANSPORT_PARAMS: dict[TransportMode, tuple[str, float, float, int]] = {
    TransportMode.VOITURE: ("Voiture", 32.0, 1.25, 2),
    TransportMode.TRAM: ("Tram", 18.0, 1.28, 5),
    TransportMode.BUS: ("Bus", 15.0, 1.32, 6),
    TransportMode.TRAIN: ("Train / TER", 50.0, 1.18, 9),
    TransportMode.VELO: ("Vélo", 16.0, 1.16, 1),
    TransportMode.PIED: ("À pied", 4.8, 1.20, 0),
}


@dataclass(frozen=True)
class CommuteEstimate:
    distance_km: float
    estimated_minutes: int
    transport_mode: TransportMode
    origin_label: str
    badge_label: str
    within_perimeter: bool


def evaluate_property_access(
    *,
    origin_lat: float,
    origin_lon: float,
    origin_label: str,
    prop_lat: float,
    prop_lon: float,
    transport_mode: TransportMode,
    search_mode: SearchPerimeterMode,
    max_radius_km: int,
    max_travel_minutes: int,
) -> CommuteEstimate:
    label, speed_kmh, detour, overhead = TRANSPORT_PARAMS[transport_mode]
    geodesic_km = haversine_meters(origin_lat, origin_lon, prop_lat, prop_lon) / 1000.0
    route_km = geodesic_km * detour
    rounded_km = round(route_km * 10.0) / 10.0
    est_min = max(1, round(overhead + (route_km / speed_kmh) * 60.0))

    if search_mode == SearchPerimeterMode.SECTOR_LIST:
        within = True
    elif search_mode == SearchPerimeterMode.RADIUS_KM:
        within = route_km <= max_radius_km + 0.05
    else:
        within = est_min <= max_travel_minutes

    km_disp = f"{round(route_km * 1000.0)} m" if rounded_km < 1.0 else f"{rounded_km:.1f} km".replace(".", ",")
    if search_mode == SearchPerimeterMode.RADIUS_KM:
        badge = f"{km_disp} • ~{est_min} min ({label})"
    elif search_mode == SearchPerimeterMode.TRAVEL_TIME:
        badge = f"~{est_min} min en {label} ({km_disp})"
    else:
        badge = f"{km_disp} • {est_min} min"

    return CommuteEstimate(
        distance_km=rounded_km,
        estimated_minutes=est_min,
        transport_mode=transport_mode,
        origin_label=origin_label,
        badge_label=badge,
        within_perimeter=within,
    )


# ---------------------------------------------------------------------------
# 4. Matching Déterministe Acquéreur / Bien (porté depuis SeedData.kt)
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class PropertyMatchInput:
    price: int
    estimated_works: int
    sector: str
    property_type: str
    surface: int
    bedrooms: int
    dpe: str
    exterior: str = ""
    parking: str = ""
    environment: str = ""
    transports: str = ""
    floor: str = ""
    latitude: float = 43.6108
    longitude: float = 3.8767


@dataclass(frozen=True)
class BuyerMatchInput:
    budget_max: int
    sectors: list[str]
    property_type: str
    min_surface: int
    min_rooms: int
    essential_criteria: list[str]
    search_mode: SearchPerimeterMode = SearchPerimeterMode.SECTOR_LIST
    search_center_lat: float = 0.0
    search_center_lon: float = 0.0
    search_center_address: str = ""
    search_radius_km: int = 5
    search_max_travel_minutes: int = 20
    search_transport_mode: TransportMode = TransportMode.TRAM


@dataclass(frozen=True)
class CompatibilityResult:
    score: int
    matched_criteria: list[str]
    incompatible_criteria: list[str]


def compute_compatibility(prop: PropertyMatchInput, buyer: BuyerMatchInput) -> CompatibilityResult:
    score = 45
    reasons: list[str] = []
    alerts: list[str] = []

    total_cost = prop.price + prop.estimated_works
    diff = buyer.budget_max - total_cost
    if diff >= 15000:
        score += 22
        reasons.append(f"Budget maîtrisé : {total_cost} € (travaux inclus), soit {diff} € sous le plafond.")
    elif diff >= 0:
        score += 15
        reasons.append(f"Enveloppe respectée ({total_cost} € pour {buyer.budget_max} € max).")
    elif diff >= -20000:
        score += 3
        alerts.append(f"Légèrement au-dessus du budget cible (+{-diff} €), négociable selon contexte vendeur.")
    else:
        score -= 25
        alerts.append(f"Dépassement budgétaire significatif (+{-diff} € au-delà de l'enveloppe).")

    sector_prefix = prop.sector.split(" ")[0].lower()
    match_sector = any(sector_prefix in s.lower() for s in buyer.sectors)
    if buyer.search_mode != SearchPerimeterMode.SECTOR_LIST and (buyer.search_center_lat != 0.0 or buyer.search_center_lon != 0.0):
        commute = evaluate_property_access(
            origin_lat=buyer.search_center_lat,
            origin_lon=buyer.search_center_lon,
            origin_label=buyer.search_center_address or "Adresse cible",
            prop_lat=prop.latitude,
            prop_lon=prop.longitude,
            transport_mode=buyer.search_transport_mode,
            search_mode=buyer.search_mode,
            max_radius_km=buyer.search_radius_km,
            max_travel_minutes=buyer.search_max_travel_minutes,
        )
        if commute.within_perimeter:
            score += 14
            reasons.append(f"Dans le périmètre défini : {commute.badge_label} depuis {commute.origin_label}.")
        else:
            score -= 14
            alerts.append(f"Hors périmètre cible ({commute.badge_label} depuis {commute.origin_label}).")
    elif match_sector:
        score += 15
        reasons.append(f"Localisation cible validée : {prop.sector}.")
    else:
        score -= 8
        alerts.append(f"Secteur ({prop.sector}) hors de la liste prioritaire.")

    if buyer.property_type and buyer.property_type.lower() in prop.property_type.lower():
        score += 8
        reasons.append(f"Typologie conforme ({prop.property_type}).")

    if prop.surface >= buyer.min_surface:
        score += 8
        reasons.append(f"Surface confortable de {prop.surface} m² (minimum souhaité : {buyer.min_surface} m²).")
    else:
        score -= 10
        alerts.append(f"Surface inférieure au critère ({prop.surface} m² vs {buyer.min_surface} m²).")

    if prop.bedrooms >= buyer.min_rooms:
        score += 7
        reasons.append(f"{prop.bedrooms} chambres disponibles (conforme aux {buyer.min_rooms} requises).")
    else:
        score -= 12
        alerts.append(f"Nombre de chambres insuffisant ({prop.bedrooms} vs {buyer.min_rooms} requises).")

    blob = f"{prop.exterior} {prop.parking} {prop.environment} {prop.transports} {prop.floor} DPE {prop.dpe}".lower()
    for crit in buyer.essential_criteria:
        c = crit.lower()
        if "terrasse" in c and ("terrasse" in blob or "jardin" in blob):
            score += 3
        elif "parking" in c and ("garage" in blob or "box" in blob or "stationnement" in blob):
            score += 3
        elif "tram" in c and "tram" in blob:
            score += 3
        elif "calme" in c and "calme" in blob:
            score += 3
        elif "ascenseur" in c and "ascenseur" in blob:
            score += 3
        elif "dernier" in c and "dernier" in blob:
            score += 4
        elif "dpe" in c and prop.dpe.upper() in ("A", "B", "C"):
            score += 3

    if prop.dpe.upper() in ("A", "B", "C"):
        reasons.append(f"Excellente performance énergétique (DPE {prop.dpe}) limitant les charges.")
    else:
        alerts.append(f"DPE {prop.dpe} : vigilance sur la consommation énergétique.")

    return CompatibilityResult(
        score=max(12, min(99, score)),
        matched_criteria=reasons,
        incompatible_criteria=alerts,
    )


# ---------------------------------------------------------------------------
# 5. Estimation DVF Pondérée & Délai de Vente (portés depuis DvfValuationService & SaleTimelineEngine)
# ---------------------------------------------------------------------------
ELEVATOR_FLOOR_APPT = {
    "RDC": -0.07,
    "1er-2e sans asc.": -0.03,
    "3e+ sans asc.": -0.10,
    "Étage avec ascenseur": 0.03,
    "Dernier étage + asc.": 0.08,
}
ELEVATOR_FLOOR_HOUSE = {
    "Plain-pied": 0.04,
    "Avec étage(s)": 0.0,
}
VIEW_EXPOSURE = {
    "Vue dégagée / parc / mer": 0.07,
    "Lumineux / Traversant SO": 0.04,
    "Exposition standard": 0.0,
    "Vis-à-vis fort / Sombre": -0.07,
}
EXTERIOR_TYPES = {
    "Aucun extérieur": -0.04,
    "Balcon / Loggia": 0.02,
    "Terrasse ≥ 15 m²": 0.06,
    "Jardin privatif / Piscine": 0.08,
}
PARKING_TYPES = {
    "Aucun stationnement": -0.03,
    "Place parking privée": 0.03,
    "Box fermé / Garage": 0.05,
    "Double garage / Borne VE": 0.07,
}
NOISE_ENVIRONMENT = {
    "Très calme / Impasse": 0.03,
    "Urbain standard": 0.0,
    "Axe passant / Bruit": -0.06,
}


@dataclass(frozen=True)
class DvfComparable:
    mutation_id: str
    date_mutation: str
    year: int
    price_euro: int
    surface_m2: int
    property_type: str
    distance_meters: int

    @property
    def price_per_m2(self) -> int:
        return self.price_euro // self.surface_m2 if self.surface_m2 > 0 else 0


@dataclass(frozen=True)
class DvfValuationCriteria:
    floor_and_elevator: str = "Étage avec ascenseur"
    view_and_exposure: str = "Lumineux / Traversant SO"
    exterior_type: str = "Balcon / Loggia"
    parking_type: str = "Place parking privée"
    environment_calm: str = "Urbain standard"
    has_cellar_or_storage: bool = False
    has_ac_or_heat_pump: bool = False
    has_recent_glazing_or_fiber: bool = True
    has_no_vis_a_vis: bool = False
    has_major_works_pending: bool = False


@dataclass(frozen=True)
class DvfValuationReport:
    estimated_low_euro: int
    estimated_mid_euro: int
    estimated_high_euro: int
    raw_dvf_price_m2: int
    adjusted_price_m2: int
    total_adjustment_percent: int
    valuation_score: int
    score_label: str
    comparables_used_count: int
    data_date: str
    disclaimer: str
    justifications: list[str]


def _lookup_delta(selected: str, table: dict[str, float]) -> float:
    clean = re.sub(r"\s*\([^)]*%\)", "", selected).strip().lower()
    for key, val in table.items():
        if key.lower() == clean or clean in key.lower():
            return val
    return 0.0


def compute_dvf_valuation_report(
    *,
    comparables: list[DvfComparable],
    property_kind: str,
    surface_m2: int,
    dpe: str,
    condition_label: str,
    provided_docs_ratio: float,
    criteria: DvfValuationCriteria = DvfValuationCriteria(),
    reference_year: int | None = None,
) -> DvfValuationReport:
    ref_year = reference_year or datetime.now(timezone.utc).year
    matching_type = [c for c in comparables if c.property_type.lower() == property_kind.lower()]
    pool = matching_type if len(matching_type) >= 2 else comparables

    weighted_sum = 0.0
    total_weight = 0.0
    for c in pool:
        ring_w = 3.0 if c.distance_meters <= 50 else (2.0 if c.distance_meters <= 100 else 1.0)
        age = max(0, min(6, ref_year - c.year))
        recency_w = 1.25 if age <= 1 else (1.0 if age == 2 else 0.75)
        w = ring_w * recency_w
        weighted_sum += c.price_per_m2 * w
        total_weight += w

    base_m2 = (weighted_sum / total_weight) if total_weight > 0.0 else 0.0
    raw_m2 = round(base_m2)
    safe_surface = max(1, surface_m2)
    base_market_euro = round((raw_m2 * safe_surface) / 500.0) * 500 if raw_m2 > 0 else 0

    dpe_map = {"A": 0.05, "B": 0.05, "C": 0.02, "D": 0.0, "E": -0.04, "F": -0.09, "G": -0.14}
    dpe_delta = dpe_map.get(dpe.strip().upper(), 0.0)

    cond_lower = condition_label.lower()
    if "rénové" in cond_lower or "neuf" in cond_lower:
        cond_delta = 0.06
    elif "rafraîchir" in cond_lower:
        cond_delta = -0.07
    elif "rénover" in cond_lower:
        cond_delta = -0.15
    else:
        cond_delta = 0.0

    floor_table = ELEVATOR_FLOOR_APPT if property_kind.lower() == "appartement" else ELEVATOR_FLOOR_HOUSE
    drivers: list[tuple[str, float]] = [
        (f"DPE {dpe.upper()}", dpe_delta),
        (f"État ({condition_label})", cond_delta),
        (f"Étage/Configuration ({criteria.floor_and_elevator})", _lookup_delta(criteria.floor_and_elevator, floor_table)),
        (f"Vue ({criteria.view_and_exposure})", _lookup_delta(criteria.view_and_exposure, VIEW_EXPOSURE)),
        (f"Extérieur ({criteria.exterior_type})", _lookup_delta(criteria.exterior_type, EXTERIOR_TYPES)),
        (f"Stationnement ({criteria.parking_type})", _lookup_delta(criteria.parking_type, PARKING_TYPES)),
        (f"Environnement ({criteria.environment_calm})", _lookup_delta(criteria.environment_calm, NOISE_ENVIRONMENT)),
    ]
    if criteria.has_cellar_or_storage:
        drivers.append(("Cave / Cellier", 0.015))
    if criteria.has_ac_or_heat_pump:
        drivers.append(("Climatisation / PAC", 0.02))
    if criteria.has_recent_glazing_or_fiber:
        drivers.append(("Double vitrage / Fibre", 0.015))
    if criteria.has_no_vis_a_vis:
        drivers.append(("Sans vis-à-vis", 0.02))
    if criteria.has_major_works_pending:
        drivers.append(("Gros travaux votés/à prévoir", -0.04))

    total_delta = max(-0.45, min(0.55, sum(d for _, d in drivers)))
    total_adj_pct = round(total_delta * 100.0)
    adj_m2 = round(base_m2 * (1.0 + total_delta)) if base_m2 > 0 else 0
    mid_euro = round((adj_m2 * safe_surface) / 500.0) * 500 if adj_m2 > 0 else 0

    c50 = sum(1 for c in pool if c.distance_meters <= 50)
    c100 = sum(1 for c in pool if c.distance_meters <= 100)
    c300 = sum(1 for c in pool if c.distance_meters <= 300)
    pts50 = 25 if c50 >= 3 else (21 if c50 == 2 else (14 if c50 == 1 else 0))
    pts100 = 20 if c100 >= 4 else (14 if c100 >= 2 else (8 if c100 == 1 else 0))
    pts300 = 20 if c300 >= 10 else (15 if c300 >= 5 else (9 if c300 >= 1 else 0))

    if len(pool) >= 2:
        vals = [c.price_per_m2 for c in pool]
        mean_v = sum(vals) / len(vals)
        cv = (math.sqrt(sum((v - mean_v) ** 2 for v in vals) / len(vals)) / mean_v) if mean_v > 0 else 0.0
        pts_homog = 15 if cv <= 0.12 else (10 if cv <= 0.22 else 5)
    else:
        pts_homog = 6 if len(pool) == 1 else 0

    recent_cnt = sum(1 for c in pool if (ref_year - c.year) <= 2)
    pts_recency = 0 if not pool else (10 if (recent_cnt >= 4 or recent_cnt / len(pool) >= 0.6) else (6 if recent_cnt >= 1 else 3))
    pts_dossier = 10 if (surface_m2 >= 10 and provided_docs_ratio >= 0.5) else (8 if surface_m2 >= 10 else 0)

    total_score = max(0, min(100, pts50 + pts100 + pts300 + pts_homog + pts_recency + pts_dossier))
    score_label = (
        "Très fiable" if total_score >= 80 else
        "Fiable" if total_score >= 60 else
        "Indicatif — Visite requise" if total_score >= 40 else
        "Données DVF insuffisantes"
    )

    spread = 0.05 if total_score >= 65 else 0.08
    low_euro = round((mid_euro * (1.0 - spread)) / 500.0) * 500 if mid_euro > 0 else 0
    high_euro = round((mid_euro * (1.0 + spread)) / 500.0) * 500 if mid_euro > 0 else 0
    latest_date = max((c.date_mutation for c in pool if c.date_mutation), default="")

    justifications = [
        f"{name} : {'+' if delta >= 0 else ''}{delta * 100:.1f}%"
        for name, delta in drivers
    ]

    return DvfValuationReport(
        estimated_low_euro=low_euro,
        estimated_mid_euro=mid_euro,
        estimated_high_euro=high_euro,
        raw_dvf_price_m2=raw_m2,
        adjusted_price_m2=adj_m2,
        total_adjustment_percent=total_adj_pct,
        valuation_score=total_score,
        score_label=score_label,
        comparables_used_count=len(pool),
        data_date=latest_date,
        disclaimer="Estimation algorithmique indicative basée sur les mutations DVF publiques. Ne constitue pas une expertise immobilière certifiée.",
        justifications=justifications,
    )


@dataclass(frozen=True)
class SaleTimelineEstimate:
    status: str  # "READY" | "INSUFFICIENT_DATA"
    low_days: int = 0
    likely_days: int = 0
    high_days: int = 0
    confidence_label: str = ""
    warnings: list[str] = field(default_factory=list)


def estimate_sale_timeline(
    *,
    sector_name: str,
    property_kind: str,
    asking_price_euro: int,
    surface_m2: int,
    dpe: str,
    condition_label: str,
    dvf_price_m2: int = 0,
    dvf_count: int = 0,
    photo_count: int = 0,
    provided_docs_ratio: float = 0.0,
    mandate_type: str = "",
) -> SaleTimelineEstimate:
    if surface_m2 <= 0:
        return SaleTimelineEstimate(
            status="INSUFFICIENT_DATA",
            confidence_label="Données locales insuffisantes",
            warnings=["Renseignez la surface habitable (> 0 m²) pour estimer le délai de vente."],
        )

    sector_ref = next(
        (
            s for s in MONTPELLIER_SECTORS
            if sector_name and (
                s.name.split(" (")[0].lower() in sector_name.lower()
                or sector_name.split(" (")[0].lower() in s.name.lower()
            )
        ),
        None,
    )
    sector_m2 = 0
    if sector_ref is not None:
        sector_m2 = (sector_ref.price_m2_house or sector_ref.price_m2_apartment) if property_kind.lower() == "maison" else sector_ref.price_m2_apartment

    ref_m2 = dvf_price_m2 if dvf_price_m2 > 0 else sector_m2
    if ref_m2 <= 0:
        return SaleTimelineEstimate(
            status="INSUFFICIENT_DATA",
            confidence_label="Données locales insuffisantes",
            warnings=["Données locales insuffisantes : lancez un repère DVF ou sélectionnez un secteur référencé."],
        )

    tension = sector_ref.tension.lower() if sector_ref else ""
    base_days = 38 if tension == "extrême" else (45 if tension == "très forte" else (56 if tension == "forte" else 68))
    delta = 0
    warnings: list[str] = []

    if asking_price_euro > 0:
        gap = (asking_price_euro / surface_m2 - ref_m2) / ref_m2
        if gap <= -0.08:
            delta -= 14
        elif gap <= -0.02:
            delta -= 7
        elif gap <= 0.04:
            delta += 0
        elif gap <= 0.10:
            delta += 14
        elif gap <= 0.18:
            delta += 28
        else:
            delta += 45

    dpe_clean = dpe.strip().upper()
    delta += {"A": -6, "B": -6, "C": -2, "D": 2, "E": 8, "F": 16, "G": 26}.get(dpe_clean, 6)

    cond_l = condition_label.lower()
    if any(k in cond_l for k in ("neuf", "rénové", "excellent")):
        delta -= 6
    elif "rafraîchir" in cond_l:
        delta += 9
    elif "rénover" in cond_l:
        delta += 20

    delta += -5 if photo_count >= 5 else (-2 if photo_count >= 3 else (5 if photo_count >= 1 else 12))
    delta += -6 if provided_docs_ratio >= 0.75 else (-2 if provided_docs_ratio >= 0.40 else 5)
    if mandate_type.lower() == "exclusif":
        delta -= 8
    elif mandate_type.lower() == "semi-exclusif":
        delta -= 4

    likely = max(18, min(240, base_days + delta))
    spread = 0.18 if (dvf_count >= 5 and asking_price_euro > 0) else (0.25 if (dvf_count >= 1 or sector_ref) else 0.32)
    low = max(14, round(likely * (1.0 - spread)))
    high = max(low + 7, round(likely * (1.0 + spread)))
    conf = "Élevée (DVF + secteur)" if (dvf_count >= 5 and asking_price_euro > 0) else ("Moyenne (indicative)" if (dvf_count >= 1 or sector_ref) else "Indicative")

    return SaleTimelineEstimate(
        status="READY",
        low_days=low,
        likely_days=likely,
        high_days=high,
        confidence_label=conf,
        warnings=warnings,
    )


# ---------------------------------------------------------------------------
# 6. Calculateurs Immobiliers Complémentaires (Frais de Notaire, Rendement Locatif, Plus-Value)
# ---------------------------------------------------------------------------
INCREASED_DMTO_DEPTS_2025: frozenset[str] = frozenset({
    "34", "75", "92", "93", "94", "69", "33", "31", "44", "59", "06", "13", "83", "35", "78", "91",
})


@dataclass(frozen=True)
class NotaryFeesResult:
    property_price: int
    is_neuf: bool
    department_code: str
    dmto_taxes_euro: int
    notaire_emoluments_ttc_euro: int
    formalities_disbursements_euro: int
    security_contribution_euro: int
    total_notary_fees_euro: int
    effective_rate_percent: float
    total_acquisition_cost_euro: int
    notes: list[str]


def compute_notary_fees(
    *,
    property_price: int,
    is_neuf: bool = False,
    department_code: str = "34",
    use_increased_dmto_if_applicable: bool = True,
) -> NotaryFeesResult:
    price = max(0, property_price)
    dept = department_code.strip().upper()[:3]
    if price == 0:
        return NotaryFeesResult(
            property_price=0,
            is_neuf=is_neuf,
            department_code=dept,
            dmto_taxes_euro=0,
            notaire_emoluments_ttc_euro=0,
            formalities_disbursements_euro=0,
            security_contribution_euro=0,
            total_notary_fees_euro=0,
            effective_rate_percent=0.0,
            total_acquisition_cost_euro=0,
            notes=["Saisissez un prix d'acquisition positif."],
        )

    # Émoluments proportionnels TTC (barème réglementé + TVA 20%)
    tranche_1 = min(price, 6500) * 0.03870
    tranche_2 = max(0, min(price, 17000) - 6500) * 0.01596
    tranche_3 = max(0, min(price, 60000) - 17000) * 0.01064
    tranche_4 = max(0, price - 60000) * 0.00799
    emoluments_ttc = round((tranche_1 + tranche_2 + tranche_3 + tranche_4) * 1.20)

    if is_neuf:
        # TPF réduite à 0,715 % dans le neuf / VEFA
        dmto_rate = 0.00715
        dmto_note = "Neuf / VEFA : taxe de publicité foncière réduite à 0,715 %."
    else:
        has_raised_dept = use_increased_dmto_if_applicable and dept in INCREASED_DMTO_DEPTS_2025
        # LF 2025 : faculté de porter la part départementale à 5,0 % (+0,5 pt -> ~6,32 % total)
        dmto_rate = 0.063185 if has_raised_dept else 0.0580665
        dmto_note = (
            f"Département {dept or 'standard'} : taux DMTO majoré intégrant la hausse LF 2025 (+0,5 pt, hors primo-accédant exonéré)."
            if has_raised_dept
            else f"Département {dept or 'standard'} : barème DMTO classique (5,81 %)."
        )

    dmto = round(price * dmto_rate)
    formalities = 1200
    csi = max(15, round(price * 0.001))
    total_fees = dmto + emoluments_ttc + formalities + csi
    effective_rate = round((total_fees / price) * 1000.0) / 10.0

    return NotaryFeesResult(
        property_price=price,
        is_neuf=is_neuf,
        department_code=dept,
        dmto_taxes_euro=dmto,
        notaire_emoluments_ttc_euro=emoluments_ttc,
        formalities_disbursements_euro=formalities,
        security_contribution_euro=csi,
        total_notary_fees_euro=total_fees,
        effective_rate_percent=effective_rate,
        total_acquisition_cost_euro=price + total_fees,
        notes=[
            dmto_note,
            "Simulation indicative hors frais d'hypothèque ou de garantie bancaire (cautionnement).",
        ],
    )


@dataclass(frozen=True)
class RentalYieldResult:
    total_investment_euro: int
    annual_gross_rent_euro: int
    annual_effective_rent_euro: int
    annual_charges_euro: int
    annual_net_rent_euro: int
    gross_yield_percent: float
    net_yield_percent: float
    monthly_cashflow_euro: int
    dpe_regulatory_alert: str
    is_rentable_compliant_now: bool


def compute_rental_yield(
    *,
    purchase_price: int,
    notary_fees: int,
    works_cost: int = 0,
    monthly_rent_hc: int = 950,
    vacancy_months_per_year: float = 0.5,
    non_recoverable_charges_year: int = 600,
    property_tax_year: int = 900,
    management_fee_percent: float = 6.5,
    insurance_pno_gli_year: int = 380,
    monthly_loan_payment: int = 0,
    dpe: str = "C",
) -> RentalYieldResult:
    total_inv = max(1, max(0, purchase_price) + max(0, notary_fees) + max(0, works_cost))
    rent_m = max(0, monthly_rent_hc)
    vacancy = min(12.0, max(0.0, vacancy_months_per_year))

    annual_gross = rent_m * 12
    effective_months = max(0.0, 12.0 - vacancy)
    annual_effective = round(rent_m * effective_months)
    mgmt_fees = round(annual_effective * (max(0.0, min(20.0, management_fee_percent)) / 100.0))
    annual_charges = (
        max(0, non_recoverable_charges_year)
        + max(0, property_tax_year)
        + mgmt_fees
        + max(0, insurance_pno_gli_year)
    )
    annual_net = annual_effective - annual_charges

    gross_yield = round((annual_gross / total_inv) * 10000.0) / 100.0
    net_yield = round((annual_net / total_inv) * 10000.0) / 100.0
    monthly_net_before_debt = annual_net / 12.0
    monthly_cashflow = round(monthly_net_before_debt - max(0, monthly_loan_payment))

    dpe_clean = dpe.strip().upper()
    if dpe_clean == "G":
        dpe_alert = "Alerte réglementaire : les logements classés G sont interdits à la location depuis le 1er janvier 2025 (rénovation énergétique requise)."
        compliant = False
    elif dpe_clean == "F":
        dpe_alert = "Vigilance réglementaire : loyer gelé et interdiction de location prévue au 1er janvier 2028 (Loi Climat et Résilience)."
        compliant = True
    elif dpe_clean == "E":
        dpe_alert = "Horizon 2034 : prévoir un plan de travaux de performance énergétique pour sécuriser la valeur locative."
        compliant = True
    else:
        dpe_alert = f"DPE {dpe_clean} conforme pour la mise en location longue durée."
        compliant = True

    return RentalYieldResult(
        total_investment_euro=total_inv,
        annual_gross_rent_euro=annual_gross,
        annual_effective_rent_euro=annual_effective,
        annual_charges_euro=annual_charges,
        annual_net_rent_euro=annual_net,
        gross_yield_percent=gross_yield,
        net_yield_percent=net_yield,
        monthly_cashflow_euro=monthly_cashflow,
        dpe_regulatory_alert=dpe_alert,
        is_rentable_compliant_now=compliant,
    )


@dataclass(frozen=True)
class CapitalGainResult:
    is_exempt: bool
    exemption_reason: str
    gross_capital_gain_euro: int
    adjusted_purchase_price_euro: int
    ir_abatement_percent: float
    ps_abatement_percent: float
    taxable_gain_ir_euro: int
    taxable_gain_ps_euro: int
    income_tax_19_euro: int
    social_levies_17_2_euro: int
    surtax_euro: int
    total_tax_euro: int
    net_gain_after_tax_euro: int
    disclaimer: str


def _french_ir_abatement_pct(years: int) -> float:
    if years < 6:
        return 0.0
    if years >= 22:
        return 100.0
    if years <= 21:
        return (years - 5) * 6.0
    return 100.0


def _french_ps_abatement_pct(years: int) -> float:
    if years < 6:
        return 0.0
    if years >= 30:
        return 100.0
    if years <= 21:
        return round((years - 5) * 1.65, 2)
    if years == 22:
        return round((16 * 1.65) + 1.60, 2)  # 28.0%
    return round(28.0 + (years - 22) * 9.0, 2)


def _french_capital_gain_surtax(taxable_ir: int) -> int:
    # Art. 1609 nonies G CGI (applicable lorsque la plus-value imposable IR > 50 000 €)
    if taxable_ir <= 50_000:
        return 0
    if taxable_ir <= 60_000:
        return round(0.02 * taxable_ir - (60_000 - taxable_ir) * 1 / 20)
    if taxable_ir <= 100_000:
        return round(0.02 * taxable_ir)
    if taxable_ir <= 110_000:
        return round(0.03 * taxable_ir - (110_000 - taxable_ir) * 1 / 10)
    if taxable_ir <= 150_000:
        return round(0.03 * taxable_ir)
    if taxable_ir <= 160_000:
        return round(0.04 * taxable_ir - (160_000 - taxable_ir) * 0.15)
    if taxable_ir <= 200_000:
        return round(0.04 * taxable_ir)
    if taxable_ir <= 210_000:
        return round(0.05 * taxable_ir - (210_000 - taxable_ir) * 0.20)
    if taxable_ir <= 250_000:
        return round(0.05 * taxable_ir)
    if taxable_ir <= 260_000:
        return round(0.06 * taxable_ir - (260_000 - taxable_ir) * 0.25)
    return round(0.06 * taxable_ir)


def compute_capital_gain_tax(
    *,
    purchase_price: int,
    sale_price: int,
    holding_years: int,
    is_primary_residence: bool = False,
    use_flat_acquisition_7_5_pct: bool = True,
    actual_acquisition_fees: int = 0,
    use_flat_works_15_pct: bool = True,
    actual_works_cost: int = 0,
    sale_agency_fees_seller_paid: int = 0,
) -> CapitalGainResult:
    disclaimer = (
        "Calcul indicatif basé sur le barème général CGI des plus-values immobilières des particuliers. "
        "Ne constitue pas une déclaration fiscale officielle (valider avec votre notaire)."
    )
    years = max(0, holding_years)
    buy_p = max(0, purchase_price)
    sell_p = max(0, sale_price)

    acq_addition = round(buy_p * 0.075) if use_flat_acquisition_7_5_pct else max(0, actual_acquisition_fees)
    if use_flat_works_15_pct:
        works_addition = round(buy_p * 0.15) if years > 5 else 0
    else:
        works_addition = max(0, actual_works_cost)

    adjusted_buy = buy_p + acq_addition + works_addition
    effective_sell = max(0, sell_p - max(0, sale_agency_fees_seller_paid))
    gross_gain = max(0, effective_sell - adjusted_buy)

    if is_primary_residence:
        return CapitalGainResult(
            is_exempt=True,
            exemption_reason="Exonération totale : cession de résidence principale (Art. 150 U II-1° CGI).",
            gross_capital_gain_euro=gross_gain,
            adjusted_purchase_price_euro=adjusted_buy,
            ir_abatement_percent=100.0,
            ps_abatement_percent=100.0,
            taxable_gain_ir_euro=0,
            taxable_gain_ps_euro=0,
            income_tax_19_euro=0,
            social_levies_17_2_euro=0,
            surtax_euro=0,
            total_tax_euro=0,
            net_gain_after_tax_euro=gross_gain,
            disclaimer=disclaimer,
        )

    if sell_p <= 15_000 and sell_p > 0:
        return CapitalGainResult(
            is_exempt=True,
            exemption_reason="Exonération légale : prix de cession ≤ 15 000 €.",
            gross_capital_gain_euro=gross_gain,
            adjusted_purchase_price_euro=adjusted_buy,
            ir_abatement_percent=100.0,
            ps_abatement_percent=100.0,
            taxable_gain_ir_euro=0,
            taxable_gain_ps_euro=0,
            income_tax_19_euro=0,
            social_levies_17_2_euro=0,
            surtax_euro=0,
            total_tax_euro=0,
            net_gain_after_tax_euro=gross_gain,
            disclaimer=disclaimer,
        )

    if gross_gain == 0:
        return CapitalGainResult(
            is_exempt=True,
            exemption_reason="Aucune plus-value taxable après majoration du prix d'acquisition.",
            gross_capital_gain_euro=0,
            adjusted_purchase_price_euro=adjusted_buy,
            ir_abatement_percent=_french_ir_abatement_pct(years),
            ps_abatement_percent=_french_ps_abatement_pct(years),
            taxable_gain_ir_euro=0,
            taxable_gain_ps_euro=0,
            income_tax_19_euro=0,
            social_levies_17_2_euro=0,
            surtax_euro=0,
            total_tax_euro=0,
            net_gain_after_tax_euro=0,
            disclaimer=disclaimer,
        )

    ir_ab = _french_ir_abatement_pct(years)
    ps_ab = _french_ps_abatement_pct(years)
    taxable_ir = round(gross_gain * (1.0 - ir_ab / 100.0))
    taxable_ps = round(gross_gain * (1.0 - ps_ab / 100.0))

    ir_tax = round(taxable_ir * 0.19)
    ps_tax = round(taxable_ps * 0.172)
    surtax = max(0, _french_capital_gain_surtax(taxable_ir))
    total_tax = ir_tax + ps_tax + surtax
    is_fully_exempt = years >= 30 and total_tax == 0
    reason = (
        "Exonération totale d'IR et de prélèvements sociaux (détention ≥ 30 ans)."
        if is_fully_exempt
        else ("Exonération d'impôt sur le revenu (≥ 22 ans), prélèvements sociaux résiduels jusqu'à 30 ans." if years >= 22 else "")
    )

    return CapitalGainResult(
        is_exempt=is_fully_exempt,
        exemption_reason=reason,
        gross_capital_gain_euro=gross_gain,
        adjusted_purchase_price_euro=adjusted_buy,
        ir_abatement_percent=ir_ab,
        ps_abatement_percent=ps_ab,
        taxable_gain_ir_euro=taxable_ir,
        taxable_gain_ps_euro=taxable_ps,
        income_tax_19_euro=ir_tax,
        social_levies_17_2_euro=ps_tax,
        surtax_euro=surtax,
        total_tax_euro=total_tax,
        net_gain_after_tax_euro=max(0, gross_gain - total_tax),
        disclaimer=disclaimer,
    )

