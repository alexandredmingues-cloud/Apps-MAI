from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timezone
from enum import Enum
import re

from app.core.rbac import Permission, TenantContext
from app.core.security import InputSanitizer
from app.domain.country_packs import CountryCode, get_country_pack


class NewsCategory(str, Enum):
    TAUX_CREDIT = "TAUX_CREDIT"
    MARCHE_LOCAL = "MARCHE_LOCAL"
    LOIS_FISCALITE = "LOIS_FISCALITE"
    DPE_TRAVAUX = "DPE_TRAVAUX"
    LOCATION_COPRO = "LOCATION_COPRO"
    INVESTISSEMENT = "INVESTISSEMENT"
    METIER_CONSEILLER = "METIER_CONSEILLER"


class AudienceScope(str, Enum):
    BOTH = "BOTH"
    PRO_ONLY = "PRO_ONLY"
    PUBLIC_ONLY = "PUBLIC_ONLY"


class ArticleStatus(str, Enum):
    DRAFT = "DRAFT"
    PUBLISHED = "PUBLISHED"
    ARCHIVED = "ARCHIVED"


LEGAL_DISCLAIMER_BY_COUNTRY: dict[CountryCode, str] = {
    CountryCode.FR: (
        "Information générale et synthèse analytique à titre indicatif (droit français) : "
        "ne constitue ni un conseil juridique, ni une consultation fiscale officielle."
    ),
    CountryCode.PT: (
        "Informação geral e síntese indicativa (enquadramento jurídico português) : "
        "não constitui aconselhamento jurídico, fiscal ou financeiro oficial."
    ),
}
LEGAL_DISCLAIMER_DEFAULT = LEGAL_DISCLAIMER_BY_COUNTRY[CountryCode.FR]


@dataclass(frozen=True)
class NewsSource:
    id: str
    country_code: CountryCode
    locale: str
    name: str
    domain: str
    base_url: str
    reliability_tier: str  # OFFICIELLE | INSTITUTIONNELLE | PROFESSIONNELLE
    is_active: bool = True
    last_verified_at: str = "2026-11-25"


@dataclass(frozen=True)
class NewsArticle:
    id: str
    slug: str
    country_code: CountryCode
    locale: str
    regulation_jurisdiction: str
    source_id: str
    source_name: str
    source_url: str
    category: NewsCategory
    audience_scope: AudienceScope
    city: str
    department_code: str
    region_name: str
    title: str
    seo_meta_title: str
    seo_meta_description: str
    public_summary: str
    public_takeaways: tuple[str, ...]
    pro_analysis: str
    pro_commercial_arguments: tuple[str, ...]
    pro_key_metrics: dict[str, str]
    ai_dual_generated: bool = False
    legal_disclaimer: str = LEGAL_DISCLAIMER_BY_COUNTRY[CountryCode.FR]
    status: ArticleStatus = ArticleStatus.PUBLISHED
    published_at: str = "2026-11-25"


@dataclass(frozen=True)
class UserNewsPreferences:
    user_id: str
    country_code: CountryCode = CountryCode.FR
    locale: str = "fr-FR"
    city: str = ""
    department_code: str = ""
    region_name: str = "National"
    interests: tuple[NewsCategory, ...] = (
        NewsCategory.TAUX_CREDIT,
        NewsCategory.MARCHE_LOCAL,
        NewsCategory.LOIS_FISCALITE,
        NewsCategory.DPE_TRAVAUX,
    )
    notifications_enabled: bool = False
    rgpd_consent_granted: bool = False
    rgpd_consent_date: str | None = None


@dataclass(frozen=True)
class NewsBookmark:
    user_id: str
    article_id: str
    alert_on_update: bool = True
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


def _slugify(title: str) -> str:
    lowered = title.lower().strip()
    slug = re.sub(r"[^a-z0-9]+", "-", lowered).strip("-")
    return slug[:120] or "actualite-immobiliere"


def _seed_sources() -> dict[str, NewsSource]:
    items = [
        # --- FRANCE (Priorité 1 & 2) ---
        NewsSource(
            id="src-bdf",
            country_code=CountryCode.FR,
            locale="fr-FR",
            name="Banque de France — Crédits aux particuliers",
            domain="webstat.banque-france.fr",
            base_url="https://www.banque-france.fr/fr/statistiques/taux-et-credits",
            reliability_tier="OFFICIELLE",
        ),
        NewsSource(
            id="src-notaires",
            country_code=CountryCode.FR,
            locale="fr-FR",
            name="Notaires de France — Indices & Conjoncture",
            domain="www.notaires.fr",
            base_url="https://www.notaires.fr/fr/immobilier-fiscalite/etudes-et-analyses-immobilieres",
            reliability_tier="INSTITUTIONNELLE",
        ),
        NewsSource(
            id="src-service-public",
            country_code=CountryCode.FR,
            locale="fr-FR",
            name="Service-Public.fr — Logement & Fiscalité",
            domain="www.service-public.fr",
            base_url="https://www.service-public.fr/particuliers/vosdroits/N19808",
            reliability_tier="OFFICIELLE",
        ),
        NewsSource(
            id="src-legifrance",
            country_code=CountryCode.FR,
            locale="fr-FR",
            name="Légifrance — Textes législatifs & décrets",
            domain="www.legifrance.gouv.fr",
            base_url="https://www.legifrance.gouv.fr",
            reliability_tier="OFFICIELLE",
        ),
        NewsSource(
            id="src-ademe",
            country_code=CountryCode.FR,
            locale="fr-FR",
            name="ADEME — Observatoire DPE-Audit",
            domain="observatoire-dpe-audit.ademe.fr",
            base_url="https://observatoire-dpe-audit.ademe.fr",
            reliability_tier="OFFICIELLE",
        ),
        NewsSource(
            id="src-dvf",
            country_code=CountryCode.FR,
            locale="fr-FR",
            name="Etalab / DGFiP — Demandes de Valeurs Foncières",
            domain="app.dvf.etalab.gouv.fr",
            base_url="https://app.dvf.etalab.gouv.fr",
            reliability_tier="OFFICIELLE",
        ),
        # --- PORTUGAL (Priorité 4 — Module national isolé) ---
        NewsSource(
            id="src-pt-bdp",
            country_code=CountryCode.PT,
            locale="pt-PT",
            name="Banco de Portugal — Crédito à Habitação & DSTI",
            domain="www.bportugal.pt",
            base_url="https://www.bportugal.pt/area-cidadao/credito-habitacao",
            reliability_tier="OFFICIELLE",
        ),
        NewsSource(
            id="src-pt-ine",
            country_code=CountryCode.PT,
            locale="pt-PT",
            name="INE Portugal — Índice de Preços da Habitação",
            domain="www.ine.pt",
            base_url="https://www.ine.pt",
            reliability_tier="OFFICIELLE",
        ),
        NewsSource(
            id="src-pt-dre",
            country_code=CountryCode.PT,
            locale="pt-PT",
            name="Diário da República / Portal das Finanças (IMT)",
            domain="diariodarepublica.pt",
            base_url="https://diariodarepublica.pt",
            reliability_tier="OFFICIELLE",
        ),
        NewsSource(
            id="src-pt-adene",
            country_code=CountryCode.PT,
            locale="pt-PT",
            name="ADENE — Sistema de Certificação Energética",
            domain="www.sce.pt",
            base_url="https://www.sce.pt",
            reliability_tier="OFFICIELLE",
        ),
    ]
    return {s.id: s for s in items}


def _seed_articles() -> list[NewsArticle]:
    return [
        # =================== FRANCE (Priorité 1 & 2) ===================
        NewsArticle(
            id="art-taux-hcsf-2026",
            slug="taux-credit-immobilier-stabilisation-2026-capacite-hcsf",
            country_code=CountryCode.FR,
            locale="fr-FR",
            regulation_jurisdiction="FR_HCSF_BANQUE_DE_FRANCE",
            source_id="src-bdf",
            source_name="Banque de France — Crédits aux particuliers",
            source_url="https://www.banque-france.fr/fr/statistiques/taux-et-credits",
            category=NewsCategory.TAUX_CREDIT,
            audience_scope=AudienceScope.BOTH,
            city="",
            department_code="",
            region_name="National",
            title="France : Taux de crédit immobilier autour de 3,25 % à 3,40 % et règle HCSF des 35 %",
            seo_meta_title="Taux immobilier France 2026 : capacité d'emprunt HCSF | Monty",
            seo_meta_description="Analyse nationale des taux de crédit immobilier en France et calcul de capacité d'emprunt sous la règle HCSF des 35 %.",
            public_summary=(
                "Sur l'ensemble du marché français, les taux moyens sur 20 et 25 ans se stabilisent entre 3,20 % et 3,45 % "
                "hors assurance. Les établissements bancaires restent encadrés par le plafond HCSF (35 % d'endettement maximal "
                "assurance comprise et 25 ans maximum hors VEFA/rénovation lourde)."
            ),
            public_takeaways=(
                "Comparez le TAEG complet (taux nominal + assurance emprunteur + garantie).",
                "Un écart de 0,30 pt sur 25 ans représente ~8 000 € de pouvoir d'achat immobilier pour 250 000 € empruntés.",
                "Simulez votre mensualité et votre reste à vivre avec le simulateur HCSF France avant vos visites.",
            ),
            pro_analysis=(
                "La stabilisation des grilles bancaires nationales redonne de la visibilité aux plans de financement, "
                "mais le taux d'effort HCSF (35 %) reste le premier filtre bloquant. La négociation se joue sur "
                "la délégation d'assurance (Loi Lemoine) et la couverture des frais de notaire par l'apport."
            ),
            pro_commercial_arguments=(
                "Chiffrez immédiatement le pouvoir d'achat net avec le calculateur HCSF lors de l'entretien découverte.",
                "Valorisez la délégation d'assurance pour regagner 25 à 45 €/mois de capacité de remboursement.",
                "Exigez une attestation de financement récente avant de soumettre une offre d'achat au vendeur.",
            ),
            pro_key_metrics={"Taux 20 ans FR": "3,25 %", "Taux 25 ans FR": "3,35 %", "Plafond HCSF": "35 % / 25 ans"},
            published_at="2026-11-24",
        ),
        NewsArticle(
            id="art-marche-france-metropoles",
            slug="marche-immobilier-france-metropoles-ecart-dpe-delais-2026",
            country_code=CountryCode.FR,
            locale="fr-FR",
            regulation_jurisdiction="FR_DVF_NOTAIRES",
            source_id="src-notaires",
            source_name="Notaires de France — Indices & Conjoncture",
            source_url="https://www.notaires.fr/fr/immobilier-fiscalite/etudes-et-analyses-immobilieres",
            category=NewsCategory.MARCHE_LOCAL,
            audience_scope=AudienceScope.BOTH,
            city="Lyon",
            department_code="69",
            region_name="Auvergne-Rhône-Alpes",
            title="Métropoles françaises (Paris, Lyon, Bordeaux, Nantes, Montpellier) : reprise sélective et prime aux DPE A–C",
            seo_meta_title="Tendances prix immobilier France et métropoles 2026 | Monty",
            seo_meta_description="Tendances nationales des transactions immobilières en France : écarts de délais selon le DPE et le positionnement prix DVF.",
            public_summary=(
                "Dans les grandes agglomérations françaises comme dans les villes moyennes attractives, la demande se concentre sur "
                "les biens bien situés, proches des transports et classés DPE A à C. Les biens affichés au prix médian DVF se vendent "
                "sensiblement plus vite que ceux nécessitant des travaux énergétiques lourds."
            ),
            public_takeaways=(
                "Appuyez toujours votre offre ou votre mise en vente sur les mutations DVF réelles de votre commune.",
                "Un dossier technique complet dès la mise en ligne rassure les acheteurs et accélère le compromis.",
                "La recherche par temps de trajet (train, tram, vélo, voiture) permet d'arbitrer efficacement entre cœur de métropole et première couronne.",
            ),
            pro_analysis=(
                "Au niveau national, toute surcote supérieure à +6/+8 % par rapport à la médiane DVF pondérée allonge le délai "
                "d'exposition de +15 à +30 jours. La transparence sur les travaux et le DPE réduit les renégociations post-visite."
            ),
            pro_commercial_arguments=(
                "Présentez systématiquement 5 comparables DVF locaux (< 300 m) lors de l'avis de valeur vendeur.",
                "Démontrez la corrélation prix juste / délai estimé pour défendre un mandat exclusif qualifié.",
            ),
            pro_key_metrics={"Délai médian au prix": "45–60 j", "Prime valeur verte": "+5 à +12 %", "Base": "Notaires / DVF"},
            published_at="2026-11-22",
        ),
        NewsArticle(
            id="art-marche-montpellier-occitanie",
            slug="marche-immobilier-montpellier-herault-prix-delais-2026",
            country_code=CountryCode.FR,
            locale="fr-FR",
            regulation_jurisdiction="FR_DVF_NOTAIRES",
            source_id="src-notaires",
            source_name="Notaires de France — Indices & Conjoncture",
            source_url="https://www.notaires.fr/fr/immobilier-fiscalite/etudes-et-analyses-immobilieres",
            category=NewsCategory.MARCHE_LOCAL,
            audience_scope=AudienceScope.BOTH,
            city="Montpellier",
            department_code="34",
            region_name="Occitanie",
            title="Occitanie & Hérault (Montpellier) : tension soutenue sur les axes tramway et Castelnau/Lattes",
            seo_meta_title="Prix immobilier Montpellier et Hérault (34) | Monty",
            seo_meta_description="Tendances locales DVF et notaires en Occitanie / Hérault : prix au m², délais de vente et impact énergétique.",
            public_summary=(
                "Dans l'Hérault et la métropole de Montpellier, les quartiers connectés au tramway et les communes périurbaines "
                "maintiennent une demande active pour les biens familiaux sans gros travaux énergétiques."
            ),
            public_takeaways=(
                "Comparez les quartiers centraux et les communes desservies par TER/tram selon votre temps de trajet réel.",
                "Exigez les 3 derniers PV d'AG et l'état des charges avant toute offre en copropriété.",
            ),
            pro_analysis=(
                "Micro-marché héraultais caractérisé par une forte sélectivité : écart de valorisation de 12 à 18 % entre "
                "biens rénovés DPE B/C et passoires F/G."
            ),
            pro_commercial_arguments=(
                "Utilisez les isochrones tram/TER pour élargir les recherches acquéreurs sur l'axe Castelnau / Baillargues / Lattes.",
            ),
            pro_key_metrics={"Tension 34": "Forte", "Délai cœur": "38–56 j", "DPE cible": "A à C"},
            published_at="2026-11-21",
        ),
        NewsArticle(
            id="art-dpe-2026-coefficient",
            slug="reforme-dpe-2026-chauffage-electrique-location-pas-a-pas",
            country_code=CountryCode.FR,
            locale="fr-FR",
            regulation_jurisdiction="FR_LOI_CLIMAT_DPE",
            source_id="src-ademe",
            source_name="ADEME — Observatoire DPE-Audit",
            source_url="https://observatoire-dpe-audit.ademe.fr",
            category=NewsCategory.DPE_TRAVAUX,
            audience_scope=AudienceScope.BOTH,
            city="",
            department_code="",
            region_name="National",
            title="France — DPE et location : facteur de conversion électrique et calendrier d'interdiction F & G",
            seo_meta_title="DPE France 2026 et interdiction de location F et G | Monty",
            seo_meta_description="Comprendre les règles françaises du DPE, l'interdiction locative des logements G (2025) et F (2028) et la valeur verte.",
            public_summary=(
                "En France, les logements classés G ne peuvent plus être remis en location depuis le 1er janvier 2025, et l'échéance 2028 "
                "concernera les biens classés F. La mise à jour du coefficient de conversion électrique peut améliorer la note de "
                "certains petits logements chauffés à l'électricité : vérifiez l'attestation ADEME officielle."
            ),
            public_takeaways=(
                "Avant tout achat locatif en France, contrôlez la classe DPE et chiffrez les travaux dans votre rendement net.",
                "Un DPE G interdit de signer un nouveau bail d'habitation longue durée sans rénovation.",
                "Un saut de classe F vers C sécurise la liquidité à la revente.",
            ),
            pro_analysis=(
                "L'audit énergétique réglementaire (obligatoire à la vente pour les monopropriétés E, F, G) structure désormais "
                "les négociations dès la première visite. Intégrez-le dans le dossier vendeur dès la signature du mandat."
            ),
            pro_commercial_arguments=(
                "Simulez le rendement locatif net après travaux pour lever les objections des investisseurs.",
                "Faites éditer l'attestation ADEME actualisée sur les studios tout-électrique avant diffusion.",
            ),
            pro_key_metrics={"Classe G": "Interdite (2025)", "Classe F": "Échéance 2028", "Audit vente": "E/F/G monoprop."},
            published_at="2026-11-19",
        ),
        NewsArticle(
            id="art-loi-le-meur-fiscalite",
            slug="loi-le-meur-meuble-touristique-fiscalite-droits-locataires",
            country_code=CountryCode.FR,
            locale="fr-FR",
            regulation_jurisdiction="FR_LOI_LE_MEUR_CGI",
            source_id="src-legifrance",
            source_name="Légifrance — Textes législatifs & décrets",
            source_url="https://www.legifrance.gouv.fr",
            category=NewsCategory.LOIS_FISCALITE,
            audience_scope=AudienceScope.BOTH,
            city="",
            department_code="",
            region_name="National",
            title="France — Loi Le Meur & meublé touristique : nouveaux seuils micro-BIC, DPE et pouvoirs des communes",
            seo_meta_title="Loi Le Meur France : fiscalité meublée et location | Monty",
            seo_meta_description="Décryptage des règles françaises sur les meublés touristiques, les abattements micro-BIC et les obligations en copropriété.",
            public_summary=(
                "La régulation française des meublés de tourisme durcit les exigences DPE et abaisse certains abattements micro-BIC "
                "afin de favoriser la location à l'année. Les mairies disposent de pouvoirs renforcés (quotas, plafond à 90 jours pour "
                "la résidence principale)."
            ),
            public_takeaways=(
                "Consultez le règlement de copropriété et la réglementation municipale avant tout projet saisonnier.",
                "Comparez location nue, LMNP au réel et bail mobilité via le calculateur de rendement Monty.",
            ),
            pro_analysis=(
                "Le rééquilibrage fiscal réoriente de nombreux propriétaires vers le bail nu/meublé classique ou la vente de "
                "lots T1/T2 urbains : opportunité directe de prise de mandats en zones tendues."
            ),
            pro_commercial_arguments=(
                "Proposez un comparatif arbitrage location longue durée vs cession avec simulation de plus-value CGI.",
            ),
            pro_key_metrics={"DPE meublé": "Obligatoire", "Plafond RP": "Jusqu'à 90 j/an", "Arbitrage": "LMNP réel / Vente"},
            published_at="2026-11-17",
        ),
        NewsArticle(
            id="art-ptz-dmto-notaire",
            slug="ptz-elargi-frais-notaire-dmto-departements-2026",
            country_code=CountryCode.FR,
            locale="fr-FR",
            regulation_jurisdiction="FR_LF_DMTO_PTZ",
            source_id="src-service-public",
            source_name="Service-Public.fr — Logement & Fiscalité",
            source_url="https://www.service-public.fr/particuliers/vosdroits/N19808",
            category=NewsCategory.INVESTISSEMENT,
            audience_scope=AudienceScope.BOTH,
            city="Paris",
            department_code="75",
            region_name="Île-de-France",
            title="France — Frais de notaire (DMTO départementaux) et PTZ élargi : optimiser votre budget d'achat",
            seo_meta_title="Frais de notaire anciens vs neufs et PTZ France | Monty",
            seo_meta_description="Comprendre la composition des frais de notaire en France par département (ancien vs neuf) et l'apport du PTZ.",
            public_summary=(
                "En France, les frais d'acte recouvrent majoritairement des droits fiscaux (DMTO) variables selon le département "
                "et des émoluments réglementés. Comptez ~7,5 à 8,3 % dans l'ancien contre ~2,2 à 3 % dans le neuf."
            ),
            public_takeaways=(
                "Estimez vos frais par département (ancien vs neuf) avec le calculateur Monty avant d'arrêter votre apport.",
                "Vérifiez les exonérations primo-accédant votées localement et votre éligibilité au PTZ élargi.",
            ),
            pro_analysis=(
                "Dans les départements ayant relevé la part départementale à 5,0 %, l'écart atteint ~1 500 € par tranche de 300 000 €. "
                "Valorisez les régimes d'exonération primo-accédant et l'inventaire mobilier justifié."
            ),
            pro_commercial_arguments=(
                "Détaillez DMTO vs émoluments notariés en rendez-vous pour rassurer l'acquéreur sur la transparence des coûts.",
            ),
            pro_key_metrics={"Ancien FR": "~7,5–8,3 %", "Neuf / VEFA FR": "~2,2–3,0 %", "PTZ": "Élargi France"},
            published_at="2026-11-14",
        ),
        NewsArticle(
            id="art-pro-mandat-exclusif-argumentaire",
            slug="pro-argumentaire-mandat-exclusif-prix-marche-et-delai",
            country_code=CountryCode.FR,
            locale="fr-FR",
            regulation_jurisdiction="FR_LOI_HOGUET_ALUR",
            source_id="src-dvf",
            source_name="Etalab / DGFiP — Demandes de Valeurs Foncières",
            source_url="https://app.dvf.etalab.gouv.fr",
            category=NewsCategory.METIER_CONSEILLER,
            audience_scope=AudienceScope.PRO_ONLY,
            city="Bordeaux",
            department_code="33",
            region_name="Nouvelle-Aquitaine",
            title="[Espace Pro France] Défendre le mandat exclusif au prix DVF juste pour réduire le délai de vente",
            seo_meta_title="",
            seo_meta_description="",
            public_summary="",
            public_takeaways=(),
            pro_analysis=(
                "Briefing réservé aux conseillers France : un mandat exclusif couplé à un dossier pré-ALUR complet "
                "et au matching acquéreurs HCSF réduit le délai médian de commercialisation de 14 à 19 jours."
            ),
            pro_commercial_arguments=(
                "Appuyez la stratégie de prix sur les ventes notariées DVF réelles.",
                "Mettez en avant les acquéreurs financés immédiatement identifiés dans le CRM Monty.",
            ),
            pro_key_metrics={"Impact surcote +10 %": "+14 à +28 j", "Dossier complet": "-6 j", "Exclusivité": "-8 j"},
            published_at="2026-11-12",
        ),
        NewsArticle(
            id="art-archive-bilan-2025",
            slug="bilan-marche-immobilier-2025-historique",
            country_code=CountryCode.FR,
            locale="fr-FR",
            regulation_jurisdiction="FR_DVF_NOTAIRES",
            source_id="src-notaires",
            source_name="Notaires de France — Indices & Conjoncture",
            source_url="https://www.notaires.fr/fr/immobilier-fiscalite/etudes-et-analyses-immobilieres",
            category=NewsCategory.MARCHE_LOCAL,
            audience_scope=AudienceScope.BOTH,
            city="",
            department_code="",
            region_name="National",
            title="France — Rétrospective 2025 : atterrissage des prix et normalisation progressive du crédit",
            seo_meta_title="Historique marché immobilier France 2025 | Monty",
            seo_meta_description="Archive analytique des transactions immobilières et des taux en France en 2025.",
            public_summary="Archive de référence sur l'évolution du marché immobilier français et des conditions d'emprunt fin 2025.",
            public_takeaways=("Consultez l'historique pour situer l'évolution des taux dans le cycle immobilier.",),
            pro_analysis="Base historique nationale pour contextualiser la reprise des volumes 2026.",
            pro_commercial_arguments=("Illustrez la stabilisation des prix et le retour de la solvabilité.",),
            pro_key_metrics={"Période": "Archive 2025"},
            status=ArticleStatus.ARCHIVED,
            published_at="2025-12-20",
        ),
        # =================== PORTUGAL (Priorité 4 — Module national isolé) ===================
        NewsArticle(
            id="art-pt-bdp-dsti-credito",
            slug="portugal-banco-de-portugal-regras-dsti-prazos-credito-habitacao",
            country_code=CountryCode.PT,
            locale="pt-PT",
            regulation_jurisdiction="PT_BANCO_DE_PORTUGAL",
            source_id="src-pt-bdp",
            source_name="Banco de Portugal — Crédito à Habitação & DSTI",
            source_url="https://www.bportugal.pt/area-cidadao/credito-habitacao",
            category=NewsCategory.TAUX_CREDIT,
            audience_scope=AudienceScope.BOTH,
            city="Lisboa",
            department_code="11",
            region_name="Área Metropolitana de Lisboa",
            title="Portugal — Regras Banco de Portugal: limites de taxa de esforço (DSTI), LTV e prazos por idade",
            seo_meta_title="Crédito Habitação Portugal : regras DSTI e LTV Banco de Portugal | Monty",
            seo_meta_description="Guia claro das regras macroprudenciais do Banco de Portugal para crédito habitação: DSTI, maturidade por idade e LTV.",
            public_summary=(
                "No mercado português, o Banco de Portugal define limites prudenciais distintos das regras francesas : "
                "taxa de esforço (DSTI) de referência até 50 % com teste de stress de taxa, LTV máximo de 90 % para "
                "habitação própria e permanente (HPP) e prazos máximos de 30 a 40 anos consoante a idade do mutuário."
            ),
            public_takeaways=(
                "Para HPP, o financiamento bancário pode ir até 90 % do menor valor entre aquisição e avaliação.",
                "Idade ≤ 30 anos : prazo até 40 anos; 30–35 anos : até 37 anos; > 35 anos : até 35 anos.",
                "Simule o IMT e o Imposto do Selo especificamente com o módulo Portugal Monty (nunca com regras francesas).",
            ),
            pro_analysis=(
                "Análise Pro Portugal : validar sempre o rácio DSTI com margem de subida da Euribor nas taxas variáveis/mistas "
                "e confirmar a avaliação bancária prévia para evitar lacunas de capital próprio."
            ),
            pro_commercial_arguments=(
                "Preparar simulação IMT + Imposto do Selo (0,8 % + 0,6 % crédito) antes do CPCV.",
                "Verificar elegibilidade IMT Jovem (≤ 35 anos, HPP até 316 772 € isento).",
            ),
            pro_key_metrics={"LTV máx HPP": "90 %", "LTV Secundária": "80 %", "Prazos": "35–40 anos"},
            legal_disclaimer=LEGAL_DISCLAIMER_BY_COUNTRY[CountryCode.PT],
            published_at="2026-11-24",
        ),
        NewsArticle(
            id="art-pt-imt-jovem-escritura",
            slug="portugal-imt-jovem-isencao-imposto-selo-escritura",
            country_code=CountryCode.PT,
            locale="pt-PT",
            regulation_jurisdiction="PT_CIMT_CIS",
            source_id="src-pt-dre",
            source_name="Diário da República / Portal das Finanças (IMT)",
            source_url="https://diariodarepublica.pt",
            category=NewsCategory.LOIS_FISCALITE,
            audience_scope=AudienceScope.BOTH,
            city="Porto",
            department_code="13",
            region_name="Norte (Porto / Braga)",
            title="Portugal — IMT Jovem e Imposto do Selo: impacto real no orçamento de compra até 35 anos",
            seo_meta_title="IMT Jovem e Imposto do Selo em Portugal | Monty",
            seo_meta_description="Como funciona a isenção de IMT e Imposto do Selo na compra da primeira habitação própria permanente em Portugal.",
            public_summary=(
                "Em Portugal, os jovens até aos 35 anos que adquiram a sua primeira habitação própria e permanente (HPP) "
                "beneficiam de isenção de IMT e Imposto do Selo sobre a aquisição até 316 772 € (com aplicação parcial na faixa seguinte)."
            ),
            public_takeaways=(
                "Confirme o cumprimento cumulativo da idade (≤ 35 anos) e do estatuto de primeira HPP no Portal das Finanças.",
                "Note que o Imposto do Selo sobre o empréstimo bancário (0,6 %) continua sujeito às condições do contrato de crédito.",
            ),
            pro_analysis=(
                "Argumento forte no segmento T1/T2 em Lisboa, Porto e Braga : a poupança fiscal na escritura pode ultrapassar 10 000 € "
                "reduzindo a entrada inicial necessária antes do CPCV."
            ),
            pro_commercial_arguments=(
                "Simular lado a lado compra com e sem IMT Jovem no calculador Portugal Monty.",
                "Coordenar a emissão das guias de IMT com solicitador antes da escritura.",
            ),
            pro_key_metrics={"Teto isenção 100 %": "316 772 €", "IS Aquisição": "0,8 % (isento)", "Regime": "HPP ≤ 35 anos"},
            legal_disclaimer=LEGAL_DISCLAIMER_BY_COUNTRY[CountryCode.PT],
            published_at="2026-11-20",
        ),
        NewsArticle(
            id="art-pt-adene-certificado",
            slug="portugal-certificado-energetico-adene-valorizacao-lisboa-porto",
            country_code=CountryCode.PT,
            locale="pt-PT",
            regulation_jurisdiction="PT_SCE_ADENE",
            source_id="src-pt-adene",
            source_name="ADENE — Sistema de Certificação Energética",
            source_url="https://www.sce.pt",
            category=NewsCategory.DPE_TRAVAUX,
            audience_scope=AudienceScope.BOTH,
            city="",
            department_code="",
            region_name="Nacional",
            title="Portugal — Certificado Energético ADENE (A+ a F): obrigação de publicação e impacto no valor",
            seo_meta_title="Certificado Energético ADENE em Portugal | Monty",
            seo_meta_description="Entenda a escala energética portuguesa (A+ a F), obrigatória em todos os anúncios e escrituras de venda ou arrendamento.",
            public_summary=(
                "Em Portugal, qualquer anúncio de venda ou arrendamento deve indicar a classe do Certificado Energético ADENE "
                "(escala de A+ a F). Um bom desempenho energético reduz custos de climatização e pode dar acesso a condições de crédito verde."
            ),
            public_takeaways=(
                "O Certificado Energético válido é obrigatório na assinatura do CPCV e da escritura.",
                "Não confunda a escala ADENE portuguesa (A+ a F) com o DPE francês (A a G).",
            ),
            pro_analysis=(
                "Garantir o número SCE válido antes de publicar o imóvel na plataforma evita coimas e bloqueios no ato da escritura."
            ),
            pro_commercial_arguments=(
                "Recolher Caderneta Predial, Certidão Permanente e Certificado Energético ADENE na angariação.",
            ),
            pro_key_metrics={"Escala PT": "A+ a F", "Obrigação": "Anúncio & Escritura", "Validade": "10 anos (hab.)"},
            legal_disclaimer=LEGAL_DISCLAIMER_BY_COUNTRY[CountryCode.PT],
            published_at="2026-11-15",
        ),
    ]


class NewsHubRepository:
    """Dépôt sécurisé multi-pays du centre d'actualités double public (isolé des données clients/agences)."""

    def __init__(self) -> None:
        self._sources: dict[str, NewsSource] = _seed_sources()
        self._articles: list[NewsArticle] = _seed_articles()
        self._preferences: dict[str, UserNewsPreferences] = {}
        self._bookmarks: dict[str, dict[str, NewsBookmark]] = {}

    def list_sources(
        self,
        include_inactive: bool = False,
        country_code: CountryCode | None = None,
    ) -> list[NewsSource]:
        return [
            s for s in self._sources.values()
            if (include_inactive or s.is_active)
            and (country_code is None or s.country_code == country_code)
        ]

    def add_source(
        self,
        ctx: TenantContext,
        *,
        name: str,
        domain: str,
        base_url: str,
        country_code: CountryCode = CountryCode.FR,
        locale: str = "",
        reliability_tier: str = "INSTITUTIONNELLE",
    ) -> NewsSource:
        ctx.require_permission(Permission.NEWS_ADMIN)
        pack = get_country_pack(country_code)
        safe_url = InputSanitizer.sanitize_public_http_url(base_url)
        if not safe_url:
            raise ValueError("URL source invalide ou non autorisée (SSRF guard).")
        safe_name = InputSanitizer.sanitize_text(name, 140)
        safe_domain = InputSanitizer.sanitize_text(domain, 140).lower()
        if not safe_name or not safe_domain:
            raise ValueError("Nom et domaine source obligatoires.")
        tier = reliability_tier.upper()
        if tier not in ("OFFICIELLE", "INSTITUTIONNELLE", "PROFESSIONNELLE"):
            raise ValueError("Niveau de fiabilité invalide.")
        src_id = f"src-{pack.country_code.value.lower()}-{_slugify(safe_domain)[:20]}-{len(self._sources) + 1}"
        source = NewsSource(
            id=src_id,
            country_code=pack.country_code,
            locale=locale or pack.default_locale,
            name=safe_name,
            domain=safe_domain,
            base_url=safe_url,
            reliability_tier=tier,
            is_active=True,
            last_verified_at=date.today().isoformat(),
        )
        self._sources[src_id] = source
        return source

    @staticmethod
    def _matches_filters(
        article: NewsArticle,
        *,
        country_code: CountryCode | None = None,
        category: NewsCategory | None = None,
        region_name: str = "",
        department_code: str = "",
        city: str = "",
        query: str = "",
    ) -> bool:
        if country_code is not None and article.country_code != country_code:
            return False
        if category and article.category != category:
            return False
        if region_name and region_name.lower() not in ("toutes", "national", "nacional"):
            art_reg = article.region_name.lower()
            if art_reg not in ("national", "nacional") and region_name.lower() not in art_reg:
                return False
        if department_code:
            if article.department_code and article.department_code != department_code.strip():
                return False
        if city:
            if article.city and city.strip().lower() not in article.city.lower():
                return False
        if query.strip():
            q = query.strip().lower()
            hay = f"{article.title} {article.public_summary} {article.pro_analysis} {article.city} {article.region_name}".lower()
            if q not in hay:
                return False
        return True

    @staticmethod
    def to_public_dto(article: NewsArticle) -> dict:
        if article.audience_scope == AudienceScope.PRO_ONLY or article.status == ArticleStatus.DRAFT:
            raise PermissionError("Article non accessible sur l'espace public.")
        return {
            "id": article.id,
            "slug": article.slug,
            "country_code": article.country_code.value,
            "locale": article.locale,
            "regulation_jurisdiction": article.regulation_jurisdiction,
            "source_name": article.source_name,
            "source_url": article.source_url,
            "category": article.category.value,
            "city": article.city,
            "department_code": article.department_code,
            "region_name": article.region_name,
            "title": article.title,
            "seo_meta_title": article.seo_meta_title or article.title,
            "seo_meta_description": article.seo_meta_description or article.public_summary[:160],
            "public_summary": article.public_summary,
            "public_takeaways": list(article.public_takeaways),
            "ai_dual_generated": article.ai_dual_generated,
            "legal_disclaimer": article.legal_disclaimer,
            "status": article.status.value,
            "published_at": article.published_at,
        }

    @staticmethod
    def to_pro_dto(article: NewsArticle) -> dict:
        return {
            "id": article.id,
            "slug": article.slug,
            "country_code": article.country_code.value,
            "locale": article.locale,
            "regulation_jurisdiction": article.regulation_jurisdiction,
            "source_id": article.source_id,
            "source_name": article.source_name,
            "source_url": article.source_url,
            "category": article.category.value,
            "audience_scope": article.audience_scope.value,
            "city": article.city,
            "department_code": article.department_code,
            "region_name": article.region_name,
            "title": article.title,
            "public_summary": article.public_summary,
            "public_takeaways": list(article.public_takeaways),
            "pro_analysis": article.pro_analysis,
            "pro_commercial_arguments": list(article.pro_commercial_arguments),
            "pro_key_metrics": dict(article.pro_key_metrics),
            "ai_dual_generated": article.ai_dual_generated,
            "legal_disclaimer": article.legal_disclaimer,
            "status": article.status.value,
            "published_at": article.published_at,
        }

    def query_public_articles(
        self,
        *,
        country_code: CountryCode | None = CountryCode.FR,
        category: NewsCategory | None = None,
        region_name: str = "",
        department_code: str = "",
        city: str = "",
        query: str = "",
        include_archived: bool = False,
    ) -> list[dict]:
        allowed_statuses = {ArticleStatus.PUBLISHED}
        if include_archived:
            allowed_statuses.add(ArticleStatus.ARCHIVED)

        matched = [
            self.to_public_dto(a)
            for a in self._articles
            if a.status in allowed_statuses
            and a.audience_scope in (AudienceScope.BOTH, AudienceScope.PUBLIC_ONLY)
            and self._matches_filters(
                a,
                country_code=country_code,
                category=category,
                region_name=region_name,
                department_code=department_code,
                city=city,
                query=query,
            )
        ]
        matched.sort(key=lambda item: item["published_at"], reverse=True)
        return matched

    def get_public_article_by_slug(self, slug: str) -> dict | None:
        clean_slug = InputSanitizer.sanitize_text(slug, 180)
        for a in self._articles:
            if a.slug == clean_slug and a.status in (ArticleStatus.PUBLISHED, ArticleStatus.ARCHIVED):
                if a.audience_scope in (AudienceScope.BOTH, AudienceScope.PUBLIC_ONLY):
                    return self.to_public_dto(a)
        return None

    def query_pro_articles(
        self,
        ctx: TenantContext,
        *,
        country_code: CountryCode | None = CountryCode.FR,
        category: NewsCategory | None = None,
        region_name: str = "",
        department_code: str = "",
        city: str = "",
        query: str = "",
        include_archived: bool = False,
        include_drafts: bool = False,
    ) -> list[dict]:
        ctx.require_permission(Permission.NEWS_PRO_READ)
        allowed_statuses = {ArticleStatus.PUBLISHED}
        if include_archived:
            allowed_statuses.add(ArticleStatus.ARCHIVED)
        if include_drafts and ctx.has_permission(Permission.NEWS_ADMIN):
            allowed_statuses.add(ArticleStatus.DRAFT)

        matched = [
            self.to_pro_dto(a)
            for a in self._articles
            if a.status in allowed_statuses
            and self._matches_filters(
                a,
                country_code=country_code,
                category=category,
                region_name=region_name,
                department_code=department_code,
                city=city,
                query=query,
            )
        ]
        matched.sort(key=lambda item: item["published_at"], reverse=True)
        return matched

    def create_article(
        self,
        ctx: TenantContext,
        *,
        source_id: str,
        source_url: str,
        category: NewsCategory,
        audience_scope: AudienceScope,
        title: str,
        country_code: CountryCode | None = None,
        locale: str = "",
        city: str = "",
        department_code: str = "",
        region_name: str = "",
        public_summary: str = "",
        public_takeaways: list[str] | None = None,
        pro_analysis: str = "",
        pro_commercial_arguments: list[str] | None = None,
        pro_key_metrics: dict[str, str] | None = None,
        ai_dual_generated: bool = False,
        publish_now: bool = True,
    ) -> NewsArticle:
        ctx.require_permission(Permission.NEWS_ADMIN)
        source = self._sources.get(source_id)
        if source is None:
            raise ValueError("Source vérifiée introuvable.")
        target_country = country_code or source.country_code
        if target_country != source.country_code:
            raise ValueError(
                f"Cloisonnement réglementaire : impossible d'associer une source {source.country_code.value} "
                f"à un article {target_country.value}."
            )
        pack = get_country_pack(target_country)
        safe_url = InputSanitizer.sanitize_public_http_url(source_url)
        if not safe_url:
            raise ValueError("URL source invalide ou interdite.")
        safe_title = InputSanitizer.sanitize_text(title, 200)
        if not safe_title:
            raise ValueError("Titre obligatoire.")

        slug_base = _slugify(safe_title)
        slug = slug_base
        existing_slugs = {a.slug for a in self._articles}
        idx = 2
        while slug in existing_slugs:
            slug = f"{slug_base[:110]}-{idx}"
            idx += 1

        default_national_region = "Nacional" if pack.country_code == CountryCode.PT else "National"
        art = NewsArticle(
            id=f"art-{len(self._articles) + 1}-{slug[:18]}",
            slug=slug,
            country_code=pack.country_code,
            locale=locale or source.locale or pack.default_locale,
            regulation_jurisdiction=pack.regulation_jurisdiction,
            source_id=source.id,
            source_name=source.name,
            source_url=safe_url,
            category=category,
            audience_scope=audience_scope,
            city=InputSanitizer.sanitize_text(city, 100),
            department_code=InputSanitizer.sanitize_text(department_code, 10),
            region_name=InputSanitizer.sanitize_text(region_name, 100) or default_national_region,
            title=safe_title,
            seo_meta_title=safe_title[:160],
            seo_meta_description=InputSanitizer.sanitize_text(public_summary, 300),
            public_summary=InputSanitizer.sanitize_text(public_summary, 3000),
            public_takeaways=tuple(InputSanitizer.sanitize_text(t, 280) for t in (public_takeaways or []) if t.strip()),
            pro_analysis=InputSanitizer.sanitize_text(pro_analysis, 4000),
            pro_commercial_arguments=tuple(
                InputSanitizer.sanitize_text(a, 280) for a in (pro_commercial_arguments or []) if a.strip()
            ),
            pro_key_metrics={
                InputSanitizer.sanitize_text(k, 60): InputSanitizer.sanitize_text(v, 80)
                for k, v in (pro_key_metrics or {}).items()
            },
            ai_dual_generated=ai_dual_generated,
            legal_disclaimer=LEGAL_DISCLAIMER_BY_COUNTRY.get(pack.country_code, LEGAL_DISCLAIMER_BY_COUNTRY[CountryCode.FR]),
            status=ArticleStatus.PUBLISHED if publish_now else ArticleStatus.DRAFT,
            published_at=date.today().isoformat(),
        )
        self._articles.append(art)
        return art

    def get_preferences(self, user_id: str) -> UserNewsPreferences:
        return self._preferences.get(user_id, UserNewsPreferences(user_id=user_id))

    def upsert_preferences(
        self,
        *,
        user_id: str,
        country_code: CountryCode = CountryCode.FR,
        locale: str = "",
        city: str = "",
        department_code: str = "",
        region_name: str = "National",
        interests: list[NewsCategory] | None = None,
        notifications_enabled: bool = False,
        rgpd_consent_granted: bool = False,
    ) -> UserNewsPreferences:
        if notifications_enabled and not rgpd_consent_granted:
            raise ValueError(
                "Conformité RGPD (Art. 6.1.a) : le consentement explicite est requis pour activer les alertes."
            )
        pack = get_country_pack(country_code)
        clean_interests = tuple(dict.fromkeys(interests or [])) or (NewsCategory.TAUX_CREDIT,)
        pref = UserNewsPreferences(
            user_id=user_id,
            country_code=pack.country_code,
            locale=locale or pack.default_locale,
            city=InputSanitizer.sanitize_text(city, 100),
            department_code=InputSanitizer.sanitize_text(department_code, 10),
            region_name=InputSanitizer.sanitize_text(region_name, 100) or ("Nacional" if pack.country_code == CountryCode.PT else "National"),
            interests=clean_interests,
            notifications_enabled=bool(notifications_enabled and rgpd_consent_granted),
            rgpd_consent_granted=rgpd_consent_granted,
            rgpd_consent_date=date.today().isoformat() if rgpd_consent_granted else None,
        )
        self._preferences[user_id] = pref
        return pref

    def list_bookmarks(self, user_id: str) -> list[NewsBookmark]:
        return list(self._bookmarks.get(user_id, {}).values())

    def toggle_bookmark(self, user_id: str, article_id: str, alert_on_update: bool = True) -> bool:
        exists = any(a.id == article_id for a in self._articles)
        if not exists:
            raise ValueError("Article introuvable.")
        user_map = self._bookmarks.setdefault(user_id, {})
        if article_id in user_map:
            del user_map[article_id]
            return False
        user_map[article_id] = NewsBookmark(
            user_id=user_id,
            article_id=article_id,
            alert_on_update=alert_on_update,
        )
        return True
