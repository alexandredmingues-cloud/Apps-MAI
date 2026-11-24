package com.example.data.model

import androidx.annotation.Keep
import com.example.data.MontyMarketData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PropertyKind(val label: String) {
    MAISON("Maison"),
    APPARTEMENT("Appartement");

    companion object {
        fun fromLabel(raw: String): PropertyKind {
            return if (raw.contains("Maison", ignoreCase = true) ||
                raw.contains("Villa", ignoreCase = true)
            ) {
                MAISON
            } else {
                APPARTEMENT
            }
        }
    }
}

enum class ResidenceType(val label: String, val alurRequired: Boolean, val badge: String) {
    MAISON_INDIVIDUELLE("Maison individuelle", false, "Hors copropriété"),
    COPROPRIETE("Résidence / Copropriété", true, "Loi ALUR requise"),
    LOTISSEMENT_ASL("Lotissement ASL", true, "Statuts & ALUR");

    companion object {
        fun fromLabel(raw: String, propertyKind: PropertyKind): ResidenceType {
            return entries.firstOrNull { it.label.equals(raw, ignoreCase = true) }
                ?: if (propertyKind == PropertyKind.APPARTEMENT) COPROPRIETE else MAISON_INDIVIDUELLE
        }
    }
}

enum class DocumentContributorRole(val code: String, val label: String) {
    AGENT("AGENT", "Agent immobilier"),
    VENDEUR("VENDEUR", "Propriétaire vendeur")
}

object SellerDocCatalogConstants {
    val STATUSES = listOf("Manquant", "Fourni", "Vérifié", "Non applicable")
    val CALLBACK_SLOTS = listOf("Matin (9h-12h)", "Après-midi (14h-18h)", "Soir (18h-20h)", "Samedi")
    val PROJECT_HORIZONS = listOf(
        "Vendre sous 3 mois",
        "Vendre sous 6 mois",
        "Simple avis de valeur / Réflexion",
        "Mise en vente immédiate"
    )
    val CALLBACK_STATUSES = listOf("Demandée", "Planifiée", "Traitée")
    val MANDATE_TYPES = listOf("Exclusif", "Simple", "Semi-exclusif", "Délégation / Confrère", "Off-Market")
}

@Keep
@IgnoreExtraProperties
data class SellerSaleDocument(
    val id: String = "",
    val code: String = "",
    val title: String = "",
    val category: String = "Essentiel",
    val required: Boolean = true,
    val status: String = "Manquant",
    val uploadedByRole: String = "AGENT",
    val uploadedByName: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val localVaultPath: String = "",
    val summaryText: String = "",
    val updatedAtLabel: String = ""
) {
    val isProvided: Boolean
        get() = status == "Fourni" || status == "Vérifié" || fileName.isNotBlank()

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id.take(80),
        "code" to code.take(64),
        "title" to title.take(140),
        "category" to category.take(40),
        "required" to required,
        "status" to status.take(30),
        "uploadedByRole" to uploadedByRole.take(20),
        "uploadedByName" to uploadedByName.take(80),
        "fileName" to fileName.take(160),
        "mimeType" to mimeType.take(100),
        "sizeBytes" to sizeBytes.coerceAtLeast(0L),
        "localVaultPath" to localVaultPath.take(260),
        "summaryText" to summaryText.take(600),
        "updatedAtLabel" to updatedAtLabel.take(40)
    )
}

@Keep
@IgnoreExtraProperties
data class BuyerDocumentOrder(
    val orderId: String = "",
    val buyerClientId: String = "",
    val buyerName: String = "",
    val buyerEmail: String = "",
    val amountEuro: Long = 0L,
    val status: String = "PENDING_PAYMENT",
    val paymentMethodLabel: String = "",
    val createdAtLabel: String = "",
    val deliveredAtLabel: String = ""
) {
    val isUnlocked: Boolean
        get() = status == "PAID" || status == "DELIVERED"

    fun toMap(): Map<String, Any> = mapOf(
        "orderId" to orderId.take(64),
        "buyerClientId" to buyerClientId.take(128),
        "buyerName" to buyerName.take(120),
        "buyerEmail" to buyerEmail.take(160),
        "amountEuro" to amountEuro.coerceIn(0L, 5000L),
        "status" to status.take(30),
        "paymentMethodLabel" to paymentMethodLabel.take(80),
        "createdAtLabel" to createdAtLabel.take(40),
        "deliveredAtLabel" to deliveredAtLabel.take(40)
    )
}

object DvfCriteriaOptions {
    val ELEVATOR_FLOOR_APPT = listOf(
        "RDC (-7%)" to -0.07,
        "1er-2e sans asc. (-3%)" to -0.03,
        "3e+ sans asc. (-10%)" to -0.10,
        "Étage avec ascenseur (+3%)" to 0.03,
        "Dernier étage + asc. (+8%)" to 0.08
    )
    val ELEVATOR_FLOOR_HOUSE = listOf(
        "Plain-pied (+4%)" to 0.04,
        "Avec étage(s) (0%)" to 0.0
    )
    val VIEW_EXPOSURE = listOf(
        "Vue dégagée / parc / mer (+7%)" to 0.07,
        "Lumineux / Traversant SO (+4%)" to 0.04,
        "Exposition standard (0%)" to 0.0,
        "Vis-à-vis fort / Sombre (-7%)" to -0.07
    )
    val EXTERIOR_TYPES = listOf(
        "Aucun extérieur (-4%)" to -0.04,
        "Balcon / Loggia (+2%)" to 0.02,
        "Terrasse ≥ 15 m² (+6%)" to 0.06,
        "Jardin privatif / Piscine (+8%)" to 0.08
    )
    val PARKING_TYPES = listOf(
        "Aucun stationnement (-3%)" to -0.03,
        "Place parking privée (+3%)" to 0.03,
        "Box fermé / Garage (+5%)" to 0.05,
        "Double garage / Borne VE (+7%)" to 0.07
    )
    val NOISE_ENVIRONMENT = listOf(
        "Très calme / Impasse (+3%)" to 0.03,
        "Urbain standard (0%)" to 0.0,
        "Axe passant / Bruit (-6%)" to -0.06
    )
}

@Keep
@IgnoreExtraProperties
data class DvfValuationCriteria(
    val floorAndElevator: String = "Étage avec ascenseur (+3%)",
    val viewAndExposure: String = "Lumineux / Traversant SO (+4%)",
    val exteriorType: String = "Balcon / Loggia (+2%)",
    val parkingType: String = "Place parking privée (+3%)",
    val environmentCalm: String = "Urbain standard (0%)",
    val hasCellarOrStorage: Boolean = false,
    val hasAcOrHeatPump: Boolean = false,
    val hasRecentGlazingOrFiber: Boolean = true,
    val hasNoVisAVis: Boolean = false,
    val hasMajorWorksPending: Boolean = false
) {
    fun qualifiedFieldCount(): Int {
        var count = 0
        if (floorAndElevator.isNotBlank()) count++
        if (viewAndExposure.isNotBlank()) count++
        if (exteriorType.isNotBlank()) count++
        if (parkingType.isNotBlank()) count++
        if (environmentCalm.isNotBlank()) count++
        return count
    }
}

@Keep
@IgnoreExtraProperties
data class DvfValuationSnapshot(
    val estimatedMidEuro: Long = 0L,
    val estimatedLowEuro: Long = 0L,
    val estimatedHighEuro: Long = 0L,
    val baseMarketEuro: Long = 0L,
    val pricePerM2: Long = 0L,
    val rawDvfPricePerM2: Long = 0L,
    val totalAdjustmentPercent: Int = 0,
    val criteriaSummary: String = "",
    val justificationLines: List<String> = emptyList(),
    val valuationScore: Int = 0,
    val scoreLabel: String = "",
    val resolvedAddress: String = "",
    val count50m: Int = 0,
    val count100m: Int = 0,
    val count300m: Int = 0,
    val avgM2_50m: Long = 0L,
    val avgM2_100m: Long = 0L,
    val avgM2_300m: Long = 0L,
    val dataSource: String = "DVF Open Data (Etalab / DGFiP)",
    val computedAtLabel: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "estimatedMidEuro" to estimatedMidEuro.coerceAtLeast(0L),
        "estimatedLowEuro" to estimatedLowEuro.coerceAtLeast(0L),
        "estimatedHighEuro" to estimatedHighEuro.coerceAtLeast(0L),
        "baseMarketEuro" to baseMarketEuro.coerceAtLeast(0L),
        "pricePerM2" to pricePerM2.coerceAtLeast(0L),
        "rawDvfPricePerM2" to rawDvfPricePerM2.coerceAtLeast(0L),
        "totalAdjustmentPercent" to totalAdjustmentPercent.coerceIn(-60, 80),
        "criteriaSummary" to criteriaSummary.take(400),
        "justificationLines" to justificationLines.take(16).map { it.take(220) },
        "valuationScore" to valuationScore.coerceIn(0, 100),
        "scoreLabel" to scoreLabel.take(60),
        "resolvedAddress" to resolvedAddress.take(220),
        "count50m" to count50m.coerceAtLeast(0),
        "count100m" to count100m.coerceAtLeast(0),
        "count300m" to count300m.coerceAtLeast(0),
        "avgM2_50m" to avgM2_50m.coerceAtLeast(0L),
        "avgM2_100m" to avgM2_100m.coerceAtLeast(0L),
        "avgM2_300m" to avgM2_300m.coerceAtLeast(0L),
        "dataSource" to dataSource.take(80),
        "computedAtLabel" to computedAtLabel.take(40)
    )
}

@Keep
@IgnoreExtraProperties
data class SellerCallbackRequest(
    val requestId: String = "",
    val sellerName: String = "",
    val sellerPhone: String = "",
    val sellerEmail: String = "",
    val preferredSlot: String = "",
    val projectHorizon: String = "",
    val notes: String = "",
    val status: String = "Demandée",
    val createdAtLabel: String = "",
    val rgpdConsentGranted: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "requestId" to requestId.take(64),
        "sellerName" to sellerName.take(120),
        "sellerPhone" to sellerPhone.take(40),
        "sellerEmail" to sellerEmail.take(160),
        "preferredSlot" to preferredSlot.take(60),
        "projectHorizon" to projectHorizon.take(80),
        "notes" to notes.take(600),
        "status" to status.take(30),
        "createdAtLabel" to createdAtLabel.take(40),
        "rgpdConsentGranted" to rgpdConsentGranted
    )
}

@Keep
@IgnoreExtraProperties
data class SellerPropertyDossier(
    @DocumentId val id: String = "",
    val userId: String = "",
    val propertyId: String = "",
    val propertyTitle: String = "",
    val propertyKind: String = PropertyKind.APPARTEMENT.label,
    val residenceType: String = ResidenceType.COPROPRIETE.label,
    val sellerName: String = "",
    val sellerEmail: String = "",
    val sellerPhone: String = "",
    val mandateRef: String = "",
    val mandateType: String = "",
    val mandateDate: String = "",
    val agencyFeePercent: Double = 0.0,
    val listingUrl: String = "",
    val listingPortal: String = "",
    val advisorEstimatedEuro: Long = 0L,
    val advisorValuationNotes: String = "",
    val syndicName: String = "",
    val annualCoproCharges: Long = 0L,
    val alurWorkFundEuro: Long = 0L,
    val packPriceEuro: Long = 29L,
    val paymentLinkUrl: String = "",
    val executiveSummary: String = "",
    val documents: List<SellerSaleDocument> = emptyList(),
    val buyerOrders: List<BuyerDocumentOrder> = emptyList(),
    val dvfValuation: DvfValuationSnapshot? = null,
    val callbackRequests: List<SellerCallbackRequest> = emptyList(),
    val rgpdConsentGranted: Boolean = false,
    val rgpdConsentDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedKind: PropertyKind
        get() = PropertyKind.fromLabel(propertyKind)

    val resolvedResidence: ResidenceType
        get() = ResidenceType.fromLabel(residenceType, resolvedKind)

    val isAlurApplicable: Boolean
        get() = resolvedResidence.alurRequired

    fun isUnlockedForBuyer(buyerClientId: String): Boolean {
        return buyerOrders.any { it.buyerClientId == buyerClientId && it.isUnlocked }
    }

    fun hasPendingOrderForBuyer(buyerClientId: String): Boolean {
        return buyerOrders.any { it.buyerClientId == buyerClientId && it.status == "PENDING_PAYMENT" }
    }

    fun toCreateMap(ownerUid: String): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "userId" to ownerUid,
            "propertyId" to propertyId.take(128),
            "propertyTitle" to propertyTitle.take(160),
            "propertyKind" to PropertyKind.fromLabel(propertyKind).label,
            "residenceType" to resolvedResidence.label,
            "sellerName" to sellerName.take(120),
            "sellerEmail" to sellerEmail.take(160),
            "sellerPhone" to sellerPhone.take(40),
            "mandateRef" to mandateRef.take(64),
            "mandateType" to mandateType.take(40),
            "mandateDate" to mandateDate.take(30),
            "agencyFeePercent" to agencyFeePercent.coerceIn(0.0, 25.0),
            "listingUrl" to listingUrl.take(500),
            "listingPortal" to listingPortal.take(60),
            "advisorEstimatedEuro" to advisorEstimatedEuro.coerceIn(0L, 50000000L),
            "advisorValuationNotes" to advisorValuationNotes.take(1500),
            "syndicName" to syndicName.take(120),
            "annualCoproCharges" to annualCoproCharges.coerceIn(0L, 500000L),
            "alurWorkFundEuro" to alurWorkFundEuro.coerceIn(0L, 5000000L),
            "packPriceEuro" to packPriceEuro.coerceIn(0L, 5000L),
            "paymentLinkUrl" to paymentLinkUrl.take(500),
            "executiveSummary" to executiveSummary.take(4000),
            "documents" to documents.take(40).map { it.toMap() },
            "buyerOrders" to buyerOrders.take(50).map { it.toMap() },
            "dvfValuation" to dvfValuation?.toMap(),
            "callbackRequests" to callbackRequests.take(30).map { it.toMap() },
            "rgpdConsentGranted" to rgpdConsentGranted,
            "rgpdConsentDate" to rgpdConsentDate.take(30),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "propertyTitle" to propertyTitle.take(160),
            "propertyKind" to PropertyKind.fromLabel(propertyKind).label,
            "residenceType" to resolvedResidence.label,
            "sellerName" to sellerName.take(120),
            "sellerEmail" to sellerEmail.take(160),
            "sellerPhone" to sellerPhone.take(40),
            "mandateRef" to mandateRef.take(64),
            "mandateType" to mandateType.take(40),
            "mandateDate" to mandateDate.take(30),
            "agencyFeePercent" to agencyFeePercent.coerceIn(0.0, 25.0),
            "listingUrl" to listingUrl.take(500),
            "listingPortal" to listingPortal.take(60),
            "advisorEstimatedEuro" to advisorEstimatedEuro.coerceIn(0L, 50000000L),
            "advisorValuationNotes" to advisorValuationNotes.take(1500),
            "syndicName" to syndicName.take(120),
            "annualCoproCharges" to annualCoproCharges.coerceIn(0L, 500000L),
            "alurWorkFundEuro" to alurWorkFundEuro.coerceIn(0L, 5000000L),
            "packPriceEuro" to packPriceEuro.coerceIn(0L, 5000L),
            "paymentLinkUrl" to paymentLinkUrl.take(500),
            "executiveSummary" to executiveSummary.take(4000),
            "documents" to documents.take(40).map { it.toMap() },
            "buyerOrders" to buyerOrders.take(50).map { it.toMap() },
            "dvfValuation" to dvfValuation?.toMap(),
            "callbackRequests" to callbackRequests.take(30).map { it.toMap() },
            "rgpdConsentGranted" to rgpdConsentGranted,
            "rgpdConsentDate" to rgpdConsentDate.take(30),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }
}

object SellerDocumentCatalog {

    private data class DocTemplate(
        val code: String,
        val title: String,
        val category: String,
        val required: Boolean,
        val defaultSummaryHint: String
    )

    fun buildChecklist(
        propertyKind: PropertyKind,
        residenceType: ResidenceType,
        property: PropertyItem?,
        existingDocs: List<SellerSaleDocument> = emptyList()
    ): List<SellerSaleDocument> {
        val templates = mutableListOf(
            DocTemplate("TITRE_PROPRIETE", "Titre de propriété (Acte notarié)", "Essentiel", true, "Origine de propriété vérifiée."),
            DocTemplate("TAXE_FONCIERE", "Dernier avis de taxe foncière", "Essentiel", true, property?.let { "Montant annuel : ${MontyMarketData.formatEuro(it.propertyTax)}/an" } ?: ""),
            DocTemplate("MANDAT_IDENTITE", "Mandat de vente & Pièce d'identité", "Essentiel", true, "Identité des vendeurs et pouvoir de signer."),
            DocTemplate("DDT_DPE", "DPE & Audit énergétique (si F/G)", "Diagnostic DDT", true, property?.let { "Classe DPE ${it.dpe} • GES ${it.ges}" } ?: ""),
            DocTemplate("DDT_AMIANTE", "Diagnostic Amiante (avant 07/1997)", "Diagnostic DDT", true, "Repérage matériaux amiantés."),
            DocTemplate("DDT_PLOMB", "CREP — Plomb (avant 1949)", "Diagnostic DDT", false, "Constat de risque d'exposition au plomb."),
            DocTemplate("DDT_ELECTRICITE", "État de l'installation électrique (> 15 ans)", "Diagnostic DDT", true, "Sécurité du tableau et des circuits."),
            DocTemplate("DDT_GAZ", "État de l'installation gaz (> 15 ans)", "Diagnostic DDT", false, "Conformité de l'installation gaz ou sans gaz."),
            DocTemplate("DDT_ERP", "État des Risques & Pollutions (ERP)", "Diagnostic DDT", true, "Risques naturels/PPRI (Montpellier & Métropole)."),
            DocTemplate("DDT_TERMITES", "État parasitaire / Termites (zone Hérault)", "Diagnostic DDT", true, "Contrôle termites valable 6 mois.")
        )

        if (propertyKind == PropertyKind.MAISON) {
            templates.addAll(
                listOf(
                    DocTemplate("MAISON_ASSAINISSEMENT", "Contrôle assainissement (collectif ou SPANC)", "Maison", true, "Conformité du raccordement eaux usées/pluviales."),
                    DocTemplate("MAISON_URBANISME", "Permis de construire / Déclarations & DAACT", "Maison", true, "Conformité extensions, piscine ou véranda."),
                    DocTemplate("MAISON_CADASTRE", "Plan cadastral & Procès-verbal de bornage", "Maison", true, "Limites séparatives et servitudes éventuelles."),
                    DocTemplate("MAISON_DO", "Assurance Dommages-Ouvrage (< 10 ans)", "Maison", false, "Garanties décennales des travaux récents.")
                )
            )
        } else {
            templates.add(
                DocTemplate(
                    "APPT_CARREZ",
                    "Attestation de superficie Loi Carrez",
                    "Appartement",
                    true,
                    property?.let { "Surface privative déclarée : ${it.surface} m²" } ?: ""
                )
            )
        }

        if (residenceType.alurRequired) {
            val isAsl = residenceType == ResidenceType.LOTISSEMENT_ASL
            templates.addAll(
                listOf(
                    DocTemplate(
                        "ALUR_REGLEMENT",
                        if (isAsl) "Statuts ASL & Cahier des charges du lotissement" else "Règlement de copropriété & EDD",
                        "Loi ALUR",
                        true,
                        "Lots privatifs, tantièmes et règlement de résidence."
                    ),
                    DocTemplate(
                        "ALUR_PV_AG",
                        "3 derniers Procès-Verbaux d'Assemblée Générale",
                        "Loi ALUR",
                        true,
                        "Travaux votés, procédures et vie de la résidence."
                    ),
                    DocTemplate(
                        "ALUR_CARNET",
                        "Carnet d'entretien de l'immeuble / résidence",
                        "Loi ALUR",
                        true,
                        "Historique ravalement, toiture, ascenseurs."
                    ),
                    DocTemplate(
                        "ALUR_PRE_ETAT_DATE",
                        "Pré-état daté & Fiche synthétique copropriété",
                        "Loi ALUR",
                        true,
                        "Charges courantes, appels travaux sur 2 ans et impayés."
                    ),
                    DocTemplate(
                        "ALUR_FONDS_TRAVAUX",
                        "Fonds de travaux Loi ALUR (cotisation & solde)",
                        "Loi ALUR",
                        true,
                        "Montant acquis du fonds de travaux rattaché au lot."
                    ),
                    DocTemplate(
                        "ALUR_DTG_PPT",
                        "Diagnostic Technique Global (DTG) & PPT",
                        "Loi ALUR",
                        false,
                        "Plan pluriannuel de travaux voté ou à l'étude."
                    ),
                    DocTemplate(
                        "ALUR_NOTICE",
                        "Notice d'information droits copropriétaires",
                        "Loi ALUR",
                        true,
                        "Annexe obligatoire Loi ALUR dès le compromis."
                    )
                )
            )
        }

        val existingByCode = existingDocs.associateBy { it.code }
        val result = templates.map { tpl ->
            val prev = existingByCode[tpl.code]
            if (prev != null) {
                prev.copy(
                    title = tpl.title,
                    category = tpl.category,
                    required = tpl.required
                )
            } else {
                SellerSaleDocument(
                    id = "doc_${tpl.code.lowercase()}",
                    code = tpl.code,
                    title = tpl.title,
                    category = tpl.category,
                    required = tpl.required,
                    status = "Manquant",
                    summaryText = tpl.defaultSummaryHint
                )
            }
        }.toMutableList()

        // Preserve any ALUR/Maison/Custom documents that already contain deposited files or notes when switching residence type ("conserver les documents ALUR")
        val templateCodes = templates.map { it.code }.toSet()
        existingDocs
            .filter { doc ->
                doc.code !in templateCodes &&
                    (doc.code.startsWith("CUSTOM_") || doc.isProvided || doc.summaryText.isNotBlank())
            }
            .forEach { conserved ->
                result.add(conserved)
            }

        return result.take(40)
    }

    fun buildFreeBuyerSummary(dossier: SellerPropertyDossier, property: PropertyItem?): String {
        val applicableDocs = dossier.documents.filter { it.status != "Non applicable" }
        val providedDocs = applicableDocs.filter { it.isProvided }
        val essentialCount = applicableDocs.count { it.category != "Loi ALUR" }
        val essentialReady = applicableDocs.count { it.category != "Loi ALUR" && it.isProvided }
        val alurDocs = applicableDocs.filter { it.category == "Loi ALUR" }
        val alurReady = alurDocs.count { it.isProvided }

        return buildString {
            appendLine("SYNTHÈSE DU DOSSIER VENDEUR — ${dossier.propertyTitle}")
            appendLine("Typologie : ${dossier.propertyKind} • Régime : ${dossier.residenceType}")
            if (dossier.mandateRef.isNotBlank() || dossier.mandateType.isNotBlank()) {
                val feeStr = if (dossier.agencyFeePercent > 0.0) " • Honoraires ${dossier.agencyFeePercent}%" else ""
                appendLine("Mandat : ${dossier.mandateType.ifBlank { "Agence" }} ${dossier.mandateRef}$feeStr".trim())
            }
            if (dossier.listingUrl.isNotBlank()) {
                val portalLabel = dossier.listingPortal.ifBlank { "Annonce en ligne" }
                appendLine("$portalLabel : ${dossier.listingUrl}")
            }
            if (property != null) {
                appendLine("DPE : ${property.dpe} (GES ${property.ges}) • Surface : ${property.surface} m² • TF : ${MontyMarketData.formatEuro(property.propertyTax)}/an")
            }
            appendLine("Complétude globale : ${providedDocs.size} / ${applicableDocs.size} pièces disponibles (Essentiels $essentialReady/$essentialCount).")
            if (dossier.isAlurApplicable) {
                appendLine(
                    "Loi ALUR (Résidence) : $alurReady / ${alurDocs.size} pièces réunies" +
                        (if (dossier.syndicName.isNotBlank()) " • Syndic : ${dossier.syndicName}" else "") +
                        (if (dossier.annualCoproCharges > 0) " • Charges : ${MontyMarketData.formatEuro(dossier.annualCoproCharges)}/an" else "") +
                        (if (dossier.alurWorkFundEuro > 0) " • Fonds travaux ALUR : ${MontyMarketData.formatEuro(dossier.alurWorkFundEuro)}" else "")
                )
            } else {
                appendLine("Régime hors copropriété : focus assainissement, urbanisme et diagnostics DDT.")
            }
            if (dossier.advisorEstimatedEuro > 0L) {
                appendLine("Avis de valeur Conseiller / Agence : ${MontyMarketData.formatEuro(dossier.advisorEstimatedEuro)}")
            }
            dossier.dvfValuation?.let { dvf ->
                if (dvf.estimatedMidEuro > 0L) {
                    val adjSign = if (dvf.totalAdjustmentPercent >= 0) "+" else ""
                    appendLine(
                        "Repère DVF : ${MontyMarketData.formatEuro(dvf.estimatedLowEuro)} – ${MontyMarketData.formatEuro(dvf.estimatedHighEuro)} " +
                            "(Indice confiance DVF ${dvf.valuationScore}/100 • Ajustement critères $adjSign${dvf.totalAdjustmentPercent}%)."
                    )
                    if (dvf.criteriaSummary.isNotBlank()) {
                        appendLine("Critères retenus : ${dvf.criteriaSummary}")
                    }
                }
            }
            if (providedDocs.isNotEmpty()) {
                appendLine("Points clés des pièces fournies :")
                providedDocs.take(6).forEach { doc ->
                    val detail = doc.summaryText.ifBlank { "Pièce vérifiable dans le pack complet." }
                    appendLine("• ${doc.title} [${doc.status}] : $detail")
                }
            }
        }.trim()
    }
}
