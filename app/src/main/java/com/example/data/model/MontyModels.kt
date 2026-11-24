package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class WorkspaceMode(val label: String) {
    ADVISOR("Espace Conseiller"),
    SELLER("Espace Vendeur"),
    BUYER("Portail Client")
}

enum class AdvisorTab(val label: String) {
    DASHBOARD("Tableau"),
    CRM("CRM & Relances"),
    TEAM("Agence & SaaS"),
    SELLER("Espace Vendeur"),
    ACQUEREURS("Acquéreurs"),
    CLIENTS("Dossiers"),
    PROPERTIES("Biens"),
    VISITS("Visites"),
    OFFERS("Offres"),
    FINANCE("Simulateur"),
    AI("Copilote IA"),
    MARKET("Marché"),
    MAP("Carte")
}

enum class BuyerTab(val key: String, val label: String) {
    EXPLORER("explorer", "Explorer"),
    SELECTION("selection", "Sélection"),
    CRITERIA("criteres", "Critères"),
    DOSSIER("dossier", "Mon Dossier")
}

@Keep
@IgnoreExtraProperties
data class TimelineEvent(
    val date: String = "",
    val type: String = "",
    val title: String = "",
    val detail: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "date" to date,
        "type" to type,
        "title" to title,
        "detail" to detail
    )
}

@Keep
@IgnoreExtraProperties
data class PropertyFeedback(
    val rating: Long = 4L,
    val status: String = "Intéressant",
    val comment: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "rating" to rating,
        "status" to status,
        "comment" to comment
    )
}

@Keep
@IgnoreExtraProperties
data class ClientDossier(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val situation: String = "",
    val status: String = "Actif",
    val projectStage: Long = 0L,
    val budgetMax: Long = 400000L,
    val downPayment: Long = 80000L,
    val monthlyIncome: Long = 6000L,
    val existingCredits: Long = 0L,
    val bankApproval: String = "",
    val sectors: List<String> = emptyList(),
    val propertyType: String = "Appartement",
    val minSurface: Long = 80L,
    val minRooms: Long = 3L,
    val essentialCriteria: List<String> = emptyList(),
    val secondaryCriteria: List<String> = emptyList(),
    val timelineDelai: String = "Sous 3 mois",
    val clientMessageToAdvisor: String = "",
    val criteriaModifiedByClient: Boolean = false,
    val lastClientUpdateLabel: String? = null,
    val selectedPropertyIds: List<String> = emptyList(),
    val propertyFeedback: Map<String, PropertyFeedback> = emptyMap(),
    val portalActive: Boolean = true,
    val portalToken: String = "",
    val portalExpiration: String = "2026-12-31",
    val privateNotes: String = "",
    val timeline: List<TimelineEvent> = emptyList(),
    val rgpdConsentGranted: Boolean = false,
    val rgpdConsentDate: String = "",
    val searchCenterAddress: String = "",
    val searchCenterLat: Double = 0.0,
    val searchCenterLon: Double = 0.0,
    val searchMode: String = "SECTOR_LIST",
    val searchRadiusKm: Int = 5,
    val searchMaxTravelMinutes: Int = 20,
    val searchTransportMode: String = "TRAM",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to ownerUid,
            "name" to name.take(120),
            "phone" to phone.take(40),
            "email" to email.take(120),
            "situation" to situation.take(300),
            "status" to status.take(80),
            "projectStage" to projectStage.coerceIn(0L, 5L),
            "budgetMax" to budgetMax.coerceIn(10000L, 50000000L),
            "downPayment" to downPayment.coerceIn(0L, 50000000L),
            "monthlyIncome" to monthlyIncome.coerceIn(0L, 1000000L),
            "existingCredits" to existingCredits.coerceIn(0L, 500000L),
            "bankApproval" to bankApproval.take(250),
            "sectors" to sectors.take(15),
            "propertyType" to propertyType.take(60),
            "minSurface" to minSurface.coerceIn(0L, 2000L),
            "minRooms" to minRooms.coerceIn(0L, 20L),
            "essentialCriteria" to essentialCriteria.take(20),
            "secondaryCriteria" to secondaryCriteria.take(20),
            "timelineDelai" to timelineDelai.take(100),
            "clientMessageToAdvisor" to clientMessageToAdvisor.take(1000),
            "criteriaModifiedByClient" to criteriaModifiedByClient,
            "lastClientUpdateLabel" to lastClientUpdateLabel?.take(80),
            "selectedPropertyIds" to selectedPropertyIds.take(50),
            "propertyFeedback" to propertyFeedback.mapValues { it.value.toMap() },
            "portalActive" to portalActive,
            "portalToken" to portalToken.take(64),
            "portalExpiration" to portalExpiration.take(40),
            "privateNotes" to privateNotes.take(3000),
            "timeline" to timeline.take(60).map { it.toMap() },
            "rgpdConsentGranted" to rgpdConsentGranted,
            "rgpdConsentDate" to rgpdConsentDate.take(30),
            "searchCenterAddress" to searchCenterAddress.take(220),
            "searchCenterLat" to searchCenterLat.coerceIn(-90.0, 90.0),
            "searchCenterLon" to searchCenterLon.coerceIn(-180.0, 180.0),
            "searchMode" to searchMode.take(24),
            "searchRadiusKm" to searchRadiusKm.coerceIn(1, 100),
            "searchMaxTravelMinutes" to searchMaxTravelMinutes.coerceIn(5, 120),
            "searchTransportMode" to searchTransportMode.take(24),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class PropertyItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val sector: String = "",
    val address: String = "",
    val type: String = "Appartement",
    val price: Long = 400000L,
    val surface: Long = 90L,
    val rooms: Long = 4L,
    val bedrooms: Long = 3L,
    val floor: String = "",
    val dpe: String = "C",
    val ges: String = "B",
    val monthlyCharges: Long = 150L,
    val propertyTax: Long = 1700L,
    val estimatedWorks: Long = 0L,
    val listingStatus: String = "Vérifiée",
    val source: String = "vérifiée",
    val exterior: String = "",
    val parking: String = "",
    val environment: String = "",
    val transports: String = "",
    val schoolsShops: String = "",
    val generalCondition: String = "",
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    val description: String = "",
    val mandateRef: String = "",
    val mandateType: String = "",
    val mandateDate: String = "",
    val agencyFeePercent: Double = 0.0,
    val listingUrl: String = "",
    val listingPortal: String = "",
    val dvfEstimatedEuro: Long = 0L,
    val dvfEstimatedLowEuro: Long = 0L,
    val dvfEstimatedHighEuro: Long = 0L,
    val dvfScore: Int = 0,
    val dvfCriteriaSummary: String = "",
    val advisorValuationNotes: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val publisherType: String = "AGENCE",
    val publisherName: String = "",
    val publicationStatus: String = "PUBLIEE",
    val hideExactAddress: Boolean = false,
    val ownerConfirmedAccuracy: Boolean = true,
    val legalPublicationConsent: Boolean = true,
    val photoVaultPaths: List<String> = emptyList(),
    val photoCount: Int = 0,
    val saleDelayMinDays: Int = 0,
    val saleDelayLikelyDays: Int = 0,
    val saleDelayMaxDays: Int = 0,
    val saleDelayConfidence: String = "",
    val saleDelaySummary: String = "",
    val cadastralRef: String = "",
    val pluZone: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "title" to title.take(160),
        "sector" to sector.take(100),
        "address" to address.take(200),
        "type" to type.take(60),
        "price" to price.coerceIn(10000L, 50000000L),
        "surface" to surface.coerceIn(5L, 5000L),
        "rooms" to rooms.coerceIn(1L, 50L),
        "bedrooms" to bedrooms.coerceIn(0L, 30L),
        "floor" to floor.take(80),
        "dpe" to dpe.take(5),
        "ges" to ges.take(5),
        "monthlyCharges" to monthlyCharges.coerceIn(0L, 50000L),
        "propertyTax" to propertyTax.coerceIn(0L, 100000L),
        "estimatedWorks" to estimatedWorks.coerceIn(0L, 5000000L),
        "listingStatus" to listingStatus.take(80),
        "source" to source.take(40),
        "exterior" to exterior.take(160),
        "parking" to parking.take(160),
        "environment" to environment.take(300),
        "transports" to transports.take(200),
        "schoolsShops" to schoolsShops.take(200),
        "generalCondition" to generalCondition.take(200),
        "strengths" to strengths.take(15),
        "weaknesses" to weaknesses.take(15),
        "description" to description.take(1500),
        "mandateRef" to mandateRef.take(64),
        "mandateType" to mandateType.take(40),
        "mandateDate" to mandateDate.take(30),
        "agencyFeePercent" to agencyFeePercent.coerceIn(0.0, 25.0),
        "listingUrl" to listingUrl.take(500),
        "listingPortal" to listingPortal.take(60),
        "dvfEstimatedEuro" to dvfEstimatedEuro.coerceIn(0L, 50000000L),
        "dvfEstimatedLowEuro" to dvfEstimatedLowEuro.coerceIn(0L, 50000000L),
        "dvfEstimatedHighEuro" to dvfEstimatedHighEuro.coerceIn(0L, 50000000L),
        "dvfScore" to dvfScore.coerceIn(0, 100),
        "dvfCriteriaSummary" to dvfCriteriaSummary.take(400),
        "advisorValuationNotes" to advisorValuationNotes.take(1500),
        "latitude" to latitude.coerceIn(-90.0, 90.0),
        "longitude" to longitude.coerceIn(-180.0, 180.0),
        "publisherType" to if (publisherType == "PARTICULIER") "PARTICULIER" else "AGENCE",
        "publisherName" to publisherName.take(120),
        "publicationStatus" to when (publicationStatus) {
            "BROUILLON", "A_VERIFIER", "PUBLIEE" -> publicationStatus
            else -> "PUBLIEE"
        },
        "hideExactAddress" to hideExactAddress,
        "ownerConfirmedAccuracy" to ownerConfirmedAccuracy,
        "legalPublicationConsent" to legalPublicationConsent,
        "photoVaultPaths" to photoVaultPaths.take(6).map { it.take(260) },
        "photoCount" to photoCount.coerceIn(0, 6),
        "saleDelayMinDays" to saleDelayMinDays.coerceIn(0, 1000),
        "saleDelayLikelyDays" to saleDelayLikelyDays.coerceIn(0, 1000),
        "saleDelayMaxDays" to saleDelayMaxDays.coerceIn(0, 1000),
        "saleDelayConfidence" to saleDelayConfidence.take(80),
        "saleDelaySummary" to saleDelaySummary.take(260),
        "cadastralRef" to cadastralRef.take(80),
        "pluZone" to pluZone.take(60),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class VisitRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val date: String = "",
    val timeSlot: String = "",
    val clientId: String = "",
    val propertyId: String = "",
    val status: String = "Programmée",
    val overallRating: Double? = null,
    val clientImpressions: String = "",
    val positives: String = "",
    val negatives: String = "",
    val technicalNotes: String = "",
    val nextAction: String = "",
    val sharedWithClient: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to ownerUid,
            "date" to date.take(40),
            "timeSlot" to timeSlot.take(80),
            "clientId" to clientId.take(128),
            "propertyId" to propertyId.take(128),
            "status" to status.take(80),
            "overallRating" to overallRating?.coerceIn(0.0, 10.0),
            "clientImpressions" to clientImpressions.take(1000),
            "positives" to positives.take(600),
            "negatives" to negatives.take(600),
            "technicalNotes" to technicalNotes.take(600),
            "nextAction" to nextAction.take(600),
            "sharedWithClient" to sharedWithClient,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class OfferRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val date: String = "",
    val clientId: String = "",
    val propertyId: String = "",
    val askingPrice: Long = 0L,
    val offeredPrice: Long = 0L,
    val estimatedWorks: Long = 0L,
    val status: String = "Transmise au vendeur",
    val sellerContext: String = "",
    val clientFinancing: String = "",
    val suspensiveConditions: String = "",
    val arguments: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "date" to date.take(40),
        "clientId" to clientId.take(128),
        "propertyId" to propertyId.take(128),
        "askingPrice" to askingPrice.coerceIn(10000L, 50000000L),
        "offeredPrice" to offeredPrice.coerceIn(10000L, 50000000L),
        "estimatedWorks" to estimatedWorks.coerceIn(0L, 5000000L),
        "status" to status.take(80),
        "sellerContext" to sellerContext.take(600),
        "clientFinancing" to clientFinancing.take(600),
        "suspensiveConditions" to suspensiveConditions.take(600),
        "arguments" to arguments.take(15),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class BuyerChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val clientId: String = "",
    val role: String = "user",
    val content: String = "",
    val topic: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "clientId" to clientId.take(128),
        "role" to if (role == "assistant") "assistant" else "user",
        "content" to content.take(6000),
        "topic" to topic.take(80),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class MontpellierSector(
    val name: String,
    val priceM2Apartment: Int,
    val priceM2House: Int?,
    val evolution: String,
    val tension: String,
    val avgDpe: String,
    val description: String
)

enum class BadgeLevel { OK, WARN, ALERT }

data class CompatibilityBadge(
    val label: String,
    val level: BadgeLevel
)

data class CompatibilityResult(
    val score: Int,
    val reasons: List<String>,
    val alerts: List<String>,
    val badges: List<CompatibilityBadge>
)

data class FinanceMetrics(
    val notaryFees: Long,
    val totalProjectCost: Long,
    val borrowedAmount: Long,
    val monthlyExclInsurance: Long,
    val monthlyInsurance: Long,
    val monthlyInclInsurance: Long,
    val totalCreditCost: Long,
    val debtRatioPercent: Double,
    val remainderToLive: Long,
    val realMonthlyHousingCost: Long,
    val scenario20Years: Long,
    val scenario25Years: Long
)

data class HcsfCapacityInput(
    val monthlyNetIncome: Long = 6800L,
    val existingMonthlyCredits: Long = 0L,
    val downPayment: Long = 95000L,
    val durationYears: Int = 25,
    val interestRatePercent: Double = 3.35,
    val insuranceRatePercent: Double = 0.32,
    val maxDebtRatioPercent: Double = 35.0,
    val notaryRatePercent: Double = 7.8,
    val isVefaNeuf: Boolean = false
)

data class HcsfDurationScenario(
    val durationYears: Int,
    val maxBorrowedCapital: Long,
    val maxPropertyPriceNet: Long,
    val isHcsfCompliant: Boolean
)

data class HcsfCapacityResult(
    val maxMonthlyDebtTotal: Long,
    val maxMonthlyLoanPayment: Long,
    val maxBorrowedCapital: Long,
    val maxTotalProjectBudget: Long,
    val maxPropertyPriceNet: Long,
    val estimatedNotaryFees: Long,
    val totalCreditCost: Long,
    val effectiveDebtRatioPercent: Double,
    val remainderToLive: Long,
    val isHcsfRatioCompliant: Boolean,
    val isHcsfDurationCompliant: Boolean,
    val durationScenarios: List<HcsfDurationScenario>
)

data class PropertyAiInsight(
    val propertyId: String,
    val aiRelevanceScore: Int,
    val badgeLabel: String,
    val whyRelevant: String,
    val suggestedDiscountPercent: Int,
    val negotiationArgument: String
)

data class CriteriaOptimizationSuggestion(
    val shouldSuggestOptimization: Boolean,
    val title: String,
    val rationale: String,
    val suggestedBudgetMax: Long,
    val suggestedSectors: List<String>,
    val suggestedMinSurface: Long
)

data class AdvisorProactiveRecommendation(
    val priorityTag: String,
    val headline: String,
    val rationale: String,
    val targetPropertyId: String
)

data class AutoAiRelevanceResult(
    val signature: String,
    val buyerHeadline: String,
    val propertyInsights: Map<String, PropertyAiInsight>,
    val criteriaOptimization: CriteriaOptimizationSuggestion?,
    val advisorRecommendation: AdvisorProactiveRecommendation?
)

data class PropertyDraftEnhancement(
    val polishedTitle: String,
    val strengthsCsv: String,
    val weaknessesCsv: String,
    val relevanceSummary: String
)

