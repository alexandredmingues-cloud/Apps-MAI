package com.example.data

import com.example.data.model.DvfCriteriaOptions
import com.example.data.model.DvfValuationCriteria
import com.example.data.model.DvfValuationSnapshot
import com.example.data.model.PropertyKind
import kotlin.math.roundToInt

enum class SaleTimelineStatus {
    READY,
    INSUFFICIENT_DATA
}

data class SaleTimelineFactor(
    val label: String,
    val impactDays: Int,
    val detail: String
)

data class SaleTimelineEstimate(
    val status: SaleTimelineStatus,
    val lowDays: Int = 0,
    val likelyDays: Int = 0,
    val highDays: Int = 0,
    val confidenceLabel: String = "",
    val marketLiquidityLabel: String = "",
    val factors: List<SaleTimelineFactor> = emptyList(),
    val warnings: List<String> = emptyList(),
    val basedOnDvfCount: Int = 0
) {
    val displayRange: String
        get() = if (status == SaleTimelineStatus.READY && lowDays > 0 && highDays >= lowDays) {
            "$lowDays à $highDays jours (~$likelyDays j.)"
        } else {
            "Données locales insuffisantes"
        }
}

/**
 * Moteur déterministe d'estimation du délai de vente approximatif :
 * aucune valeur inventée si surface <= 0 ou en l'absence totale de repère de prix/m².
 */
object SaleTimelineEngine {

    fun estimate(
        sectorName: String,
        propertyKind: PropertyKind,
        askingPriceEuro: Long,
        surfaceM2: Int,
        dpe: String,
        conditionLabel: String,
        criteria: DvfValuationCriteria = DvfValuationCriteria(),
        dvfSnapshot: DvfValuationSnapshot? = null,
        photoCount: Int = 0,
        providedDocsRatio: Float = 0f,
        mandateType: String = ""
    ): SaleTimelineEstimate {
        if (surfaceM2 <= 0) {
            return SaleTimelineEstimate(
                status = SaleTimelineStatus.INSUFFICIENT_DATA,
                confidenceLabel = "Données locales insuffisantes",
                marketLiquidityLabel = "Surface requise",
                warnings = listOf("Renseignez la surface habitable (> 0 m²) pour estimer le délai de vente.")
            )
        }

        val sectorRef = MontyMarketData.SECTEURS_MONTPELLIER.firstOrNull {
            sectorName.isNotBlank() && (
                it.name.contains(sectorName.substringBefore(" (").trim(), ignoreCase = true) ||
                    sectorName.contains(it.name.substringBefore(" (").trim(), ignoreCase = true)
                )
        }

        val sectorRefM2 = if (sectorRef != null) {
            if (propertyKind == PropertyKind.MAISON) {
                (sectorRef.priceM2House ?: sectorRef.priceM2Apartment).toLong()
            } else {
                sectorRef.priceM2Apartment.toLong()
            }
        } else {
            0L
        }

        val dvfCount = dvfSnapshot?.count300m ?: 0
        val dvfRefM2 = dvfSnapshot?.pricePerM2?.takeIf { it > 0L }
            ?: dvfSnapshot?.rawDvfPricePerM2?.takeIf { it > 0L }
            ?: 0L

        val referencePriceM2 = if (dvfRefM2 > 0L) dvfRefM2 else sectorRefM2
        if (referencePriceM2 <= 0L) {
            return SaleTimelineEstimate(
                status = SaleTimelineStatus.INSUFFICIENT_DATA,
                confidenceLabel = "Données locales insuffisantes",
                marketLiquidityLabel = "Référence locale absente",
                warnings = listOf(
                    "Données locales insuffisantes : lancez un repère DVF (50/100/300m) ou sélectionnez un secteur référencé."
                )
            )
        }

        val baseDays = when (sectorRef?.tension?.lowercase()) {
            "extrême" -> 38
            "très forte" -> 45
            "forte" -> 56
            else -> 68
        }
        val liquidityLabel = when (sectorRef?.tension) {
            "Extrême" -> "Marché très tendu (${sectorRef.name})"
            "Très forte" -> "Forte liquidité (${sectorRef.name})"
            "Forte" -> "Liquidité soutenue (${sectorRef.name})"
            else -> "Liquidité modérée (repère DVF local)"
        }

        val factors = mutableListOf<SaleTimelineFactor>()
        val warnings = mutableListOf<String>()
        var deltaDays = 0

        if (dvfCount == 0) {
            warnings += "Repère sectoriel utilisé en l'absence d'actes DVF récents à 300 m : fiabilité indicative."
        }

        // 1. Positionnement prix / m² vs référence locale
        if (askingPriceEuro > 0L) {
            val askingM2 = askingPriceEuro.toDouble() / surfaceM2.toDouble()
            val gapRatio = (askingM2 - referencePriceM2.toDouble()) / referencePriceM2.toDouble()
            val gapPct = (gapRatio * 100.0).roundToInt()
            val priceImpact = when {
                gapRatio <= -0.08 -> -14
                gapRatio <= -0.02 -> -7
                gapRatio <= 0.04 -> 0
                gapRatio <= 0.10 -> 14
                gapRatio <= 0.18 -> 28
                else -> 45
            }
            deltaDays += priceImpact
            factors += SaleTimelineFactor(
                label = "Positionnement prix (${askingM2.roundToInt()} €/m²)",
                impactDays = priceImpact,
                detail = if (gapPct >= 0) "+$gapPct% vs repère ($referencePriceM2 €/m²)" else "$gapPct% vs repère ($referencePriceM2 €/m²)"
            )
            if (gapPct > 10) {
                warnings += "Prix supérieur de $gapPct% aux références locales : risque d'allongement du délai."
            }
        } else {
            warnings += "Prix demandé non saisi : simulation centrée sur le prix du marché."
        }

        // 2. DPE
        val dpeClean = dpe.trim().uppercase()
        val dpeImpact = when (dpeClean) {
            "A", "B" -> -6
            "C" -> -2
            "D" -> 2
            "E" -> 8
            "F" -> 16
            "G" -> 26
            else -> 6
        }
        deltaDays += dpeImpact
        factors += SaleTimelineFactor(
            label = "Étiquette énergie (DPE ${dpeClean.ifBlank { "NC" }})",
            impactDays = dpeImpact,
            detail = when (dpeClean) {
                "A", "B", "C" -> "Demande soutenue, financement facilité"
                "F", "G" -> "Audit / travaux énergétiques à intégrer par l'acquéreur"
                else -> "Performance thermique standard"
            }
        )

        // 3. État général
        val condImpact = when {
            conditionLabel.contains("Neuf", ignoreCase = true) ||
                conditionLabel.contains("Rénové", ignoreCase = true) ||
                conditionLabel.contains("Excellent", ignoreCase = true) -> -6
            conditionLabel.contains("rafraîchir", ignoreCase = true) -> 9
            conditionLabel.contains("rénover", ignoreCase = true) -> 20
            else -> 0
        }
        if (condImpact != 0) {
            deltaDays += condImpact
            factors += SaleTimelineFactor(
                label = "État général",
                impactDays = condImpact,
                detail = conditionLabel
            )
        }

        // 4. Critères déterminants (ascenseur, vue, extérieur, parking, environnement)
        var criteriaBonus = 0
        if (criteria.floorAndElevator.contains("Dernier étage", ignoreCase = true) ||
            criteria.floorAndElevator.contains("Plain-pied", ignoreCase = true)
        ) {
            criteriaBonus -= 4
        } else if (criteria.floorAndElevator.contains("sans asc", ignoreCase = true) &&
            criteria.floorAndElevator.contains("3e", ignoreCase = true)
        ) {
            criteriaBonus += 9
        }
        if (criteria.viewAndExposure.contains("Vue dégagée", ignoreCase = true)) {
            criteriaBonus -= 4
        } else if (criteria.viewAndExposure.contains("Vis-à-vis", ignoreCase = true)) {
            criteriaBonus += 7
        }
        if (criteria.exteriorType.contains("Terrasse", ignoreCase = true) ||
            criteria.exteriorType.contains("Jardin", ignoreCase = true)
        ) {
            criteriaBonus -= 4
        } else if (criteria.exteriorType == DvfCriteriaOptions.EXTERIOR_TYPES.first().first) {
            criteriaBonus += 5
        }
        if (criteria.parkingType.contains("Aucun", ignoreCase = true)) {
            criteriaBonus += 5
        }
        if (criteria.environmentCalm.contains("Bruit", ignoreCase = true)) {
            criteriaBonus += 9
        }
        if (criteria.hasMajorWorksPending) {
            criteriaBonus += 8
        }
        if (criteriaBonus != 0) {
            deltaDays += criteriaBonus
            factors += SaleTimelineFactor(
                label = "Critères physiques & environnement",
                impactDays = criteriaBonus,
                detail = if (criteriaBonus < 0) "Atouts recherchés (extérieur, vue, étage/calme)" else "Points de vigilance (sans ascenseur/parking ou travaux)"
            )
        }

        // 5. Qualité de présentation : photos & dossier technique / ALUR
        val photoImpact = when {
            photoCount >= 5 -> -5
            photoCount >= 3 -> -2
            photoCount in 1..2 -> 5
            else -> 12
        }
        deltaDays += photoImpact
        factors += SaleTimelineFactor(
            label = "Dossier photo ($photoCount photo${if (photoCount > 1) "s" else ""})",
            impactDays = photoImpact,
            detail = if (photoCount >= 3) "Annonce illustrée attractive" else "Ajoutez au moins 3 photos pour accélérer les prises de visite"
        )

        val docImpact = when {
            providedDocsRatio >= 0.75f -> -6
            providedDocsRatio >= 0.40f -> -2
            else -> 5
        }
        deltaDays += docImpact
        factors += SaleTimelineFactor(
            label = "Préparation dossier vente / ALUR (${(providedDocsRatio * 100).roundToInt()}%)",
            impactDays = docImpact,
            detail = if (providedDocsRatio >= 0.75f) "Compromis rapide possible chez le notaire" else "Pièces à compléter pour éviter les délais post-offre"
        )

        // 6. Stratégie de mandat / diffusion
        val mandateImpact = when {
            mandateType.equals("Exclusif", ignoreCase = true) -> -8
            mandateType.equals("Semi-exclusif", ignoreCase = true) -> -4
            else -> 0
        }
        if (mandateImpact != 0) {
            deltaDays += mandateImpact
            factors += SaleTimelineFactor(
                label = "Mandat $mandateType",
                impactDays = mandateImpact,
                detail = "Pilotage cohérent du prix et des acquéreurs qualifiés"
            )
        }

        val likely = (baseDays + deltaDays).coerceIn(18, 240)
        val spreadRatio = when {
            dvfCount >= 5 && askingPriceEuro > 0L -> 0.18
            dvfCount >= 1 || sectorRef != null -> 0.25
            else -> 0.32
        }
        val low = (likely * (1.0 - spreadRatio)).roundToInt().coerceAtLeast(14)
        val high = (likely * (1.0 + spreadRatio)).roundToInt().coerceAtLeast(low + 7)

        val confidence = when {
            dvfCount >= 5 && askingPriceEuro > 0L && dpeClean in listOf("A", "B", "C", "D", "E", "F", "G") -> "Élevée (DVF + secteur)"
            dvfCount >= 1 || (sectorRef != null && askingPriceEuro > 0L) -> "Moyenne (indicative)"
            else -> "Indicative (données partielles)"
        }

        return SaleTimelineEstimate(
            status = SaleTimelineStatus.READY,
            lowDays = low,
            likelyDays = likely,
            highDays = high,
            confidenceLabel = confidence,
            marketLiquidityLabel = liquidityLabel,
            factors = factors,
            warnings = warnings,
            basedOnDvfCount = dvfCount
        )
    }
}
