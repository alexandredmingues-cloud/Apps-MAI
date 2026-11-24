package com.example.data

import com.example.data.model.PropertyItem
import kotlin.math.roundToInt

enum class SearchPerimeterMode(val code: String, val label: String) {
    SECTOR_LIST("SECTOR_LIST", "Quartiers"),
    RADIUS_KM("RADIUS_KM", "Rayon (km)"),
    TRAVEL_TIME("TRAVEL_TIME", "Temps de trajet");

    companion object {
        fun fromCode(code: String): SearchPerimeterMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SECTOR_LIST
    }
}

enum class TransportMode(
    val code: String,
    val label: String,
    val speedKmh: Double,
    val detourFactor: Double,
    val accessOverheadMin: Int
) {
    VOITURE("VOITURE", "Voiture", 32.0, 1.25, 2),
    TRAM("TRAM", "Tram", 18.0, 1.28, 5),
    BUS("BUS", "Bus", 15.0, 1.32, 6),
    TRAIN("TRAIN", "Train / TER", 50.0, 1.18, 9),
    VELO("VELO", "Vélo", 16.0, 1.16, 1),
    PIED("PIED", "À pied", 4.8, 1.20, 0);

    companion object {
        fun fromCode(code: String): TransportMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: TRAM
    }
}

data class GeoPointPreset(
    val label: String,
    val queryHint: String,
    val latitude: Double,
    val longitude: Double
)

data class PropertySearchPerimeter(
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val mode: SearchPerimeterMode = SearchPerimeterMode.SECTOR_LIST,
    val radiusKm: Int = 5,
    val maxTravelMinutes: Int = 20,
    val transportMode: TransportMode = TransportMode.TRAM
) {
    val isSpatialActive: Boolean
        get() = mode != SearchPerimeterMode.SECTOR_LIST && (latitude != 0.0 || longitude != 0.0)
}

data class PropertyCommuteEstimate(
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val transportMode: TransportMode,
    val originLabel: String,
    val badgeLabel: String,
    val withinPerimeter: Boolean
)

/**
 * Calcul géospatial de secteur par adresse + distance (km) ou temps de trajet
 * multimodal (Train/TER, Bus, Tram, Vélo, À pied, Voiture).
 */
object GeoCommuteService {

    val MONTPELLIER_HUBS: List<GeoPointPreset> = listOf(
        GeoPointPreset("Place de la Comédie", "Place de la Comédie, Montpellier", 43.6085, 3.8795),
        GeoPointPreset("Gare Saint-Roch", "Gare Saint-Roch, Montpellier", 43.6047, 3.8806),
        GeoPointPreset("Gare Sud de France", "Gare Sud de France, Montpellier", 43.5955, 3.9234),
        GeoPointPreset("CHU Lapeyronie", "CHU Lapeyronie, Montpellier", 43.6285, 3.8548),
        GeoPointPreset("Odysseum", "Odysseum, Montpellier", 43.6041, 3.9195),
        GeoPointPreset("Port Marianne", "Port Marianne, Montpellier", 43.6003, 3.9041)
    )

    private val MONTPELLIER_SECTOR_CENTROIDS: List<Pair<String, Pair<Double, Double>>> = listOf(
        "ovalie" to (43.5936 to 3.8467),
        "croix d'argent" to (43.5908 to 3.8614),
        "mas drevon" to (43.5974 to 3.8648),
        "port marianne" to (43.6003 to 3.9041),
        "antigone" to (43.6087 to 3.8902),
        "arceaux" to (43.6119 to 3.8645),
        "hôpitaux" to (43.6335 to 3.8618),
        "hopitaux" to (43.6335 to 3.8618),
        "facult" to (43.6335 to 3.8618),
        "juvignac" to (43.6132 to 3.8104),
        "baillargues" to (43.6606 to 4.0132),
        "castelnau" to (43.6365 to 3.9012),
        "lattes" to (43.5681 to 3.9022),
        "pérols" to (43.5648 to 3.9508),
        "perols" to (43.5648 to 3.9508),
        "comédie" to (43.6085 to 3.8795),
        "comedie" to (43.6085 to 3.8795),
        "saint-roch" to (43.6047 to 3.8806),
        "st-roch" to (43.6047 to 3.8806),
        "sud de france" to (43.5955 to 3.9234),
        "lapeyronie" to (43.6285 to 3.8548),
        "odysseum" to (43.6041 to 3.9195),
        "dugrand" to (43.6001 to 3.9035),
        "rugby" to (43.5931 to 3.8475)
    )

    private val DEFAULT_MONTPELLIER_CENTER = 43.6108 to 3.8767

    fun resolveCoordinatesForProperty(property: PropertyItem): Pair<Double, Double> {
        if (property.latitude != 0.0 && property.longitude != 0.0) {
            return property.latitude to property.longitude
        }
        val haystack = "${property.address} ${property.sector} ${property.title}".lowercase()
        val matched = MONTPELLIER_SECTOR_CENTROIDS.firstOrNull { (key, _) ->
            haystack.contains(key)
        }
        return matched?.second ?: DEFAULT_MONTPELLIER_CENTER
    }

    fun resolveOfflineAddressFallback(query: String): GeocodedAddressResult? {
        val clean = query.trim().lowercase()
        if (clean.length < 2) return null

        MONTPELLIER_HUBS.firstOrNull {
            clean.contains(it.label.lowercase()) || it.label.lowercase().contains(clean)
        }?.let { hub ->
            return GeocodedAddressResult(
                label = hub.queryHint,
                latitude = hub.latitude,
                longitude = hub.longitude,
                cityCode = "34172"
            )
        }

        MONTPELLIER_SECTOR_CENTROIDS.firstOrNull { (key, _) ->
            clean.contains(key)
        }?.let { (_, coords) ->
            return GeocodedAddressResult(
                label = query.trim(),
                latitude = coords.first,
                longitude = coords.second,
                cityCode = "34172"
            )
        }

        if (clean.contains("montpellier") || clean.contains("34000") || clean.contains("34070") || clean.contains("34090")) {
            return GeocodedAddressResult(
                label = query.trim(),
                latitude = DEFAULT_MONTPELLIER_CENTER.first,
                longitude = DEFAULT_MONTPELLIER_CENTER.second,
                cityCode = "34172"
            )
        }
        return null
    }

    fun evaluatePropertyAccess(
        originLat: Double,
        originLon: Double,
        originLabel: String,
        property: PropertyItem,
        transportMode: TransportMode,
        searchMode: SearchPerimeterMode,
        maxRadiusKm: Int,
        maxTravelMinutes: Int
    ): PropertyCommuteEstimate {
        val (propLat, propLon) = resolveCoordinatesForProperty(property)
        val geodesicMeters = DvfValuationService.haversineMeters(originLat, originLon, propLat, propLon)
        val geodesicKm = geodesicMeters / 1000.0
        val routeKm = geodesicKm * transportMode.detourFactor
        val roundedKm = ((routeKm * 10.0).roundToInt() / 10.0)
        val estimatedMinutes = (transportMode.accessOverheadMin + (routeKm / transportMode.speedKmh) * 60.0)
            .roundToInt()
            .coerceAtLeast(1)

        val withinPerimeter = when (searchMode) {
            SearchPerimeterMode.SECTOR_LIST -> true
            SearchPerimeterMode.RADIUS_KM -> routeKm <= maxRadiusKm.toDouble() + 0.05
            SearchPerimeterMode.TRAVEL_TIME -> estimatedMinutes <= maxTravelMinutes
        }

        val kmDisplay = if (roundedKm < 1.0) {
            "${(routeKm * 1000.0).roundToInt()} m"
        } else {
            String.format(java.util.Locale.FRANCE, "%.1f km", roundedKm)
        }

        val badgeLabel = when (searchMode) {
            SearchPerimeterMode.RADIUS_KM -> "$kmDisplay • ~${estimatedMinutes} min (${transportMode.label})"
            SearchPerimeterMode.TRAVEL_TIME -> "~${estimatedMinutes} min en ${transportMode.label} ($kmDisplay)"
            SearchPerimeterMode.SECTOR_LIST -> "$kmDisplay • ${estimatedMinutes} min"
        }

        return PropertyCommuteEstimate(
            distanceKm = roundedKm,
            estimatedMinutes = estimatedMinutes,
            transportMode = transportMode,
            originLabel = originLabel,
            badgeLabel = badgeLabel,
            withinPerimeter = withinPerimeter
        )
    }

    fun evaluateForPerimeter(
        perimeter: PropertySearchPerimeter,
        property: PropertyItem
    ): PropertyCommuteEstimate? {
        if (!perimeter.isSpatialActive) return null
        return evaluatePropertyAccess(
            originLat = perimeter.latitude,
            originLon = perimeter.longitude,
            originLabel = perimeter.address.ifBlank { "Adresse cible" },
            property = property,
            transportMode = perimeter.transportMode,
            searchMode = perimeter.mode,
            maxRadiusKm = perimeter.radiusKm,
            maxTravelMinutes = perimeter.maxTravelMinutes
        )
    }

    fun evaluateMultiModeReachability(
        originLat: Double,
        originLon: Double,
        originLabel: String,
        property: PropertyItem,
        modes: Set<TransportMode>,
        matchAnyMode: Boolean,
        searchMode: SearchPerimeterMode,
        maxRadiusKm: Int,
        maxTravelMinutes: Int
    ): MultiModeReachabilityResult {
        val activeModes = if (modes.isEmpty()) setOf(TransportMode.TRAM) else modes
        val estimates = activeModes.map { mode ->
            evaluatePropertyAccess(
                originLat = originLat,
                originLon = originLon,
                originLabel = originLabel,
                property = property,
                transportMode = mode,
                searchMode = searchMode,
                maxRadiusKm = maxRadiusKm,
                maxTravelMinutes = maxTravelMinutes
            )
        }
        val within = when (searchMode) {
            SearchPerimeterMode.SECTOR_LIST -> true
            SearchPerimeterMode.RADIUS_KM, SearchPerimeterMode.TRAVEL_TIME -> {
                if (matchAnyMode) {
                    estimates.any { it.withinPerimeter }
                } else {
                    estimates.all { it.withinPerimeter }
                }
            }
        }
        val bestEstimate = estimates.minByOrNull { it.estimatedMinutes } ?: estimates.first()
        val summaryBadge = if (estimates.size <= 1) {
            bestEstimate.badgeLabel
        } else {
            val fastest = bestEstimate
            val modeTags = estimates.sortedBy { it.estimatedMinutes }.joinToString(" / ") {
                "${it.transportMode.label}: ${it.estimatedMinutes}m"
            }
            "${fastest.distanceKm} km • $modeTags"
        }
        return MultiModeReachabilityResult(
            withinPerimeter = within,
            bestEstimate = bestEstimate,
            modeEstimates = estimates,
            summaryBadge = summaryBadge
        )
    }
}

data class MultiModeReachabilityResult(
    val withinPerimeter: Boolean,
    val bestEstimate: PropertyCommuteEstimate,
    val modeEstimates: List<PropertyCommuteEstimate>,
    val summaryBadge: String
)

