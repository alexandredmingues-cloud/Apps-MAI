package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class MarketCountry(
    val code: String,
    val label: String,
    val flagEmoji: String,
    val creditRuleLabel: String,
    val usuryOrRateNote: String,
    val energyLabel: String
) {
    FRANCE(
        code = "FR",
        label = "France entière",
        flagEmoji = "🇫🇷",
        creditRuleLabel = "HCSF 35 % • 25 ans",
        usuryOrRateNote = "~5,05 % (Usure 20 ans+)",
        energyLabel = "DPE A–G"
    ),
    PORTUGAL(
        code = "PT",
        label = "Portugal (Isolé)",
        flagEmoji = "🇵🇹",
        creditRuleLabel = "DSTI Banco de Portugal",
        usuryOrRateNote = "Euribor + Spread (PT)",
        energyLabel = "CE ADENE A+–F"
    )
}

data class MarketCityBenchmark(
    val id: String,
    val country: MarketCountry,
    val cityName: String,
    val regionLabel: String,
    val avgAptM2: Int,
    val avgHouseM2: Int,
    val topDynamicSectorLabel: String
)

enum class MarketInfoCategory(val label: String) {
    ALL("Tout"),
    RATES("Taux & Crédit"),
    MARKET("Marché"),
    LAWS("Nouvelles Lois"),
    ENERGY_AID("Énergie & Aides")
}

enum class MarketInfoAction(val ctaLabel: String) {
    NONE(""),
    OPEN_HCSF_SIMULATOR("Simuler ma capacité HCSF 35%"),
    OPEN_MARKET_OBSERVATORY("Voir l'Observatoire du Marché")
}

data class MortgageRateRow(
    val durationLabel: String,
    val averageRateLabel: String,
    val bestProfileRateLabel: String,
    val trendLabel: String,
    val isDownwardTrend: Boolean
)

data class RealEstateNewsItem(
    val id: String,
    val category: MarketInfoCategory,
    val badgeText: String,
    val title: String,
    val shortSummary: String,
    val fullAnalysis: String,
    val impactBuyer: String,
    val impactSellerAdvisor: String,
    val keyStatLabel: String,
    val keyStatValue: String,
    val sourceRef: String,
    val publishedLabel: String,
    val actionType: MarketInfoAction = MarketInfoAction.NONE,
    val country: MarketCountry = MarketCountry.FRANCE
)

data class MarketBriefingSnapshot(
    val updatedLabel: String,
    val montpellierAvgAptM2: Int,
    val montpellierAvgHouseM2: Int,
    val topDynamicSectorLabel: String,
    val usuryRate20PlusLabel: String,
    val hcsfCapLabel: String,
    val mortgageRates: List<MortgageRateRow>,
    val items: List<RealEstateNewsItem>,
    val country: MarketCountry = MarketCountry.FRANCE,
    val selectedCity: MarketCityBenchmark,
    val availableCities: List<MarketCityBenchmark>,
    val legalDisclaimer: String
)

object RealEstateMarketBriefingProvider {
    private const val TAG = "RealEstateBriefing"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Optionnel : récupère le taux directeur de facilité de dépôt BCE (SDMX JSON public sans clé).
     * Retourne null en cas d'indisponibilité réseau sans jamais bloquer l'affichage d'accueil.
     */
    suspend fun fetchLiveEcbDepositRatePercent(): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://data-api.ecb.europa.eu/service/data/FM/B.U2.EUR.4F.KR.DFR.LEV?lastNObservations=1&format=jsondata")
                .header("Accept", "application/json")
                .header("User-Agent", "MontyImmo-Android/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val dataSets = root.optJSONArray("dataSets") ?: return@withContext null
                if (dataSets.length() == 0) return@withContext null
                val series = dataSets.getJSONObject(0).optJSONObject("series") ?: return@withContext null
                val firstSeriesKey = series.keys().asSequence().firstOrNull() ?: return@withContext null
                val observations = series.getJSONObject(firstSeriesKey).optJSONObject("observations")
                    ?: return@withContext null
                val firstObsKey = observations.keys().asSequence().firstOrNull() ?: return@withContext null
                val obsArray = observations.optJSONArray(firstObsKey) ?: return@withContext null
                val value = obsArray.optDouble(0, Double.NaN)
                if (value.isNaN()) return@withContext null
                String.format(java.util.Locale.FRANCE, "%.2f %%", value)
            }
        } catch (e: Exception) {
            Log.d(TAG, "ECB live rate unavailable (offline fallback used): ${e.message}")
            null
        }
    }

    fun buildSnapshot(
        country: MarketCountry = MarketCountry.FRANCE,
        cityId: String? = null
    ): MarketBriefingSnapshot {
        val sectors = MontyMarketData.SECTEURS_MONTPELLIER
        val mplAvgAptM2 = if (sectors.isNotEmpty()) {
            sectors.map { it.priceM2Apartment }.average().roundToInt()
        } else 3800
        val housePrices = sectors.mapNotNull { it.priceM2House }
        val mplAvgHouseM2 = if (housePrices.isNotEmpty()) {
            housePrices.average().roundToInt()
        } else 4350
        val topMplSector = sectors.maxByOrNull { sec ->
            sec.evolution.replace("+", "").replace("%", "").trim().toDoubleOrNull() ?: 0.0
        }
        val topMplLabel = topMplSector?.let {
            "${it.name.substringBefore(" (")} (${it.evolution}/an)"
        } ?: "Baillargues (+4.2%/an)"

        val frCities = listOf(
            MarketCityBenchmark("lyon", MarketCountry.FRANCE, "Lyon", "Auvergne-Rhône-Alpes", 4950, 5600, "Croix-Rousse / Part-Dieu (+2.4%/an)"),
            MarketCityBenchmark("paris", MarketCountry.FRANCE, "Paris", "Île-de-France", 9650, 10200, "15e / Grand Paris Express (+1.8%/an)"),
            MarketCityBenchmark("bordeaux", MarketCountry.FRANCE, "Bordeaux", "Nouvelle-Aquitaine", 4550, 5100, "Nansouty / Tram B (+2.1%/an)"),
            MarketCityBenchmark("nantes", MarketCountry.FRANCE, "Nantes", "Pays de la Loire", 3850, 4400, "Procé / Île de Nantes (+2.6%/an)"),
            MarketCityBenchmark("montpellier", MarketCountry.FRANCE, "Montpellier", "Occitanie", mplAvgAptM2, mplAvgHouseM2, topMplLabel)
        )

        val ptCities = listOf(
            MarketCityBenchmark("lisboa", MarketCountry.PORTUGAL, "Lisboa", "Área Metropolitana de Lisboa", 5200, 5850, "Avenidas Novas / Parque das Nações (+4.8%/an)"),
            MarketCityBenchmark("porto", MarketCountry.PORTUGAL, "Porto", "Norte (Porto / Braga)", 3650, 4150, "Boavista / Foz do Douro (+4.1%/an)")
        )

        val availableCities = if (country == MarketCountry.PORTUGAL) ptCities else frCities
        val selectedCity = availableCities.firstOrNull { it.id == cityId } ?: availableCities.first()

        val rates = if (country == MarketCountry.PORTUGAL) {
            listOf(
                MortgageRateRow("15 ans (Taxa Fixa)", "3,10 %", "2,85 %", "-0,10 pt", true),
                MortgageRateRow("20 ans (Taxa Mista)", "3,25 %", "2,98 %", "-0,12 pt", true),
                MortgageRateRow("30 ans (Euribor 6m)", "3,40 %", "3,12 %", "Variable / Mista", true),
                MortgageRateRow("DSTI Banco PT", "≤ 35–50 %", "LTV ≤ 90 % HPP", "Règle BdP", true)
            )
        } else {
            listOf(
                MortgageRateRow("10 ans", "3,00 %", "2,80 %", "-0,10 pt", true),
                MortgageRateRow("15 ans", "3,15 %", "2,95 %", "-0,12 pt", true),
                MortgageRateRow("20 ans", "3,30 %", "3,08 %", "-0,15 pt", true),
                MortgageRateRow("25 ans", "3,45 %", "3,22 %", "Stabilisé", true)
            )
        }

        val frNews = listOf(
            RealEstateNewsItem(
                id = "rates_2026",
                category = MarketInfoCategory.RATES,
                badgeText = "TAUX CRÉDIT • HCSF",
                title = "Taux immobiliers France : fenêtre à ~3,30 % sur 20 ans",
                shortSummary = "Les barèmes bancaires nationaux se stabilisent autour de 3,15 % sur 15 ans et 3,30 % à 3,45 % sur 20-25 ans.",
                fullAnalysis = "Après la détente amorcée par la BCE, les banques françaises maintiennent des grilles offensives pour capter les primo-accédants et les profils patrimoniaux (apport ≥ 15 %, DPE A à C). Le taux d'usure Banque de France laisse une marge confortable pour financer assurance emprunteur et garantie.",
                impactBuyer = "Sécurisez un accord de principe avant vos visites et comparez la délégation d'assurance (économie de 0,15 à 0,25 pt sur le TAEG). Règle HCSF : endettement plafonné à 35 % assurance incluse.",
                impactSellerAdvisor = "Un acquéreur avec accord bancaire récent et simulation HCSF validée réduit fortement le risque de condition suspensive refusée.",
                keyStatLabel = "Taux moyen 20 ans",
                keyStatValue = "3,30 %",
                sourceRef = "Observatoire Crédit Logement / Banque de France",
                publishedLabel = "Mise à jour marché FR",
                actionType = MarketInfoAction.OPEN_HCSF_SIMULATOR,
                country = MarketCountry.FRANCE
            ),
            RealEstateNewsItem(
                id = "market_france_city",
                category = MarketInfoCategory.MARKET,
                badgeText = "MARCHÉ • ${selectedCity.cityName.uppercase()}",
                title = "${selectedCity.cityName} (${selectedCity.regionLabel}) : prime à la valeur verte",
                shortSummary = "Repère médian à ${MontyMarketData.formatEuro(selectedCity.avgAptM2.toLong())}/m² (Appt) et ${MontyMarketData.formatEuro(selectedCity.avgHouseM2.toLong())}/m² (Maison). Secteur moteur : ${selectedCity.topDynamicSectorLabel}.",
                fullAnalysis = "Partout en France (Paris, Lyon, Bordeaux, Nantes, Montpellier...), la demande se concentre autour des transports structurants (tram, métro, TER) et des biens DPE A à C. Les biens sans défaut majeur partent sous 45 jours au prix marché DVF, tandis que les passoires F/G subissent des décotes de 7 % à 15 %.",
                impactBuyer = "Appuyez vos offres sur les références DVF récentes à 300 m et chiffrez précisément les travaux éventuels pour négocier sereinement.",
                impactSellerAdvisor = "Positionnez le prix dans la fourchette DVF dès la mise en vente : un bien surévalué rallonge le délai de vente de +35 à +60 jours.",
                keyStatLabel = "Appt médian ${selectedCity.cityName}",
                keyStatValue = "${MontyMarketData.formatEuro(selectedCity.avgAptM2.toLong())}/m²",
                sourceRef = "DVF Etalab / Notaires de France",
                publishedLabel = "Baromètre France entière",
                actionType = MarketInfoAction.OPEN_MARKET_OBSERVATORY,
                country = MarketCountry.FRANCE
            ),
            RealEstateNewsItem(
                id = "law_dpe_2026",
                category = MarketInfoCategory.LAWS,
                badgeText = "LOI CLIMAT & DPE 2026",
                title = "DPE : interdiction des passoires G, réforme électricité & audit E",
                shortSummary = "Les logements classés G sont interdits à la location depuis 2025 (F en 2028). L'actualisation du coefficient électricité revalorise les petites surfaces.",
                fullAnalysis = "La loi Climat et Résilience impose la décence énergétique progressive : les logements DPE G ne peuvent plus être remis en location (F en 2028, E en 2034). L'audit énergétique réglementaire s'applique lors de la vente de maisons/monopropriétés classées E, F ou G, et la mise à jour du coefficient électricité (1,9 vs 2,3) revalorise de nombreux T1/T2 électriques.",
                impactBuyer = "Vérifiez la date du DPE (méthode post-juillet 2021) et contrôlez si un chauffage électrique bénéficie du recalcul favorable avant d'exclure un bien.",
                impactSellerAdvisor = "Anticipez le DPE et l'audit énergétique dès l'estimation : un passage de F/G vers D sécurise la valeur patrimoniale.",
                keyStatLabel = "Coeff. électricité",
                keyStatValue = "1,9 (vs 2,3)",
                sourceRef = "Code de la construction — Loi Climat & Décret DPE",
                publishedLabel = "Réglementation FR",
                actionType = MarketInfoAction.NONE,
                country = MarketCountry.FRANCE
            ),
            RealEstateNewsItem(
                id = "law_le_meur",
                category = MarketInfoCategory.LAWS,
                badgeText = "NOUVELLE LOI • LE MEUR",
                title = "Loi Le Meur : encadrement des meublés touristiques & copropriétés",
                shortSummary = "Fiscalité micro-BIC réduite (30 % non classés / 50 % classés), DPE obligatoire et possibilité pour la copropriété d'interdire la location touristique.",
                fullAnalysis = "La loi n° 2024-1039 (loi Le Meur) rééquilibre le marché locatif longue durée partout en France : abattements micro-BIC réduits, plafond possible de 90 jours/an sur résidence principale selon les communes, et modification du règlement de copropriété à la majorité des 2/3 (art. 26) pour interdire les meublés touristiques.",
                impactBuyer = "Pour un achat locatif ou en copropriété, vérifiez le règlement de copropriété et les délibérations de la commune.",
                impactSellerAdvisor = "Le recentrage vers la location nue ou meublée longue durée rassure les résidences familiales et les banques.",
                keyStatLabel = "Abattement non classé",
                keyStatValue = "30 % (15 k€)",
                sourceRef = "Loi n° 2024-1039 — Légifrance",
                publishedLabel = "Cadre juridique FR",
                actionType = MarketInfoAction.NONE,
                country = MarketCountry.FRANCE
            ),
            RealEstateNewsItem(
                id = "aid_ptz_elargi",
                category = MarketInfoCategory.ENERGY_AID,
                badgeText = "AIDES & PTZ ÉLARGI",
                title = "PTZ élargi à toute la France & zonage A / B1 / B2 / C",
                shortSummary = "Le Prêt à Taux Zéro couvre le neuf sur tout le territoire jusqu'à fin 2027 et l'ancien avec 25 % de travaux en zones détendues.",
                fullAnalysis = "La loi de finances a étendu le PTZ sur l'ensemble du territoire national pour les primo-accédants achetant un logement neuf (collectif et individuel selon quotité et ressources), avec des plafonds adaptés aux zones A bis, A, B1, B2 et C. Il peut financer jusqu'à 40-50 % du projet sans intérêts, avec différé.",
                impactBuyer = "Cumulez PTZ + prêt principal bancaire lissé : cela réduit votre mensualité et facilite le respect du plafond HCSF de 35 %.",
                impactSellerAdvisor = "Valorisez l'éligibilité PTZ auprès des primo-accédants et vérifiez la zone communale dès la prise de mandat.",
                keyStatLabel = "Couverture PTZ",
                keyStatValue = "France entière",
                sourceRef = "Loi de Finances / Service-Public.fr",
                publishedLabel = "Financement & Aides FR",
                actionType = MarketInfoAction.OPEN_HCSF_SIMULATOR,
                country = MarketCountry.FRANCE
            ),
            RealEstateNewsItem(
                id = "law_alur_dmto",
                category = MarketInfoCategory.LAWS,
                badgeText = "NOTAIRE & LOI ALUR",
                title = "DMTO départementaux & Pack ALUR copropriété",
                shortSummary = "Droits de mutation (~7,5-8 % ancien, ~2,5-3 % neuf) variables par département et Plan Pluriannuel de Travaux (PPT) obligatoire (>15 ans).",
                fullAnalysis = "En copropriété, le vendeur doit fournir l'état daté, les 3 derniers PV d'AG, le carnet d'entretien, le DTG et le projet de Plan Pluriannuel de Travaux (PPT). Côté droits de mutation (DMTO), plusieurs départements appliquent des relèvements ciblés avec exonérations possibles pour certains primo-accédants.",
                impactBuyer = "Contrôlez le fonds de travaux ALUR et les appels de fonds votés avant toute offre d'achat.",
                impactSellerAdvisor = "Réunissez le pack complet ALUR avant la mise en ligne pour raccourcir les délais chez le notaire.",
                keyStatLabel = "Frais ancien / neuf",
                keyStatValue = "~7,8 % / ~2,8 %",
                sourceRef = "Loi ALUR / Notaires de France",
                publishedLabel = "Transaction FR",
                actionType = MarketInfoAction.NONE,
                country = MarketCountry.FRANCE
            )
        )

        val ptNews = listOf(
            RealEstateNewsItem(
                id = "pt_rates_bdp",
                category = MarketInfoCategory.RATES,
                badgeText = "BANCO DE PORTUGAL • DSTI",
                title = "Crédit habitação em Portugal : limites DSTI, LTV e taxa mista",
                shortSummary = "Banco de Portugal recomenda um rácio DSTI ≤ 50 % (35 % recomendado) e LTV máximo de 90 % para Habitação Própria Permanente (HPP).",
                fullAnalysis = "No mercado português (totalmente isolado das regras francesas), o Banco de Portugal impõe critérios prudenciais específicos: maturidade máxima consoante a idade do mutuário (35 a 40 anos) e avaliação da taxa de esforço com teste de choque Euribor. As ofertas de taxa mista a 2–5 anos rondam 2,95 % a 3,35 %.",
                impactBuyer = "Compare TAEG e MTIC entre bancos portugueses e valide antecipadamente a sua taxa de esforço (DSTI).",
                impactSellerAdvisor = "Qualifique os compradores internacionais/residentes com pré-aprovação bancária portuguesa válida.",
                keyStatLabel = "LTV máx. HPP",
                keyStatValue = "90 % (BdP)",
                sourceRef = "Banco de Portugal",
                publishedLabel = "Module PT Isolé",
                actionType = MarketInfoAction.NONE,
                country = MarketCountry.PORTUGAL
            ),
            RealEstateNewsItem(
                id = "pt_imt_jovem",
                category = MarketInfoCategory.LAWS,
                badgeText = "FISCALITÉ PT • IMT JOVEM",
                title = "IMT, Imposto do Selo (0,8 %) & isenção IMT Jovem (≤ 35 anos)",
                shortSummary = "Em Portugal, os custos de aquisição incluem o IMT progressivo e o Imposto do Selo (0,8 % + 0,6 % crédito), com isenção IMT Jovem até 324 058 €.",
                fullAnalysis = "As regras fiscais portuguesas (IMT — Imposto Municipal sobre as Transmissões Onerosas de Imóveis e Imposto do Selo) são calculadas segundo tabelas próprias do Portal das Finanças, sem qualquer mistura com os DMTO franceses. A medida IMT Jovem isenta de IMT e Imposto do Selo na aquisição de 1.ª HPP até ao 4.º escalão.",
                impactBuyer = "Utilize o simulador PT isolado para estimar exatamente o seu IMT e Imposto do Selo antes da escritura (CPCV).",
                impactSellerAdvisor = "Informe os jovens compradores (≤ 35 anos) elegíveis para acelerar decisões em imóveis T1/T2/T3.",
                keyStatLabel = "Imposto do Selo",
                keyStatValue = "0,8 % imóvel",
                sourceRef = "Portal das Finanças (Portugal)",
                publishedLabel = "Fiscalité PT",
                actionType = MarketInfoAction.NONE,
                country = MarketCountry.PORTUGAL
            ),
            RealEstateNewsItem(
                id = "pt_market_city",
                category = MarketInfoCategory.MARKET,
                badgeText = "MERCADO PT • ${selectedCity.cityName.uppercase()}",
                title = "${selectedCity.cityName} : preços médios e procura residencial",
                shortSummary = "Referência mediana em ${selectedCity.cityName} : ${MontyMarketData.formatEuro(selectedCity.avgAptM2.toLong())}/m² (Apartamento) e ${MontyMarketData.formatEuro(selectedCity.avgHouseM2.toLong())}/m² (Moradia).",
                fullAnalysis = "Em ${selectedCity.cityName} (${selectedCity.regionLabel}), os bairros bem servidos por metro/comboio e imóveis com Certificado Energético A+ a C mantêm elevada liquidez, com destaque para ${selectedCity.topDynamicSectorLabel}.",
                impactBuyer = "Compare o preço/m² por freguesia e verifique a Licença de Utilização bem como a Caderneta Predial.",
                impactSellerAdvisor = "Prepare toda a documentação predial (Caderneta, Certidão Permanente, FTH, CE) antes da assinatura do CPCV.",
                keyStatLabel = "Média ${selectedCity.cityName}",
                keyStatValue = "${MontyMarketData.formatEuro(selectedCity.avgAptM2.toLong())}/m²",
                sourceRef = "INE Portugal / Confidencial Imobiliário",
                publishedLabel = "Baromètre PT",
                actionType = MarketInfoAction.NONE,
                country = MarketCountry.PORTUGAL
            ),
            RealEstateNewsItem(
                id = "pt_adene_ce",
                category = MarketInfoCategory.ENERGY_AID,
                badgeText = "ADENE • CERTIFICADO",
                title = "Certificado Energético ADENE (A+ a F) obrigatório antes do anúncio",
                shortSummary = "Em Portugal, a classe energética ADENE (escala A+ até F) é obrigatória desde a publicação do anúncio e na escritura.",
                fullAnalysis = "Gerida pela ADENE, a certificação energética portuguesa é independente do DPE francês. Classes A+, A ou B permitem frequentemente condições bancárias bonificadas (crédito verde) e elegibilidade a apoios de eficiência energética.",
                impactBuyer = "Verifique a validade de 10 anos do Certificado Energético e eventuais recomendações de isolamento ou bomba de calor.",
                impactSellerAdvisor = "Emita o Certificado ADENE logo na angariação para publicar em total conformidade legal.",
                keyStatLabel = "Escala PT",
                keyStatValue = "A+ até F",
                sourceRef = "ADENE — Agência para a Energia",
                publishedLabel = "Énergie PT",
                actionType = MarketInfoAction.NONE,
                country = MarketCountry.PORTUGAL
            )
        )

        val newsItems = if (country == MarketCountry.PORTUGAL) ptNews else frNews

        return MarketBriefingSnapshot(
            updatedLabel = "${country.flagEmoji} ${country.label} • ${selectedCity.cityName}",
            montpellierAvgAptM2 = selectedCity.avgAptM2,
            montpellierAvgHouseM2 = selectedCity.avgHouseM2,
            topDynamicSectorLabel = selectedCity.topDynamicSectorLabel,
            usuryRate20PlusLabel = country.usuryOrRateNote,
            hcsfCapLabel = country.creditRuleLabel,
            mortgageRates = rates,
            items = newsItems,
            country = country,
            selectedCity = selectedCity,
            availableCities = availableCities,
            legalDisclaimer = "Repères indicatifs cloisonnés par pays (${country.code}). Ne constitue pas un conseil juridique ou financier officiel."
        )
    }
}
