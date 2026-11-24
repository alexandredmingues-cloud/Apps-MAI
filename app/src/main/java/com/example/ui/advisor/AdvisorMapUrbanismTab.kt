package com.example.ui.advisor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.GeoCommuteService
import com.example.data.GeoLatLon
import com.example.data.GeoPolygonRing
import com.example.data.GeocodedAddressResult
import com.example.data.MontyMarketData
import com.example.data.PropertyGeoUrbanismReport
import com.example.data.SearchPerimeterMode
import com.example.data.UrbanismCadastreService
import com.example.data.model.ClientDossier
import com.example.data.model.PropertyItem
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldMid
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvisorMapUrbanismTab(
    properties: List<PropertyItem>,
    activeClient: ClientDossier?,
    urbanismReport: PropertyGeoUrbanismReport?,
    isLoadingUrbanism: Boolean,
    onInspectLocation: (lat: Double, lon: Double, addressHint: String) -> Unit,
    onClearReport: () -> Unit,
    onSearchBanAddresses: suspend (String) -> Result<List<GeocodedAddressResult>>,
    onFetchOsmTileBytes: suspend (z: Int, x: Int, y: Int) -> ByteArray?,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Montpellier Comédie par défaut ou premier bien du portefeuille
    val initialCenter = remember(properties.firstOrNull()?.id) {
        properties.firstOrNull()?.let { GeoCommuteService.resolveCoordinatesForProperty(it) }
            ?: (43.6085 to 3.8795)
    }

    var centerLat by remember { mutableDoubleStateOf(initialCenter.first) }
    var centerLon by remember { mutableDoubleStateOf(initialCenter.second) }
    var zoomLevel by remember { mutableIntStateOf(16) }

    var searchQuery by remember { mutableStateOf("") }
    var banSuggestions by remember { mutableStateOf<List<GeocodedAddressResult>>(emptyList()) }
    var isSearchingBan by remember { mutableStateOf(false) }

    var showOsmTiles by remember { mutableStateOf(true) }
    var showCadastreLayer by remember { mutableStateOf(true) }
    var showPluLayer by remember { mutableStateOf(true) }
    var showSupLayer by remember { mutableStateOf(true) }
    var showPropertiesLayer by remember { mutableStateOf(true) }
    var showMobilityLayer by remember { mutableStateOf(true) }

    var canvasSize by remember { mutableStateOf(IntSize(800, 600)) }
    val tileBitmaps = remember { mutableStateMapOf<String, Bitmap?>() }

    // Synchroniser le centre si le rapport change depuis un clic ou recherche
    LaunchedEffect(urbanismReport?.latitude, urbanismReport?.longitude) {
        if (urbanismReport != null) {
            centerLat = urbanismReport.latitude
            centerLon = urbanismReport.longitude
        }
    }

    // Chargement asynchrone des tuiles OSM visibles
    LaunchedEffect(centerLat, centerLon, zoomLevel, showOsmTiles, canvasSize) {
        if (!showOsmTiles || canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        val tileKeys = computeVisibleTileCoords(
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = zoomLevel,
            widthPx = canvasSize.width.toFloat(),
            heightPx = canvasSize.height.toFloat()
        )
        tileKeys.forEach { (tx, ty) ->
            val key = "$zoomLevel/$tx/$ty"
            if (!tileBitmaps.containsKey(key)) {
                tileBitmaps[key] = null
                launch {
                    val bytes = onFetchOsmTileBytes(zoomLevel, tx, ty)
                    val bmp = if (bytes != null && bytes.isNotEmpty()) {
                        withContext(Dispatchers.Default) {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    } else null
                    tileBitmaps[key] = bmp
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. BANNER & RECHERCHE ADRESSE BAN
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = GoldChampagne)
                        Column {
                            Text(
                                text = "CARTE FONCIÈRE & URBANISME",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldChampagne
                            )
                            Text(
                                text = "Cadastre IGN • PLU/PLUi • SUP • Géorisques",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IvoryWarm
                            )
                        }
                    }
                    if (urbanismReport != null) {
                        OutlinedButton(
                            onClick = onClearReport,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldChampagne)
                        ) {
                            Text("Effacer fiche", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Barre de recherche BAN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { q ->
                            searchQuery = q
                            if (q.trim().length >= 3) {
                                scope.launch {
                                    isSearchingBan = true
                                    onSearchBanAddresses(q).onSuccess { banSuggestions = it }
                                    isSearchingBan = false
                                }
                            } else {
                                banSuggestions = emptyList()
                            }
                        },
                        placeholder = { Text("Adresse BAN (ex. Antigone, Montpellier)", color = IvoryWarm.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldChampagne) },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = IvoryWarm,
                            unfocusedTextColor = IvoryWarm,
                            focusedBorderColor = GoldChampagne,
                            unfocusedBorderColor = IvoryWarm.copy(alpha = 0.35f),
                            cursorColor = GoldChampagne
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_ban_search_input")
                    )
                    Button(
                        onClick = {
                            if (searchQuery.trim().length >= 3) {
                                scope.launch {
                                    isSearchingBan = true
                                    onSearchBanAddresses(searchQuery)
                                        .onSuccess { list ->
                                            banSuggestions = list
                                            list.firstOrNull()?.let { first ->
                                                centerLat = first.latitude
                                                centerLon = first.longitude
                                                banSuggestions = emptyList()
                                                onInspectLocation(first.latitude, first.longitude, first.label)
                                            } ?: onShowToast("Aucune adresse BAN trouvée.")
                                        }
                                        .onFailure { onShowToast(it.localizedMessage ?: "Recherche BAN indisponible.") }
                                    isSearchingBan = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAntique, contentColor = ObsidianInk),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_ban_search_button")
                    ) {
                        Text("Localiser", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (isSearchingBan) {
                    Text("Interrogation Base Adresse Nationale...", style = MaterialTheme.typography.labelSmall, color = GoldChampagne)
                }

                if (banSuggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        banSuggestions.forEach { sugg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        searchQuery = sugg.label
                                        banSuggestions = emptyList()
                                        centerLat = sugg.latitude
                                        centerLon = sugg.longitude
                                        zoomLevel = 17
                                        onInspectLocation(sugg.latitude, sugg.longitude, sugg.label)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(16.dp))
                                Text(sugg.label, style = MaterialTheme.typography.bodySmall, color = ObsidianInk)
                            }
                        }
                    }
                }

                // Raccourcis vers les biens du portefeuille
                if (properties.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        properties.forEach { prop ->
                            Surface(
                                color = EmeraldMid,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .border(1.dp, GoldAntique.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val (pLat, pLon) = GeoCommuteService.resolveCoordinatesForProperty(prop)
                                        centerLat = pLat
                                        centerLon = pLon
                                        zoomLevel = 17
                                        onInspectLocation(pLat, pLon, prop.address.ifBlank { "${prop.title}, ${prop.sector}" })
                                    }
                                    .testTag("map_property_chip_${prop.id}")
                            ) {
                                Text(
                                    text = prop.title.take(26),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IvoryWarm,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. CONTRÔLE DES COUCHES CARTOGRAPHIQUES
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(16.dp))
                    Text("Couches actives (touchez un terrain ou bien pour l'analyser)", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LayerToggleChip("Fond OSM", showOsmTiles) { showOsmTiles = !showOsmTiles }
                    LayerToggleChip("Cadastre IGN", showCadastreLayer) { showCadastreLayer = !showCadastreLayer }
                    LayerToggleChip("PLU / PLUi", showPluLayer) { showPluLayer = !showPluLayer }
                    LayerToggleChip("Servitudes SUP", showSupLayer) { showSupLayer = !showSupLayer }
                    LayerToggleChip("Biens (${properties.size})", showPropertiesLayer) { showPropertiesLayer = !showPropertiesLayer }
                    LayerToggleChip("Zone Mobilité", showMobilityLayer) { showMobilityLayer = !showMobilityLayer }
                }
            }
        }

        // 3. VIEWPORT CARTOGRAPHIQUE INTERACTIF (CANVAS + TUILES OSM + VECTEURS CADASTRE/PLU)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE7ECE7)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(zoomLevel) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val worldScale = 256.0 * 2.0.pow(zoomLevel)
                                val centerPxX = lonToWorldX(centerLon, worldScale) - dragAmount.x
                                val centerPxY = latToWorldY(centerLat, worldScale) - dragAmount.y
                                centerLon = worldXToLon(centerPxX, worldScale).coerceIn(-180.0, 180.0)
                                centerLat = worldYToLat(centerPxY, worldScale).coerceIn(-85.0, 85.0)
                            }
                        }
                        .pointerInput(centerLat, centerLon, zoomLevel, properties) {
                            detectTapGestures { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w <= 0f || h <= 0f) return@detectTapGestures
                                val tappedProperty = if (showPropertiesLayer) {
                                    properties.firstOrNull { prop ->
                                        val (pLat, pLon) = GeoCommuteService.resolveCoordinatesForProperty(prop)
                                        val pt = latLonToCanvas(pLat, pLon, centerLat, centerLon, zoomLevel, w, h)
                                        hypot((pt.x - offset.x).toDouble(), (pt.y - offset.y).toDouble()) < 34.0
                                    }
                                } else null

                                if (tappedProperty != null) {
                                    val (pLat, pLon) = GeoCommuteService.resolveCoordinatesForProperty(tappedProperty)
                                    onInspectLocation(
                                        pLat,
                                        pLon,
                                        tappedProperty.address.ifBlank { "${tappedProperty.title}, ${tappedProperty.sector}" }
                                    )
                                } else {
                                    val (tapLat, tapLon) = canvasToLatLon(
                                        offset.x,
                                        offset.y,
                                        centerLat,
                                        centerLon,
                                        zoomLevel,
                                        w,
                                        h
                                    )
                                    onInspectLocation(tapLat, tapLon, "")
                                }
                            }
                        }
                        .testTag("urbanism_interactive_map_canvas")
                ) {
                    val w = size.width
                    val h = size.height
                    val worldScale = 256.0 * 2.0.pow(zoomLevel)
                    val cx = lonToWorldX(centerLon, worldScale)
                    val cy = latToWorldY(centerLat, worldScale)
                    val topLeftWorldX = cx - w / 2.0
                    val topLeftWorldY = cy - h / 2.0

                    // Fond grille si tuiles OSM en cours de chargement ou désactivées
                    drawRect(color = Color(0xFFEDF2ED))

                    if (showOsmTiles) {
                        val tiles = computeVisibleTileCoords(centerLat, centerLon, zoomLevel, w, h)
                        tiles.forEach { (tx, ty) ->
                            val bmp = tileBitmaps["$zoomLevel/$tx/$ty"]
                            val dx = (tx * 256.0 - topLeftWorldX).roundToInt()
                            val dy = (ty * 256.0 - topLeftWorldY).roundToInt()
                            if (bmp != null) {
                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    dstOffset = IntOffset(dx, dy),
                                    dstSize = IntSize(257, 257)
                                )
                            }
                        }
                    } else {
                        val step = 48f
                        var gx = 0f
                        while (gx < w) {
                            drawLine(Color(0xFFD1DDD4), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                            gx += step
                        }
                        var gy = 0f
                        while (gy < h) {
                            drawLine(Color(0xFFD1DDD4), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                            gy += step
                        }
                    }

                    // Zone de mobilité client actif ou halo 300m DVF
                    if (showMobilityLayer) {
                        if (activeClient != null && activeClient.searchCenterLat != 0.0 && activeClient.searchCenterLon != 0.0) {
                            val originPt = latLonToCanvas(
                                activeClient.searchCenterLat,
                                activeClient.searchCenterLon,
                                centerLat,
                                centerLon,
                                zoomLevel,
                                w,
                                h
                            )
                            val radiusMeters = when (SearchPerimeterMode.fromCode(activeClient.searchMode)) {
                                SearchPerimeterMode.RADIUS_KM -> activeClient.searchRadiusKm * 1000.0
                                SearchPerimeterMode.TRAVEL_TIME -> (activeClient.searchMaxTravelMinutes / 60.0) * 18000.0
                                SearchPerimeterMode.SECTOR_LIST -> 1200.0
                            }
                            val pxRadius = metersToCanvasPixels(radiusMeters, activeClient.searchCenterLat, zoomLevel)
                            drawCircle(
                                color = EmeraldMid.copy(alpha = 0.14f),
                                radius = pxRadius,
                                center = originPt
                            )
                            drawCircle(
                                color = EmeraldDeep,
                                radius = pxRadius,
                                center = originPt,
                                style = Stroke(width = 3f)
                            )
                        }
                        if (urbanismReport != null) {
                            val inspectPt = latLonToCanvas(
                                urbanismReport.latitude,
                                urbanismReport.longitude,
                                centerLat,
                                centerLon,
                                zoomLevel,
                                w,
                                h
                            )
                            val r300 = metersToCanvasPixels(300.0, urbanismReport.latitude, zoomLevel)
                            drawCircle(
                                color = GoldAntique.copy(alpha = 0.12f),
                                radius = r300,
                                center = inspectPt
                            )
                            drawCircle(
                                color = GoldAntique,
                                radius = r300,
                                center = inspectPt,
                                style = Stroke(width = 2.5f)
                            )
                        }
                    }

                    // Couche PLU / PLUi
                    if (showPluLayer && urbanismReport != null) {
                        urbanismReport.pluZones.forEach { zone ->
                            drawPolygonRings(
                                rings = zone.rings,
                                fillColor = Color(0xFF2563EB).copy(alpha = 0.18f),
                                strokeColor = Color(0xFF1D4ED8),
                                strokeWidth = 3f,
                                centerLat = centerLat,
                                centerLon = centerLon,
                                zoom = zoomLevel,
                                w = w,
                                h = h
                            )
                        }
                    }

                    // Couche Servitudes SUP
                    if (showSupLayer && urbanismReport != null) {
                        urbanismReport.servitudes.forEach { sup ->
                            drawPolygonRings(
                                rings = sup.rings,
                                fillColor = Color(0xFFDC2626).copy(alpha = 0.14f),
                                strokeColor = Color(0xFFDC2626),
                                strokeWidth = 2.5f,
                                centerLat = centerLat,
                                centerLon = centerLon,
                                zoom = zoomLevel,
                                w = w,
                                h = h
                            )
                        }
                    }

                    // Couche Parcelle Cadastrale IGN
                    if (showCadastreLayer && urbanismReport?.parcel != null) {
                        drawPolygonRings(
                            rings = urbanismReport.parcel.rings,
                            fillColor = Color(0xFFD97706).copy(alpha = 0.25f),
                            strokeColor = Color(0xFFB45309),
                            strokeWidth = 4f,
                            centerLat = centerLat,
                            centerLon = centerLon,
                            zoom = zoomLevel,
                            w = w,
                            h = h
                        )
                    }

                    // Marqueurs des biens du portefeuille
                    if (showPropertiesLayer) {
                        properties.forEach { prop ->
                            val (pLat, pLon) = GeoCommuteService.resolveCoordinatesForProperty(prop)
                            val pt = latLonToCanvas(pLat, pLon, centerLat, centerLon, zoomLevel, w, h)
                            if (pt.x in -30f..(w + 30f) && pt.y in -30f..(h + 30f)) {
                                drawCircle(color = Color.White, radius = 14f, center = pt)
                                drawCircle(
                                    color = if (prop.publisherType == "PARTICULIER") GoldAntique else EmeraldDeep,
                                    radius = 10f,
                                    center = pt
                                )
                            }
                        }
                    }

                    // Marqueur du point inspecté
                    if (urbanismReport != null) {
                        val pt = latLonToCanvas(
                            urbanismReport.latitude,
                            urbanismReport.longitude,
                            centerLat,
                            centerLon,
                            zoomLevel,
                            w,
                            h
                        )
                        drawCircle(color = Color.White, radius = 15f, center = pt)
                        drawCircle(color = ErrorRed, radius = 9f, center = pt)
                    }
                }

                // Overlay contrôles zoom + recentrage
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(color = Color.White.copy(alpha = 0.94f), shape = CircleShape, shadowElevation = 2.dp) {
                        IconButton(
                            onClick = { zoomLevel = (zoomLevel + 1).coerceAtMost(18) },
                            modifier = Modifier.size(48.dp).testTag("map_zoom_in")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoomer", tint = EmeraldDeep)
                        }
                    }
                    Surface(color = Color.White.copy(alpha = 0.94f), shape = CircleShape, shadowElevation = 2.dp) {
                        IconButton(
                            onClick = { zoomLevel = (zoomLevel - 1).coerceAtLeast(13) },
                            modifier = Modifier.size(48.dp).testTag("map_zoom_out")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Dézoomer", tint = EmeraldDeep)
                        }
                    }
                    Surface(color = Color.White.copy(alpha = 0.94f), shape = CircleShape, shadowElevation = 2.dp) {
                        IconButton(
                            onClick = {
                                centerLat = initialCenter.first
                                centerLon = initialCenter.second
                                zoomLevel = 16
                                onInspectLocation(initialCenter.first, initialCenter.second, "")
                            },
                            modifier = Modifier.size(48.dp).testTag("map_recenter")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Recentrer", tint = EmeraldDeep)
                        }
                    }
                }

                // Indicateur coordonnée + zoom en bas
                Surface(
                    color = ObsidianInk.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = String.format(Locale.FRANCE, "Zoom %d • %.5f, %.5f • © OSM / IGN", zoomLevel, centerLat, centerLon),
                        style = MaterialTheme.typography.labelSmall,
                        color = IvoryWarm,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                if (isLoadingUrbanism) {
                    Surface(
                        color = EmeraldDeep.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = GoldChampagne, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Analyse IGN Cadastre, GPU PLU & Géorisques...", color = IvoryWarm, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // 4. FICHE GÉOGRAPHIQUE & URBANISTIQUE DÉTAILLÉE
        if (urbanismReport == null && !isLoadingUrbanism) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sélectionnez une parcelle ou un bien", style = MaterialTheme.typography.titleMedium, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Touchez n'importe quel point de la carte, cliquez sur un bien du portefeuille ou saisissez une adresse BAN ci-dessus pour ouvrir la fiche cadastrale, le zonage PLU/PLUi, les prescriptions, les SUP et les risques Géorisques.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                    Button(
                        onClick = { onInspectLocation(centerLat, centerLon, "") },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        modifier = Modifier.fillMaxWidth().testTag("map_inspect_center_button")
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Inspecter le centre de la carte")
                    }
                }
            }
        } else if (urbanismReport != null) {
            UrbanismReportCard(
                report = urbanismReport,
                onOpenUrl = { rawUrl ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)))
                    }.onFailure { onShowToast("Impossible d'ouvrir le document.") }
                }
            )
        }
    }
}

@Composable
private fun LayerToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EmeraldDeep,
            selectedLabelColor = GoldChampagne
        )
    )
}

@Composable
private fun UrbanismReportCard(
    report: PropertyGeoUrbanismReport,
    onOpenUrl: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .testTag("urbanism_report_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("FICHE GÉOGRAPHIQUE & URBANISTIQUE", style = MaterialTheme.typography.labelSmall, color = GoldAntique)
            Text(report.resolvedAddress, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldDeep)

            if (report.layerNotices.isNotEmpty()) {
                Surface(color = WarningContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        report.layerNotices.forEach { notice ->
                            Text("• $notice", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
                        }
                    }
                }
            }

            // Cadastre IGN
            Surface(color = IvoryWarm, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Apartment, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(16.dp))
                        Text("Référence Cadastrale (API Carto IGN)", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                    }
                    val p = report.parcel
                    if (p == null) {
                        Text("Aucune parcelle retournée sur ce point précis.", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    } else {
                        Text("ID Parcelle : ${p.idParcelle}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Section ${p.section.ifBlank { "—" }} • Numéro ${p.numero.ifBlank { "—" }} • Commune ${p.communeName.ifBlank { p.communeCode }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (p.contenanceM2 > 0) {
                            Text("Contenance cadastrale : ${p.contenanceM2} m²", style = MaterialTheme.typography.bodySmall, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Zonage PLU / PLUi
            Surface(color = IvoryWarm, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Zonage PLU / PLUi (Géoportail de l'Urbanisme)", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                    if (report.pluZones.isEmpty()) {
                        Text("Aucune zone PLU retournée.", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    } else {
                        report.pluZones.forEach { z ->
                            Text(
                                text = "Zone ${z.libelle} (${z.typeZone}) — ${z.libelleLong}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ObsidianInk
                            )
                            if (z.destDomi.isNotBlank()) {
                                Text("Destination dominante : ${z.destDomi}", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                            }
                            if (z.urlDoc.startsWith("http", ignoreCase = true)) {
                                OutlinedButton(onClick = { onOpenUrl(z.urlDoc) }, shape = RoundedCornerShape(8.dp)) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Règlement écrit de la zone", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Prescriptions PLU
            if (report.prescriptions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Prescriptions PLU (${report.prescriptions.size})", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                    report.prescriptions.take(8).forEach { pr ->
                        Surface(color = WarningContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("[${pr.geometryKind}] ${pr.libelle}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ObsidianInk)
                                if (pr.txt.isNotBlank() && pr.txt != pr.libelle) {
                                    Text(pr.txt, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                }
                            }
                        }
                    }
                }
            }

            // Servitudes SUP
            if (report.servitudes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Servitudes d'Utilité Publique — SUP (${report.servitudes.size})", style = MaterialTheme.typography.labelLarge, color = ErrorRed, fontWeight = FontWeight.Bold)
                    report.servitudes.take(6).forEach { sup ->
                        Surface(color = ErrorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(sup.nomSup, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ErrorRed)
                                val meta = listOf(sup.categorie, sup.gestionnaire).filter { it.isNotBlank() }.joinToString(" • ")
                                if (meta.isNotBlank()) {
                                    Text(meta, style = MaterialTheme.typography.bodySmall, color = ObsidianInk)
                                }
                            }
                        }
                    }
                }
            }

            // Risques Géorisques
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Risques naturels & technologiques (Géorisques)", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                if (report.risks.isEmpty()) {
                    Surface(color = SuccessContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Aucun risque GASPAR majeur répertorié sur la coordonnée.", style = MaterialTheme.typography.bodySmall, color = SuccessGreen, modifier = Modifier.padding(10.dp))
                    }
                } else {
                    report.risks.take(10).forEach { r ->
                        Surface(color = WarningContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                Text(r.libelle, style = MaterialTheme.typography.bodySmall, color = ObsidianInk)
                            }
                        }
                    }
                }
            }

            // Documents PLU associés
            if (report.pluDocuments.isNotEmpty()) {
                HorizontalDivider(color = BorderSubtle)
                Text("Documents d'urbanisme associés", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep, fontWeight = FontWeight.Bold)
                report.pluDocuments.take(5).forEach { doc ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${doc.typeDoc} • ${doc.status}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            if (doc.dateApprobation.isNotBlank()) {
                                Text("Approbation : ${doc.dateApprobation}", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                            }
                        }
                        if (doc.urlFic.startsWith("http", ignoreCase = true)) {
                            OutlinedButton(onClick = { onOpenUrl(doc.urlFic) }) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ouvrir", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Mention légale obligatoire
            Surface(color = IvoryWarm, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(16.dp))
                    Text(
                        text = UrbanismCadastreService.URBANISM_LEGAL_DISCLAIMER,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMuted
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygonRings(
    rings: List<GeoPolygonRing>,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float,
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    w: Float,
    h: Float
) {
    rings.forEach { ring ->
        if (ring.points.size < 3) return@forEach
        val path = Path()
        ring.points.forEachIndexed { idx, pt: GeoLatLon ->
            val offset = latLonToCanvas(pt.latitude, pt.longitude, centerLat, centerLon, zoom, w, h)
            if (idx == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        path.close()
        drawPath(path = path, color = fillColor)
        drawPath(path = path, color = strokeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

private fun lonToWorldX(lon: Double, worldScale: Double): Double =
    ((lon + 180.0) / 360.0) * worldScale

private fun latToWorldY(lat: Double, worldScale: Double): Double {
    val clamped = lat.coerceIn(-85.0511, 85.0511)
    val rad = Math.toRadians(clamped)
    val n = ln(tan(rad) + 1.0 / cos(rad))
    return (1.0 - n / PI) / 2.0 * worldScale
}

private fun worldXToLon(wx: Double, worldScale: Double): Double =
    (wx / worldScale) * 360.0 - 180.0

private fun worldYToLat(wy: Double, worldScale: Double): Double {
    val n = PI - (2.0 * PI * wy) / worldScale
    return Math.toDegrees(atan(sinh(n)))
}

private fun latLonToCanvas(
    lat: Double,
    lon: Double,
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    w: Float,
    h: Float
): Offset {
    val worldScale = 256.0 * 2.0.pow(zoom)
    val cx = lonToWorldX(centerLon, worldScale)
    val cy = latToWorldY(centerLat, worldScale)
    val px = lonToWorldX(lon, worldScale)
    val py = latToWorldY(lat, worldScale)
    return Offset((w / 2.0 + (px - cx)).toFloat(), (h / 2.0 + (py - cy)).toFloat())
}

private fun canvasToLatLon(
    x: Float,
    y: Float,
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    w: Float,
    h: Float
): Pair<Double, Double> {
    val worldScale = 256.0 * 2.0.pow(zoom)
    val cx = lonToWorldX(centerLon, worldScale)
    val cy = latToWorldY(centerLat, worldScale)
    val wx = cx + (x - w / 2.0)
    val wy = cy + (y - h / 2.0)
    return worldYToLat(wy, worldScale) to worldXToLon(wx, worldScale)
}

private fun metersToCanvasPixels(meters: Double, lat: Double, zoom: Int): Float {
    val metersPerPixel = 156543.03392 * cos(Math.toRadians(lat)) / 2.0.pow(zoom)
    return (meters / metersPerPixel).toFloat().coerceIn(8f, 600f)
}

private fun computeVisibleTileCoords(
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    widthPx: Float,
    heightPx: Float
): List<Pair<Int, Int>> {
    val worldScale = 256.0 * 2.0.pow(zoom)
    val maxTile = (1 shl zoom) - 1
    val cx = lonToWorldX(centerLon, worldScale)
    val cy = latToWorldY(centerLat, worldScale)
    val minX = floor((cx - widthPx / 2.0) / 256.0).toInt().coerceIn(0, maxTile)
    val maxX = floor((cx + widthPx / 2.0) / 256.0).toInt().coerceIn(0, maxTile)
    val minY = floor((cy - heightPx / 2.0) / 256.0).toInt().coerceIn(0, maxTile)
    val maxY = floor((cy + heightPx / 2.0) / 256.0).toInt().coerceIn(0, maxTile)
    return buildList {
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                add(x to y)
            }
        }
    }
}
