package com.example.ui.advisor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MontyMarketData
import com.example.data.local.AcquereurEntity
import com.example.data.local.AcquereurStatuses
import com.example.data.model.ClientDossier
import com.example.data.model.PropertyItem
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldMid
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcquereursScreen(
    viewModel: AcquereursViewModel,
    properties: List<PropertyItem> = emptyList(),
    onOpenBuyerPortal: () -> Unit = {},
    onShowToast: (String) -> Unit
) {
    val allAcquereurs by viewModel.allAcquereurs.collectAsStateWithLifecycle()
    val filteredAcquereurs by viewModel.filteredAcquereurs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()

    var showFormDialog by remember { mutableStateOf(false) }
    var editingAcquereur by remember { mutableStateOf<AcquereurEntity?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // En-tête Room & statistiques rapides
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Base locale Room",
                                tint = GoldChampagne,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "BASE LOCALE ROOM • ACQUÉREURS",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldChampagne
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Répertoire Acquéreurs Potentiels",
                            style = MaterialTheme.typography.titleLarge,
                            color = IvoryWarm
                        )
                    }
                    Button(
                        onClick = {
                            editingAcquereur = null
                            showFormDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAntique,
                            contentColor = ObsidianInk
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_acquereur_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter acquéreur", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ajouter", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = EmeraldMid,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total enregistrés", style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.7f))
                            Text("${allAcquereurs.size} acquéreurs", style = MaterialTheme.typography.titleMedium, color = GoldChampagne)
                        }
                    }
                    val activeCount = allAcquereurs.count {
                        it.status.contains("Actif", ignoreCase = true) ||
                            it.status.contains("Offre", ignoreCase = true) ||
                            it.status.contains("compromis", ignoreCase = true)
                    }
                    Surface(
                        color = EmeraldMid,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Actifs / En offre", style = MaterialTheme.typography.labelSmall, color = IvoryWarm.copy(alpha = 0.7f))
                            Text("$activeCount prioritaires", style = MaterialTheme.typography.titleMedium, color = IvoryWarm)
                        }
                    }
                }
            }
        }

        // Recherche et filtres de statut
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Rechercher") },
                    placeholder = { Text("Filtrer par nom, secteur, type ou critère...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("acquereurs_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (listOf("Tous") + AcquereurStatuses.ALL).forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { viewModel.setStatusFilter(st) },
                            label = { Text(st, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldDeep,
                                selectedLabelColor = GoldChampagne
                            ),
                            modifier = Modifier.testTag("status_filter_${st.lowercase().replace(" ", "_")}")
                        )
                    }
                }
            }
        }

        // Liste des acquéreurs potentiels
        if (filteredAcquereurs.isEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(36.dp))
                    Text("Aucun acquéreur trouvé", style = MaterialTheme.typography.titleMedium, color = EmeraldDeep)
                    Text(
                        text = "Ajoutez un nouvel acquéreur ou réinitialisez les filtres de recherche.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                    if (searchQuery.isNotBlank() || statusFilter != "Tous") {
                        OutlinedButton(
                            onClick = {
                                viewModel.setSearchQuery("")
                                viewModel.setStatusFilter("Tous")
                            }
                        ) {
                            Text("Réinitialiser les filtres")
                        }
                    }
                }
            }
        } else {
            filteredAcquereurs.forEach { acquereur ->
                val bestMatch = remember(acquereur, properties) {
                    if (properties.isEmpty()) null
                    else {
                        val pseudoClient = ClientDossier(
                            id = "room-${acquereur.id}",
                            name = acquereur.fullName,
                            budgetMax = acquereur.budgetMax,
                            downPayment = acquereur.downPayment,
                            propertyType = acquereur.propertyType,
                            minSurface = acquereur.minSurface,
                            minRooms = acquereur.minRooms,
                            sectors = acquereur.sectorsSummary.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            essentialCriteria = acquereur.essentialList()
                        )
                        properties
                            .map { it to MontyMarketData.computeCompatibility(it, pseudoClient).score }
                            .maxByOrNull { it.second }
                    }
                }
                AcquereurItemCard(
                    acquereur = acquereur,
                    bestMatch = bestMatch,
                    onOpenBuyerPortal = onOpenBuyerPortal,
                    onChangeStatus = { newStatus ->
                        viewModel.updateStatus(acquereur.id, newStatus, onShowToast)
                    },
                    onEdit = {
                        editingAcquereur = acquereur
                        showFormDialog = true
                    },
                    onDelete = {
                        viewModel.deleteAcquereur(acquereur, onShowToast)
                    }
                )
            }
        }
    }

    if (showFormDialog) {
        AcquereurFormDialog(
            initial = editingAcquereur,
            onDismiss = { showFormDialog = false },
            onConfirm = { entity ->
                viewModel.saveAcquereur(entity, onShowToast)
                showFormDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcquereurItemCard(
    acquereur: AcquereurEntity,
    bestMatch: Pair<PropertyItem, Int>?,
    onOpenBuyerPortal: () -> Unit,
    onChangeStatus: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    val (statusBg, statusFg) = when {
        acquereur.status.contains("Offre", ignoreCase = true) ||
            acquereur.status.contains("compromis", ignoreCase = true) -> SuccessContainer to SuccessGreen
        acquereur.status.contains("Actif", ignoreCase = true) -> EmeraldDeep to GoldChampagne
        acquereur.status.contains("veille", ignoreCase = true) -> IvoryWarm to SlateMuted
        else -> WarningContainer to WarningAmber
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .testTag("acquereur_card_${acquereur.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Ligne Identité + Statut cliquable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = acquereur.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldDeep
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (acquereur.phone.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(13.dp))
                                Text(acquereur.phone, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            }
                        }
                        if (acquereur.email.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(13.dp))
                                Text(acquereur.email, style = MaterialTheme.typography.bodySmall, color = SlateMuted, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { statusMenuExpanded = true }
                            .testTag("acquereur_status_pill_${acquereur.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = acquereur.status,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusFg
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Modifier le statut",
                                tint = statusFg,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false }
                    ) {
                        AcquereurStatuses.ALL.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onChangeStatus(option)
                                    statusMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle)

            // Bloc Critères de recherche
            Surface(
                color = IvoryWarm,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Budget max", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                            Text(
                                text = MontyMarketData.formatEuro(acquereur.budgetMax),
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldDeep,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Apport", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                            Text(
                                text = MontyMarketData.formatEuro(acquereur.downPayment),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ObsidianInk,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Délai", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                            Text(
                                text = acquereur.timelineDelai,
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldDeep,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.HomeWork, contentDescription = null, tint = GoldAntique, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${acquereur.propertyType} • ≥ ${acquereur.minSurface} m² • ${acquereur.minRooms} ch. min",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ObsidianInk
                        )
                    }

                    if (acquereur.sectorsSummary.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(16.dp))
                            Text(
                                text = acquereur.sectorsSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldDeep
                            )
                        }
                    }

                    if (acquereur.bankApproval.isNotBlank()) {
                        Text(
                            text = "Financement : ${acquereur.bankApproval}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                }
            }

            // Critères indispensables
            val essentials = acquereur.essentialList()
            if (essentials.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    essentials.forEach { criterion ->
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.border(1.dp, GoldAntique.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        ) {
                            Text(
                                text = criterion,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldDeep,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (bestMatch != null) {
                Surface(
                    color = EmeraldDeep.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GoldAntique.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldAntique, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Meilleure pertinence portefeuille (${bestMatch.second}/100)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldDeep,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${bestMatch.first.title} • ${MontyMarketData.formatEuro(bestMatch.first.price)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ObsidianInk,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Actions Portail Acquéreur / Modifier / Supprimer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenBuyerPortal,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_buyer_portal_from_acquereur_${acquereur.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Portail Acquéreur", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Portail", style = MaterialTheme.typography.labelSmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("edit_acquereur_${acquereur.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier critères", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Critères", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_acquereur_${acquereur.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Supprimer acquéreur",
                            tint = ErrorRed
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcquereurFormDialog(
    initial: AcquereurEntity?,
    onDismiss: () -> Unit,
    onConfirm: (AcquereurEntity) -> Unit
) {
    var fullName by remember(initial) { mutableStateOf(initial?.fullName.orEmpty()) }
    var phone by remember(initial) { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember(initial) { mutableStateOf(initial?.email.orEmpty()) }
    var status by remember(initial) { mutableStateOf(initial?.status ?: AcquereurStatuses.ALL.first()) }
    var budgetMax by remember(initial) { mutableStateOf((initial?.budgetMax ?: 420000L).toString()) }
    var downPayment by remember(initial) { mutableStateOf((initial?.downPayment ?: 85000L).toString()) }
    var propertyType by remember(initial) { mutableStateOf(initial?.propertyType ?: "Appartement") }
    var sectorsSummary by remember(initial) { mutableStateOf(initial?.sectorsSummary ?: "Ovalie (34070), Croix d'Argent (34070)") }
    var minSurface by remember(initial) { mutableStateOf((initial?.minSurface ?: 85L).toString()) }
    var minRooms by remember(initial) { mutableStateOf((initial?.minRooms ?: 3L).toString()) }
    var essentialCriteria by remember(initial) { mutableStateOf(initial?.essentialCriteria ?: "Terrasse / Extérieur, Parking / Garage, DPE A, B ou C") }
    var timelineDelai by remember(initial) { mutableStateOf(initial?.timelineDelai ?: "Sous 3 mois") }
    var bankApproval by remember(initial) { mutableStateOf(initial?.bankApproval ?: "Simulation courtier validée") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Nouvel acquéreur potentiel" else "Modifier critères acquéreur",
                style = MaterialTheme.typography.titleLarge,
                color = EmeraldDeep
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nom / Foyer *") },
                    placeholder = { Text("Ex : Martin — Léa & Hugo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("acquereur_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Téléphone") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Statut du dossier", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AcquereurStatuses.ALL.forEach { opt ->
                        FilterChip(
                            selected = status == opt,
                            onClick = { status = opt },
                            label = { Text(opt, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = budgetMax,
                        onValueChange = { budgetMax = it.filter(Char::isDigit) },
                        label = { Text("Budget max (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("acquereur_budget_input")
                    )
                    OutlinedTextField(
                        value = downPayment,
                        onValueChange = { downPayment = it.filter(Char::isDigit) },
                        label = { Text("Apport (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Type de bien recherché", style = MaterialTheme.typography.labelLarge, color = EmeraldDeep)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MontyMarketData.OPTIONS_TYPES_BIEN.forEach { t ->
                        FilterChip(
                            selected = propertyType == t,
                            onClick = { propertyType = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minSurface,
                        onValueChange = { minSurface = it.filter(Char::isDigit) },
                        label = { Text("Surf. min (m²)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minRooms,
                        onValueChange = { minRooms = it.filter(Char::isDigit) },
                        label = { Text("Chambres min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = sectorsSummary,
                    onValueChange = { sectorsSummary = it },
                    label = { Text("Secteurs recherchés") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = essentialCriteria,
                    onValueChange = { essentialCriteria = it },
                    label = { Text("Critères essentiels (virgules)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timelineDelai,
                    onValueChange = { timelineDelai = it },
                    label = { Text("Délai souhaité") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bankApproval,
                    onValueChange = { bankApproval = it },
                    label = { Text("Financement / Validation bancaire") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = fullName.trim()
                    if (cleanName.isNotEmpty()) {
                        onConfirm(
                            AcquereurEntity(
                                id = initial?.id ?: 0L,
                                fullName = cleanName,
                                phone = phone.ifBlank { "06 00 00 00 00" },
                                email = email.ifBlank { "acquereur@exemple.fr" },
                                status = status,
                                budgetMax = budgetMax.toLongOrNull() ?: 420000L,
                                downPayment = downPayment.toLongOrNull() ?: 85000L,
                                propertyType = propertyType,
                                sectorsSummary = sectorsSummary,
                                minSurface = minSurface.toLongOrNull() ?: 85L,
                                minRooms = minRooms.toLongOrNull() ?: 3L,
                                essentialCriteria = essentialCriteria,
                                timelineDelai = timelineDelai,
                                bankApproval = bankApproval
                            )
                        )
                    }
                },
                enabled = fullName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                modifier = Modifier.testTag("confirm_acquereur_button")
            ) {
                Text(if (initial == null) "Enregistrer" else "Mettre à jour")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
