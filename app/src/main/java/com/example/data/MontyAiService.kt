package com.example.data

import android.graphics.Bitmap
import com.example.data.model.AdvisorProactiveRecommendation
import com.example.data.model.AutoAiRelevanceResult
import com.example.data.model.BuyerChatMessage
import com.example.data.model.ClientDossier
import com.example.data.model.CriteriaOptimizationSuggestion
import com.example.data.model.OfferRecord
import com.example.data.model.PropertyAiInsight
import com.example.data.model.PropertyDraftEnhancement
import com.example.data.model.PropertyItem
import com.example.data.model.SellerPropertyDossier
import com.example.data.model.VisitRecord
import com.example.security.InputSanitizer
import com.example.security.RgpdComplianceManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class SellerListingAiDraft(
    val polishedTitle: String,
    val description: String,
    val highlightsCsv: String,
    val vigilancesCsv: String,
    val photoObservations: String
)

enum class AdvisorAiAction(val label: String) {
    DOSSIER_SYNTHESIS("Synthèse & Classement Dossier Client"),
    NEGOTIATION_STRATEGY("Stratégie & Argumentaire Négociation"),
    CLIENT_EMAIL("Email / Message Client Personnalisé"),
    VISIT_REPORT("Compte Rendu Officiel de Visite")
}

class MontyAiService {
    private val advisorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Tu es Mon Agent Immo Copilot, l'assistant stratégique d'un conseiller immobilier patrimonial à Montpellier & Métropole (France).
                    Rédige en français professionnel, précis, structuré et actionnable.
                    Appuie-toi strictement sur les données chiffrées du dossier client, du bien et du marché montpelliérain fournis dans le prompt.
                    N'invente jamais de données inexistantes. Garde un ton sobre de banque privée / conseil immobilier haut de gamme.
                    """.trimIndent()
                )
            }
        )
    }

    private val buyerAssistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Tu es l'Assistant Acquéreur Mon Agent Immo, un guide immobilier bienveillant et rigoureux dédié aux acheteurs dans le secteur de Montpellier & Métropole (Hérault 34).
                    Tu réponds clairement, de manière concise (avec puces structurées), et avec des repères locaux précis sur :
                    1. Les options de financement en France : taux immobiliers actuels indicatifs (~3.1% à 3.7% selon durée/profil), apport personnel & ratio prêt/valeur (LTV / quotité), règles HCSF (taux d'endettement plafonné à 35% assurance incluse, durée max 25 ans, reste à vivre), Prêt à Taux Zéro (PTZ) en zone A (Montpellier est en zone A) pour le neuf.
                    2. Les frais d'acquisition (closing costs) : frais de notaire (~7,5% à 8% dans l'ancien vs ~2,5% à 3% dans le neuf), droits de mutation départementaux dans l'Hérault (34), taxe foncière à Montpellier (relativement élevée, à vérifier sur chaque bien) et charges de copropriété.
                    3. Les étapes d'achat : offre d'achat écrite (prix, durée de validité, conditions), compromis de vente ou promesse unilatérale chez le notaire/agence, délai légal de rétractation de 10 jours (loi SRU), dépôt de garantie / séquestre (5% à 10%), conditions suspensives d'obtention de prêt (45 à 60 jours), acte authentique (~3 mois après le compromis).
                    4. Le DPE (Diagnostic de Performance Énergétique) : classes A à G, étiquette climat GES, impact sur la valeur verte, audit énergétique obligatoire pour les passoires F/G, interdiction progressive de location (G depuis 2025, F en 2028) et confort d'été indispensable sous le climat méditerranéen de Montpellier.
                    5. Conseils locaux à Montpellier & Métropole : proximité Tramway (L1, L2, L3, L4, future L5), attractivité des quartiers (Port Marianne, Ovalie, Croix d'Argent, Mas Drevon, Les Arceaux, Antigone, Hôpitaux-Facultés, Juvignac, Baillargues avec gare TER), vigilance PPRI / zones inondables près du Lez ou de la Mosson, vérification des 3 derniers PV d'AG de copropriété et du fonds de travaux loi ALUR.

                    Réponds dans la langue utilisée par l'acheteur (français par défaut, ou anglais si la question est posée en anglais).
                    Personnalise tes conseils avec le profil du dossier acheteur fourni sans jamais mentionner d'informations internes confidentielles.
                    Termine si utile par une recommandation pratique liée à son dossier (ex : vérifier sa capacité dans le simulateur ou poser la question à son conseiller Alexandre Dinis Domingues).
                    """.trimIndent()
                )
            }
        )
    }

    fun streamAdvisorAnalysis(
        action: AdvisorAiAction,
        client: ClientDossier,
        property: PropertyItem,
        allProperties: List<PropertyItem>,
        visits: List<VisitRecord>,
        customInstruction: String
    ): Flow<String> = flow {
        val compat = MontyMarketData.computeCompatibility(property, client)
        val priceM2 = if (property.surface > 0) property.price / property.surface else 0L
        val sectorRef = MontyMarketData.SECTEURS_MONTPELLIER.find {
            it.name.contains(property.sector.substringBefore(" "), ignoreCase = true)
        }
        val rankedPortfolio = allProperties
            .map { it to MontyMarketData.computeCompatibility(it, client) }
            .sortedByDescending { it.second.score }
            .take(4)
            .joinToString("\n") { (p, c) ->
                "- ${p.title} (${p.sector}) : ${MontyMarketData.formatEuro(p.price)}, ${p.surface} m², DPE ${p.dpe}, Score ${c.score}/100"
            }

        val relevantVisit = visits.firstOrNull { it.clientId == client.id && it.propertyId == property.id }

        val prompt = buildString {
            appendLine("ACTION DEMANDÉE : ${action.label}")
            appendLine("DOSSIER ACQUÉREUR (pseudonymisé RGPD) : ${RgpdComplianceManager.redactDirectPiiForAi(client.name)}")
            appendLine("- Situation : ${RgpdComplianceManager.redactDirectPiiForAi(client.situation)}")
            appendLine("- Budget max : ${MontyMarketData.formatEuro(client.budgetMax)} | Apport : ${MontyMarketData.formatEuro(client.downPayment)} | Revenus : ${client.monthlyIncome} €/mois")
            appendLine("- Financement : ${RgpdComplianceManager.redactDirectPiiForAi(client.bankApproval)}")
            appendLine("- Critères : ${client.propertyType}, min ${client.minSurface} m², ${client.minRooms} ch., secteurs : ${client.sectors.joinToString(", ")}")
            appendLine("- Indispensables : ${client.essentialCriteria.joinToString(", ")}")
            appendLine("- Notes internes conseiller : ${RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(client.privateNotes, 1000))}")
            appendLine()
            appendLine("BIEN ANALYSÉ : ${property.title}")
            appendLine("- Secteur : ${property.sector} | Adresse : ${property.address}")
            appendLine("- Prix : ${MontyMarketData.formatEuro(property.price)} ($priceM2 €/m²) | Travaux est. : ${MontyMarketData.formatEuro(property.estimatedWorks)}")
            appendLine("- Surface : ${property.surface} m² | ${property.bedrooms} ch. | DPE ${property.dpe} | Charges ${property.monthlyCharges} €/mois | TF ${property.propertyTax} €/an")
            appendLine("- Points forts : ${property.strengths.joinToString("; ")}")
            appendLine("- Points faibles : ${property.weaknesses.joinToString("; ")}")
            appendLine("- Compatibilité calculée : ${compat.score}/100")
            if (sectorRef != null) {
                appendLine("- Référence observatoire secteur : Appart ${sectorRef.priceM2Apartment} €/m², Maison ${sectorRef.priceM2House ?: "N/A"} €/m², évolution ${sectorRef.evolution}, tension ${sectorRef.tension}")
            }
            if (relevantVisit != null) {
                appendLine("- Dernière visite (${relevantVisit.date}) : statut=${relevantVisit.status}, note=${relevantVisit.overallRating ?: "N/A"}/10, impressions=${RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(relevantVisit.clientImpressions, 400))}, suite=${RgpdComplianceManager.redactDirectPiiForAi(relevantVisit.nextAction)}")
            }
            appendLine()
            appendLine("TOP PORTEFEUILLE POUR CE CLIENT :\n$rankedPortfolio")
            val safeInstruction = InputSanitizer.sanitizeAiPromptInput(customInstruction, 600)
            if (safeInstruction.isNotBlank()) {
                appendLine("\nCONSIGNE COMPLÉMENTAIRE DU CONSEILLER : $safeInstruction")
            }
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            advisorModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Le modèle n'a renvoyé aucune réponse.")
        }
    }

    fun streamBuyerAssistantReply(
        question: String,
        client: ClientDossier,
        favoriteProperties: List<PropertyItem>,
        history: List<BuyerChatMessage>
    ): Flow<String> = flow {
        val buyerContext = buildString {
            appendLine("CONTEXTE DU DOSSIER ACQUÉREUR (Portail Client — minimisation RGPD) :")
            appendLine("- Prénom/Réf : ${client.name.substringBefore("—").trim()}")
            appendLine("- Étape actuelle : ${MontyMarketData.ETAPES_PROJET.getOrElse(client.projectStage.toInt()) { "Découverte" }}")
            appendLine("- Budget max : ${MontyMarketData.formatEuro(client.budgetMax)} | Apport personnel : ${MontyMarketData.formatEuro(client.downPayment)}")
            appendLine("- Revenus mensuels du foyer : ${client.monthlyIncome} €/mois | Crédits en cours : ${client.existingCredits} €/mois")
            appendLine("- Financement : ${RgpdComplianceManager.redactDirectPiiForAi(client.bankApproval)}")
            appendLine("- Secteurs ciblés : ${client.sectors.joinToString(", ")}")
            appendLine("- Recherche : ${client.propertyType}, min ${client.minSurface} m², ${client.minRooms} ch., délai : ${client.timelineDelai}")
            if (favoriteProperties.isNotEmpty()) {
                appendLine("- Biens favoris dans sa sélection :")
                favoriteProperties.forEach { p ->
                    appendLine("  * ${p.title} (${p.sector}) : ${MontyMarketData.formatEuro(p.price)}, ${p.surface} m², DPE ${p.dpe}, TF ${p.propertyTax} €/an, Charges ${p.monthlyCharges} €/mois")
                }
            }
        }

        val recentHistory = history.takeLast(6).map { msg ->
            content(role = if (msg.role == "assistant") "model" else "user") {
                text(RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(msg.content, 1500)))
            }
        }

        val chat = buyerAssistantModel.startChat(history = recentHistory)
        val safeQuestion = RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(question, 1200))
        val fullPrompt = "$buyerContext\n\nQUESTION DE L'ACQUÉREUR :\n$safeQuestion"

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(fullPrompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("L'assistant n'a pas pu générer de réponse.")
        }
    }

    private val relevanceSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "buyerHeadline" to Schema.string(
                    description = "Phrase synthétique courte (max 110 caractères) résumant les meilleures opportunités IA pour cet acheteur"
                ),
                "propertyInsights" to Schema.array(
                    description = "Analyse de pertinence pour chaque bien du portefeuille",
                    items = Schema.obj(
                        properties = mapOf(
                            "propertyId" to Schema.string(description = "Identifiant exact du bien"),
                            "aiRelevanceScore" to Schema.integer(description = "Score de pertinence IA entre 0 et 100"),
                            "badgeLabel" to Schema.string(
                                description = "Étiquette courte : 'Pépite IA', 'Top Match', 'Compromis malin', 'Potentiel négo' ou 'Hors cible'"
                            ),
                            "whyRelevant" to Schema.string(
                                description = "1 phrase concise expliquant pourquoi ce bien est pertinent ou à nuancer pour cet acheteur"
                            ),
                            "suggestedDiscountPercent" to Schema.integer(
                                description = "Marge de négociation réaliste suggérée entre 0 et 10"
                            ),
                            "negotiationArgument" to Schema.string(
                                description = "Argument de négociation concis basé sur le DPE, travaux, taxe foncière ou prix/m² secteur"
                            )
                        )
                    )
                ),
                "criteriaOptimization" to Schema.obj(
                    description = "Suggestion proactive pour améliorer la pertinence des critères de recherche si utile",
                    properties = mapOf(
                        "shouldSuggestOptimization" to Schema.boolean(
                            description = "true uniquement si ajuster un critère débloque ou améliore nettement les opportunités"
                        ),
                        "title" to Schema.string(description = "Titre court de l'amélioration suggérée"),
                        "rationale" to Schema.string(description = "Explication chiffrée en 1-2 phrases"),
                        "suggestedBudgetMax" to Schema.integer(
                            description = "Nouveau budget max conseillé en euros, ou 0 si inchangé"
                        ),
                        "suggestedSectors" to Schema.array(
                            items = Schema.string(),
                            description = "Liste des secteurs suggérés (noms exacts Montpellier), ou vide si inchangé"
                        ),
                        "suggestedMinSurface" to Schema.integer(
                            description = "Nouvelle surface min en m², ou 0 si inchangée"
                        )
                    )
                ),
                "advisorRecommendation" to Schema.obj(
                    description = "Recommandation proactive prioritaire pour le conseiller immobilier",
                    properties = mapOf(
                        "priorityTag" to Schema.string(description = "Priorité : 'Urgent', 'Opportunité' ou 'Suivi'"),
                        "headline" to Schema.string(description = "Action prioritaire recommandée au conseiller"),
                        "rationale" to Schema.string(description = "Justification en 1-2 phrases basée sur le dossier"),
                        "targetPropertyId" to Schema.string(description = "ID du bien cible principal")
                    )
                )
            )
        )
    }

    private val relevanceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = relevanceSchema
            },
            systemInstruction = content {
                text(
                    """
                    Tu es le moteur d'intelligence et de pertinence immobilière de Mon Agent Immo (Montpellier & Métropole).
                    Analyse le dossier acquéreur, ses critères, sa capacité HCSF (35% max) et l'ensemble des biens disponibles.
                    Calcule un score de pertinence IA réaliste (0..100), identifie les pépites sous le marché, propose une marge de négociation réaliste (0..10%),
                    suggère une optimisation de critères uniquement si pertinent, et formule la meilleure action suivante pour le conseiller.
                    Réponds exclusivement en français concis, précis et actionnable.
                    """.trimIndent()
                )
            }
        )
    }

    private val propertyDraftSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "polishedTitle" to Schema.string(description = "Titre immobilier attractif et précis (max 65 caractères)"),
                "strengthsCsv" to Schema.string(description = "3 à 4 atouts clés séparés par des virgules"),
                "weaknessesCsv" to Schema.string(description = "1 à 2 points de vigilance transparents séparés par des virgules"),
                "relevanceSummary" to Schema.string(description = "1 phrase résumant le profil d'acquéreur idéal pour ce bien")
            )
        )
    }

    private val propertyDraftModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = propertyDraftSchema
            }
        )
    }

    private val sellerListingSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "polishedTitle" to Schema.string(description = "Titre d'annonce immobilier vendeur clair et précis (max 80 caractères)"),
                "description" to Schema.string(description = "Texte complet structuré d'annonce immobilière en français (8 à 14 lignes) basé exclusivement sur les faits fournis et ce qui est visible sur les photos"),
                "highlightsCsv" to Schema.string(description = "3 à 5 atouts factuels séparés par des virgules"),
                "vigilancesCsv" to Schema.string(description = "1 à 2 points de transparence séparés par des virgules"),
                "photoObservations" to Schema.string(description = "Résumé factuel de ce qui est réellement observable sur les photos fournies (luminosité, état apparent, matériaux), ou 'Aucune photo fournie'")
            )
        )
    }

    private val sellerListingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = sellerListingSchema
            },
            systemInstruction = content {
                text(
                    """
                    Tu es le rédacteur immobilier expert de Mon Agent Immo (France).
                    Rédige une annonce immobilière fidèle, attractive, sobre et conforme aux mentions réglementaires françaises.
                    RÈGLE ABSOLUE : N'invente JAMAIS une vue, une exposition, un équipement, un matériau, un ascenseur ou une surface qui n'est ni mentionné dans les critères ni visible sur les photos.
                    N'inclus jamais de téléphone, d'e-mail ni d'adresse exacte si l'anonymat particulier est demandé.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun analyzePortfolioRelevance(
        signature: String,
        client: ClientDossier,
        properties: List<PropertyItem>,
        visits: List<VisitRecord>,
        offers: List<OfferRecord>
    ): AutoAiRelevanceResult {
        val availableSectors = MontyMarketData.SECTEURS_MONTPELLIER.joinToString(" | ") {
            "${it.name} (Appart ${it.priceM2Apartment}€/m², Maison ${it.priceM2House ?: "N/A"}€/m²)"
        }
        val portfolioText = properties.joinToString("\n") { p ->
            val detCompat = MontyMarketData.computeCompatibility(p, client)
            val m2 = if (p.surface > 0) p.price / p.surface else 0L
            "- id=${p.id} | ${p.title} | ${p.sector} | ${p.type} | ${p.price}€ ($m2€/m²) | ${p.surface}m² | ${p.bedrooms}ch | DPE ${p.dpe} | Travaux ${p.estimatedWorks}€ | TF ${p.propertyTax}€ | Charges ${p.monthlyCharges}€/m | Ext: ${p.exterior} | ScoreBase=${detCompat.score}"
        }
        val clientVisits = visits.filter { it.clientId == client.id }
            .joinToString("; ") { "${it.propertyId}:${it.status}(${it.overallRating ?: "-"})" }
        val clientOffers = offers.filter { it.clientId == client.id }
            .joinToString("; ") { "${it.propertyId}:${it.offeredPrice}€(${it.status})" }

        val prompt = buildString {
            appendLine("DOSSIER ACQUÉREUR : ${client.name}")
            appendLine("Situation : ${client.situation} | Statut : ${client.status} | Délai : ${client.timelineDelai}")
            appendLine("Budget max : ${client.budgetMax}€ | Apport : ${client.downPayment}€ | Revenus : ${client.monthlyIncome}€/m | Crédits : ${client.existingCredits}€/m")
            appendLine("Financement : ${client.bankApproval}")
            appendLine("Critères actuels : ${client.propertyType}, min ${client.minSurface}m², min ${client.minRooms} ch.")
            appendLine("Secteurs ciblés : ${client.sectors.joinToString(", ")}")
            appendLine("Indispensables : ${client.essentialCriteria.joinToString(", ")}")
            appendLine("Favoris actuels : ${client.selectedPropertyIds.joinToString(", ").ifBlank { "aucun" }}")
            appendLine("Visites client : ${clientVisits.ifBlank { "aucune" }} | Offres client : ${clientOffers.ifBlank { "aucune" }}")
            appendLine()
            appendLine("SECTEURS MONTPELLIER DISPONIBLES : $availableSectors")
            appendLine()
            appendLine("BIENS DU PORTEFEUILLE :")
            appendLine(portfolioText)
        }

        val response = withTimeout(60_000L) {
            relevanceModel.generateContent(prompt)
        }
        val jsonString = response.text ?: throw IllegalStateException("Réponse IA vide.")
        val json = JSONObject(jsonString)

        val insightsMap = mutableMapOf<String, PropertyAiInsight>()
        val insightsArr = json.optJSONArray("propertyInsights")
        if (insightsArr != null) {
            for (i in 0 until insightsArr.length()) {
                val item = insightsArr.getJSONObject(i)
                val propId = item.optString("propertyId")
                if (propId.isNotBlank()) {
                    insightsMap[propId] = PropertyAiInsight(
                        propertyId = propId,
                        aiRelevanceScore = item.optInt("aiRelevanceScore", 70).coerceIn(0, 100),
                        badgeLabel = item.optString("badgeLabel", "Pertinent"),
                        whyRelevant = item.optString("whyRelevant", ""),
                        suggestedDiscountPercent = item.optInt("suggestedDiscountPercent", 0).coerceIn(0, 10),
                        negotiationArgument = item.optString("negotiationArgument", "")
                    )
                }
            }
        }

        val optObj = json.optJSONObject("criteriaOptimization")
        val criteriaOpt = optObj?.let {
            val sectorsArr = it.optJSONArray("suggestedSectors")
            val sectorsList = if (sectorsArr != null) {
                (0 until sectorsArr.length()).mapNotNull { idx -> sectorsArr.optString(idx).takeIf { s -> s.isNotBlank() } }
            } else emptyList()
            CriteriaOptimizationSuggestion(
                shouldSuggestOptimization = it.optBoolean("shouldSuggestOptimization", false),
                title = it.optString("title", ""),
                rationale = it.optString("rationale", ""),
                suggestedBudgetMax = it.optLong("suggestedBudgetMax", 0L).coerceAtLeast(0L),
                suggestedSectors = sectorsList,
                suggestedMinSurface = it.optLong("suggestedMinSurface", 0L).coerceAtLeast(0L)
            )
        }

        val advObj = json.optJSONObject("advisorRecommendation")
        val advisorRec = advObj?.let {
            AdvisorProactiveRecommendation(
                priorityTag = it.optString("priorityTag", "Opportunité"),
                headline = it.optString("headline", ""),
                rationale = it.optString("rationale", ""),
                targetPropertyId = it.optString("targetPropertyId", properties.firstOrNull()?.id.orEmpty())
            )
        }

        return AutoAiRelevanceResult(
            signature = signature,
            buyerHeadline = json.optString("buyerHeadline", "Sélection classée par pertinence IA."),
            propertyInsights = insightsMap,
            criteriaOptimization = criteriaOpt,
            advisorRecommendation = advisorRec
        )
    }

    suspend fun enhancePropertyListing(
        rawTitle: String,
        sector: String,
        type: String,
        price: Long,
        surface: Long,
        bedrooms: Long,
        dpe: String,
        exterior: String,
        parking: String,
        rawStrengths: String,
        rawWeaknesses: String,
        activeClient: ClientDossier?
    ): PropertyDraftEnhancement {
        val m2 = if (surface > 0) price / surface else 0L
        val cleanTitle = InputSanitizer.sanitizeAiPromptInput(rawTitle, 160)
        val cleanStrengths = InputSanitizer.sanitizeAiPromptInput(rawStrengths, 400)
        val cleanWeaknesses = InputSanitizer.sanitizeAiPromptInput(rawWeaknesses, 400)
        val prompt = buildString {
            appendLine("Optimise cette fiche bien immobilier à Montpellier pour maximiser sa clarté et sa pertinence :")
            appendLine("Titre brut : ${cleanTitle.ifBlank { "$type $bedrooms ch. — $sector" }}")
            appendLine("Secteur : $sector | Type : $type | Prix : $price€ ($m2€/m²) | Surface : ${surface}m² | $bedrooms chambres | DPE $dpe")
            appendLine("Extérieur : $exterior | Stationnement : $parking")
            appendLine("Points forts saisis : $cleanStrengths")
            appendLine("Vigilances saisies : $cleanWeaknesses")
            if (activeClient != null) {
                appendLine("Client actif de référence : ${activeClient.name}, budget ${activeClient.budgetMax}€, critères : ${activeClient.essentialCriteria.joinToString(", ")}")
            }
        }
        val response = withTimeout(45_000L) {
            propertyDraftModel.generateContent(prompt)
        }
        val jsonString = response.text ?: throw IllegalStateException("Réponse IA vide.")
        val json = JSONObject(jsonString)
        return PropertyDraftEnhancement(
            polishedTitle = json.optString("polishedTitle", rawTitle),
            strengthsCsv = json.optString("strengthsCsv", rawStrengths),
            weaknessesCsv = json.optString("weaknessesCsv", rawWeaknesses),
            relevanceSummary = json.optString("relevanceSummary", "")
        )
    }

    suspend fun summarizeSellerDossierWithAi(
        dossier: SellerPropertyDossier,
        property: PropertyItem?
    ): String {
        val docsList = dossier.documents.joinToString("\n") { doc ->
            "- [${doc.category}] ${doc.title} : statut=${doc.status}" +
                if (doc.summaryText.isNotBlank()) " | résumé=${RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(doc.summaryText, 240))}" else ""
        }
        val prompt = buildString {
            appendLine("Rédige une synthèse claire, transparente et rassurante (8 à 12 lignes max) destinée à un acquéreur consultant le résumé gratuit avant de commander le pack complet des documents de vente.")
            appendLine("Bien : ${dossier.propertyTitle} (${dossier.propertyKind} — ${dossier.residenceType})")
            if (property != null) {
                appendLine("DPE : ${property.dpe} | GES : ${property.ges} | Surface : ${property.surface} m² | Taxe foncière : ${property.propertyTax} €/an")
            }
            if (dossier.isAlurApplicable) {
                appendLine("Copropriété Loi ALUR : Syndic=${dossier.syndicName.ifBlank { "non précisé" }}, charges=${dossier.annualCoproCharges} €/an, fonds travaux ALUR=${dossier.alurWorkFundEuro} €")
            }
            dossier.dvfValuation?.let { dvf ->
                appendLine("Repère DVF (50/100/300m) : ${dvf.estimatedLowEuro}€ - ${dvf.estimatedHighEuro}€, Score confiance ${dvf.valuationScore}/100 (${dvf.scoreLabel})")
            }
            appendLine("Inventaire des pièces :\n$docsList")
            appendLine("Ne révèle aucune coordonnée personnelle du vendeur ni donnée confidentielle ; mets en avant la transparence, les pièces vérifiées et les points restant à vérifier.")
        }
        val response = withTimeout(45_000L) {
            advisorModel.generateContent(prompt)
        }
        return response.text?.trim().orEmpty().take(3800)
    }

    suspend fun generateSellerListingWithPhotosAndLocation(
        rawTitle: String,
        publisherType: String,
        propertyType: String,
        sector: String,
        addressHint: String,
        hideExactAddress: Boolean,
        priceEuro: Long,
        surfaceM2: Long,
        rooms: Long,
        bedrooms: Long,
        dpe: String,
        conditionLabel: String,
        exterior: String,
        parking: String,
        transports: String,
        environment: String,
        highlightsText: String,
        photos: List<Bitmap>
    ): SellerListingAiDraft {
        val m2 = if (surfaceM2 > 0) priceEuro / surfaceM2 else 0L
        val safeTitle = RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(rawTitle, 160))
        val safeSector = RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(sector, 100))
        val safeAddress = if (hideExactAddress) {
            "Masquée à la demande du vendeur particulier (secteur $safeSector uniquement)"
        } else {
            RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(addressHint, 180))
        }
        val safeExt = RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(exterior, 180))
        val safePark = RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(parking, 180))
        val safeTransports = RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(transports, 200))
        val safeEnv = RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(environment, 300))
        val safeHighlights = RgpdComplianceManager.redactDirectPiiForAi(InputSanitizer.sanitizeAiPromptInput(highlightsText, 400))

        val promptText = buildString {
            appendLine("Rédige une annonce immobilière complète en français avec les informations vérifiées suivantes :")
            appendLine("- Diffuseur : $publisherType")
            appendLine("- Titre provisoire : ${safeTitle.ifBlank { "$propertyType $rooms pièces — $safeSector" }}")
            appendLine("- Type de bien : $propertyType | État : $conditionLabel")
            appendLine("- Secteur / Quartier : $safeSector | Localisation : $safeAddress")
            appendLine("- Prix : ${MontyMarketData.formatEuro(priceEuro)} ($m2 €/m²) | Surface : $surfaceM2 m² | $rooms pièces | $bedrooms chambres | DPE ${dpe.ifBlank { "NC" }}")
            if (safeExt.isNotBlank()) appendLine("- Extérieur : $safeExt")
            if (safePark.isNotBlank()) appendLine("- Stationnement : $safePark")
            if (safeTransports.isNotBlank()) appendLine("- Transports / Mobilité : $safeTransports")
            if (safeEnv.isNotBlank()) appendLine("- Environnement / Quartier : $safeEnv")
            if (safeHighlights.isNotBlank()) appendLine("- Atouts déclarés par le propriétaire : $safeHighlights")
            appendLine("- Nombre de photos jointes : ${photos.take(4).size}")
            if (photos.isNotEmpty()) {
                appendLine("Analyse les photos jointes de façon purement factuelle (luminosité, état apparent, agencement visible) sans rien inventer d'absent.")
            }
        }

        val inputContent = content {
            photos.take(4).forEach { bmp ->
                image(bmp)
            }
            text(promptText)
        }

        val response = withTimeout(60_000L) {
            sellerListingModel.generateContent(inputContent)
        }
        val jsonString = response.text ?: throw IllegalStateException("Réponse IA vide.")
        val json = JSONObject(jsonString)
        return SellerListingAiDraft(
            polishedTitle = json.optString("polishedTitle", rawTitle).take(140),
            description = json.optString("description", "").take(1500),
            highlightsCsv = json.optString("highlightsCsv", ""),
            vigilancesCsv = json.optString("vigilancesCsv", ""),
            photoObservations = json.optString("photoObservations", "")
        )
    }
}
