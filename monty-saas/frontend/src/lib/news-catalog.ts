import type { CountryCode, NewsCategory, NewsSource, ProNewsArticle, PublicNewsArticle } from "./types";

export const CATEGORY_LABELS: Record<NewsCategory, { fr: string; pt: string }> = {
  TAUX_CREDIT: { fr: "Taux & Crédit", pt: "Taxas & Crédito" },
  MARCHE_LOCAL: { fr: "Marché & Prix", pt: "Mercado & Preços" },
  LOIS_FISCALITE: { fr: "Lois & Fiscalité", pt: "Leis & Fiscalidade" },
  DPE_TRAVAUX: { fr: "Énergie & DPE", pt: "Energia & SCE" },
  LOCATION_COPRO: { fr: "Location & Copro", pt: "Arrendamento" },
  INVESTISSEMENT: { fr: "Investissement", pt: "Investimento" },
  METIER_CONSEILLER: { fr: "Métier & Stratégie Pro", pt: "Estratégia Pro" },
};

export const SEEDED_SOURCES: NewsSource[] = [
  {
    id: "src-bdf",
    country_code: "FR",
    locale: "fr-FR",
    name: "Banque de France — Crédits aux particuliers",
    domain: "webstat.banque-france.fr",
    base_url: "https://www.banque-france.fr/fr/statistiques/taux-et-credits",
    reliability_tier: "OFFICIELLE",
    last_verified_at: "2026-11-25",
  },
  {
    id: "src-notaires",
    country_code: "FR",
    locale: "fr-FR",
    name: "Notaires de France — Indices & Conjoncture",
    domain: "www.notaires.fr",
    base_url: "https://www.notaires.fr/fr/immobilier-fiscalite/etudes-et-analyses-immobilieres",
    reliability_tier: "INSTITUTIONNELLE",
    last_verified_at: "2026-11-25",
  },
  {
    id: "src-ademe",
    country_code: "FR",
    locale: "fr-FR",
    name: "ADEME — Observatoire DPE-Audit",
    domain: "observatoire-dpe-audit.ademe.fr",
    base_url: "https://observatoire-dpe-audit.ademe.fr",
    reliability_tier: "OFFICIELLE",
    last_verified_at: "2026-11-25",
  },
  {
    id: "src-legifrance",
    country_code: "FR",
    locale: "fr-FR",
    name: "Légifrance — Textes législatifs & décrets",
    domain: "www.legifrance.gouv.fr",
    base_url: "https://www.legifrance.gouv.fr",
    reliability_tier: "OFFICIELLE",
    last_verified_at: "2026-11-25",
  },
  {
    id: "src-service-public",
    country_code: "FR",
    locale: "fr-FR",
    name: "Service-Public.fr — Logement & Fiscalité",
    domain: "www.service-public.fr",
    base_url: "https://www.service-public.fr/particuliers/vosdroits/N19808",
    reliability_tier: "OFFICIELLE",
    last_verified_at: "2026-11-25",
  },
  {
    id: "src-pt-bdp",
    country_code: "PT",
    locale: "pt-PT",
    name: "Banco de Portugal — Crédito à Habitação & DSTI",
    domain: "www.bportugal.pt",
    base_url: "https://www.bportugal.pt/area-cidadao/credito-habitacao",
    reliability_tier: "OFFICIELLE",
    last_verified_at: "2026-11-25",
  },
  {
    id: "src-pt-dre",
    country_code: "PT",
    locale: "pt-PT",
    name: "Diário da República / Portal das Finanças (IMT)",
    domain: "diariodarepublica.pt",
    base_url: "https://diariodarepublica.pt",
    reliability_tier: "OFFICIELLE",
    last_verified_at: "2026-11-25",
  },
  {
    id: "src-pt-adene",
    country_code: "PT",
    locale: "pt-PT",
    name: "ADENE — Sistema de Certificação Energética",
    domain: "www.sce.pt",
    base_url: "https://www.sce.pt",
    reliability_tier: "OFFICIELLE",
    last_verified_at: "2026-11-25",
  },
];

export const SEEDED_ARTICLES: ProNewsArticle[] = [
  {
    id: "art-taux-hcsf-2026",
    slug: "taux-credit-immobilier-stabilisation-2026-capacite-hcsf",
    country_code: "FR",
    locale: "fr-FR",
    regulation_jurisdiction: "FR_HCSF_BANQUE_DE_FRANCE",
    source_id: "src-bdf",
    source_name: "Banque de France — Crédits aux particuliers",
    source_url: "https://www.banque-france.fr/fr/statistiques/taux-et-credits",
    category: "TAUX_CREDIT",
    audience_scope: "BOTH",
    city: "",
    department_code: "",
    region_name: "National",
    title: "France : Taux de crédit immobilier autour de 3,25 % à 3,40 % et règle HCSF des 35 %",
    seo_meta_title: "Taux immobilier France 2026 : capacité d'emprunt HCSF | Monty",
    seo_meta_description:
      "Analyse nationale des taux de crédit immobilier en France et calcul de capacité d'emprunt sous la règle HCSF des 35 %.",
    public_summary:
      "Sur l'ensemble du marché français, les taux moyens sur 20 et 25 ans se stabilisent entre 3,20 % et 3,45 % hors assurance. Les banques restent soumises au plafond HCSF (35 % d'endettement assurance comprise et 25 ans maximum).",
    public_takeaways: [
      "Comparez le TAEG complet (taux nominal + assurance emprunteur + frais de garantie).",
      "Un écart de 0,30 pt sur 25 ans représente ~8 000 € de pouvoir d'achat immobilier pour 250 000 €.",
      "Simulez votre mensualité et votre reste à vivre avec l'outil HCSF France avant de visiter.",
    ],
    pro_analysis:
      "La stabilisation des barèmes bancaires redonne de la prévisibilité aux plans de financement, mais le taux d'effort HCSF (35 %) reste le premier filtre bloquant. La négociation se joue sur la délégation d'assurance (Loi Lemoine) et l'apport couvrant les frais de notaire.",
    pro_commercial_arguments: [
      "Chiffrez immédiatement le pouvoir d'achat net avec le simulateur HCSF lors de l'entretien découverte.",
      "Valorisez la délégation d'assurance pour regagner 25 à 45 €/mois de capacité.",
      "Exigez une attestation de financement datée de moins de 30 jours avant offre.",
    ],
    pro_key_metrics: { "Taux 20 ans FR": "3,25 %", "Taux 25 ans FR": "3,35 %", "Plafond HCSF": "35 % / 25 ans" },
    ai_dual_generated: false,
    legal_disclaimer:
      "Information générale et synthèse analytique à titre indicatif (droit français) : ne constitue ni un conseil juridique, ni une consultation fiscale officielle.",
    status: "PUBLISHED",
    published_at: "2026-11-24",
  },
  {
    id: "art-marche-france-metropoles",
    slug: "marche-immobilier-france-metropoles-ecart-dpe-delais-2026",
    country_code: "FR",
    locale: "fr-FR",
    regulation_jurisdiction: "FR_DVF_NOTAIRES",
    source_id: "src-notaires",
    source_name: "Notaires de France — Indices & Conjoncture",
    source_url: "https://www.notaires.fr/fr/immobilier-fiscalite/etudes-et-analyses-immobilieres",
    category: "MARCHE_LOCAL",
    audience_scope: "BOTH",
    city: "Lyon",
    department_code: "69",
    region_name: "Auvergne-Rhône-Alpes",
    title: "Métropoles françaises (Paris, Lyon, Bordeaux, Nantes, Montpellier) : reprise sélective et prime aux DPE A–C",
    seo_meta_title: "Tendances prix immobilier France et métropoles 2026 | Monty",
    seo_meta_description:
      "Tendances nationales des transactions immobilières en France : écarts de délais selon le DPE et le positionnement prix DVF.",
    public_summary:
      "Dans les grandes métropoles françaises et villes moyennes attractives, la demande privilégie les biens connectés aux transports et classés DPE A à C. Les biens au prix médian DVF se vendent sensiblement plus vite.",
    public_takeaways: [
      "Appuyez votre offre ou votre mise en vente sur les ventes notariées DVF réelles de votre commune.",
      "Un dossier technique complet dès la mise en ligne accélère le compromis.",
      "La recherche par temps de trajet (train, tram, vélo, voiture) élargit efficacement votre périmètre.",
    ],
    pro_analysis:
      "Au niveau national, toute surcote supérieure à +6/+8 % par rapport à la médiane DVF pondérée allonge le délai d'exposition de +15 à +30 jours.",
    pro_commercial_arguments: [
      "Présentez 5 comparables DVF locaux (< 300 m) lors de l'avis de valeur vendeur.",
      "Démontrez la corrélation prix juste / délai estimé pour défendre un mandat exclusif.",
    ],
    pro_key_metrics: { "Délai médian au prix": "45–60 j", "Prime valeur verte": "+5 à +12 %", Base: "Notaires / DVF" },
    ai_dual_generated: false,
    legal_disclaimer:
      "Information générale et synthèse analytique à titre indicatif (droit français) : ne constitue ni un conseil juridique, ni une consultation fiscale officielle.",
    status: "PUBLISHED",
    published_at: "2026-11-22",
  },
  {
    id: "art-dpe-2026-coefficient",
    slug: "reforme-dpe-2026-chauffage-electrique-location-pas-a-pas",
    country_code: "FR",
    locale: "fr-FR",
    regulation_jurisdiction: "FR_LOI_CLIMAT_DPE",
    source_id: "src-ademe",
    source_name: "ADEME — Observatoire DPE-Audit",
    source_url: "https://observatoire-dpe-audit.ademe.fr",
    category: "DPE_TRAVAUX",
    audience_scope: "BOTH",
    city: "",
    department_code: "",
    region_name: "National",
    title: "France — DPE et location : facteur de conversion électrique et calendrier d'interdiction F & G",
    seo_meta_title: "DPE France 2026 et interdiction de location F et G | Monty",
    seo_meta_description:
      "Comprendre les règles françaises du DPE, l'interdiction locative des logements G (2025) et F (2028) et la valeur verte.",
    public_summary:
      "En France, les logements classés G ne peuvent plus être remis en location depuis janvier 2025, et l'échéance 2028 concerne les biens classés F. Vérifiez l'attestation ADEME officielle avant tout projet locatif.",
    public_takeaways: [
      "Contrôlez l'étiquette DPE et intégrez le budget travaux dans le calcul de rendement net.",
      "Un DPE G interdit la signature d'un nouveau bail d'habitation longue durée sans rénovation.",
      "Un saut de classe F vers C sécurise la valeur à la revente.",
    ],
    pro_analysis:
      "L'audit énergétique réglementaire (monopropriétés E, F, G) structure les négociations dès la première visite.",
    pro_commercial_arguments: [
      "Simulez le rendement locatif net après travaux pour transformer une décote DPE en opportunité.",
      "Faites éditer l'attestation ADEME actualisée sur les petites surfaces électriques.",
    ],
    pro_key_metrics: { "Classe G": "Interdite (2025)", "Classe F": "Échéance 2028", "Audit vente": "E/F/G monoprop." },
    ai_dual_generated: false,
    legal_disclaimer:
      "Information générale et synthèse analytique à titre indicatif (droit français) : ne constitue ni un conseil juridique, ni une consultation fiscale officielle.",
    status: "PUBLISHED",
    published_at: "2026-11-19",
  },
  {
    id: "art-loi-le-meur-fiscalite",
    slug: "loi-le-meur-meuble-touristique-fiscalite-droits-locataires",
    country_code: "FR",
    locale: "fr-FR",
    regulation_jurisdiction: "FR_LOI_LE_MEUR_CGI",
    source_id: "src-legifrance",
    source_name: "Légifrance — Textes législatifs & décrets",
    source_url: "https://www.legifrance.gouv.fr",
    category: "LOIS_FISCALITE",
    audience_scope: "BOTH",
    city: "",
    department_code: "",
    region_name: "National",
    title: "France — Loi Le Meur & meublé touristique : nouveaux seuils micro-BIC, DPE et pouvoirs des communes",
    seo_meta_title: "Loi Le Meur France : fiscalité meublée et location | Monty",
    seo_meta_description:
      "Décryptage des règles françaises sur les meublés touristiques, les abattements micro-BIC et les obligations en copropriété.",
    public_summary:
      "La régulation française des meublés de tourisme durcit les exigences DPE et réduit certains abattements micro-BIC au profit de la location longue durée.",
    public_takeaways: [
      "Vérifiez le règlement de copropriété et la mairie avant tout projet saisonnier.",
      "Comparez location nue, LMNP au réel et bail mobilité via le calculateur de rendement.",
    ],
    pro_analysis:
      "Opportunité directe de sourcing vendeur et gestion locative sur les studios et T2 en zones tendues.",
    pro_commercial_arguments: [
      "Proposez un bilan comparatif location longue durée vs revente avec simulation de plus-value CGI.",
    ],
    pro_key_metrics: { "DPE meublé": "Obligatoire", "Plafond RP": "Jusqu'à 90 j/an", Arbitrage: "LMNP / Vente" },
    ai_dual_generated: false,
    legal_disclaimer:
      "Information générale et synthèse analytique à titre indicatif (droit français) : ne constitue ni un conseil juridique, ni une consultation fiscale officielle.",
    status: "PUBLISHED",
    published_at: "2026-11-17",
  },
  {
    id: "art-ptz-dmto-notaire",
    slug: "ptz-elargi-frais-notaire-dmto-departements-2026",
    country_code: "FR",
    locale: "fr-FR",
    regulation_jurisdiction: "FR_LF_DMTO_PTZ",
    source_id: "src-service-public",
    source_name: "Service-Public.fr — Logement & Fiscalité",
    source_url: "https://www.service-public.fr/particuliers/vosdroits/N19808",
    category: "INVESTISSEMENT",
    audience_scope: "BOTH",
    city: "Paris",
    department_code: "75",
    region_name: "Île-de-France",
    title: "France — Frais de notaire (DMTO départementaux) et PTZ élargi : optimiser votre budget d'achat",
    seo_meta_title: "Frais de notaire anciens vs neufs et PTZ France | Monty",
    seo_meta_description:
      "Comprendre la composition des frais de notaire en France par département (ancien vs neuf) et l'apport du PTZ.",
    public_summary:
      "En France, les frais d'acte regroupent majoritairement des droits fiscaux (DMTO) variables selon le département et des émoluments notariés. Comptez ~7,5 à 8,3 % dans l'ancien contre ~2,2 à 3 % dans le neuf.",
    public_takeaways: [
      "Estimez vos frais par département (ancien vs neuf) avant de fixer votre apport.",
      "Vérifiez les exonérations primo-accédant locales et votre éligibilité PTZ.",
    ],
    pro_analysis:
      "Dans les départements ayant relevé les DMTO à 5,0 %, l'écart atteint ~1 500 € par tranche de 300 000 €.",
    pro_commercial_arguments: [
      "Utilisez le calculateur Frais de Notaire pour détailler taxes publiques vs émoluments.",
    ],
    pro_key_metrics: { "Ancien FR": "~7,5–8,3 %", "Neuf / VEFA FR": "~2,2–3,0 %", PTZ: "Élargi France" },
    ai_dual_generated: false,
    legal_disclaimer:
      "Information générale et synthèse analytique à titre indicatif (droit français) : ne constitue ni un conseil juridique, ni une consultation fiscale officielle.",
    status: "PUBLISHED",
    published_at: "2026-11-14",
  },
  {
    id: "art-pro-mandat-exclusif-argumentaire",
    slug: "pro-argumentaire-mandat-exclusif-prix-marche-et-delai",
    country_code: "FR",
    locale: "fr-FR",
    regulation_jurisdiction: "FR_LOI_HOGUET_ALUR",
    source_id: "src-notaires",
    source_name: "Notaires de France — Indices & Conjoncture",
    source_url: "https://www.notaires.fr/fr/immobilier-fiscalite/etudes-et-analyses-immobilieres",
    category: "METIER_CONSEILLER",
    audience_scope: "PRO_ONLY",
    city: "Bordeaux",
    department_code: "33",
    region_name: "Nouvelle-Aquitaine",
    title: "[Espace Pro France] Défendre le mandat exclusif au prix DVF juste pour réduire le délai de vente",
    seo_meta_title: "",
    seo_meta_description: "",
    public_summary: "",
    public_takeaways: [],
    pro_analysis:
      "Briefing réservé aux conseillers France : un mandat exclusif couplé à un dossier pré-ALUR complet et au matching acquéreurs HCSF réduit le délai médian de commercialisation de 14 à 19 jours.",
    pro_commercial_arguments: [
      "Appuyez la stratégie de prix sur les ventes notariées DVF réelles.",
      "Mettez en avant les acquéreurs financés immédiatement identifiés dans le CRM Monty.",
    ],
    pro_key_metrics: { "Impact surcote +10 %": "+14 à +28 j", "Dossier complet": "-6 j", Exclusivité: "-8 j" },
    ai_dual_generated: false,
    legal_disclaimer:
      "Information générale et synthèse analytique à titre indicatif (droit français) : ne constitue ni un conseil juridique, ni une consultation fiscale officielle.",
    status: "PUBLISHED",
    published_at: "2026-11-12",
  },
  {
    id: "art-archive-bilan-2025",
    slug: "bilan-marche-immobilier-2025-historique",
    country_code: "FR",
    locale: "fr-FR",
    regulation_jurisdiction: "FR_DVF_NOTAIRES",
    source_id: "src-notaires",
    source_name: "Notaires de France — Indices & Conjoncture",
    source_url: "https://www.notaires.fr/fr/immobilier-fiscalite/etudes-et-analyses-immobilieres",
    category: "MARCHE_LOCAL",
    audience_scope: "BOTH",
    city: "",
    department_code: "",
    region_name: "National",
    title: "France — Rétrospective 2025 : atterrissage des prix et normalisation progressive du crédit",
    seo_meta_title: "Historique marché immobilier France 2025 | Monty",
    seo_meta_description: "Archive analytique des transactions immobilières et des taux en France en 2025.",
    public_summary:
      "Archive de référence sur l'évolution du marché immobilier français et des conditions d'emprunt fin 2025.",
    public_takeaways: ["Consultez l'historique pour situer l'évolution des taux dans le cycle immobilier."],
    pro_analysis: "Base historique nationale pour contextualiser la reprise des volumes 2026.",
    pro_commercial_arguments: ["Illustrez la stabilisation des prix et le retour de la solvabilité."],
    pro_key_metrics: { Période: "Archive 2025" },
    ai_dual_generated: false,
    legal_disclaimer:
      "Information générale et synthèse analytique à titre indicatif (droit français) : ne constitue ni un conseil juridique, ni une consultation fiscale officielle.",
    status: "ARCHIVED",
    published_at: "2025-12-20",
  },
  // --- PORTUGAL (Priorité 4 — Isolé) ---
  {
    id: "art-pt-bdp-dsti-credito",
    slug: "portugal-banco-de-portugal-regras-dsti-prazos-credito-habitacao",
    country_code: "PT",
    locale: "pt-PT",
    regulation_jurisdiction: "PT_BANCO_DE_PORTUGAL",
    source_id: "src-pt-bdp",
    source_name: "Banco de Portugal — Crédito à Habitação & DSTI",
    source_url: "https://www.bportugal.pt/area-cidadao/credito-habitacao",
    category: "TAUX_CREDIT",
    audience_scope: "BOTH",
    city: "Lisboa",
    department_code: "11",
    region_name: "Área Metropolitana de Lisboa",
    title: "Portugal — Regras Banco de Portugal: limites de taxa de esforço (DSTI), LTV e prazos por idade",
    seo_meta_title: "Crédito Habitação Portugal : regras DSTI e LTV Banco de Portugal | Monty",
    seo_meta_description:
      "Guia claro das regras macroprudenciais do Banco de Portugal para crédito habitação: DSTI, maturidade por idade e LTV.",
    public_summary:
      "No mercado português, o Banco de Portugal define limites prudenciais próprios : DSTI até 50 % com teste de stress de taxa, LTV máximo de 90 % para HPP e prazos de 35 a 40 anos consoante a idade.",
    public_takeaways: [
      "Para HPP, o financiamento bancário pode ir até 90 % do menor valor entre compra e avaliação.",
      "Idade ≤ 30 anos : prazo até 40 anos; 30–35 anos : até 37 anos; > 35 anos : até 35 anos.",
      "Simule o IMT e o Imposto do Selo no módulo Portugal Monty (isolado das regras francesas).",
    ],
    pro_analysis:
      "Análise Pro Portugal : validar o rácio DSTI com margem de stress Euribor e antecipar a avaliação bancária prévia antes do CPCV.",
    pro_commercial_arguments: [
      "Preparar simulação IMT + Imposto do Selo (0,8 % + 0,6 % crédito) antes do CPCV.",
      "Verificar elegibilidade IMT Jovem (≤ 35 anos, HPP até 316 772 € isento).",
    ],
    pro_key_metrics: { "LTV máx HPP": "90 %", "LTV Secundária": "80 %", Prazos: "35–40 anos" },
    ai_dual_generated: false,
    legal_disclaimer:
      "Informação geral e síntese indicativa (enquadramento jurídico português) : não constitui aconselhamento jurídico, fiscal ou financeiro oficial.",
    status: "PUBLISHED",
    published_at: "2026-11-24",
  },
  {
    id: "art-pt-imt-jovem-escritura",
    slug: "portugal-imt-jovem-isencao-imposto-selo-escritura",
    country_code: "PT",
    locale: "pt-PT",
    regulation_jurisdiction: "PT_CIMT_CIS",
    source_id: "src-pt-dre",
    source_name: "Diário da República / Portal das Finanças (IMT)",
    source_url: "https://diariodarepublica.pt",
    category: "LOIS_FISCALITE",
    audience_scope: "BOTH",
    city: "Porto",
    department_code: "13",
    region_name: "Norte (Porto / Braga)",
    title: "Portugal — IMT Jovem e Imposto do Selo: impacto real no orçamento de compra até 35 anos",
    seo_meta_title: "IMT Jovem e Imposto do Selo em Portugal | Monty",
    seo_meta_description:
      "Como funciona a isenção de IMT e Imposto do Selo na compra da primeira habitação própria permanente em Portugal.",
    public_summary:
      "Em Portugal, compradores até 35 anos na primeira HPP beneficiam de isenção de IMT e Imposto do Selo sobre a aquisição até 316 772 €.",
    public_takeaways: [
      "Confirme a idade (≤ 35 anos) e o estatuto de primeira HPP no Portal das Finanças.",
      "O Imposto do Selo sobre o crédito (0,6 %) continua aplicável conforme o contrato bancário.",
    ],
    pro_analysis:
      "Alavanca relevante nos segmentos T1/T2 em Lisboa, Porto e Braga ao reduzir significativamente o capital próprio à escritura.",
    pro_commercial_arguments: [
      "Simular lado a lado compra com e sem IMT Jovem no calculador Portugal Monty.",
    ],
    pro_key_metrics: { "Teto isenção 100 %": "316 772 €", "IS Aquisição": "0,8 % (isento)", Regime: "HPP ≤ 35 anos" },
    ai_dual_generated: false,
    legal_disclaimer:
      "Informação geral e síntese indicativa (enquadramento jurídico português) : não constitui aconselhamento jurídico, fiscal ou financeiro oficial.",
    status: "PUBLISHED",
    published_at: "2026-11-20",
  },
  {
    id: "art-pt-adene-certificado",
    slug: "portugal-certificado-energetico-adene-valorizacao-lisboa-porto",
    country_code: "PT",
    locale: "pt-PT",
    regulation_jurisdiction: "PT_SCE_ADENE",
    source_id: "src-pt-adene",
    source_name: "ADENE — Sistema de Certificação Energética",
    source_url: "https://www.sce.pt",
    category: "DPE_TRAVAUX",
    audience_scope: "BOTH",
    city: "",
    department_code: "",
    region_name: "Nacional",
    title: "Portugal — Certificado Energético ADENE (A+ a F): obrigação de publicação e impacto no valor",
    seo_meta_title: "Certificado Energético ADENE em Portugal | Monty",
    seo_meta_description:
      "Entenda a escala energética portuguesa (A+ a F), obrigatória em todos os anúncios e escrituras de venda ou arrendamento.",
    public_summary:
      "Em Portugal, todo o anúncio imobiliário deve exibir a classe do Certificado Energético ADENE (A+ a F), independente da escala DPE francesa.",
    public_takeaways: [
      "O Certificado Energético válido é obrigatório na assinatura do CPCV e escritura.",
      "Escala portuguesa (A+ a F) isolada das regras francesas.",
    ],
    pro_analysis:
      "Recolher número SCE válido e Caderneta Predial desde a angariação evita atrasos na escritura.",
    pro_commercial_arguments: ["Validar documentação completa (Caderneta, Certidão, SCE) antes de publicar."],
    pro_key_metrics: { "Escala PT": "A+ a F", Obrigação: "Anúncio & Escritura", Validade: "10 anos" },
    ai_dual_generated: false,
    legal_disclaimer:
      "Informação geral e síntese indicativa (enquadramento jurídico português) : não constitui aconselhamento jurídico, fiscal ou financeiro oficial.",
    status: "PUBLISHED",
    published_at: "2026-11-15",
  },
];

export function getPublicArticlesCatalog(
  countryCode: CountryCode,
  includeArchived = false
): PublicNewsArticle[] {
  return SEEDED_ARTICLES.filter(
    (a) =>
      a.country_code === countryCode &&
      a.audience_scope !== "PRO_ONLY" &&
      (a.status === "PUBLISHED" || (includeArchived && a.status === "ARCHIVED"))
  );
}

export function getPublicArticleBySlugCatalog(slug: string): PublicNewsArticle | null {
  const found = SEEDED_ARTICLES.find((a) => a.slug === slug && a.audience_scope !== "PRO_ONLY");
  return found ?? null;
}
