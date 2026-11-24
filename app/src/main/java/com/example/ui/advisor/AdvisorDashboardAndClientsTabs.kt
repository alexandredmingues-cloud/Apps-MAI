package com.example.ui.advisor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.MontyMarketData
import com.example.data.model.AdvisorTab
import com.example.data.model.ClientDossier
import com.example.data.model.OfferRecord
import com.example.data.model.PropertyItem
import com.example.data.model.VisitRecord
import com.example.ui.AutoAiUiState
import com.example.ui.components.AdvisorProactiveAiCard
import com.example.ui.components.RealEstateHomeInfoSection
import com.example.ui.components.ScoreBadge
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

@Composable
fun AdvisorDashboardTab(
    clients: List<ClientDossier>,
    activeClient: ClientDossier?,
    properties: List<PropertyItem>,
    visits: List<VisitRecord>,
    offers: List<OfferRecord>,
    autoAiState: AutoAiUiState,
    onRefreshAutoAi: () -> Unit,
    onSelectClient: (String) -> Unit,
    onAcknowledgeClientUpdate: (String) -> Unit,
    onOpenBuyerPortal: () -> Unit,
    onOpenCapacityScreen: () -> Unit,
    onNavigateTab: (AdvisorTab) -> Unit,
    onOpenNewClientDialog: () -> Unit
) {
    val updatedClients = clients.filter { it.criteriaModifiedByClient }
    val topMatches = remember(properties, activeClient) {
        properties
            .map { it to MontyMarketData.computeCompatibility(it, activeClient) }
            .sortedByDescending { it.second.score }
            .take(3)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Alertes Portail Client en direct
        if (updatedClients.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = WarningContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WarningAmber.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = WarningAmber)
                        Text(
                            text = "Activité Portail Client détectée (${updatedClients.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = ObsidianInk
                        )
                    }
                    updatedClients.forEach { cli ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cli.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    cli.lastClientUpdateLabel ?: "Mise à jour sélection / avis / critères",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    onSelectClient(cli.id)
                                    onNavigateTab(AdvisorTab.CLIENTS)
                                }) {
                                    Text("Ouvrir", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { onAcknowledgeClientUpdate(cli.id) }) {
                                    Text("Vu", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4 KPI Cards (2x2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiStatCard(
                label = "Dossiers actifs",
                value = "${clients.size}",
                sub = "${clients.count { it.portalActive }} portails ouverts",
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                label = "Biens qualifiés",
                value = "${properties.size}",
                sub = "Montpellier & Métropole",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val pendingRequests = visits.count { it.status.contains("Demande", ignoreCase = true) }
            KpiStatCard(
                label = "Visites & demandes",
                value = "${visits.size}",
                sub = if (pendingRequests > 0) "$pendingRequests demande(s) client" else "Planning à jour",
                modifier = Modifier.weight(1f)
            )
            val totalOfferVolume = offers.sumOf { it.offeredPrice }
            KpiStatCard(
                label = "Offres en cours",
                value = "${offers.size}",
                sub = if (offers.isNotEmpty()) MontyMarketData.formatEuro(totalOfferVolume) else "Aucune offre",
                modifier = Modifier.weight(1f)
            )
        }

        // Baromètre immobilier : taux, marché Montpellier & nouvelles lois
        RealEstateHomeInfoSection(
            onOpenHcsfSimulator = onOpenCapacityScreen,
            onOpenMarketObservatory = { onNavigateTab(AdvisorTab.MARKET) }
        )

        // Copilote Proactif IA (Amélioration automatique selon pertinence)
        if (activeClient != null) {
            AdvisorProactiveAiCard(
                autoAiState = autoAiState,
                onRefresh = onRefreshAutoAi,
                onActionProperty = { onNavigateTab(AdvisorTab.AI) }
            )
        }

        // Portefeuille Acquéreurs Actifs
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
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
                    Text("Portefeuille Acquéreurs", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
                    TextButton(onClick = onOpenNewClientDialog) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nouveau")
                    }
                }
                clients.forEach { cli ->
                    val isSelected = cli.id == activeClient?.id
                    Surface(
                        color = if (isSelected) EmeraldDeep else IvoryWarm,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isSelected) GoldAntique else BorderSubtle,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectClient(cli.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cli.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) IvoryWarm else EmeraldDeep
                                )
                                Text(
                                    text = "${cli.status} • ${MontyMarketData.formatEuro(cli.budgetMax)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) GoldChampagne else SlateMuted
                                )
                            }
                            Surface(
                                color = if (cli.portalActive) SuccessContainer else ErrorContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = if (cli.portalActive) "Portail actif" else "Révoqué",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (cli.portalActive) SuccessGreen else ErrorRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                if (activeClient != null) {
                    Button(
                        onClick = onOpenBuyerPortal,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAntique, contentColor = ObsidianInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Prévisualiser le Portail Client (${activeClient.name.substringBefore("—").trim()})")
                    }
                    OutlinedButton(
                        onClick = onOpenCapacityScreen,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("advisor_dashboard_hcsf_button")
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulation Bancaire HCSF 35% (${activeClient.name.substringBefore("—").trim()})", color = EmeraldDeep)
                    }
                }
            }
        }

        // Top Matches IA pour le client actif
        if (activeClient != null && topMatches.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
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
                            text = "Top Matchs • ${activeClient.name.substringBefore("—").trim()}",
                            style = MaterialTheme.typography.titleLarge,
                            color = EmeraldDeep
                        )
                        TextButton(onClick = { onNavigateTab(AdvisorTab.PROPERTIES) }) {
                            Text("Catalogue")
                        }
                    }
                    topMatches.forEach { (prop, compat) ->
                        Surface(
                            color = IvoryWarm,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateTab(AdvisorTab.PROPERTIES) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prop.title, style = MaterialTheme.typography.titleMedium, color = EmeraldDeep)
                                    Text(
                                        "${prop.sector} • ${MontyMarketData.formatEuro(prop.price)} • ${prop.surface} m²",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                ScoreBadge(score = compat.score)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier.border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = SlateMuted)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = EmeraldDeep, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = GoldAntique)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvisorClientsTab(
    clients: List<ClientDossier>,
    activeClient: ClientDossier?,
    onSelectClient: (String) -> Unit,
    onUpdateStage: (String, Int) -> Unit,
    onTogglePortal: (ClientDossier) -> Unit,
    onSavePrivateNotes: (String, String) -> Unit,
    onAcknowledgeUpdates: (String) -> Unit,
    onOpenBuyerPortal: () -> Unit,
    onOpenNewClientDialog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Sélecteur horizontal / chips des dossiers
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
                    Text("Dossiers CRM (${clients.size})", style = MaterialTheme.typography.titleMedium, color = EmeraldDeep)
                    Button(
                        onClick = onOpenNewClientDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nouveau dossier", style = MaterialTheme.typography.labelSmall)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    clients.forEach { cli ->
                        val selected = cli.id == activeClient?.id
                        Surface(
                            color = if (selected) EmeraldDeep else IvoryWarm,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .border(1.dp, if (selected) GoldAntique else BorderSubtle, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onSelectClient(cli.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (selected) GoldChampagne else SlateMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = cli.name.substringBefore("—").trim(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) IvoryWarm else ObsidianInk
                                )
                            }
                        }
                    }
                }
            }
        }

        if (activeClient != null) {
            var notesDraft by remember(activeClient.id, activeClient.privateNotes) {
                mutableStateOf(activeClient.privateNotes)
            }

            // Fiche détaillée client
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(activeClient.name, style = MaterialTheme.typography.headlineSmall, color = EmeraldDeep)
                            Text(activeClient.situation, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            Text(
                                "${activeClient.phone} • ${activeClient.email}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldDeep
                            )
                        }
                        if (activeClient.criteriaModifiedByClient) {
                            TextButton(onClick = { onAcknowledgeUpdates(activeClient.id) }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Marquer lu", color = WarningAmber, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSubtle)

                    // Pipeline 6 étapes cliquable
                    Text("Pipeline d'avancement (toucher pour actualiser)", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MontyMarketData.ETAPES_PROJET.forEachIndexed { idx, stepName ->
                            val isCurrent = idx.toLong() == activeClient.projectStage
                            val isDone = idx.toLong() < activeClient.projectStage
                            Surface(
                                color = when {
                                    isCurrent -> EmeraldDeep
                                    isDone -> SuccessContainer
                                    else -> IvoryWarm
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (isCurrent) GoldAntique else BorderSubtle,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onUpdateStage(activeClient.id, idx) }
                            ) {
                                Text(
                                    text = "${idx + 1}. $stepName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        isCurrent -> GoldChampagne
                                        isDone -> SuccessGreen
                                        else -> SlateMuted
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Enveloppe & Critères
                    Surface(
                        color = IvoryWarm,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Budget max : ${MontyMarketData.formatEuro(activeClient.budgetMax)} • Apport : ${MontyMarketData.formatEuro(activeClient.downPayment)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldDeep
                            )
                            Text("Financement : ${activeClient.bankApproval}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Cibles : ${activeClient.propertyType} ≥ ${activeClient.minSurface} m², ${activeClient.minRooms} ch. • ${activeClient.sectors.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMuted
                            )
                            if (activeClient.clientMessageToAdvisor.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Message client : « ${activeClient.clientMessageToAdvisor} »",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldDeep,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Contrôle Portail Client
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onTogglePortal(activeClient) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (activeClient.portalActive) "Révoquer portail" else "Réactiver portail",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Button(
                            onClick = onOpenBuyerPortal,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ouvrir Portail", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Notes confidentielles Conseiller (strictement masquées du Portail Client)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, EmeraldMid.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = GoldAntique, modifier = Modifier.size(18.dp))
                        Text(
                            "Notes Confidentielles Conseiller",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDeep
                        )
                    }
                    Text(
                        "Strictement internes — Jamais visibles ni exposées dans le Portail Client ou l'Assistant Acquéreur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                    OutlinedTextField(
                        value = notesDraft,
                        onValueChange = { notesDraft = it },
                        label = { Text("Notes stratégiques & psychologie acheteur") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("advisor_private_notes_input")
                    )
                    Button(
                        onClick = { onSavePrivateNotes(activeClient.id, notesDraft) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.End).testTag("save_private_notes_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer notes internes")
                    }
                }
            }

            // Historique Timeline
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Journal & Timeline Dossier", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
                    if (activeClient.timeline.isEmpty()) {
                        Text("Aucun événement enregistré.", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    } else {
                        activeClient.timeline.forEach { ev ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(GoldAntique)
                                )
                                Column {
                                    Text(
                                        "${ev.date} • ${ev.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldDeep
                                    )
                                    Text(ev.detail, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
