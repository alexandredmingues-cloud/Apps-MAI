package com.example.ui.buyer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.data.GeoCommuteService
import com.example.data.GeocodedAddressResult
import com.example.data.MontyMarketData
import com.example.data.PropertyCommuteEstimate
import com.example.data.SearchPerimeterMode
import com.example.data.TransportMode
import com.example.data.model.BuyerChatMessage
import com.example.data.model.BuyerTab
import com.example.data.model.ClientDossier
import com.example.data.model.CompatibilityResult
import com.example.data.model.CriteriaOptimizationSuggestion
import com.example.data.model.FinanceMetrics
import com.example.data.model.HcsfCapacityResult
import com.example.data.model.PropertyAiInsight
import com.example.data.model.PropertyFeedback
import com.example.data.model.PropertyItem
import com.example.data.model.SellerPropertyDossier
import com.example.data.model.VisitRecord
import com.example.data.model.WorkspaceMode
import com.example.ui.AiGenState
import com.example.ui.AutoAiUiState
import com.example.ui.SimulatorInput
import com.example.ui.components.AiRelevanceInsightStrip
import com.example.ui.components.BuyerAutoAiBanner
import com.example.ui.components.CompatibilityBadgeRow
import com.example.ui.components.DpeBadge
import com.example.ui.components.PropertyEditorialHeader
import com.example.ui.components.RealEstateHomeInfoSection
import com.example.ui.components.WorkspaceSwitcherBar
import com.example.ui.components.sharePropertyWithPartner
import kotlin.math.roundToInt
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldMid
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuyerPortalScreen(
    client: ClientDossier,
    properties: List<PropertyItem>,
    visits: List<VisitRecord>,
    sellerDossiers: List<SellerPropertyDossier> = emptyList(),
    buyerChats: List<BuyerChatMessage>,
    activeBuyerTab: BuyerTab,
    buyerAiState: AiGenState,
    aiAutoEnhanceEnabled: Boolean,
    autoAiState: AutoAiUiState,
    simulatorInput: SimulatorInput,
    financeMetrics: FinanceMetrics,
    hcsfCapacityResult: HcsfCapacityResult,
    onSelectTab: (BuyerTab) -> Unit,
    onSwitchMode: (WorkspaceMode) -> Unit,
    onOpenPdfPreview: () -> Unit,
    onOpenCapacityScreen: () -> Unit,
    onToggleAutoAi: (Boolean) -> Unit,
    onRefreshAutoAi: () -> Unit,
    onApplyAiOptimization: (CriteriaOptimizationSuggestion) -> Unit,
    onToggleFavorite: (PropertyItem) -> Unit,
    onUpdateFeedback: (propertyId: String, rating: Long, status: String, comment: String) -> Unit,
    onSubmitVisitRequest: (property: PropertyItem, date: String, slot: String, message: String) -> Unit,
    onSaveCriteria: (
        budgetMax: Long,
        downPayment: Long,
        sectors: List<String>,
        propertyType: String,
        minSurface: Long,
        minRooms: Long,
        essentials: List<String>,
        delai: String,
        note: String,
        searchCenterAddress: String,
        searchCenterLat: Double,
        searchCenterLon: Double,
        searchMode: String,
        searchRadiusKm: Int,
        searchMaxTravelMinutes: Int,
        searchTransportMode: String,
        rgpdConsentGranted: Boolean
    ) -> Unit,
    onSearchBanAddresses: suspend (String) -> Result<List<GeocodedAddressResult>> = { Result.success(emptyList()) },
    onUpdateRgpdConsent: (Boolean) -> Unit = {},
    onBuildFullRgpdExportJson: () -> String? = { null },
    onEraseClientRgpdData: () -> Unit = {},
    onAskAssistant: (question: String, topic: String) -> Unit,
    onClearChat: () -> Unit,
    onUpdateSimulator: ((SimulatorInput) -> SimulatorInput) -> Unit,
    onLoadPropertyInSimulator: (PropertyItem) -> Unit,
    onSubmitBuyerDocOrder: (SellerPropertyDossier, PropertyItem?, String, String, Boolean) -> Unit = { _, _, _, _, _ -> },
    onShowToast: (String) -> Unit = {},
    onSignOut: () -> Unit
) {
    BackHandler {
        if (activeBuyerTab != BuyerTab.EXPLORER) {
            onSelectTab(BuyerTab.EXPLORER)
        } else {
            onSwitchMode(WorkspaceMode.ADVISOR)
        }
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var visitRequestProperty by remember { mutableStateOf<PropertyItem?>(null) }
    var detailProperty by remember { mutableStateOf<Pair<PropertyItem, CompatibilityResult>?>(null) }
    var filterSector by remember { mutableStateOf("Tous") }
    var filterOnlyBudget by remember { mutableStateOf(false) }
    var filterOnlyPerimeter by remember(client.id, client.searchMode) {
        mutableStateOf(client.searchMode != SearchPerimeterMode.SECTOR_LIST.code)
    }
    var swipeMode by remember { mutableStateOf(false) }
    var swipeIndex by remember { mutableIntStateOf(0) }

    val aiInsights = (autoAiState as? AutoAiUiState.Ready)?.result?.propertyInsights.orEmpty()
    val hasSpatialOrigin = client.searchMode != SearchPerimeterMode.SECTOR_LIST.code &&
        (client.searchCenterLat != 0.0 || client.searchCenterLon != 0.0)

    val scoredProperties = remember(properties, client, aiAutoEnhanceEnabled, aiInsights) {
        properties
            .filter { p -> p.publicationStatus.isBlank() || p.publicationStatus == "PUBLIEE" }
            .map { prop ->
                val compat = MontyMarketData.computeCompatibility(prop, client)
                val insight = aiInsights[prop.id]
                val effectiveScore = if (aiAutoEnhanceEnabled && insight != null) {
                    (compat.score * 0.45 + insight.aiRelevanceScore * 0.55).roundToInt()
                } else {
                    compat.score
                }
                Triple(prop, compat.copy(score = effectiveScore), insight)
            }
            .sortedByDescending { it.second.score }
    }

    val commuteByPropId = remember(scoredProperties, client, hasSpatialOrigin) {
        if (!hasSpatialOrigin) emptyMap()
        else {
            val mode = SearchPerimeterMode.fromCode(client.searchMode)
            val transport = TransportMode.fromCode(client.searchTransportMode)
            scoredProperties.associate { (prop, _, _) ->
                prop.id to GeoCommuteService.evaluatePropertyAccess(
                    originLat = client.searchCenterLat,
                    originLon = client.searchCenterLon,
                    originLabel = client.searchCenterAddress.ifBlank { "Point cible" },
                    property = prop,
                    transportMode = transport,
                    searchMode = mode,
                    maxRadiusKm = client.searchRadiusKm,
                    maxTravelMinutes = client.searchMaxTravelMinutes
                )
            }
        }
    }

    val favoriteProperties = remember(scoredProperties, client.selectedPropertyIds) {
        scoredProperties
            .filter { (prop, _) -> client.selectedPropertyIds.contains(prop.id) }
            .map { it.first }
    }

    val clientVisits = remember(visits, client.id) {
        visits.filter { it.clientId == client.id && it.sharedWithClient }
    }

    val clientChats = remember(buyerChats, client.id) {
        buyerChats.filter { it.clientId == client.id }
    }

    fun estimateMonthlyFor(prop: PropertyItem): Long {
        return MontyMarketData.computeFinanceMetrics(
            price = prop.price,
            downPayment = client.downPayment,
            notaryRatePercent = simulatorInput.notaryRatePercent,
            durationYears = simulatorInput.durationYears,
            interestRatePercent = simulatorInput.interestRatePercent,
            insuranceRatePercent = simulatorInput.insuranceRatePercent,
            monthlyIncome = client.monthlyIncome,
            existingCredits = client.existingCredits,
            monthlyCharges = prop.monthlyCharges + (prop.propertyTax / 12L)
        ).monthlyInclInsurance
    }

    Scaffold(
        containerColor = IvoryWarm,
        topBar = {
            Column {
                WorkspaceSwitcherBar(
                    currentMode = WorkspaceMode.BUYER,
                    activeClient = client,
                    onSwitchMode = onSwitchMode,
                    onOpenDossierPdf = onOpenPdfPreview,
                    onSignOut = onSignOut
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldDeep)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ESPACE PRIVÉ ACQUÉREUR",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldChampagne
                            )
                            Text(
                                text = client.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = IvoryWarm
                            )
                            Text(
                                text = "${client.propertyType} • ≥${client.minSurface} m² • ${client.minRooms} ch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IvoryWarm.copy(alpha = 0.72f)
                            )
                        }
                        Surface(
                            color = EmeraldMid,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .border(1.dp, GoldAntique.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenCapacityScreen)
                                .testTag("buyer_header_hcsf_shortcut")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("Budget max • Simuler HCSF", style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.7f))
                                Text(
                                    MontyMarketData.formatEuro(client.budgetMax),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GoldChampagne,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                BuyerTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        BuyerTab.EXPLORER -> Icons.Default.Explore
                        BuyerTab.SELECTION -> Icons.Default.Favorite
                        BuyerTab.CRITERIA -> Icons.Default.Tune
                        BuyerTab.DOSSIER -> Icons.Default.FolderShared
                    }
                    NavigationBarItem(
                        selected = activeBuyerTab == tab,
                        onClick = { onSelectTab(tab) },
                        icon = { Icon(icon, contentDescription = tab.label) },
                        label = {
                            Text(
                                text = if (tab == BuyerTab.SELECTION) "${tab.label} (${favoriteProperties.size})" else tab.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldDeep,
                            selectedTextColor = EmeraldDeep,
                            indicatorColor = GoldAntique.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.testTag("buyer_tab_${tab.key}")
                    )
                }
            }
        }
    ) { innerPadding ->
        if (!client.portalActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(36.dp))
                        Text("Portail temporairement désactivé", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Votre conseiller a suspendu ce lien d'accès. Contactez votre conseiller ou réactivez-le depuis l'Espace Pro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (activeBuyerTab) {
                BuyerTab.EXPLORER -> {
                    val displayed = scoredProperties.filter { (p, _, _) ->
                        val comm = commuteByPropId[p.id]
                        (filterSector == "Tous" || p.sector == filterSector) &&
                            (!filterOnlyBudget || p.price <= client.budgetMax) &&
                            (!filterOnlyPerimeter || comm == null || comm.withinPerimeter)
                    }

                    item(key = "home_real_estate_briefing") {
                        RealEstateHomeInfoSection(
                            onOpenHcsfSimulator = onOpenCapacityScreen,
                            onOpenMarketObservatory = null
                        )
                    }

                    item {
                        BuyerAutoAiBanner(
                            autoAiEnabled = aiAutoEnhanceEnabled,
                            autoAiState = autoAiState,
                            onToggleAutoAi = onToggleAutoAi,
                            onRefresh = onRefreshAutoAi,
                            onApplyOptimization = onApplyAiOptimization
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Biens qualifiés (${displayed.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EmeraldDeep
                                    )
                                    // Switch Vue Liste / Match Swipe Flash
                                    Surface(
                                        color = IvoryWarm,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                    ) {
                                        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            ModePill(
                                                label = "Liste",
                                                icon = Icons.Default.ViewAgenda,
                                                selected = !swipeMode,
                                                onClick = { swipeMode = false },
                                                tag = "explorer_mode_list"
                                            )
                                            ModePill(
                                                label = "Match",
                                                icon = Icons.Default.Style,
                                                selected = swipeMode,
                                                onClick = {
                                                    swipeIndex = 0
                                                    swipeMode = true
                                                },
                                                tag = "explorer_mode_swipe"
                                            )
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val sectorOptions = listOf("Tous") + MontyMarketData.SECTEURS_MONTPELLIER.map { it.name }.take(5)
                                    sectorOptions.forEach { sec ->
                                        FilterChip(
                                            selected = filterSector == sec,
                                            onClick = {
                                                filterSector = sec
                                                swipeIndex = 0
                                            },
                                            label = { Text(sec.substringBefore(" ("), style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = EmeraldDeep,
                                                selectedLabelColor = GoldChampagne
                                            )
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        filterOnlyBudget = !filterOnlyBudget
                                        swipeIndex = 0
                                    }
                                ) {
                                    Checkbox(
                                        checked = filterOnlyBudget,
                                        onCheckedChange = {
                                            filterOnlyBudget = it
                                            swipeIndex = 0
                                        }
                                    )
                                    Text(
                                        "Uniquement dans mon budget (≤ ${MontyMarketData.formatEuro(client.budgetMax)})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (hasSpatialOrigin) {
                                    val modeObj = SearchPerimeterMode.fromCode(client.searchMode)
                                    val transportObj = TransportMode.fromCode(client.searchTransportMode)
                                    val periSummary = if (modeObj == SearchPerimeterMode.RADIUS_KM) {
                                        "Dans mon rayon ≤ ${client.searchRadiusKm} km (${client.searchCenterAddress.ifBlank { "Adresse" }})"
                                    } else {
                                        "≤ ${client.searchMaxTravelMinutes} min en ${transportObj.label} (${client.searchCenterAddress.ifBlank { "Adresse" }})"
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            filterOnlyPerimeter = !filterOnlyPerimeter
                                            swipeIndex = 0
                                        }
                                    ) {
                                        Checkbox(
                                            checked = filterOnlyPerimeter,
                                            onCheckedChange = {
                                                filterOnlyPerimeter = it
                                                swipeIndex = 0
                                            }
                                        )
                                        Text(periSummary, style = MaterialTheme.typography.bodySmall, color = EmeraldDeep, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    if (swipeMode && displayed.isNotEmpty()) {
                        item {
                            val safeIndex = swipeIndex % displayed.size
                            val (prop, compat, aiInsight) = displayed[safeIndex]
                            val monthlyEst = estimateMonthlyFor(prop)
                            val isFav = client.selectedPropertyIds.contains(prop.id)

                            AnimatedContent(targetState = prop.id, label = "swipe_deck") { _ ->
                                BuyerSwipeMatchCard(
                                    property = prop,
                                    compat = compat,
                                    aiInsight = aiInsight,
                                    commuteEstimate = commuteByPropId[prop.id],
                                    monthlyEst = monthlyEst,
                                    positionLabel = "${safeIndex + 1} / ${displayed.size}",
                                    isFavorite = isFav,
                                    onOpenDetails = { detailProperty = prop to compat },
                                    onPass = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        swipeIndex = (safeIndex + 1) % displayed.size
                                    },
                                    onShare = {
                                        sharePropertyWithPartner(context, prop, compat.score, monthlyEst)
                                    },
                                    onLove = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!isFav) onToggleFavorite(prop)
                                        swipeIndex = (safeIndex + 1) % displayed.size
                                    }
                                )
                            }
                        }
                    } else {
                        items(displayed, key = { it.first.id }) { (prop, compat, aiInsight) ->
                            val isFav = client.selectedPropertyIds.contains(prop.id)
                            val monthlyEst = estimateMonthlyFor(prop)
                            BuyerPropertyCard(
                                property = prop,
                                compat = compat,
                                aiInsight = aiInsight,
                                commuteEstimate = commuteByPropId[prop.id],
                                monthlyEstimate = monthlyEst,
                                isFavorite = isFav,
                                onOpenDetails = { detailProperty = prop to compat },
                                onShare = { sharePropertyWithPartner(context, prop, compat.score, monthlyEst) },
                                onToggleFavorite = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleFavorite(prop)
                                },
                                onRequestVisit = { visitRequestProperty = prop }
                            )
                        }
                    }
                }

                BuyerTab.SELECTION -> {
                    if (favoriteProperties.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(36.dp))
                                    Text("Votre shortlist est vide", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        "Ajoutez des biens depuis Explorer (ou le mode Match) pour les noter, comparer et partager avec votre conjoint.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                    Button(
                                        onClick = { onSelectTab(BuyerTab.EXPLORER) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
                                    ) {
                                        Text("Explorer les annonces")
                                    }
                                }
                            }
                        }
                    } else {
                        // Comparateur Express quand >= 2 favoris
                        if (favoriteProperties.size >= 2) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.5.dp, GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = GoldAntique)
                                            Text(
                                                "Comparateur Express de Votre Shortlist",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = EmeraldDeep
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            favoriteProperties.forEach { fp ->
                                                val c = MontyMarketData.computeCompatibility(fp, client)
                                                val m2 = if (fp.surface > 0) fp.price / fp.surface else 0L
                                                val monthly = estimateMonthlyFor(fp)
                                                Surface(
                                                    color = IvoryWarm,
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier
                                                        .width(205.dp)
                                                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                                                        .clickable { detailProperty = fp to c }
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(fp.title, style = MaterialTheme.typography.titleSmall, color = EmeraldDeep, maxLines = 2)
                                                        Text("Match ${c.score}/100", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                                                        HorizontalDivider(color = BorderSubtle)
                                                        Text(MontyMarketData.formatEuro(fp.price), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                        Text("~${MontyMarketData.formatEuro(monthly)}/mois", style = MaterialTheme.typography.bodySmall, color = EmeraldDeep)
                                                        Text("${MontyMarketData.formatEuro(m2)}/m² • ${fp.surface} m² • ${fp.bedrooms} ch.", style = MaterialTheme.typography.bodySmall)
                                                        Text("DPE ${fp.dpe} • Charges ${fp.monthlyCharges}€/m", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        items(favoriteProperties, key = { it.id }) { prop ->
                            val fb = client.propertyFeedback[prop.id] ?: PropertyFeedback()
                            val compat = MontyMarketData.computeCompatibility(prop, client)
                            val monthlyEst = estimateMonthlyFor(prop)
                            BuyerSelectionItemCard(
                                property = prop,
                                score = compat.score,
                                monthlyEstimate = monthlyEst,
                                feedback = fb,
                                onOpenDetails = { detailProperty = prop to compat },
                                onShare = { sharePropertyWithPartner(context, prop, compat.score, monthlyEst) },
                                onRemove = { onToggleFavorite(prop) },
                                onRequestVisit = { visitRequestProperty = prop },
                                onSimulate = {
                                    onLoadPropertyInSimulator(prop)
                                    onSelectTab(BuyerTab.DOSSIER)
                                },
                                onSaveFeedback = { rating, status, comment ->
                                    onUpdateFeedback(prop.id, rating, status, comment)
                                }
                            )
                        }
                    }
                }

                BuyerTab.CRITERIA -> {
                    item {
                        BuyerCriteriaEditorCard(
                            client = client,
                            onOpenCapacityScreen = onOpenCapacityScreen,
                            onSearchBanAddresses = onSearchBanAddresses,
                            onSaveCriteria = onSaveCriteria
                        )
                    }
                }

                BuyerTab.DOSSIER -> {
                    item {
                        BuyerDossierAndAiTab(
                            client = client,
                            favoriteProperties = favoriteProperties,
                            allProperties = properties,
                            clientVisits = clientVisits,
                            sellerDossiers = sellerDossiers,
                            chatMessages = clientChats,
                            buyerAiState = buyerAiState,
                            simulatorInput = simulatorInput,
                            financeMetrics = financeMetrics,
                            hcsfCapacityResult = hcsfCapacityResult,
                            onAskAssistant = onAskAssistant,
                            onClearChat = onClearChat,
                            onUpdateSimulator = onUpdateSimulator,
                            onOpenCapacityScreen = onOpenCapacityScreen,
                            onOpenPdfPreview = onOpenPdfPreview,
                            onSubmitBuyerDocOrder = onSubmitBuyerDocOrder,
                            onUpdateRgpdConsent = onUpdateRgpdConsent,
                            onBuildFullRgpdExportJson = onBuildFullRgpdExportJson,
                            onEraseClientRgpdData = onEraseClientRgpdData,
                            onShowToast = onShowToast
                        )
                    }
                }
            }
        }
    }

    detailProperty?.let { (prop, compat) ->
        val matchedDossier = sellerDossiers.firstOrNull { it.propertyId == prop.id }
        BuyerPropertyDetailDialog(
            property = prop,
            client = client,
            compat = compat,
            simulatorInput = simulatorInput,
            isFavorite = client.selectedPropertyIds.contains(prop.id),
            sellerDossier = matchedDossier,
            aiInsight = if (aiAutoEnhanceEnabled) aiInsights[prop.id] else null,
            onDismiss = { detailProperty = null },
            onToggleFavorite = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleFavorite(prop)
            },
            onRequestVisit = { visitRequestProperty = prop },
            onSubmitBuyerDocOrder = onSubmitBuyerDocOrder,
            onShowToast = onShowToast
        )
    }

    visitRequestProperty?.let { prop ->
        VisitRequestDialog(
            property = prop,
            onDismiss = { visitRequestProperty = null },
            onSubmit = { date, slot, msg ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSubmitVisitRequest(prop, date, slot, msg)
                visitRequestProperty = null
            }
        )
    }
}

@Composable
private fun ModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        color = if (selected) EmeraldDeep else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) GoldChampagne else SlateMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) IvoryWarm else ObsidianInk
            )
        }
    }
}

@Composable
private fun BuyerSwipeMatchCard(
    property: PropertyItem,
    compat: CompatibilityResult,
    aiInsight: PropertyAiInsight?,
    commuteEstimate: PropertyCommuteEstimate? = null,
    monthlyEst: Long,
    positionLabel: String,
    isFavorite: Boolean,
    onOpenDetails: () -> Unit,
    onPass: () -> Unit,
    onShare: () -> Unit,
    onLove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenDetails)
            .testTag("swipe_match_card_${property.id}")
    ) {
        Column {
            PropertyEditorialHeader(
                property = property,
                score = compat.score,
                height = 240.dp,
                topTag = "DÉCOUVERTE $positionLabel • ${property.sector.uppercase()}"
            )
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = MontyMarketData.formatEuro(property.price),
                            style = MaterialTheme.typography.headlineMedium,
                            color = EmeraldDeep,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "≈ ${MontyMarketData.formatEuro(monthlyEst)} / mois • ${property.surface} m² • ${property.bedrooms} ch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldAntique,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    DpeBadge(dpe = property.dpe)
                }

                if (aiInsight != null) {
                    AiRelevanceInsightStrip(insight = aiInsight)
                }

                if (commuteEstimate != null) {
                    Surface(
                        color = if (commuteEstimate.withinPerimeter) IvoryWarm else ErrorRed.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DirectionsTransit, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(15.dp))
                            Text(
                                text = "${commuteEstimate.badgeLabel} depuis ${commuteEstimate.originLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldDeep,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                CompatibilityBadgeRow(badges = compat.badges)

                Text(
                    text = property.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianInk.copy(alpha = 0.85f),
                    maxLines = 3
                )

                OutlinedButton(
                    onClick = onOpenDetails,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Voir fiche complète & simuler une offre", style = MaterialTheme.typography.labelLarge)
                }

                // Barre d'actions virales Passer / Partager / Coup de Cœur
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = IvoryWarm,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(58.dp)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onPass)
                            .testTag("swipe_pass_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Passer ce bien", tint = SlateMuted, modifier = Modifier.size(26.dp))
                        }
                    }

                    Surface(
                        color = IvoryWarm,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(54.dp)
                            .border(1.dp, GoldAntique.copy(alpha = 0.6f), CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onShare)
                            .testTag("swipe_share_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, contentDescription = "Partager avec mes proches", tint = EmeraldDeep, modifier = Modifier.size(22.dp))
                        }
                    }

                    Surface(
                        color = EmeraldDeep,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(64.dp)
                            .border(2.dp, GoldAntique, CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onLove)
                            .testTag("swipe_love_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = if (isFavorite) "Déjà favori — Suivant" else "Coup de cœur",
                                tint = GoldChampagne,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuyerPropertyCard(
    property: PropertyItem,
    compat: CompatibilityResult,
    aiInsight: PropertyAiInsight?,
    commuteEstimate: PropertyCommuteEstimate? = null,
    monthlyEstimate: Long,
    isFavorite: Boolean,
    onOpenDetails: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestVisit: () -> Unit
) {
    val heartScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fav_heart_scale"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isFavorite) 1.5.dp else 1.dp,
                color = if (isFavorite) GoldAntique else BorderSubtle,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onOpenDetails)
            .testTag("buyer_property_card_${property.id}")
    ) {
        Column {
            PropertyEditorialHeader(
                property = property,
                score = compat.score,
                height = 185.dp
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = MontyMarketData.formatEuro(property.price),
                            style = MaterialTheme.typography.headlineSmall,
                            color = EmeraldDeep
                        )
                        val priceM2 = if (property.surface > 0) property.price / property.surface else 0L
                        Text(
                            text = "${MontyMarketData.formatEuro(priceM2)}/m² • ${property.surface} m² • ${property.bedrooms} ch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                        Text(
                            text = "Mensualité est. : ~${MontyMarketData.formatEuro(monthlyEstimate)}/mois",
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldAntique
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DpeBadge(dpe = property.dpe)
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("share_property_${property.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Partager avec conjoint",
                                tint = EmeraldDeep
                            )
                        }
                    }
                }

                if (aiInsight != null) {
                    AiRelevanceInsightStrip(insight = aiInsight)
                }

                if (commuteEstimate != null || property.saleDelaySummary.isNotBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        commuteEstimate?.let { est ->
                            Surface(color = IvoryWarm, shape = RoundedCornerShape(8.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.DirectionsTransit, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(13.dp))
                                    Text(est.badgeLabel, style = MaterialTheme.typography.labelSmall, color = EmeraldDeep, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (property.saleDelaySummary.isNotBlank()) {
                            Surface(color = IvoryWarm, shape = RoundedCornerShape(8.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = GoldAntique, modifier = Modifier.size(13.dp))
                                    Text("Vente est. : ${property.saleDelaySummary}", style = MaterialTheme.typography.labelSmall, color = GoldAntique)
                                }
                            }
                        }
                    }
                }

                CompatibilityBadgeRow(badges = compat.badges)

                Text(
                    text = property.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianInk.copy(alpha = 0.82f),
                    maxLines = 3
                )

                val reason = compat.reasons.firstOrNull()
                val alert = compat.alerts.firstOrNull()
                if (reason != null || alert != null) {
                    Surface(
                        color = IvoryWarm,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (reason != null) {
                                Text("✓ $reason", style = MaterialTheme.typography.bodySmall, color = EmeraldDeep)
                            }
                            if (alert != null) {
                                Text("! $alert", style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) GoldAntique else EmeraldDeep,
                            modifier = Modifier.size(16.dp).scale(heartScale)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFavorite) "Sélectionné" else "Sélectionner",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Button(
                        onClick = onRequestVisit,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAntique, contentColor = ObsidianInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Visiter", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuyerSelectionItemCard(
    property: PropertyItem,
    score: Int,
    monthlyEstimate: Long,
    feedback: PropertyFeedback,
    onOpenDetails: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onRequestVisit: () -> Unit,
    onSimulate: () -> Unit,
    onSaveFeedback: (Long, String, String) -> Unit
) {
    var rating by remember(property.id, feedback.rating) { mutableLongStateOf(feedback.rating) }
    var status by remember(property.id, feedback.status) { mutableStateOf(feedback.status) }
    var comment by remember(property.id, feedback.comment) { mutableStateOf(feedback.comment) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
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
                Text(
                    text = "${property.sector} • $score/100",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldDeep
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Partager ce favori", tint = EmeraldDeep, modifier = Modifier.size(18.dp))
                    }
                    TextButton(onClick = onRemove) {
                        Text("Retirer", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                text = property.title,
                style = MaterialTheme.typography.titleLarge,
                color = EmeraldDeep,
                modifier = Modifier.clickable(onClick = onOpenDetails)
            )
            Text(
                "${MontyMarketData.formatEuro(property.price)} (~${MontyMarketData.formatEuro(monthlyEstimate)}/m) • ${property.surface} m² • ${property.bedrooms} ch. • DPE ${property.dpe}",
                style = MaterialTheme.typography.bodySmall,
                color = SlateMuted
            )

            Surface(
                color = IvoryWarm,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Votre note", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                        Row {
                            (1L..5L).forEach { s ->
                                IconButton(
                                    onClick = { rating = s },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Note $s étoiles",
                                        tint = if (s <= rating) GoldAntique else BorderSubtle,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Coup de cœur", "Intéressant", "À écarter").forEach { opt ->
                            FilterChip(
                                selected = status == opt,
                                onClick = { status = opt },
                                label = { Text(opt, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Impressions pour votre conseiller") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSaveFeedback(rating, status, comment) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Enregistrer avis", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = onSimulate) {
                            Text("Simuler", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = onRequestVisit) {
                            Text("Visiter", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuyerCriteriaEditorCard(
    client: ClientDossier,
    onOpenCapacityScreen: () -> Unit,
    onSearchBanAddresses: suspend (String) -> Result<List<GeocodedAddressResult>>,
    onSaveCriteria: (
        budgetMax: Long,
        downPayment: Long,
        sectors: List<String>,
        propertyType: String,
        minSurface: Long,
        minRooms: Long,
        essentials: List<String>,
        delai: String,
        note: String,
        searchCenterAddress: String,
        searchCenterLat: Double,
        searchCenterLon: Double,
        searchMode: String,
        searchRadiusKm: Int,
        searchMaxTravelMinutes: Int,
        searchTransportMode: String,
        rgpdConsentGranted: Boolean
    ) -> Unit
) {
    val scope = rememberCoroutineScope()
    var budgetMax by remember(client.id) { mutableLongStateOf(client.budgetMax) }
    var downPayment by remember(client.id) { mutableLongStateOf(client.downPayment) }
    var selectedSectors by remember(client.id) { mutableStateOf(client.sectors) }
    var propertyType by remember(client.id) { mutableStateOf(client.propertyType) }
    var minSurface by remember(client.id) { mutableLongStateOf(client.minSurface) }
    var minRooms by remember(client.id) { mutableLongStateOf(client.minRooms) }
    var essentials by remember(client.id) { mutableStateOf(client.essentialCriteria) }
    var delai by remember(client.id) { mutableStateOf(client.timelineDelai) }
    var note by remember(client.id) { mutableStateOf(client.clientMessageToAdvisor) }

    var searchMode by remember(client.id) { mutableStateOf(SearchPerimeterMode.fromCode(client.searchMode)) }
    var searchCenterAddress by remember(client.id) { mutableStateOf(client.searchCenterAddress) }
    var searchCenterLat by remember(client.id) { mutableStateOf(client.searchCenterLat) }
    var searchCenterLon by remember(client.id) { mutableStateOf(client.searchCenterLon) }
    var searchRadiusKm by remember(client.id) { mutableIntStateOf(client.searchRadiusKm.coerceIn(1, 30)) }
    var searchMaxTravelMinutes by remember(client.id) { mutableIntStateOf(client.searchMaxTravelMinutes.coerceIn(5, 60)) }
    var searchTransportMode by remember(client.id) { mutableStateOf(TransportMode.fromCode(client.searchTransportMode)) }
    var banSuggestions by remember { mutableStateOf<List<GeocodedAddressResult>>(emptyList()) }
    var rgpdConsentGranted by remember(client.id) { mutableStateOf(client.rgpdConsentGranted) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Ajuster Mes Critères de Recherche",
                style = MaterialTheme.typography.headlineSmall,
                color = EmeraldDeep
            )
            Text(
                text = "Chaque mise à jour recalcule les compatibilités et alerte immédiatement votre conseiller.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateMuted
            )

            // Préréglages intelligents 1-tap
            Text("Profils rapides", style = MaterialTheme.typography.labelLarge, color = GoldAntique)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = false,
                    onClick = {
                        budgetMax = 450000L
                        propertyType = "Appartement"
                        minSurface = 88L
                        minRooms = 3L
                        selectedSectors = listOf("Ovalie (34070)", "Croix d'Argent (34070)", "Mas Drevon (34070)")
                        essentials = listOf("Terrasse / Extérieur", "Parking / Garage", "Proche Tramway", "3 chambres minimum")
                    },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    label = { Text("Famille 3 ch. + Tram", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        budgetMax = 690000L
                        propertyType = "Appartement"
                        minSurface = 105L
                        minRooms = 3L
                        selectedSectors = listOf("Port Marianne (34000)", "Antigone (34000)", "Les Arceaux (34000)")
                        essentials = listOf("Terrasse / Extérieur", "Dernier étage", "Ascenseur", "Parking / Garage")
                    },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    label = { Text("Dernier Étage Vue", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        budgetMax = 590000L
                        propertyType = "Villa"
                        minSurface = 115L
                        minRooms = 4L
                        selectedSectors = listOf("Baillargues (34670)", "Juvignac (34990)", "Hôpitaux-Facultés (34090)")
                        essentials = listOf("Calme absolu", "DPE A, B ou C", "Parking / Garage", "Terrasse / Extérieur")
                    },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    label = { Text("Villa Calme & Jardin", style = MaterialTheme.typography.labelSmall) }
                )
            }

            Text(
                text = "Budget max : ${MontyMarketData.formatEuro(budgetMax)}",
                style = MaterialTheme.typography.titleMedium,
                color = EmeraldDeep
            )
            Slider(
                value = budgetMax.toFloat(),
                onValueChange = { budgetMax = (it / 5000f).toLong() * 5000L },
                valueRange = 200000f..1200000f,
                colors = SliderDefaults.colors(thumbColor = GoldAntique, activeTrackColor = EmeraldDeep)
            )

            OutlinedButton(
                onClick = onOpenCapacityScreen,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("criteria_open_hcsf_button")
            ) {
                Text(
                    text = "Estimer ma capacité d'emprunt HCSF 35%",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldDeep,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = downPayment.toString(),
                onValueChange = { v -> v.toLongOrNull()?.let { downPayment = it } },
                label = { Text("Apport personnel (€)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = BorderSubtle)

            // PÉRIMÈTRE GÉOGRAPHIQUE : QUARTIERS, RAYON KM OU TEMPS DE TRAJET MULTIMODAL
            Text("Zone de recherche & Mobilité", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchPerimeterMode.entries.forEach { mode ->
                    FilterChip(
                        selected = searchMode == mode,
                        onClick = { searchMode = mode },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldDeep,
                            selectedLabelColor = GoldChampagne
                        )
                    )
                }
            }

            if (searchMode != SearchPerimeterMode.SECTOR_LIST) {
                OutlinedTextField(
                    value = searchCenterAddress,
                    onValueChange = { q ->
                        searchCenterAddress = q
                        if (q.trim().length >= 3) {
                            scope.launch {
                                onSearchBanAddresses(q).onSuccess { banSuggestions = it }
                            }
                        } else {
                            banSuggestions = emptyList()
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Adresse point de départ (travail, gare, école...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("criteria_center_address_input")
                )

                if (banSuggestions.isNotEmpty()) {
                    Surface(
                        color = IvoryWarm,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            banSuggestions.forEach { sugg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchCenterAddress = sugg.label
                                            searchCenterLat = sugg.latitude
                                            searchCenterLon = sugg.longitude
                                            banSuggestions = emptyList()
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(15.dp))
                                    Text(sugg.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Raccourcis pôles de Montpellier
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GeoCommuteService.MONTPELLIER_HUBS.forEach { hub ->
                        FilterChip(
                            selected = searchCenterAddress.contains(hub.label, ignoreCase = true),
                            onClick = {
                                searchCenterAddress = hub.queryHint
                                searchCenterLat = hub.latitude
                                searchCenterLon = hub.longitude
                                banSuggestions = emptyList()
                            },
                            label = { Text(hub.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Mode de déplacement", style = MaterialTheme.typography.labelMedium, color = EmeraldDeep)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TransportMode.entries.forEach { tm ->
                        FilterChip(
                            selected = searchTransportMode == tm,
                            onClick = { searchTransportMode = tm },
                            label = { Text(tm.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                    }
                }

                if (searchMode == SearchPerimeterMode.RADIUS_KM) {
                    Text(
                        text = "Distance maximale : $searchRadiusKm km",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldDeep
                    )
                    Slider(
                        value = searchRadiusKm.toFloat(),
                        onValueChange = { searchRadiusKm = it.roundToInt().coerceIn(1, 30) },
                        valueRange = 1f..30f,
                        colors = SliderDefaults.colors(thumbColor = GoldAntique, activeTrackColor = EmeraldDeep)
                    )
                } else {
                    Text(
                        text = "Temps de trajet max : $searchMaxTravelMinutes min (${searchTransportMode.label})",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldDeep
                    )
                    Slider(
                        value = searchMaxTravelMinutes.toFloat(),
                        onValueChange = { searchMaxTravelMinutes = it.roundToInt().coerceIn(5, 60) },
                        valueRange = 5f..60f,
                        colors = SliderDefaults.colors(thumbColor = GoldAntique, activeTrackColor = EmeraldDeep)
                    )
                }
            }

            Text("Secteurs ciblés", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MontyMarketData.SECTEURS_MONTPELLIER.forEach { sec ->
                    val active = selectedSectors.contains(sec.name)
                    FilterChip(
                        selected = active,
                        onClick = {
                            selectedSectors = if (active) selectedSectors - sec.name else selectedSectors + sec.name
                        },
                        label = { Text(sec.name.substringBefore(" ("), style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldDeep,
                            selectedLabelColor = GoldChampagne
                        )
                    )
                }
            }

            Text("Type de bien", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MontyMarketData.OPTIONS_TYPES_BIEN.forEach { type ->
                    FilterChip(
                        selected = propertyType == type,
                        onClick = { propertyType = type },
                        label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minSurface.toString(),
                    onValueChange = { v -> v.toLongOrNull()?.let { minSurface = it } },
                    label = { Text("Surface min (m²)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minRooms.toString(),
                    onValueChange = { v -> v.toLongOrNull()?.let { minRooms = it } },
                    label = { Text("Chambres min") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Critères indispensables", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MontyMarketData.OPTIONS_CRITERES_ESSENTIELS.forEach { crit ->
                    val active = essentials.contains(crit)
                    FilterChip(
                        selected = active,
                        onClick = {
                            essentials = if (active) essentials - crit else essentials + crit
                        },
                        label = { Text(crit, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Précisions pour votre conseiller") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { rgpdConsentGranted = !rgpdConsentGranted }
            ) {
                Checkbox(checked = rgpdConsentGranted, onCheckedChange = { rgpdConsentGranted = it })
                Text(
                    "J'accepte le traitement RGPD de mes critères pour recevoir des biens adaptés.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    val resolvedLatLon = if (searchMode != SearchPerimeterMode.SECTOR_LIST && searchCenterLat == 0.0 && searchCenterAddress.isNotBlank()) {
                        GeoCommuteService.resolveOfflineAddressFallback(searchCenterAddress)?.let { it.latitude to it.longitude }
                    } else null

                    onSaveCriteria(
                        budgetMax,
                        downPayment,
                        selectedSectors,
                        propertyType,
                        minSurface,
                        minRooms,
                        essentials,
                        delai,
                        note,
                        searchCenterAddress,
                        resolvedLatLon?.first ?: searchCenterLat,
                        resolvedLatLon?.second ?: searchCenterLon,
                        searchMode.code,
                        searchRadiusKm,
                        searchMaxTravelMinutes,
                        searchTransportMode.code,
                        rgpdConsentGranted
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_buyer_criteria_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enregistrer & Recalculer", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun VisitRequestDialog(
    property: PropertyItem,
    onDismiss: () -> Unit,
    onSubmit: (date: String, slot: String, message: String) -> Unit
) {
    var date by remember { mutableStateOf("2026-11-25") }
    var slot by remember { mutableStateOf("Fin de journée (17h30 - 19h00)") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demander une visite", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(property.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date souhaitée (AAAA-MM-JJ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Créneau préférentiel", style = MaterialTheme.typography.labelLarge)
                    listOf(
                        "Matinée (09h30 - 12h00)",
                        "Après-midi (14h30 - 17h00)",
                        "Fin de journée (17h30 - 19h00)",
                        "Samedi matin"
                    ).forEach { opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { slot = opt }
                                .padding(vertical = 4.dp)
                        ) {
                            Surface(
                                color = if (slot == opt) EmeraldDeep else IvoryWarm,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = (if (slot == opt) "✓ " else "") + opt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (slot == opt) GoldChampagne else ObsidianInk,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message pour le conseiller") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(date, slot, message) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
            ) {
                Text("Envoyer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
