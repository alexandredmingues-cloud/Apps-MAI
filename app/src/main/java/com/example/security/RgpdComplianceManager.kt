package com.example.security

import com.example.data.local.AcquereurEntity
import com.example.data.model.ClientDossier
import com.example.data.model.CrmContact
import com.example.data.model.FollowUpTask
import com.example.data.model.SellerPropertyDossier
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Moteur de conformité RGPD (Règlement UE 2016/679 & Référentiel CNIL Immobilier) :
 * - Calcul des durées de conservation (36 mois prospection / 60 mois mandat & obligation légale)
 * - Minimisation et masquage des PII avant appel IA (Art. 5.1.c)
 * - Export structuré JSON pour droit d'accès (Art. 15) et portabilité (Art. 20)
 */
object RgpdComplianceManager {

    const val LEGAL_BASIS_CONSENT = "Consentement explicite (Art. 6.1.a)"
    const val LEGAL_BASIS_PRECONTRACT = "Mesures précontractuelles (Art. 6.1.b)"
    const val LEGAL_BASIS_MANDATE = "Exécution de mandat / contrat (Art. 6.1.b)"
    const val LEGAL_BASIS_LEGITIMATE = "Intérêt légitime prospection (Art. 6.1.f)"
    const val LEGAL_BASIS_OBLIGATION = "Obligation légale ALUR / TRACFIN (Art. 6.1.c)"

    val LEGAL_BASIS_OPTIONS: List<String> = listOf(
        LEGAL_BASIS_CONSENT,
        LEGAL_BASIS_PRECONTRACT,
        LEGAL_BASIS_MANDATE,
        LEGAL_BASIS_LEGITIMATE,
        LEGAL_BASIS_OBLIGATION
    )

    private val PHONE_REGEX = Regex("""(?:\+33|0)\s*[1-9](?:[\s.\-]?\d{2}){4}""")
    private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val IBAN_REGEX = Regex("""\b[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){2,7}\b""")

    fun todayIso(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(date)

    fun retentionMonthsFor(legalBasis: String): Int = when {
        legalBasis.contains("mandat", ignoreCase = true) ||
            legalBasis.contains("obligation", ignoreCase = true) ||
            legalBasis.contains("TRACFIN", ignoreCase = true) -> 60
        else -> 36
    }

    fun computeRetentionUntilIso(
        legalBasis: String,
        referenceDate: Date = Date()
    ): String {
        val cal = Calendar.getInstance(Locale.FRANCE).apply {
            time = referenceDate
            add(Calendar.MONTH, retentionMonthsFor(legalBasis))
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(cal.time)
    }

    fun isRetentionExpired(
        retentionUntilIso: String,
        today: String = todayIso()
    ): Boolean {
        val clean = retentionUntilIso.trim().take(10)
        if (clean.length < 10) return false
        return clean < today
    }

    /**
     * Minimise les données envoyées aux modèles génératifs externes (Art. 5.1.c RGPD) :
     * aucun numéro de téléphone, e-mail ou IBAN ne doit transiter dans un prompt LLM.
     */
    fun redactDirectPiiForAi(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(EMAIL_REGEX, "[email-masqué-rgpd]")
            .replace(PHONE_REGEX, "[tél-masqué-rgpd]")
            .replace(IBAN_REGEX, "[iban-masqué-rgpd]")
    }

    /**
     * Export JSON structuré Art. 15 / Art. 20 pour une fiche contact CRM.
     */
    fun buildContactRgpdJson(
        contact: CrmContact,
        tasks: List<FollowUpTask>,
        exportedBy: String
    ): String {
        val root = JSONObject().apply {
            put("exportStandard", "RGPD UE 2016/679 — Droit d'accès & Portabilité (Art. 15 & 20)")
            put("generatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE).format(Date()))
            put("exportedBy", exportedBy)
            put("contact", JSONObject().apply {
                put("id", contact.id)
                put("firstName", contact.firstName)
                put("lastName", contact.lastName)
                put("fullName", contact.fullName)
                put("email", contact.email)
                put("phone", contact.phone)
                put("postalAddress", contact.postalAddress)
                put("contactType", contact.contactType)
                put("status", contact.status)
                put("sector", contact.sector)
                put("budgetMin", contact.budgetMin)
                put("budgetMax", contact.budgetMax)
                put("rgpdConsentGranted", contact.rgpdConsentGranted)
                put("rgpdConsentDate", contact.rgpdConsentDate)
                put("rgpdLegalBasis", contact.rgpdLegalBasis)
                put("rgpdRetentionUntil", contact.rgpdRetentionUntil)
                put("archived", contact.archived)
            })
            val exArr = JSONArray()
            contact.exchangeLogs.forEach { log ->
                exArr.put(JSONObject().apply {
                    put("dateTime", log.dateTime)
                    put("channel", log.channel)
                    put("authorName", log.authorName)
                    put("summary", log.summary)
                })
            }
            put("exchangeLogs", exArr)
            val taskArr = JSONArray()
            tasks.filter { it.contactId == contact.id }.forEach { t ->
                taskArr.put(JSONObject().apply {
                    put("title", t.title)
                    put("channel", t.channel)
                    put("dueDate", t.dueDate)
                    put("dueTime", t.dueTime)
                    put("status", t.status)
                })
            }
            put("followUpTasks", taskArr)
        }
        return root.toString(2)
    }

    /**
     * Export complet Art. 15 / Art. 20 des données personnelles détenues dans l'espace utilisateur.
     */
    fun buildFullWorkspaceRgpdJson(
        userUid: String,
        userEmail: String,
        clients: List<ClientDossier>,
        sellerDossiers: List<SellerPropertyDossier>,
        crmContacts: List<CrmContact>,
        localAcquereurs: List<AcquereurEntity>
    ): String {
        val root = JSONObject().apply {
            put("exportStandard", "RGPD UE 2016/679 — Export intégral de portabilité (Art. 15 & 20)")
            put("generatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE).format(Date()))
            put("account", JSONObject().apply {
                put("uid", userUid)
                put("email", userEmail)
            })
            val clientsArr = JSONArray()
            clients.forEach { c ->
                clientsArr.put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("email", c.email)
                    put("budgetMax", c.budgetMax)
                    put("sectors", JSONArray(c.sectors))
                    put("searchMode", c.searchMode)
                    put("searchCenterAddress", c.searchCenterAddress)
                    put("searchTransportMode", c.searchTransportMode)
                    put("rgpdConsentGranted", c.rgpdConsentGranted)
                    put("rgpdConsentDate", c.rgpdConsentDate)
                })
            }
            put("buyerDossiers", clientsArr)

            val sellersArr = JSONArray()
            sellerDossiers.forEach { s ->
                sellersArr.put(JSONObject().apply {
                    put("id", s.id)
                    put("propertyTitle", s.propertyTitle)
                    put("sellerName", s.sellerName)
                    put("sellerEmail", s.sellerEmail)
                    put("sellerPhone", s.sellerPhone)
                    put("mandateRef", s.mandateRef)
                    put("mandateType", s.mandateType)
                    put("rgpdConsentGranted", s.rgpdConsentGranted)
                    put("rgpdConsentDate", s.rgpdConsentDate)
                })
            }
            put("sellerDossiers", sellersArr)

            val crmArr = JSONArray()
            crmContacts.forEach { ct ->
                crmArr.put(JSONObject().apply {
                    put("id", ct.id)
                    put("fullName", ct.fullName)
                    put("email", ct.email)
                    put("phone", ct.phone)
                    put("rgpdLegalBasis", ct.rgpdLegalBasis)
                    put("rgpdConsentGranted", ct.rgpdConsentGranted)
                    put("rgpdRetentionUntil", ct.rgpdRetentionUntil)
                })
            }
            put("crmContacts", crmArr)

            val localArr = JSONArray()
            localAcquereurs.forEach { a ->
                localArr.put(JSONObject().apply {
                    put("id", a.id)
                    put("fullName", a.fullName)
                    put("phone", a.phone)
                    put("email", a.email)
                    put("budgetMax", a.budgetMax)
                    put("sectorsSummary", a.sectorsSummary)
                })
            }
            put("localAcquereurs", localArr)
        }
        return root.toString(2)
    }
}
