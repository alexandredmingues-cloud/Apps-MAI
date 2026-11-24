package com.example.data

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GeoLatLon(
    val latitude: Double,
    val longitude: Double
)

data class GeoPolygonRing(
    val points: List<GeoLatLon>
)

data class CadastralParcelInfo(
    val idParcelle: String,
    val section: String,
    val numero: String,
    val prefixeSection: String,
    val communeCode: String,
    val communeName: String,
    val contenanceM2: Int,
    val rings: List<GeoPolygonRing> = emptyList()
)

data class PluZoningInfo(
    val libelle: String,
    val libelleLong: String,
    val typeZone: String,
    val destDomi: String,
    val inseeCode: String,
    val partition: String,
    val urlDoc: String,
    val rings: List<GeoPolygonRing> = emptyList()
)

data class PluPrescriptionInfo(
    val libelle: String,
    val txt: String,
    val typePsc: String,
    val nature: String,
    val geometryKind: String
)

data class SupServitudeInfo(
    val nomSup: String,
    val categorie: String,
    val gestionnaire: String,
    val type: String,
    val rings: List<GeoPolygonRing> = emptyList()
)

data class PluDocumentInfo(
    val partition: String,
    val typeDoc: String,
    val status: String,
    val dateApprobation: String,
    val urlFic: String
)

data class GeorisquesItem(
    val codeNational: String,
    val libelle: String,
    val numRisque: String
)

data class PropertyGeoUrbanismReport(
    val latitude: Double,
    val longitude: Double,
    val resolvedAddress: String,
    val parcel: CadastralParcelInfo? = null,
    val pluZones: List<PluZoningInfo> = emptyList(),
    val prescriptions: List<PluPrescriptionInfo> = emptyList(),
    val servitudes: List<SupServitudeInfo> = emptyList(),
    val pluDocuments: List<PluDocumentInfo> = emptyList(),
    val risks: List<GeorisquesItem> = emptyList(),
    val layerNotices: List<String> = emptyList()
)

/**
 * Connecteur cartographique & urbanisme (France) :
 * - BAN (Base Adresse Nationale) : géocodage & géocodage inverse
 * - API Carto IGN Cadastre : parcelle, section, numéro, contenance, géométrie
 * - API Carto IGN Géoportail de l'Urbanisme (GPU) : zone-urba, prescriptions, SUP, documents
 * - Géorisques API v1 : risques naturels & technologiques (GASPAR)
 * - Tuiles raster OpenStreetMap HTTPS
 */
class UrbanismCadastreService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val tileMemoryCache = object : LruCache<String, ByteArray>(64) {}

    suspend fun searchBanSuggestions(query: String): Result<List<GeocodedAddressResult>> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.length < 3) return@withContext Result.success(emptyList())
            val online = runCatching {
                val encoded = URLEncoder.encode(clean, "UTF-8")
                val url = "https://api-adresse.data.gouv.fr/search/?q=$encoded&limit=5"
                val body = executeGet(url)
                parseBanFeatures(body)
            }
            if (online.isSuccess) return@withContext online
            val offline = GeoCommuteService.resolveOfflineAddressFallback(clean)
            if (offline != null) {
                Result.success(listOf(offline))
            } else {
                Result.failure(online.exceptionOrNull() ?: IllegalStateException("Service BAN indisponible."))
            }
        }

    suspend fun reverseGeocodeBan(lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = String.format(Locale.US, "https://api-adresse.data.gouv.fr/reverse/?lon=%.6f&lat=%.6f", lon, lat)
                val body = executeGet(url)
                parseBanFeatures(body).firstOrNull()?.label.orEmpty()
            }.getOrDefault("")
        }

    suspend fun fetchGeoUrbanismReport(
        lat: Double,
        lon: Double,
        addressHint: String = ""
    ): PropertyGeoUrbanismReport = withContext(Dispatchers.IO) {
        val geomJson = String.format(Locale.US, """{"type":"Point","coordinates":[%.6f,%.6f]}""", lon, lat)
        val encodedGeom = URLEncoder.encode(geomJson, "UTF-8")
        val notices = mutableListOf<String>()

        coroutineScope {
            val addrDeferred = async {
                if (addressHint.isNotBlank()) addressHint else reverseGeocodeBan(lat, lon)
            }
            val parcelDeferred = async {
                runCatching {
                    val body = executeGet("https://apicarto.ign.fr/api/cadastre/parcelle?geom=$encodedGeom")
                    parseCadastreResponse(body)
                }
            }
            val zoneDeferred = async {
                runCatching {
                    val body = executeGet("https://apicarto.ign.fr/api/gpu/zone-urba?geom=$encodedGeom")
                    parseGpuZoneResponse(body)
                }
            }
            val prescSurfDeferred = async {
                runCatching {
                    val body = executeGet("https://apicarto.ign.fr/api/gpu/prescription-surf?geom=$encodedGeom")
                    parseGpuPrescriptionsResponse(body, "Surfacique")
                }
            }
            val prescLinDeferred = async {
                runCatching {
                    val body = executeGet("https://apicarto.ign.fr/api/gpu/prescription-lin?geom=$encodedGeom")
                    parseGpuPrescriptionsResponse(body, "Linéaire")
                }
            }
            val prescPctDeferred = async {
                runCatching {
                    val body = executeGet("https://apicarto.ign.fr/api/gpu/prescription-pct?geom=$encodedGeom")
                    parseGpuPrescriptionsResponse(body, "Ponctuelle")
                }
            }
            val supDeferred = async {
                runCatching {
                    val body = executeGet("https://apicarto.ign.fr/api/gpu/assiette-sup-s?geom=$encodedGeom")
                    parseGpuSupResponse(body)
                }
            }
            val docDeferred = async {
                runCatching {
                    val body = executeGet("https://apicarto.ign.fr/api/gpu/document?geom=$encodedGeom")
                    parseGpuDocumentResponse(body)
                }
            }
            val risksDeferred = async {
                runCatching {
                    val url = String.format(Locale.US, "https://www.georisques.gouv.fr/api/v1/gaspar/risques?latlon=%.6f,%.6f", lon, lat)
                    val body = executeGet(url)
                    parseGeorisquesResponse(body)
                }
            }

            val resolvedAddress = addrDeferred.await().ifBlank {
                String.format(Locale.FRANCE, "%.5f, %.5f", lat, lon)
            }
            val parcelRes = parcelDeferred.await()
            val zoneRes = zoneDeferred.await()
            val prescList = buildList {
                prescSurfDeferred.await().getOrNull()?.let(::addAll)
                prescLinDeferred.await().getOrNull()?.let(::addAll)
                prescPctDeferred.await().getOrNull()?.let(::addAll)
            }
            val supRes = supDeferred.await()
            val docRes = docDeferred.await()
            val riskRes = risksDeferred.await()

            if (parcelRes.isFailure) notices += "Cadastre IGN momentanément indisponible."
            if (zoneRes.isFailure) notices += "Zonage PLU/PLUi Géoportail momentanément indisponible."
            if (supRes.isFailure) notices += "Couche SUP indisponible."
            if (riskRes.isFailure) notices += "Couche Géorisques indisponible."

            PropertyGeoUrbanismReport(
                latitude = lat,
                longitude = lon,
                resolvedAddress = resolvedAddress,
                parcel = parcelRes.getOrNull(),
                pluZones = zoneRes.getOrNull().orEmpty(),
                prescriptions = prescList,
                servitudes = supRes.getOrNull().orEmpty(),
                pluDocuments = docRes.getOrNull().orEmpty(),
                risks = riskRes.getOrNull().orEmpty(),
                layerNotices = notices
            )
        }
    }

    suspend fun fetchOsmTileBytes(z: Int, x: Int, y: Int): ByteArray? = withContext(Dispatchers.IO) {
        val key = "$z/$x/$y"
        tileMemoryCache.get(key)?.let { return@withContext it }
        runCatching {
            val req = Request.Builder()
                .url("https://tile.openstreetmap.org/$z/$x/$y.png")
                .header("User-Agent", "MonAgentImmo-Android/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                if (bytes.isNotEmpty()) {
                    tileMemoryCache.put(key, bytes)
                }
                bytes
            }
        }.getOrNull()
    }

    private fun executeGet(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "MonAgentImmo-Android/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}")
            }
            return resp.body?.string().orEmpty()
        }
    }

    companion object {
        const val URBANISM_LEGAL_DISCLAIMER =
            "Les données PLU et risques affichées ne constituent pas une étude réglementaire complète ni une confirmation de constructibilité."

        fun parseBanFeatures(jsonBody: String): List<GeocodedAddressResult> {
            if (jsonBody.isBlank()) return emptyList()
            val root = JSONObject(jsonBody)
            val features = root.optJSONArray("features") ?: return emptyList()
            return buildList {
                for (i in 0 until features.length()) {
                    val feat = features.optJSONObject(i) ?: continue
                    val coords = feat.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                    if (coords.length() < 2) continue
                    val lon = coords.optDouble(0, Double.NaN)
                    val lat = coords.optDouble(1, Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    val props = feat.optJSONObject("properties") ?: continue
                    add(
                        GeocodedAddressResult(
                            label = props.optString("label", ""),
                            latitude = lat,
                            longitude = lon,
                            cityCode = props.optString("citycode", "")
                        )
                    )
                }
            }
        }

        fun parseCadastreResponse(jsonBody: String): CadastralParcelInfo? {
            if (jsonBody.isBlank()) return null
            val root = JSONObject(jsonBody)
            val features = root.optJSONArray("features") ?: return null
            if (features.length() == 0) return null
            val feat = features.getJSONObject(0)
            val props = feat.optJSONObject("properties") ?: JSONObject()
            val rings = extractPolygonRings(feat.optJSONObject("geometry"))
            val idPar = props.optString("id", props.optString("idu", ""))
            val section = props.optString("section", "")
            val numero = props.optString("numero", "")
            val codeCom = props.optString("code_com", props.optString("commune", ""))
            val nomCom = props.optString("nom_com", "")
            val contenance = props.optInt("contenance", 0)
            val prefixe = props.optString("code_arr", props.optString("prefixe", ""))
            return CadastralParcelInfo(
                idParcelle = idPar.ifBlank { listOf(codeCom, prefixe, section, numero).filter { it.isNotBlank() }.joinToString("-") },
                section = section,
                numero = numero,
                prefixeSection = prefixe,
                communeCode = codeCom,
                communeName = nomCom,
                contenanceM2 = contenance,
                rings = rings
            )
        }

        fun parseGpuZoneResponse(jsonBody: String): List<PluZoningInfo> {
            if (jsonBody.isBlank()) return emptyList()
            val root = JSONObject(jsonBody)
            val features = root.optJSONArray("features") ?: return emptyList()
            return buildList {
                for (i in 0 until features.length()) {
                    val feat = features.optJSONObject(i) ?: continue
                    val props = feat.optJSONObject("properties") ?: continue
                    val rings = extractPolygonRings(feat.optJSONObject("geometry"))
                    add(
                        PluZoningInfo(
                            libelle = props.optString("libelle", ""),
                            libelleLong = props.optString("libelong", ""),
                            typeZone = props.optString("typezone", ""),
                            destDomi = props.optString("destdomi", ""),
                            inseeCode = props.optString("insee", ""),
                            partition = props.optString("partition", ""),
                            urlDoc = props.optString("urlfic", ""),
                            rings = rings
                        )
                    )
                }
            }
        }

        fun parseGpuPrescriptionsResponse(jsonBody: String, geometryKind: String): List<PluPrescriptionInfo> {
            if (jsonBody.isBlank()) return emptyList()
            val root = JSONObject(jsonBody)
            val features = root.optJSONArray("features") ?: return emptyList()
            return buildList {
                for (i in 0 until features.length()) {
                    val feat = features.optJSONObject(i) ?: continue
                    val props = feat.optJSONObject("properties") ?: continue
                    val lib = props.optString("libelle", "")
                    val txt = props.optString("txt", "")
                    val typePsc = props.optString("typepsc", "")
                    val nature = props.optString("stypepsc", props.optString("nature", ""))
                    if (lib.isNotBlank() || txt.isNotBlank()) {
                        add(
                            PluPrescriptionInfo(
                                libelle = lib.ifBlank { txt },
                                txt = txt,
                                typePsc = typePsc,
                                nature = nature,
                                geometryKind = geometryKind
                            )
                        )
                    }
                }
            }
        }

        fun parseGpuSupResponse(jsonBody: String): List<SupServitudeInfo> {
            if (jsonBody.isBlank()) return emptyList()
            val root = JSONObject(jsonBody)
            val features = root.optJSONArray("features") ?: return emptyList()
            return buildList {
                for (i in 0 until features.length()) {
                    val feat = features.optJSONObject(i) ?: continue
                    val props = feat.optJSONObject("properties") ?: continue
                    val rings = extractPolygonRings(feat.optJSONObject("geometry"))
                    add(
                        SupServitudeInfo(
                            nomSup = props.optString("nomsup", props.optString("libelle", "Servitude d'utilité publique")),
                            categorie = props.optString("categorie", props.optString("suptype", "")),
                            gestionnaire = props.optString("gest_sup", ""),
                            type = props.optString("typeass", ""),
                            rings = rings
                        )
                    )
                }
            }
        }

        fun parseGpuDocumentResponse(jsonBody: String): List<PluDocumentInfo> {
            if (jsonBody.isBlank()) return emptyList()
            val root = JSONObject(jsonBody)
            val features = root.optJSONArray("features") ?: return emptyList()
            return buildList {
                for (i in 0 until features.length()) {
                    val feat = features.optJSONObject(i) ?: continue
                    val props = feat.optJSONObject("properties") ?: continue
                    add(
                        PluDocumentInfo(
                            partition = props.optString("partition", ""),
                            typeDoc = props.optString("typedoc", props.optString("du_type", "PLU/PLUi")),
                            status = props.optString("etat", ""),
                            dateApprobation = props.optString("datappro", ""),
                            urlFic = props.optString("urlfic", props.optString("urlpe", ""))
                        )
                    )
                }
            }
        }

        fun parseGeorisquesResponse(jsonBody: String): List<GeorisquesItem> {
            if (jsonBody.isBlank()) return emptyList()
            val root = JSONObject(jsonBody)
            val dataArr = root.optJSONArray("data") ?: return emptyList()
            val seen = mutableSetOf<String>()
            val out = mutableListOf<GeorisquesItem>()
            for (i in 0 until dataArr.length()) {
                val entry = dataArr.optJSONObject(i) ?: continue
                val detailArr = entry.optJSONArray("risques_detail")
                if (detailArr != null) {
                    for (j in 0 until detailArr.length()) {
                        val r = detailArr.optJSONObject(j) ?: continue
                        val num = r.optString("num_risque", "")
                        val lib = r.optString("libelle_risque_long", "")
                        val codeNat = entry.optString("code_national_risque", "")
                        if (lib.isNotBlank() && seen.add("$num|$lib")) {
                            out.add(GeorisquesItem(codeNational = codeNat, libelle = lib, numRisque = num))
                        }
                    }
                } else {
                    val lib = entry.optString("libelle_risque", entry.optString("libelle", ""))
                    if (lib.isNotBlank() && seen.add(lib)) {
                        out.add(
                            GeorisquesItem(
                                codeNational = entry.optString("code_national_risque", ""),
                                libelle = lib,
                                numRisque = entry.optString("num_risque", "")
                            )
                        )
                    }
                }
            }
            return out
        }

        fun extractPolygonRings(geometry: JSONObject?): List<GeoPolygonRing> {
            if (geometry == null) return emptyList()
            val type = geometry.optString("type", "")
            val coords = geometry.optJSONArray("coordinates") ?: return emptyList()
            val rings = mutableListOf<GeoPolygonRing>()
            fun parseRingArray(ringArr: JSONArray) {
                val pts = buildList {
                    for (i in 0 until ringArr.length()) {
                        val pt = ringArr.optJSONArray(i) ?: continue
                        if (pt.length() >= 2) {
                            val lon = pt.optDouble(0, Double.NaN)
                            val lat = pt.optDouble(1, Double.NaN)
                            if (!lat.isNaN() && !lon.isNaN()) add(GeoLatLon(lat, lon))
                        }
                    }
                }
                if (pts.size >= 3) rings.add(GeoPolygonRing(pts))
            }
            when (type) {
                "Polygon" -> {
                    val outer = coords.optJSONArray(0)
                    if (outer != null) parseRingArray(outer)
                }
                "MultiPolygon" -> {
                    for (p in 0 until coords.length()) {
                        val poly = coords.optJSONArray(p) ?: continue
                        val outer = poly.optJSONArray(0)
                        if (outer != null) parseRingArray(outer)
                    }
                }
            }
            return rings
        }
    }
}
