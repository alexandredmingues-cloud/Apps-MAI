package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToInt

enum class SaasPlan(
    val code: String,
    val label: String,
    val subtitle: String,
    val priceMonthlyLabel: String
) {
    INDIVIDUAL(
        code = "INDIVIDUAL",
        label = "Formule Individuelle",
        subtitle = "Agent indépendant • 1 poste • CRM complet, relances & export Excel",
        priceMonthlyLabel = "39 € HT / mois"
    ),
    AGENCY(
        code = "AGENCY",
        label = "Formule Agence",
        subtitle = "Multi-collaborateurs (jusqu'à 25) • Rôles RBAC, répartition & audit",
        priceMonthlyLabel = "149 € HT / mois"
    );

    companion object {
        fun fromCode(code: String?): SaasPlan =
            entries.firstOrNull { it.code == code } ?: INDIVIDUAL
    }
}

enum class AgencyRole(val code: String, val labelFr: String) {
    ADMIN("ADMIN", "Administrateur"),
    DIRECTOR("DIRECTOR", "Responsable d'agence"),
    AGENT("AGENT", "Agent immobilier");

    fun isManagerOrAdmin(): Boolean = this == ADMIN || this == DIRECTOR

    companion object {
        fun fromCode(code: String?): AgencyRole =
            entries.firstOrNull { it.code == code } ?: AGENT
    }
}

object CrmCatalog {
    val CONTACT_TYPES = listOf(
        "Acquéreur", "Vendeur", "Propriétaire", "Locataire", "Investisseur", "Partenaire", "Autre"
    )
    val CONTACT_STATUSES = listOf(
        "Nouveau", "À qualifier", "Qualifié", "Actif", "En attente", "Converti", "Perdu"
    )
    val PRIORITIES = listOf("Faible", "Normal", "Élevé", "Urgent")
    val SOURCES = listOf(
        "Manuel", "Import Excel", "Portail", "Site web", "Recommandation", "Vitrine", "Autre"
    )
    val FINANCING_MODES = listOf(
        "Crédit immobilier", "Comptant", "Mixte (Apport + Prêt)", "Prêt relais", "À définir"
    )
    val TIMELINES = listOf(
        "Immédiat (< 1 mois)", "Sous 3 mois", "3 à 6 mois", "6 à 12 mois", "Veille / Réflexion"
    )
    val PROPERTY_TYPES = listOf(
        "Appartement", "Maison", "Villa", "Terrain", "Immeuble / Investissement", "Local commercial", "Autre"
    )
    val EXCHANGE_CHANNELS = listOf("Appel", "SMS", "Email", "Note")
}

fun computeViewerUids(directorUids: List<String>, assignedToUid: String): List<String> {
    return (directorUids + assignedToUid)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(25)
}

@Keep
@IgnoreExtraProperties
data class ContactExchangeEntry(
    val id: String = "",
    val dateTime: String = "",
    val channel: String = "Note",
    val authorName: String = "",
    val summary: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id.take(64),
        "dateTime" to dateTime.take(40),
        "channel" to channel.take(30),
        "authorName" to authorName.take(120),
        "summary" to summary.take(800)
    )
}

@Keep
@IgnoreExtraProperties
data class AgencyOrg(
    @DocumentId val id: String = "",
    val name: String = "",
    val plan: String = SaasPlan.INDIVIDUAL.code,
    val subscriptionStatus: String = "ACTIVE",
    val ownerUid: String = "",
    val city: String = "Montpellier",
    val inviteCode: String = "",
    val directorUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val saasPlan: SaasPlan get() = SaasPlan.fromCode(plan)

    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "name" to name.take(120),
        "plan" to SaasPlan.fromCode(plan).code,
        "subscriptionStatus" to if (subscriptionStatus == "TRIAL") "TRIAL" else "ACTIVE",
        "ownerUid" to ownerUid,
        "city" to city.take(80),
        "inviteCode" to inviteCode.take(64),
        "directorUids" to directorUids.ifEmpty { listOf(ownerUid) }.distinct().take(5),
        "memberUids" to memberUids.ifEmpty { listOf(ownerUid) }.distinct().take(25),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AgencyMember(
    @DocumentId val id: String = "",
    val agencyId: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = AgencyRole.AGENT.code,
    val status: String = "ACTIVE",
    val inviteCode: String = "",
    val viewerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val agencyRole: AgencyRole get() = AgencyRole.fromCode(role)

    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "agencyId" to agencyId.take(128),
            "uid" to uid.take(128),
            "email" to email.take(160),
            "displayName" to displayName.take(120),
            "role" to AgencyRole.fromCode(role).code,
            "status" to if (status == "INVITED") "INVITED" else "ACTIVE",
            "inviteCode" to inviteCode.take(64).ifBlank { null },
            "viewerUids" to viewerUids.distinct().take(25),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class CrmContact(
    @DocumentId val id: String = "",
    val agencyId: String = "",
    val createdByUid: String = "",
    val assignedToUid: String = "",
    val assignedToName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val postalAddress: String = "",
    val contactType: String = "Acquéreur",
    val status: String = "Nouveau",
    val priority: String = "Normal",
    val budgetMin: Long = 0L,
    val budgetMax: Long = 0L,
    val sector: String = "",
    val desiredPropertyType: String = "",
    val desiredSurfaceMin: Long = 0L,
    val desiredRoomsMin: Long = 0L,
    val financingMode: String = "",
    val projectTimeline: String = "",
    val source: String = "Manuel",
    val notes: String = "",
    val lastContactedAt: String = "",
    val nextFollowUpAt: String = "",
    val linkedPropertyId: String = "",
    val linkedPropertyTitle: String = "",
    val archived: Boolean = false,
    val exchangeLogs: List<ContactExchangeEntry> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val rgpdConsentGranted: Boolean = false,
    val rgpdConsentDate: String = "",
    val rgpdLegalBasis: String = "Consentement explicite (Art. 6.1.a)",
    val rgpdRetentionUntil: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isRgpdRetentionExpired(todayIso: String): Boolean {
        val clean = rgpdRetentionUntil.trim().take(10)
        return clean.length >= 10 && clean < todayIso
    }

    fun toCreateMap(): Map<String, Any> = mapOf(
        "agencyId" to agencyId.take(128),
        "createdByUid" to createdByUid.take(128),
        "assignedToUid" to assignedToUid.take(128),
        "assignedToName" to assignedToName.take(120),
        "firstName" to firstName.take(80),
        "lastName" to lastName.take(80),
        "fullName" to fullName.take(140),
        "email" to email.take(160),
        "phone" to phone.take(40),
        "postalAddress" to postalAddress.take(220),
        "contactType" to normalizeContactType(contactType),
        "status" to status.take(60),
        "priority" to normalizePriority(priority),
        "budgetMin" to budgetMin.coerceIn(0L, 50_000_000L),
        "budgetMax" to budgetMax.coerceIn(0L, 50_000_000L),
        "sector" to sector.take(160),
        "desiredPropertyType" to desiredPropertyType.take(80),
        "desiredSurfaceMin" to desiredSurfaceMin.coerceIn(0L, 5000L),
        "desiredRoomsMin" to desiredRoomsMin.coerceIn(0L, 30L),
        "financingMode" to financingMode.take(80),
        "projectTimeline" to projectTimeline.take(80),
        "source" to source.take(60),
        "notes" to notes.take(2500),
        "lastContactedAt" to lastContactedAt.take(40),
        "nextFollowUpAt" to nextFollowUpAt.take(40),
        "linkedPropertyId" to linkedPropertyId.take(128),
        "linkedPropertyTitle" to linkedPropertyTitle.take(160),
        "archived" to archived,
        "exchangeLogs" to exchangeLogs.take(60).map { it.toMap() },
        "viewerUids" to viewerUids.distinct().take(25),
        "rgpdConsentGranted" to rgpdConsentGranted,
        "rgpdConsentDate" to rgpdConsentDate.take(30),
        "rgpdLegalBasis" to rgpdLegalBasis.take(80),
        "rgpdRetentionUntil" to rgpdRetentionUntil.take(20),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun normalizeContactType(raw: String): String =
            if (raw in CrmCatalog.CONTACT_TYPES || raw == "Mixte") raw else "Acquéreur"

        fun normalizePriority(raw: String): String =
            if (raw in CrmCatalog.PRIORITIES) raw else "Normal"
    }
}

@Keep
@IgnoreExtraProperties
data class FollowUpTask(
    @DocumentId val id: String = "",
    val agencyId: String = "",
    val contactId: String = "",
    val contactName: String = "",
    val createdByUid: String = "",
    val assignedToUid: String = "",
    val assignedToName: String = "",
    val title: String = "",
    val channel: String = "Appel",
    val dueDate: String = "",
    val dueTime: String = "10:00",
    val priority: String = "Normale",
    val status: String = "À faire",
    val autoGenerated: Boolean = false,
    val viewerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isDueToday(todayIso: String): Boolean =
        status != "Terminée" && dueDate == todayIso

    fun isOverdue(todayIso: String, currentTimeHHmm: String): Boolean {
        if (status == "Terminée" || dueDate.isBlank()) return false
        if (dueDate < todayIso) return true
        if (dueDate == todayIso) {
            val normalizedTime = dueTime.take(5).ifBlank { "23:59" }
            return normalizedTime < currentTimeHHmm
        }
        return false
    }

    fun toCreateMap(): Map<String, Any> = mapOf(
        "agencyId" to agencyId.take(128),
        "contactId" to contactId.take(128),
        "contactName" to contactName.take(140),
        "createdByUid" to createdByUid.take(128),
        "assignedToUid" to assignedToUid.take(128),
        "assignedToName" to assignedToName.take(120),
        "title" to title.take(200),
        "channel" to when (channel) {
            "Email", "SMS", "Visite" -> channel
            else -> "Appel"
        },
        "dueDate" to dueDate.take(40),
        "dueTime" to dueTime.take(10),
        "priority" to when (priority) {
            "Haute", "Urgente", "Normale", "Faible", "Basse" -> priority
            else -> "Normale"
        },
        "status" to when (status) {
            "En cours", "Terminée" -> status
            else -> "À faire"
        },
        "autoGenerated" to autoGenerated,
        "viewerUids" to viewerUids.distinct().take(25),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AgencyAuditLog(
    @DocumentId val id: String = "",
    val agencyId: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actionType: String = "",
    val targetLabel: String = "",
    val detail: String = "",
    val viewerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "agencyId" to agencyId.take(128),
        "actorUid" to actorUid.take(128),
        "actorName" to actorName.take(120),
        "actionType" to actionType.take(60),
        "targetLabel" to targetLabel.take(140),
        "detail" to detail.take(500),
        "viewerUids" to viewerUids.distinct().take(25),
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AgencyInvite(
    @DocumentId val id: String = "",
    val inviteCode: String = "",
    val agencyId: String = "",
    val agencyName: String = "",
    val targetEmail: String = "",
    val targetName: String = "",
    val role: String = AgencyRole.AGENT.code,
    val directorUid: String = "",
    val used: Boolean = false,
    val claimedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "inviteCode" to inviteCode.take(64),
        "agencyId" to agencyId.take(128),
        "agencyName" to agencyName.take(120),
        "targetEmail" to targetEmail.take(160),
        "targetName" to targetName.take(120),
        "role" to AgencyRole.AGENT.code,
        "directorUid" to directorUid.take(128),
        "used" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class CrmDashboardMetrics(
    val totalContacts: Int = 0,
    val newContacts: Int = 0,
    val toQualifyContacts: Int = 0,
    val activeProspects: Int = 0,
    val todayFollowUps: Int = 0,
    val overdueFollowUps: Int = 0,
    val convertedContacts: Int = 0,
    val conversionRatePercent: Double = 0.0
)

fun computeCrmDashboardMetrics(
    contacts: List<CrmContact>,
    tasks: List<FollowUpTask>,
    todayIso: String,
    currentTimeHHmm: String
): CrmDashboardMetrics {
    val nonArchived = contacts.filter { !it.archived }
    val total = nonArchived.size
    val nouveaux = nonArchived.count { it.status == "Nouveau" }
    val aQualifier = nonArchived.count { it.status == "À qualifier" }
    val actifs = nonArchived.count { it.status == "Actif" || it.status == "Qualifié" || it.status == "Visite planifiée" }
    val convertis = nonArchived.count { it.status == "Converti" }
    val rate = if (total == 0) 0.0 else ((convertis.toDouble() / total.toDouble()) * 1000.0).roundToInt() / 10.0

    val todayCount = tasks.count { it.isDueToday(todayIso) }
    val overdueCount = tasks.count { it.isOverdue(todayIso, currentTimeHHmm) }

    return CrmDashboardMetrics(
        totalContacts = total,
        newContacts = nouveaux,
        toQualifyContacts = aQualifier,
        activeProspects = actifs,
        todayFollowUps = todayCount,
        overdueFollowUps = overdueCount,
        convertedContacts = convertis,
        conversionRatePercent = rate
    )
}
