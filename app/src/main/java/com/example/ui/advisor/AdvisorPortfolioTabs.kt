package com.example.ui.advisor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.MontyMarketData
import com.example.data.model.ClientDossier
import com.example.data.model.OfferRecord
import com.example.data.model.PropertyItem
import com.example.data.model.VisitRecord
import com.example.ui.components.CompatibilityBadgeRow
import com.example.ui.components.DpeBadge
import com.example.ui.components.PropertyEditorialHeader
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvisorPropertiesTab(
    properties: List<PropertyItem>,
    activeClient: ClientDossier?,
    comparePropertyIds: List<String>,
    onToggleCompare: (String) -> Unit,
    onSimulateProperty: (PropertyItem) -> Unit,
    onOpenSellerDossier: (PropertyItem) -> Unit = {},
    onValidatePropertyPublication: (PropertyItem) -> Unit = {},
    onOpenPropertyOnMap: (PropertyItem) -> Unit = {},
    onOpenNewPropertyDialog: () -> Unit
) {
    var searchTerm by remember { mutableStateOf("") }
    var sectorFilter by remember { mutableStateOf("Tous") }

    val scored = remember(properties, activeClient, searchTerm, sectorFilter) {
        properties
            .filter { p ->
                val matchesSearch = searchTerm.isBlank() ||
                    p.title.contains(searchTerm, ignoreCase = true) ||
                    p.sector.contains(searchTerm, ignoreCase = true) ||
                    p.description.contains(searchTerm, ignoreCase = true)
                val matchesSector = sectorFilter == "Tous" || p.sector == sectorFilter
                matchesSearch && matchesSector
            }
            .map { it to MontyMarketData.computeCompatibility(it, activeClient) }
            .sortedByDescending { it.second.score }
    }

    val comparedProps = remember(properties, comparePropertyIds) {
        properties.filter { comparePropertyIds.contains(it.id) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Recherche & filtres
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Catalogue Biens (${scored.size})", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
                    Button(
                        onClick = onOpenNewPropertyDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("open_new_property_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajouter bien", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = { searchTerm = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Rechercher quartier, titre, terrasse...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("Tous") + MontyMarketData.SECTEURS_MONTPELLIER.take(5).map { it.name }).forEach { sec ->
                        FilterChip(
                            selected = sectorFilter == sec,
                            onClick = { sectorFilter = sec },
                            label = { Text(sec.substringBefore(" ("), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            )
                        )
                    }
                }
            }
        }

        // Comparateur côte-à-côte
        if (comparedProps.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = GoldAntique)
                        Text(
                            "Comparateur Multi-Biens (${comparedProps.size}/4)",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDeep
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        comparedProps.forEach { p ->
                            val compat = MontyMarketData.computeCompatibility(p, activeClient)
                            val priceM2 = if (p.surface > 0) p.price / p.surface else 0L
                            Surface(
                                color = IvoryWarm,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .width(210.dp)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(p.title, style = MaterialTheme.typography.titleSmall, color = EmeraldDeep, maxLines = 2)
                                    Text(p.sector.substringBefore(" ("), style = MaterialTheme.typography.labelSmall, color = GoldAntique)
                                    HorizontalDivider(color = BorderSubtle)
                                    Text("Prix : ${MontyMarketData.formatEuro(p.price)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Prix/m² : ${MontyMarketData.formatEuro(priceM2)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Surface : ${p.surface} m² • ${p.bedrooms} ch.", style = MaterialTheme.typography.bodySmall)
                                    Text("DPE ${p.dpe} • Charges ${p.monthlyCharges}€/m", style = MaterialTheme.typography.bodySmall)
                                    Text("TF : ${MontyMarketData.formatEuro(p.propertyTax)}/an", style = MaterialTheme.typography.bodySmall)
                                    Text("Score client : ${compat.score}/100", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Liste des biens
        scored.forEach { (prop, compat) ->
            val isCompared = comparePropertyIds.contains(prop.id)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        if (isCompared) 1.5.dp else 1.dp,
                        if (isCompared) GoldAntique else BorderSubtle,
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column {
                    PropertyEditorialHeader(
                        property = prop,
                        score = compat.score,
                        height = 160.dp,
                        topTag = prop.listingStatus.uppercase()
                    )
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(MontyMarketData.formatEuro(prop.price), style = MaterialTheme.typography.headlineSmall, color = EmeraldDeep)
                                val m2 = if (prop.surface > 0) prop.price / prop.surface else 0L
                                Text("${MontyMarketData.formatEuro(m2)}/m² • ${prop.surface} m² • ${prop.bedrooms} ch.", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            }
                            DpeBadge(dpe = prop.dpe)
                        }
                        CompatibilityBadgeRow(badges = compat.badges)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = if (prop.publicationStatus == "PUBLIEE") SuccessContainer else WarningContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${prop.publisherType} • ${prop.publicationStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (prop.publicationStatus == "PUBLIEE") SuccessGreen else WarningAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (prop.mandateType.isNotBlank() || prop.mandateRef.isNotBlank()) {
                                Surface(color = IvoryWarm, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        text = "Mandat ${prop.mandateType} ${prop.mandateRef}".trim(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldDeep,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (prop.saleDelaySummary.isNotBlank()) {
                                Surface(color = IvoryWarm, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        text = "Délai est. : ${prop.saleDelaySummary}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldAntique,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Text(prop.description, style = MaterialTheme.typography.bodySmall, color = ObsidianInk.copy(alpha = 0.8f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onToggleCompare(prop.id) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isCompared) "Comparé ✓" else "+ Comparer", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { onSimulateProperty(prop) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simuler prêt", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onOpenSellerDossier(prop) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(Icons.Default.HomeWork, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Vendeur / ALUR", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { onOpenPropertyOnMap(prop) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("advisor_prop_map_${prop.id}")
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Urbanisme", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(
                            onClick = { onValidatePropertyPublication(prop) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("advisor_moderate_prop_${prop.id}")
                        ) {
                            Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (prop.publicationStatus == "PUBLIEE") "Repasser en modération (masquer acquéreurs)" else "Valider & Publier côté Acquéreur",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvisorVisitsTab(
    visits: List<VisitRecord>,
    clients: List<ClientDossier>,
    properties: List<PropertyItem>,
    onOpenNewVisitDialog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Visites & Comptes Rendus (${visits.size})", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
            Button(
                onClick = onOpenNewVisitDialog,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("open_new_visit_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nouvelle visite", style = MaterialTheme.typography.labelSmall)
            }
        }

        visits.forEach { visit ->
            val client = clients.firstOrNull { it.id == visit.clientId }
            val prop = properties.firstOrNull { it.id == visit.propertyId }
            val isClientRequest = visit.status.contains("Demande", ignoreCase = true)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        if (isClientRequest) 1.5.dp else 1.dp,
                        if (isClientRequest) WarningAmber else BorderSubtle,
                        RoundedCornerShape(18.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${visit.date} • ${visit.timeSlot}", style = MaterialTheme.typography.labelLarge, color = GoldAntique)
                        Surface(
                            color = when {
                                visit.status == "Réalisée" -> SuccessContainer
                                isClientRequest -> WarningContainer
                                else -> IvoryWarm
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = visit.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    visit.status == "Réalisée" -> SuccessGreen
                                    isClientRequest -> WarningAmber
                                    else -> EmeraldDeep
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(prop?.title ?: "Bien immobilier", style = MaterialTheme.typography.titleMedium, color = EmeraldDeep)
                    Text(
                        "Client : ${client?.name ?: visit.clientId}" + (visit.overallRating?.let { " • Note : $it/10" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                    if (visit.clientImpressions.isNotBlank()) {
                        Text(visit.clientImpressions, style = MaterialTheme.typography.bodySmall)
                    }
                    if (visit.positives.isNotBlank()) {
                        Text("✓ Points positifs : ${visit.positives}", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                    }
                    if (visit.negatives.isNotBlank()) {
                        Text("! Réserves : ${visit.negatives}", style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                    }
                    if (visit.nextAction.isNotBlank()) {
                        Surface(color = IvoryWarm, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Prochaine action : ${visit.nextAction}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldDeep,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvisorOffersTab(
    offers: List<OfferRecord>,
    clients: List<ClientDossier>,
    properties: List<PropertyItem>,
    onOpenNewOfferDialog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Offres & Négociation (${offers.size})", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
            Button(
                onClick = onOpenNewOfferDialog,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("open_new_offer_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nouvelle offre", style = MaterialTheme.typography.labelSmall)
            }
        }

        offers.forEach { offer ->
            val client = clients.firstOrNull { it.id == offer.clientId }
            val prop = properties.firstOrNull { it.id == offer.propertyId }
            val discountPct = if (offer.askingPrice > 0) {
                ((offer.askingPrice - offer.offeredPrice) * 100.0 / offer.askingPrice)
            } else 0.0

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(offer.date, style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                        Surface(color = SuccessContainer, shape = RoundedCornerShape(16.dp)) {
                            Text(
                                offer.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(prop?.title ?: "Bien", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
                    Text("Acquéreur : ${client?.name ?: offer.clientId}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)

                    Surface(color = EmeraldDeep, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Prix affiché", style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.7f))
                                Text(MontyMarketData.formatEuro(offer.askingPrice), style = MaterialTheme.typography.titleMedium, color = IvoryWarm)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Décote", style = MaterialTheme.typography.labelSmall, color = GoldChampagne)
                                Text("-${String.format("%.1f", discountPct)}%", style = MaterialTheme.typography.titleMedium, color = GoldChampagne)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Offre formulée", style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.7f))
                                Text(MontyMarketData.formatEuro(offer.offeredPrice), style = MaterialTheme.typography.titleLarge, color = GoldChampagne)
                            }
                        }
                    }

                    if (offer.sellerContext.isNotBlank()) {
                        Text("Contexte vendeur : ${offer.sellerContext}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (offer.clientFinancing.isNotBlank()) {
                        Text("Financement : ${offer.clientFinancing}", style = MaterialTheme.typography.bodySmall, color = EmeraldDeep)
                    }
                    if (offer.suspensiveConditions.isNotBlank()) {
                        Text("Conditions suspensives : ${offer.suspensiveConditions}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    }
                    if (offer.arguments.isNotEmpty()) {
                        Surface(color = IvoryWarm, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Arguments de négociation :", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                                offer.arguments.forEach { arg ->
                                    Text("• $arg", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
