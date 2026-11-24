package com.example.data

import com.example.data.model.DvfCriteriaOptions
import com.example.data.model.DvfValuationCriteria
import com.example.data.model.DvfValuationSnapshot
import com.example.data.model.PropertyKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class GeocodedAddressResult(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val cityCode: String
)

data class DvfMutationComparable(
    val mutationId: String,
    val dateMutation: String,
    val year: Int,
    val priceEuro: Long,
    val surfaceM2: Int,
    val rooms: Int,
    val propertyType: String,
    val addressLabel: String,
    val distanceMeters: Int
) {
    val pricePerM2: Long
        get() = if (surfaceM2 > 0) priceEuro / surfaceM2 else 0L
}

data class DvfRingStats(
    val radiusMeters: Int,
    val count: Int,
    val medianPricePerM2: Long,
    val avgPricePerM2: Long,
    val minPricePerM2: Long,
    val maxPricePerM2: Long,
    val comparables: List<DvfMutationComparable>
)

data class DvfPriceAdjustmentDriver(
    val label: String,
    val detail: String,
    val impactPercent: Double,
    val euroImpact: Long
)

data class DvfValuationReport(
    val snapshot: DvfValuationSnapshot,
    val ring50m: DvfRingStats,
    val ring100m: DvfRingStats,
    val ring300m: DvfRingStats,
    val adjustmentDrivers: List<DvfPriceAdjustmentDriver>,
    val scoreBreakdown: List<Pair<String, Int>>,
    val methodologyNote: String,
    val matchingTypeOnlyCount: Int
)

class DvfValuationService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun evaluatePropertyWithDvf(
        addressQuery: String,
        propertyKind: PropertyKind,
        surfaceM2: Int,
        dpe: String,
        conditionLabel: String,
        hasExteriorOrParking: Boolean = true,
        providedDocsRatio: Float,
        criteria: DvfValuationCriteria = DvfValuationCriteria()
    ): Result<DvfValuationReport> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanQuery = addressQuery.trim()
            require(cleanQuery.length >= 4) {
                "Renseignez une adresse ou un secteur précis (ex. 12 Avenue Raymond Dugrand, Montpellier)."
            }

            val geo = geocodeFrenchAddress(cleanQuery)
            val (rawComparables, sourceLabel) = fetchDvfComparablesWithin300m(
                lat = geo.latitude,
                lon = geo.longitude
            )

            computeValuationReport(
                resolvedAddress = geo.label,
                dataSource = sourceLabel,
                comparables = rawComparables,
                propertyKind = propertyKind,
                surfaceM2 = surfaceM2,
                dpe = dpe,
                conditionLabel = conditionLabel,
                hasExteriorOrParking = hasExteriorOrParking,
                providedDocsRatio = providedDocsRatio,
                criteria = criteria
            )
        }
    }

    suspend fun geocodeAddressForSearch(query: String): Result<GeocodedAddressResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Saisissez une adresse ou un lieu à Montpellier."))
        }
        val onlineAttempt = runCatching { geocodeFrenchAddress(cleanQuery) }
        if (onlineAttempt.isSuccess) {
            return@withContext onlineAttempt
        }
        val fallback = GeoCommuteService.resolveOfflineAddressFallback(cleanQuery)
        if (fallback != null) {
            return@withContext Result.success(fallback)
        }
        Result.failure(
            onlineAttempt.exceptionOrNull()
                ?: IllegalArgumentException("Adresse introuvable : « $cleanQuery ».")
        )
    }

    private fun geocodeFrenchAddress(query: String): GeocodedAddressResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api-adresse.data.gouv.fr/search/?q=$encoded&limit=1"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "MonAgentImmo-Android/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("Service d'adressage BAN indisponible (HTTP ${resp.code}).")
            }
            val body = resp.body?.string().orEmpty()
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: JSONArray()
            if (features.length() == 0) {
                error("Adresse introuvable dans la Base Adresse Nationale : « $query ».")
            }
            val first = features.getJSONObject(0)
            val coords = first.getJSONObject("geometry").getJSONArray("coordinates")
            val lon = coords.getDouble(0)
            val lat = coords.getDouble(1)
            val props = first.getJSONObject("properties")
            return GeocodedAddressResult(
                label = props.optString("label", query),
                latitude = lat,
                longitude = lon,
                cityCode = props.optString("citycode", "")
            )
        }
    }

    private fun fetchDvfComparablesWithin300m(
        lat: Double,
        lon: Double
    ): Pair<List<DvfMutationComparable>, String> {
        val cquestAttempt = runCatching { fetchFromCquestDvf(lat, lon) }
        if (cquestAttempt.isSuccess) {
            return cquestAttempt.getOrThrow() to "DVF Open Data (Etalab / DGFiP • api.cquest.org)"
        }
        val ceremaAttempt = runCatching { fetchFromCeremaDvf(lat, lon) }
        if (ceremaAttempt.isSuccess) {
            return ceremaAttempt.getOrThrow() to "DVF+ Open Data (Cerema / DGFiP)"
        }
        val err = cquestAttempt.exceptionOrNull()?.localizedMessage
            ?: ceremaAttempt.exceptionOrNull()?.localizedMessage
            ?: "Réseau indisponible"
        error("Impossible d'interroger les serveurs DVF officiels : $err")
    }

    private fun fetchFromCquestDvf(lat: Double, lon: Double): List<DvfMutationComparable> {
        val url = String.format(
            Locale.US,
            "https://api.cquest.org/dvf?lat=%.6f&lon=%.6f&dist=320",
            lat,
            lon
        )
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "MonAgentImmo-Android/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code} sur api.cquest.org/dvf")
            }
            val body = resp.body?.string().orEmpty()
            val root = JSONObject(body)
            val arr = root.optJSONArray("resultats") ?: JSONArray()
            return parseCquestArray(arr, lat, lon)
        }
    }

    private fun fetchFromCeremaDvf(lat: Double, lon: Double): List<DvfMutationComparable> {
        val dLat = 0.0032
        val dLon = 0.0042
        val bbox = String.format(
            Locale.US,
            "%.6f,%.6f,%.6f,%.6f",
            lon - dLon,
            lat - dLat,
            lon + dLon,
            lat + dLat
        )
        val url = "https://apidf-preprod.cerema.fr/dvf_opendata/mutations/?in_bbox=$bbox&page_size=200"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "MonAgentImmo-Android/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code} sur apidf-preprod.cerema.fr")
            }
            val body = resp.body?.string().orEmpty()
            val root = JSONObject(body)
            val arr = root.optJSONArray("results") ?: JSONArray()
            return parseCeremaArray(arr, lat, lon)
        }
    }

    companion object {
        fun parseCquestArray(
            arr: JSONArray,
            refLat: Double,
            refLon: Double
        ): List<DvfMutationComparable> {
            val byMutation = linkedMapOf<String, DvfMutationComparable>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val nature = obj.optString("nature_mutation", "")
                if (nature.isNotBlank() && !nature.contains("Vente", ignoreCase = true)) continue

                val typeLocal = obj.optString("type_local", "")
                if (!typeLocal.equals("Appartement", ignoreCase = true) &&
                    !typeLocal.equals("Maison", ignoreCase = true)
                ) {
                    continue
                }

                val price = obj.optDouble("valeur_fonciere", 0.0).roundToLong()
                val surfaceRaw = obj.optDouble(
                    "surface_relle_bati",
                    obj.optDouble("surface_reelle_bati", 0.0)
                ).roundToInt()
                if (price < 15000L || surfaceRaw < 10) continue

                val m2 = price / surfaceRaw
                if (m2 < 600L || m2 > 22000L) continue

                val tLat = obj.optDouble("latitude", Double.NaN)
                val tLon = obj.optDouble("longitude", Double.NaN)
                if (tLat.isNaN() || tLon.isNaN()) continue

                val distMeters = haversineMeters(refLat, refLon, tLat, tLon).roundToInt()
                if (distMeters > 300) continue

                val dateMut = obj.optString("date_mutation", "")
                val year = dateMut.take(4).toIntOrNull() ?: 0
                val mutationId = obj.optString("id_mutation", "cquest_${dateMut}_$i")
                val street = listOf(
                    obj.optString("numero_voie", ""),
                    obj.optString("voie", ""),
                    obj.optString("nom_commune", "")
                ).filter { it.isNotBlank() }.joinToString(" ")

                val candidate = DvfMutationComparable(
                    mutationId = mutationId,
                    dateMutation = dateMut,
                    year = year,
                    priceEuro = price,
                    surfaceM2 = surfaceRaw,
                    rooms = obj.optInt("nombre_pieces_principales", 0),
                    propertyType = typeLocal,
                    addressLabel = street.ifBlank { "Parcelle proche" },
                    distanceMeters = distMeters
                )
                val prev = byMutation[mutationId]
                if (prev == null || candidate.surfaceM2 > prev.surfaceM2) {
                    byMutation[mutationId] = candidate
                }
            }
            return byMutation.values.sortedWith(
                compareBy<DvfMutationComparable> { it.distanceMeters }.thenByDescending { it.dateMutation }
            )
        }

        fun parseCeremaArray(
            arr: JSONArray,
            refLat: Double,
            refLon: Double
        ): List<DvfMutationComparable> {
            val result = mutableListOf<DvfMutationComparable>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val nature = obj.optString("libnatmut", "")
                if (nature.isNotBlank() && !nature.contains("Vente", ignoreCase = true)) continue
                val typeLocal = obj.optString("libtypbien", "")
                val normalizedType = when {
                    typeLocal.contains("MAISON", ignoreCase = true) -> "Maison"
                    typeLocal.contains("APPARTEMENT", ignoreCase = true) -> "Appartement"
                    else -> continue
                }
                val price = obj.optDouble("valeurfonc", 0.0).roundToLong()
                val surface = obj.optDouble("sbati", obj.optDouble("surf_bati", 0.0)).roundToInt()
                if (price < 15000L || surface < 10) continue
                val m2 = price / surface
                if (m2 < 600L || m2 > 22000L) continue

                val geom = obj.optJSONObject("geom") ?: obj.optJSONObject("geomlocmut")
                val coords = geom?.optJSONArray("coordinates")
                val distMeters = if (coords != null && coords.length() >= 2) {
                    val tLon = coords.optDouble(0, Double.NaN)
                    val tLat = coords.optDouble(1, Double.NaN)
                    if (!tLat.isNaN() && !tLon.isNaN()) haversineMeters(refLat, refLon, tLat, tLon).roundToInt() else 180
                } else {
                    180
                }
                if (distMeters > 300) continue

                val dateMut = obj.optString("datemut", "")
                result.add(
                    DvfMutationComparable(
                        mutationId = obj.optString("idmutation", "cerema_$i"),
                        dateMutation = dateMut,
                        year = obj.optInt("anneemut", dateMut.take(4).toIntOrNull() ?: 0),
                        priceEuro = price,
                        surfaceM2 = surface,
                        rooms = obj.optInt("nb_p_princ", 0),
                        propertyType = normalizedType,
                        addressLabel = obj.optString("libcomm", "Secteur proche"),
                        distanceMeters = distMeters
                    )
                )
            }
            return result.sortedWith(
                compareBy<DvfMutationComparable> { it.distanceMeters }.thenByDescending { it.dateMutation }
            )
        }

        fun computeValuationReport(
            resolvedAddress: String,
            dataSource: String,
            comparables: List<DvfMutationComparable>,
            propertyKind: PropertyKind,
            surfaceM2: Int,
            dpe: String,
            conditionLabel: String,
            hasExteriorOrParking: Boolean = true,
            providedDocsRatio: Float,
            criteria: DvfValuationCriteria = DvfValuationCriteria(),
            referenceYear: Int = Calendar.getInstance().get(Calendar.YEAR)
        ): DvfValuationReport {
            val matchingType = comparables.filter {
                it.propertyType.equals(propertyKind.label, ignoreCase = true)
            }
            val effectivePool = if (matchingType.size >= 2) matchingType else comparables

            val list50 = effectivePool.filter { it.distanceMeters <= 50 }
            val list100 = effectivePool.filter { it.distanceMeters <= 100 }
            val list300 = effectivePool.filter { it.distanceMeters <= 300 }

            val stats50 = buildRingStats(50, list50)
            val stats100 = buildRingStats(100, list100)
            val stats300 = buildRingStats(300, list300)

            var weightedSum = 0.0
            var totalWeight = 0.0
            effectivePool.forEach { comp ->
                val ringWeight = when {
                    comp.distanceMeters <= 50 -> 3.0
                    comp.distanceMeters <= 100 -> 2.0
                    else -> 1.0
                }
                val ageYears = (referenceYear - comp.year).coerceIn(0, 6)
                val recencyWeight = when {
                    ageYears <= 1 -> 1.25
                    ageYears == 2 -> 1.0
                    else -> 0.75
                }
                val w = ringWeight * recencyWeight
                weightedSum += comp.pricePerM2 * w
                totalWeight += w
            }

            val basePriceM2 = if (totalWeight > 0.0) (weightedSum / totalWeight) else 0.0
            val rawDvfPriceM2 = basePriceM2.roundToLong()
            val safeSurface = surfaceM2.coerceAtLeast(1)
            val baseMarketEuro = if (rawDvfPriceM2 > 0L) {
                ((rawDvfPriceM2 * safeSurface) / 500.0).roundToLong() * 500L
            } else {
                0L
            }

            val dpeDelta = when (dpe.trim().uppercase(Locale.ROOT)) {
                "A", "B" -> 0.05
                "C" -> 0.02
                "D" -> 0.0
                "E" -> -0.04
                "F" -> -0.09
                "G" -> -0.14
                else -> 0.0
            }
            val conditionDelta = when {
                conditionLabel.contains("Rénové", ignoreCase = true) ||
                    conditionLabel.contains("Neuf", ignoreCase = true) -> 0.06
                conditionLabel.contains("rafraîchir", ignoreCase = true) -> -0.07
                conditionLabel.contains("rénover", ignoreCase = true) -> -0.15
                else -> 0.0
            }

            val floorOptions = if (propertyKind == PropertyKind.APPARTEMENT) {
                DvfCriteriaOptions.ELEVATOR_FLOOR_APPT
            } else {
                DvfCriteriaOptions.ELEVATOR_FLOOR_HOUSE
            }
            val floorDelta = lookupCriterionDelta(criteria.floorAndElevator, floorOptions)
            val viewDelta = lookupCriterionDelta(criteria.viewAndExposure, DvfCriteriaOptions.VIEW_EXPOSURE)
            val exteriorDelta = if (!hasExteriorOrParking && criteria.exteriorType.isBlank()) {
                0.0
            } else {
                lookupCriterionDelta(criteria.exteriorType, DvfCriteriaOptions.EXTERIOR_TYPES)
            }
            val parkingDelta = if (!hasExteriorOrParking && criteria.parkingType.isBlank()) {
                0.0
            } else {
                lookupCriterionDelta(criteria.parkingType, DvfCriteriaOptions.PARKING_TYPES)
            }
            val noiseDelta = lookupCriterionDelta(criteria.environmentCalm, DvfCriteriaOptions.NOISE_ENVIRONMENT)

            val rawDrivers = mutableListOf(
                Triple("Performance énergétique", "Classe DPE $dpe", dpeDelta),
                Triple("État général du bien", conditionLabel, conditionDelta),
                Triple(
                    if (propertyKind == PropertyKind.APPARTEMENT) "Étage & Ascenseur" else "Configuration bâti",
                    criteria.floorAndElevator,
                    floorDelta
                ),
                Triple("Vue & Luminosité", criteria.viewAndExposure, viewDelta),
                Triple("Extérieur (Balcon / Terrasse / Jardin)", criteria.exteriorType, exteriorDelta),
                Triple("Stationnement / Garage", criteria.parkingType, parkingDelta),
                Triple("Environnement & Calme", criteria.environmentCalm, noiseDelta)
            )

            if (criteria.hasCellarOrStorage) {
                rawDrivers.add(Triple("Annexe de rangement", "Cave / Cellier inclus", 0.015))
            }
            if (criteria.hasAcOrHeatPump) {
                rawDrivers.add(Triple("Confort thermique", "Climatisation / Pompe à chaleur", 0.02))
            }
            if (criteria.hasRecentGlazingOrFiber) {
                rawDrivers.add(Triple("Équipements techniques", "Double vitrage récent / Fibre", 0.015))
            }
            if (criteria.hasNoVisAVis) {
                rawDrivers.add(Triple("Intimité", "Sans vis-à-vis direct", 0.02))
            }
            if (criteria.hasMajorWorksPending) {
                rawDrivers.add(Triple("Travaux à anticiper", "Ravalement ou gros travaux votés/à prévoir", -0.04))
            }

            val totalDeltaRatio = rawDrivers.sumOf { it.third }.coerceIn(-0.45, 0.55)
            val totalAdjustmentPercent = (totalDeltaRatio * 100.0).roundToInt()

            val adjustmentDrivers = rawDrivers.map { (label, detail, deltaRatio) ->
                val pct = (deltaRatio * 1000.0).roundToInt() / 10.0
                val euroImpact = if (baseMarketEuro > 0L) {
                    ((baseMarketEuro * deltaRatio) / 100.0).roundToLong() * 100L
                } else {
                    0L
                }
                DvfPriceAdjustmentDriver(
                    label = label,
                    detail = detail.replace(Regex("\\s*\\([^)]*%\\)"), "").trim(),
                    impactPercent = pct,
                    euroImpact = euroImpact
                )
            }

            val adjustedPriceM2 = if (basePriceM2 > 0.0) {
                (basePriceM2 * (1.0 + totalDeltaRatio)).roundToLong()
            } else {
                0L
            }

            val midEuro = if (adjustedPriceM2 > 0L) {
                ((adjustedPriceM2 * safeSurface) / 500.0).roundToLong() * 500L
            } else {
                0L
            }

            // Transparent 0-100 Score breakdown
            val pts50 = when {
                stats50.count >= 3 -> 25
                stats50.count == 2 -> 21
                stats50.count == 1 -> 14
                else -> 0
            }
            val pts100 = when {
                stats100.count >= 4 -> 20
                stats100.count >= 2 -> 14
                stats100.count == 1 -> 8
                else -> 0
            }
            val pts300 = when {
                stats300.count >= 10 -> 20
                stats300.count >= 5 -> 15
                stats300.count >= 1 -> 9
                else -> 0
            }

            val cv = computeCoefficientOfVariation(effectivePool.map { it.pricePerM2.toDouble() })
            val ptsHomogeneity = when {
                effectivePool.size < 2 -> if (effectivePool.size == 1) 6 else 0
                cv <= 0.12 -> 15
                cv <= 0.22 -> 10
                else -> 5
            }

            val recentCount = effectivePool.count { (referenceYear - it.year) <= 2 }
            val ptsRecency = when {
                effectivePool.isEmpty() -> 0
                recentCount >= 4 || (recentCount.toDouble() / effectivePool.size) >= 0.6 -> 10
                recentCount >= 1 -> 6
                else -> 3
            }

            val criteriaQualified = criteria.qualifiedFieldCount() >= 4
            val ptsDossier = when {
                surfaceM2 >= 10 && criteriaQualified && providedDocsRatio >= 0.5f -> 10
                surfaceM2 >= 10 && criteriaQualified -> 8
                surfaceM2 >= 10 && providedDocsRatio > 0f -> 6
                surfaceM2 >= 10 -> 4
                else -> 0
            }

            val totalScore = (pts50 + pts100 + pts300 + ptsHomogeneity + ptsRecency + ptsDossier).coerceIn(0, 100)
            val scoreLabel = when {
                totalScore >= 80 -> "Très fiable"
                totalScore >= 60 -> "Fiable"
                totalScore >= 40 -> "Indicatif — Visite requise"
                else -> "Données DVF insuffisantes"
            }

            val spreadRatio = if (totalScore >= 65) 0.05 else 0.08
            val lowEuro = if (midEuro > 0L) (((midEuro * (1.0 - spreadRatio)) / 500.0).roundToLong() * 500L) else 0L
            val highEuro = if (midEuro > 0L) (((midEuro * (1.0 + spreadRatio)) / 500.0).roundToLong() * 500L) else 0L

            val breakdown = listOf(
                "Proximité immédiate ≤ 50 m (${stats50.count} ventes)" to pts50,
                "Périmètre proche ≤ 100 m (${stats100.count} ventes)" to pts100,
                "Quartier ≤ 300 m (${stats300.count} ventes)" to pts300,
                "Homogénéité des prix/m²" to ptsHomogeneity,
                "Fraîcheur des actes notariés" to ptsRecency,
                "Précision critères physiques & dossier" to ptsDossier
            )

            val justificationStrings = adjustmentDrivers.map { drv ->
                val pctSign = if (drv.impactPercent >= 0) "+" else ""
                val pctStr = if (drv.impactPercent % 1.0 == 0.0) {
                    "${drv.impactPercent.roundToInt()}"
                } else {
                    String.format(Locale.US, "%.1f", drv.impactPercent)
                }
                val euroSign = if (drv.euroImpact >= 0) "+" else ""
                "${drv.label} (${drv.detail}) : $pctSign$pctStr% ($euroSign${MontyMarketData.formatEuro(drv.euroImpact)})"
            }

            val criteriaSummary = listOf(
                criteria.floorAndElevator,
                criteria.viewAndExposure,
                criteria.exteriorType,
                criteria.parkingType,
                criteria.environmentCalm
            ).joinToString(" • ") { it.replace(Regex("\\s*\\([^)]*%\\)"), "").trim() }

            val nowLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date())
            val snapshot = DvfValuationSnapshot(
                estimatedMidEuro = midEuro,
                estimatedLowEuro = lowEuro,
                estimatedHighEuro = highEuro,
                baseMarketEuro = baseMarketEuro,
                pricePerM2 = adjustedPriceM2,
                rawDvfPricePerM2 = rawDvfPriceM2,
                totalAdjustmentPercent = totalAdjustmentPercent,
                criteriaSummary = criteriaSummary,
                justificationLines = justificationStrings,
                valuationScore = totalScore,
                scoreLabel = scoreLabel,
                resolvedAddress = resolvedAddress,
                count50m = stats50.count,
                count100m = stats100.count,
                count300m = stats300.count,
                avgM2_50m = stats50.avgPricePerM2,
                avgM2_100m = stats100.avgPricePerM2,
                avgM2_300m = stats300.avgPricePerM2,
                dataSource = dataSource,
                computedAtLabel = nowLabel
            )

            val adjSign = if (totalAdjustmentPercent >= 0) "+" else ""
            val note = buildString {
                if (effectivePool.isEmpty()) {
                    append("Aucune mutation résidentielle DVF publiée à moins de 300 m autour de cette adresse.")
                } else {
                    append("Base DVF pondérée (3x ≤50m, 2x ≤100m, 1x ≤300m) sur ${effectivePool.size} mutation(s)")
                    if (matchingType.size >= 2) {
                        append(" de type ${propertyKind.label}")
                    } else {
                        append(" (tous biens résidentiels faute de ≥2 ${propertyKind.label.lowercase()}s)")
                    }
                    append(" : ${MontyMarketData.formatEuro(rawDvfPriceM2)}/m², puis ajustement global $adjSign$totalAdjustmentPercent% selon vos critères (étage/ascenseur, vue, extérieur, parking, DPE, état).")
                }
            }

            return DvfValuationReport(
                snapshot = snapshot,
                ring50m = stats50,
                ring100m = stats100,
                ring300m = stats300,
                adjustmentDrivers = adjustmentDrivers,
                scoreBreakdown = breakdown,
                methodologyNote = note,
                matchingTypeOnlyCount = matchingType.size
            )
        }

        private fun lookupCriterionDelta(
            selected: String,
            options: List<Pair<String, Double>>
        ): Double {
            if (selected.isBlank()) return 0.0
            val exact = options.firstOrNull { it.first.equals(selected, ignoreCase = true) }
            if (exact != null) return exact.second
            val clean = selected.replace(Regex("\\s*\\([^)]*%\\)"), "").trim()
            return options.firstOrNull {
                it.first.contains(clean, ignoreCase = true)
            }?.second ?: 0.0
        }

        private fun buildRingStats(radius: Int, items: List<DvfMutationComparable>): DvfRingStats {
            if (items.isEmpty()) {
                return DvfRingStats(
                    radiusMeters = radius,
                    count = 0,
                    medianPricePerM2 = 0L,
                    avgPricePerM2 = 0L,
                    minPricePerM2 = 0L,
                    maxPricePerM2 = 0L,
                    comparables = emptyList()
                )
            }
            val sortedM2 = items.map { it.pricePerM2 }.sorted()
            val median = if (sortedM2.size % 2 == 1) {
                sortedM2[sortedM2.size / 2]
            } else {
                (sortedM2[sortedM2.size / 2 - 1] + sortedM2[sortedM2.size / 2]) / 2L
            }
            val avg = sortedM2.average().roundToLong()
            return DvfRingStats(
                radiusMeters = radius,
                count = items.size,
                medianPricePerM2 = median,
                avgPricePerM2 = avg,
                minPricePerM2 = sortedM2.first(),
                maxPricePerM2 = sortedM2.last(),
                comparables = items.take(12)
            )
        }

        private fun computeCoefficientOfVariation(values: List<Double>): Double {
            if (values.size < 2) return 0.0
            val mean = values.average()
            if (mean <= 0.0) return 0.0
            val variance = values.sumOf { (it - mean).pow(2) } / values.size
            return sqrt(variance) / mean
        }

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return abs(r * c)
        }
    }
}
