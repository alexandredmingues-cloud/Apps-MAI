package com.example.ui.buyer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.MontyMarketData
import androidx.compose.runtime.mutableStateOf
import com.example.data.model.ClientDossier
import com.example.data.model.CompatibilityResult
import com.example.data.model.PropertyAiInsight
import com.example.data.model.PropertyItem
import com.example.data.model.SellerPropertyDossier
import com.example.ui.SimulatorInput
import com.example.ui.components.AiRelevanceInsightStrip
import com.example.ui.components.CompatibilityBadgeRow
import com.example.ui.components.DpeBadge
import com.example.ui.components.PropertyEditorialHeader
import com.example.ui.components.computeSectorMarketDelta
import com.example.ui.components.sharePropertyWithPartner
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldMid
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuyerPropertyDetailDialog(
    property: PropertyItem,
    client: ClientDossier,
    compat: CompatibilityResult,
    simulatorInput: SimulatorInput,
    isFavorite: Boolean,
    sellerDossier: SellerPropertyDossier? = null,
    aiInsight: PropertyAiInsight? = null,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestVisit: () -> Unit,
    onSubmitBuyerDocOrder: (SellerPropertyDossier, PropertyItem?, String, String, Boolean) -> Unit = { _, _, _, _, _ -> },
    onShowToast: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showDocSummaryDialog by remember { mutableStateOf(false) }
    var showDocCheckoutDialog by remember { mutableStateOf(false) }
    var discountPercent by remember(property.id, aiInsight?.suggestedDiscountPercent) {
        mutableFloatStateOf((aiInsight?.suggestedDiscountPercent ?: 0).toFloat())
    }
    val roundedDiscount = discountPercent.roundToInt()
    val simulatedOfferPrice = ((property.price * (100 - roundedDiscount)) / 100.0).roundToLong()
    val savings = property.price - simulatedOfferPrice

    val simulatedFinance = remember(simulatedOfferPrice, client, simulatorInput) {
        MontyMarketData.computeFinanceMetrics(
            price = simulatedOfferPrice,
            downPayment = client.downPayment,
            notaryRatePercent = simulatorInput.notaryRatePercent,
            durationYears = simulatorInput.durationYears,
            interestRatePercent = simulatorInput.interestRatePercent,
            insuranceRatePercent = simulatorInput.insuranceRatePercent,
            monthlyIncome = client.monthlyIncome,
            existingCredits = client.existingCredits,
            monthlyCharges = property.monthlyCharges + (property.propertyTax / 12L)
        )
    }

    val delta = remember(property) { computeSectorMarketDelta(property) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 18.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                // Header photo + bouton fermer + partager
                androidx.compose.foundation.layout.Box {
                    PropertyEditorialHeader(
                        property = property,
                        score = compat.score,
                        height = 200.dp,
                        topTag = "${property.sector} • ${property.type}".uppercase()
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 56.dp, end = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = ObsidianInk.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.size(44.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    sharePropertyWithPartner(
                                        context = context,
                                        property = property,
                                        score = compat.score,
                                        estimatedMonthlyEuro = simulatedFinance.monthlyInclInsurance
                                    )
                                },
                                modifier = Modifier.testTag("detail_share_property_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Partager ce bien", tint = GoldChampagne)
                            }
                        }
                        Surface(
                            color = ObsidianInk.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.size(44.dp)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_property_detail_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer la fiche", tint = IvoryWarm)
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Prix & caractéristiques
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = MontyMarketData.formatEuro(property.price),
                                style = MaterialTheme.typography.headlineSmall,
                                color = EmeraldDeep
                            )
                            Text(
                                text = "${MontyMarketData.formatEuro(delta.propertyM2.toLong())}/m² • ${property.surface} m² • ${property.rooms} p. (${property.bedrooms} ch.)",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMuted
                            )
                        }
                        DpeBadge(dpe = property.dpe)
                    }

                    CompatibilityBadgeRow(badges = compat.badges)

                    if (aiInsight != null) {
                        AiRelevanceInsightStrip(insight = aiInsight)
                        if (aiInsight.negotiationArgument.isNotBlank()) {
                            Surface(
                                color = IvoryWarm,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = GoldAntique,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Stratégie négo IA : ${aiInsight.negotiationArgument}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EmeraldDeep
                                    )
                                }
                            }
                        }
                    }

                    // Benchmark Observatoire Montpellier
                    Surface(
                        color = IvoryWarm,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "REPÈRE OBSERVATOIRE MONTPELLIER",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldAntique
                            )
                            Text(
                                text = "Ce bien : ${MontyMarketData.formatEuro(delta.propertyM2.toLong())}/m²  vs  Moyenne secteur : ${MontyMarketData.formatEuro(delta.sectorAvgM2.toLong())}/m²",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldDeep
                            )
                            Text(
                                text = "Charges : ${property.monthlyCharges} €/mois • Taxe foncière : ${MontyMarketData.formatEuro(property.propertyTax)}/an",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMuted
                            )
                            Text(
                                text = if (property.hideExactAddress) {
                                    "Localisation : ${property.sector} (adresse exacte masquée avant prise de rendez-vous)"
                                } else {
                                    "Localisation : ${property.address.ifBlank { property.sector }}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldDeep
                            )
                            if (property.saleDelaySummary.isNotBlank()) {
                                Text(
                                    text = "Délai de vente estimé : ${property.saleDelaySummary}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoldAntique,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Simulateur d'offre & mensualité interactif
                    Surface(
                        color = EmeraldDeep,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                Text(
                                    text = "SIMULATEUR D'OFFRE INSTANTANÉ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldChampagne
                                )
                                Text(
                                    text = if (roundedDiscount == 0) "Prix affiché" else "-$roundedDiscount% (${MontyMarketData.formatEuro(savings)} éco.)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6EE7B7)
                                )
                            }
                            Slider(
                                value = discountPercent,
                                onValueChange = { discountPercent = it },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = GoldChampagne,
                                    activeTrackColor = GoldAntique,
                                    inactiveTrackColor = EmeraldMid
                                ),
                                modifier = Modifier.testTag("offer_negotiation_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Offre testée", style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.7f))
                                    Text(
                                        MontyMarketData.formatEuro(simulatedOfferPrice),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = IvoryWarm,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Mensualité est.", style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.7f))
                                    Text(
                                        "${MontyMarketData.formatEuro(simulatedFinance.monthlyInclInsurance)}/m",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = GoldChampagne,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Endettement", style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.7f))
                                    Text(
                                        "${simulatedFinance.debtRatioPercent}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (simulatedFinance.debtRatioPercent <= 35.0) Color(0xFF6EE7B7) else Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text(property.description, style = MaterialTheme.typography.bodySmall, color = ObsidianInk)

                    // Points forts & vigilance
                    if (property.strengths.isNotEmpty()) {
                        Surface(color = SuccessContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                    Text("Atouts majeurs", style = MaterialTheme.typography.labelLarge, color = SuccessGreen)
                                }
                                property.strengths.forEach { s ->
                                    Text("• $s", style = MaterialTheme.typography.bodySmall, color = ObsidianInk)
                                }
                            }
                        }
                    }

                    if (property.weaknesses.isNotEmpty()) {
                        Surface(color = WarningContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                    Text("Points à vérifier en visite", style = MaterialTheme.typography.labelLarge, color = WarningAmber)
                                }
                                property.weaknesses.forEach { w ->
                                    Text("• $w", style = MaterialTheme.typography.bodySmall, color = ObsidianInk)
                                }
                            }
                        }
                    }

                    // Quartier & transports
                    Surface(
                        color = IvoryWarm,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.DirectionsTransit, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(16.dp))
                                Text("Vie de quartier & Mobilité", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                            }
                            Text(property.transports, style = MaterialTheme.typography.bodySmall)
                            Text(property.schoolsShops, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                        }
                    }

                    BuyerPropertyDocsSectionCard(
                        property = property,
                        dossier = sellerDossier,
                        client = client,
                        onOpenSummary = { showDocSummaryDialog = true },
                        onOpenCheckoutOrPack = { showDocCheckoutDialog = true }
                    )
                }

                HorizontalDivider(color = BorderSubtle)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isFavorite) "Retenu" else "Favori", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = {
                            onDismiss()
                            onRequestVisit()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f).height(48.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Visiter ce bien", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (showDocSummaryDialog && sellerDossier != null) {
        BuyerDocumentSummaryDialog(
            dossier = sellerDossier,
            property = property,
            isUnlocked = sellerDossier.isUnlockedForBuyer(client.id),
            onDismiss = { showDocSummaryDialog = false },
            onUpgradeToPaidPack = { showDocCheckoutDialog = true }
        )
    }

    if (showDocCheckoutDialog && sellerDossier != null) {
        BuyerDocumentCheckoutAndPackDialog(
            dossier = sellerDossier,
            property = property,
            client = client,
            onDismiss = { showDocCheckoutDialog = false },
            onSubmitOrder = { email, method, markPaid ->
                onSubmitBuyerDocOrder(sellerDossier, property, email, method, markPaid)
            },
            onShowToast = onShowToast
        )
    }
}
