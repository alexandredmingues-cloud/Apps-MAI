package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.MarketBriefingSnapshot
import com.example.data.MarketCountry
import com.example.data.MarketInfoAction
import com.example.data.MarketInfoCategory
import com.example.data.MontyMarketData
import com.example.data.MortgageRateRow
import com.example.data.RealEstateMarketBriefingProvider
import com.example.data.RealEstateNewsItem
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldMid
import com.example.ui.theme.EmeraldSurface
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryCard
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

@Composable
fun RealEstateHomeInfoSection(
    onOpenHcsfSimulator: () -> Unit,
    onOpenMarketObservatory: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedCountry by rememberSaveable { mutableStateOf(MarketCountry.FRANCE) }
    var selectedCityId by rememberSaveable { mutableStateOf("lyon") }
    val snapshot = remember(selectedCountry, selectedCityId) {
        RealEstateMarketBriefingProvider.buildSnapshot(
            country = selectedCountry,
            cityId = selectedCityId
        )
    }
    var liveEcbRate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        liveEcbRate = RealEstateMarketBriefingProvider.fetchLiveEcbDepositRatePercent()
    }

    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(MarketInfoCategory.ALL) }
    var selectedNewsItem by remember { mutableStateOf<RealEstateNewsItem?>(null) }
    var showAllDialog by remember { mutableStateOf(false) }

    val filteredItems = remember(snapshot.items, selectedCategory) {
        if (selectedCategory == MarketInfoCategory.ALL) {
            snapshot.items
        } else {
            snapshot.items.filter { it.category == selectedCategory }
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = IvoryCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
            .testTag("home_real_estate_info_section")
    ) {
        Column {
            // En-tête Émeraude + Bandeau Taux & Baromètre Montpellier
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(EmeraldDeep, EmeraldMid)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(GoldAntique.copy(alpha = 0.2f))
                                    .border(1.dp, GoldAntique.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = GoldChampagne,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Baromètre & Lois Immobilières",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IvoryWarm,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = snapshot.updatedLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldChampagne
                                    )
                                    if (liveEcbRate != null) {
                                        Surface(
                                            color = EmeraldSurface,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "BCE $liveEcbRate",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IvoryWarm,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showAllDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("market_see_all_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Tout voir",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoldChampagne,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = { isExpanded = !isExpanded },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("market_toggle_collapse_button")
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) {
                                        "Réduire les informations immobilières"
                                    } else {
                                        "Afficher les informations immobilières"
                                    },
                                    tint = IvoryWarm
                                )
                            }
                        }
                    }

                    // Sélecteur Multi-Pays (France entière / Portugal isolé) + Villes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MarketCountry.entries.forEach { c ->
                            val isSelected = selectedCountry == c
                            Surface(
                                color = if (isSelected) GoldAntique else EmeraldSurface.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedCountry = c
                                        selectedCityId = if (c == MarketCountry.PORTUGAL) "lisboa" else "lyon"
                                    }
                                    .testTag("market_country_${c.code.lowercase()}")
                            ) {
                                Text(
                                    text = "${c.flagEmoji} ${c.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) EmeraldDeep else IvoryWarm,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                        snapshot.availableCities.forEach { city ->
                            val citySelected = snapshot.selectedCity.id == city.id
                            Surface(
                                color = if (citySelected) IvoryWarm else EmeraldSurface.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedCityId = city.id }
                                    .testTag("market_city_${city.id}")
                            ) {
                                Text(
                                    text = city.cityName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (citySelected) EmeraldDeep else IvoryWarm.copy(alpha = 0.9f),
                                    fontWeight = if (citySelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Bandeau synthétique des taux & marché local (cliquable)
                    Surface(
                        color = EmeraldSurface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GoldAntique.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showAllDialog = true }
                            .testTag("market_rates_strip")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (snapshot.country == MarketCountry.PORTUGAL) {
                                    RateHeaderCell(label = "Taxa 15a", value = "3,10 %", highlight = false)
                                    RateHeaderCell(label = "Taxa 20a", value = "3,25 %", highlight = true)
                                    RateHeaderCell(label = "LTV HPP", value = "90 %", highlight = false)
                                    RateHeaderCell(label = "Cert.", value = "ADENE", highlight = false)
                                } else {
                                    RateHeaderCell(label = "15 ans", value = "3,15 %", highlight = false)
                                    RateHeaderCell(label = "20 ans", value = "3,30 %", highlight = true)
                                    RateHeaderCell(label = "25 ans", value = "3,45 %", highlight = false)
                                    RateHeaderCell(label = "HCSF max", value = "35 %", highlight = false)
                                }
                            }
                            HorizontalDivider(color = IvoryWarm.copy(alpha = 0.14f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${snapshot.selectedCity.cityName} : ~${MontyMarketData.formatEuro(snapshot.selectedCity.avgAptM2.toLong())}/m² (Appt) • ${snapshot.selectedCity.topDynamicSectorLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IvoryWarm.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Ouvrir le baromètre complet",
                                    tint = GoldChampagne,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Contenu dépliable : filtres par catégorie + carrousel éditorial
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MarketInfoCategory.entries.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = {
                                    Text(
                                        text = cat.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = categoryIcon(cat),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDeep,
                                    selectedLabelColor = IvoryWarm,
                                    selectedLeadingIconColor = GoldChampagne,
                                    containerColor = IvoryWarm,
                                    labelColor = ObsidianInk,
                                    iconColor = EmeraldMid
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = BorderSubtle,
                                    selectedBorderColor = EmeraldDeep
                                ),
                                modifier = Modifier.testTag("market_category_chip_${cat.name.lowercase()}")
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            MarketNewsCarouselCard(
                                item = item,
                                onClick = { selectedNewsItem = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal détail d'une actualité / loi / tendance
    selectedNewsItem?.let { item ->
        RealEstateNewsDetailDialog(
            item = item,
            onDismiss = { selectedNewsItem = null },
            onOpenHcsfSimulator = {
                selectedNewsItem = null
                onOpenHcsfSimulator()
            },
            onOpenMarketObservatory = onOpenMarketObservatory?.let { openObservatory ->
                {
                    selectedNewsItem = null
                    openObservatory()
                }
            },
            onOpenFullBriefing = {
                selectedNewsItem = null
                showAllDialog = true
            }
        )
    }

    // Modal "Tout voir" : grille des taux + baromètre Montpellier + toutes les lois & actus
    if (showAllDialog) {
        RealEstateFullBriefingDialog(
            snapshot = snapshot,
            liveEcbRate = liveEcbRate,
            onDismiss = { showAllDialog = false },
            onSelectNews = { item ->
                showAllDialog = false
                selectedNewsItem = item
            },
            onOpenHcsfSimulator = {
                showAllDialog = false
                onOpenHcsfSimulator()
            },
            onOpenMarketObservatory = onOpenMarketObservatory?.let { openObservatory ->
                {
                    showAllDialog = false
                    openObservatory()
                }
            }
        )
    }
}

@Composable
private fun RateHeaderCell(
    label: String,
    value: String,
    highlight: Boolean
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = IvoryWarm.copy(alpha = 0.72f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = if (highlight) GoldChampagne else IvoryWarm,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MarketNewsCarouselCard(
    item: RealEstateNewsItem,
    onClick: () -> Unit
) {
    val accent = categoryAccentColor(item.category)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IvoryWarm),
        modifier = Modifier
            .width(284.dp)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("market_news_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = categoryIcon(item.category),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = item.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = item.keyStatValue,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = ObsidianInk,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.shortSummary,
                style = MaterialTheme.typography.bodySmall,
                color = SlateMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(color = BorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.publishedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Lire l'analyse",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = EmeraldDeep,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RealEstateNewsDetailDialog(
    item: RealEstateNewsItem,
    onDismiss: () -> Unit,
    onOpenHcsfSimulator: () -> Unit,
    onOpenMarketObservatory: (() -> Unit)?,
    onOpenFullBriefing: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = IvoryCard,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .heightIn(max = 680.dp)
                .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
                .testTag("market_detail_dialog")
        ) {
            Column {
                // Header émeraude
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(EmeraldDeep, EmeraldMid)))
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = GoldAntique.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = item.badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldChampagne,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("market_detail_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fermer l'analyse",
                                    tint = IvoryWarm
                                )
                            }
                        }

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryWarm,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            color = EmeraldSurface,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.keyStatLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IvoryWarm.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = item.keyStatValue,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GoldChampagne,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Corps scrollable
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = item.fullAnalysis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ObsidianInk
                    )

                    Surface(
                        color = SuccessContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Impact Acquéreur",
                                style = MaterialTheme.typography.labelMedium,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.impactBuyer,
                                style = MaterialTheme.typography.bodySmall,
                                color = ObsidianInk
                            )
                        }
                    }

                    Surface(
                        color = WarningContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Impact Vendeur & Conseiller",
                                style = MaterialTheme.typography.labelMedium,
                                color = WarningAmber,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.impactSellerAdvisor,
                                style = MaterialTheme.typography.bodySmall,
                                color = ObsidianInk
                            )
                        }
                    }

                    Text(
                        text = "Source : ${item.sourceRef} • ${item.publishedLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMuted
                    )
                }

                HorizontalDivider(color = BorderSubtle)

                // Pied de modale avec action contextuelle réelle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Fermer")
                    }
                    when (item.actionType) {
                        MarketInfoAction.OPEN_HCSF_SIMULATOR -> {
                            Button(
                                onClick = onOpenHcsfSimulator,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("market_detail_cta_hcsf")
                            ) {
                                Text(item.actionType.ctaLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        MarketInfoAction.OPEN_MARKET_OBSERVATORY -> {
                            Button(
                                onClick = {
                                    if (onOpenMarketObservatory != null) {
                                        onOpenMarketObservatory()
                                    } else {
                                        onOpenFullBriefing()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("market_detail_cta_observatory")
                            ) {
                                Text(
                                    text = if (onOpenMarketObservatory != null) {
                                        item.actionType.ctaLabel
                                    } else {
                                        "Voir les repères complets"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        MarketInfoAction.NONE -> {
                            Button(
                                onClick = onOpenFullBriefing,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMid),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("market_detail_cta_all")
                            ) {
                                Text("Tout le baromètre")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealEstateFullBriefingDialog(
    snapshot: MarketBriefingSnapshot,
    liveEcbRate: String?,
    onDismiss: () -> Unit,
    onSelectNews: (RealEstateNewsItem) -> Unit,
    onOpenHcsfSimulator: () -> Unit,
    onOpenMarketObservatory: (() -> Unit)?
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = IvoryWarm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .widthIn(max = 600.dp)
                .heightIn(max = 720.dp)
                .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
                .testTag("market_all_dialog")
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(EmeraldDeep, EmeraldMid)))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "INFORMATIONS IMMOBILIÈRES",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldChampagne
                            )
                            Text(
                                text = "Taux, Marché & Lois en vigueur",
                                style = MaterialTheme.typography.titleLarge,
                                color = IvoryWarm,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("market_all_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer le baromètre complet",
                                tint = IvoryWarm
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Grille complète des taux immobiliers
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = IvoryCard),
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
                                    text = "Barème indicatif des taux fixes",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldDeep,
                                    fontWeight = FontWeight.Bold
                                )
                                if (liveEcbRate != null) {
                                    Surface(
                                        color = SuccessContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Taux dépôt BCE : $liveEcbRate",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Durée", style = MaterialTheme.typography.labelSmall, color = SlateMuted, modifier = Modifier.weight(1f))
                                Text("Moyen", style = MaterialTheme.typography.labelSmall, color = SlateMuted, modifier = Modifier.weight(1f))
                                Text("Excellent", style = MaterialTheme.typography.labelSmall, color = SlateMuted, modifier = Modifier.weight(1f))
                                Text("Tendance", style = MaterialTheme.typography.labelSmall, color = SlateMuted, modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider(color = BorderSubtle)

                            snapshot.mortgageRates.forEach { row ->
                                MortgageRateTableLine(row)
                            }

                            Surface(
                                color = IvoryWarm,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Usure : ${snapshot.usuryRate20PlusLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ObsidianInk,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "HCSF : ${snapshot.hcsfCapLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldDeep,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = onOpenHcsfSimulator,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("market_all_hcsf_button")
                            ) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Simuler ma capacité HCSF 35%")
                            }
                        }
                    }

                    // Repères locaux multi-villes France & Portugal
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = IvoryCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Tendance ${snapshot.selectedCity.cityName} (${snapshot.selectedCity.regionLabel})",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldDeep,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = IvoryWarm,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Appartement médian", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                                        Text(
                                            text = "${MontyMarketData.formatEuro(snapshot.selectedCity.avgAptM2.toLong())}/m²",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = EmeraldDeep,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Surface(
                                    color = IvoryWarm,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Maison médiane", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                                        Text(
                                            text = "${MontyMarketData.formatEuro(snapshot.selectedCity.avgHouseM2.toLong())}/m²",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = EmeraldDeep,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Secteur dynamique : ${snapshot.selectedCity.topDynamicSectorLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = snapshot.legalDisclaimer,
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateMuted
                            )
                            if (onOpenMarketObservatory != null && snapshot.country == MarketCountry.FRANCE) {
                                OutlinedButton(
                                    onClick = onOpenMarketObservatory,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("market_all_observatory_button")
                                ) {
                                    Text("Ouvrir l'Observatoire complet")
                                }
                            }
                        }
                    }

                    // Toutes les actualités, lois et aides
                    Text(
                        text = "Lois, fiscalité, DPE & tendances (${snapshot.items.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Bold
                    )

                    snapshot.items.forEach { item ->
                        Surface(
                            color = IvoryCard,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectNews(item) }
                                .testTag("market_all_item_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(categoryAccentColor(item.category).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = categoryIcon(item.category),
                                        contentDescription = null,
                                        tint = categoryAccentColor(item.category),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = item.badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = categoryAccentColor(item.category),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ObsidianInk,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = item.shortSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Lire l'article ${item.title}",
                                    tint = EmeraldDeep,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MortgageRateTableLine(row: MortgageRateRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.durationLabel,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = ObsidianInk,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = row.averageRateLabel,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = EmeraldDeep,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = row.bestProfileRateLabel,
            style = MaterialTheme.typography.bodySmall,
            color = SuccessGreen,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = row.trendLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (row.isDownwardTrend) SuccessGreen else SlateMuted,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun categoryIcon(category: MarketInfoCategory): ImageVector = when (category) {
    MarketInfoCategory.ALL -> Icons.AutoMirrored.Filled.TrendingUp
    MarketInfoCategory.RATES -> Icons.Default.Percent
    MarketInfoCategory.MARKET -> Icons.Default.LocationCity
    MarketInfoCategory.LAWS -> Icons.Default.Gavel
    MarketInfoCategory.ENERGY_AID -> Icons.Default.EnergySavingsLeaf
}

private fun categoryAccentColor(category: MarketInfoCategory): Color = when (category) {
    MarketInfoCategory.ALL -> EmeraldDeep
    MarketInfoCategory.RATES -> EmeraldMid
    MarketInfoCategory.MARKET -> GoldAntique
    MarketInfoCategory.LAWS -> WarningAmber
    MarketInfoCategory.ENERGY_AID -> SuccessGreen
}
