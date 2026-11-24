package com.example.ui.saas

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.ExcelImportExecutionResult
import com.example.data.ExcelImportPreview
import com.example.data.MontyImportField
import com.example.data.MontyMarketData
import com.example.data.ParsedSpreadsheetTable
import com.example.data.model.AgencyMember
import com.example.data.model.CrmCatalog
import com.example.data.model.CrmContact
import com.example.data.model.FollowUpTask
import com.example.data.model.PropertyItem
import com.example.security.RgpdComplianceManager
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

@Composable
fun CrmContactDetailDialog(
    contact: CrmContact,
    tasks: List<FollowUpTask>,
    properties: List<PropertyItem>,
    isDirector: Boolean,
    activeMembers: List<AgencyMember>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onAddExchange: (String, String, String?) -> Unit,
    onPlanFollowUp: () -> Unit,
    onLinkProperty: (PropertyItem?) -> Unit,
    onReassign: (AgencyMember) -> Unit,
    onExportRgpdJson: () -> String? = { null },
    onEraseRgpd: () -> Unit = {}
) {
    val context = LocalContext.current
    var quickChannel by remember { mutableStateOf("Note") }
    var quickNote by remember { mutableStateOf("") }
    var statusOverride by remember { mutableStateOf(contact.status) }
    var propertyMenuOpen by remember { mutableStateOf(false) }
    var agentMenuOpen by remember { mutableStateOf(false) }
    var exchangeFilter by remember { mutableStateOf("Tous") }
    var showEraseRgpdConfirm by remember { mutableStateOf(false) }

    val linkedTasks = remember(tasks, contact.id) {
        tasks.filter { it.contactId == contact.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(contact.fullName, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                Text(
                    text = "${contact.contactType} • ${contact.status} • Priorité ${contact.priority}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Modifier")
                    }
                    OutlinedButton(onClick = onPlanFollowUp) {
                        Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Relance")
                    }
                    OutlinedButton(onClick = onArchiveToggle) {
                        Icon(
                            if (contact.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (contact.archived) "Restaurer" else "Archiver")
                    }
                }

                // Identity & Qualification Summary
                Surface(color = IvoryWarm, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Identité & Qualification", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                        Text("Tél : ${contact.phone.ifBlank { "Non renseigné" }} • Email : ${contact.email.ifBlank { "Non renseigné" }}", style = MaterialTheme.typography.bodySmall)
                        if (contact.postalAddress.isNotBlank()) {
                            Text("Adresse : ${contact.postalAddress}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Origine : ${contact.source} • Agent : ${contact.assignedToName}", style = MaterialTheme.typography.bodySmall)
                        val budgetLabel = when {
                            contact.budgetMin > 0 && contact.budgetMax > 0 ->
                                "${MontyMarketData.formatEuro(contact.budgetMin)} – ${MontyMarketData.formatEuro(contact.budgetMax)}"
                            contact.budgetMax > 0 -> "Max ${MontyMarketData.formatEuro(contact.budgetMax)}"
                            else -> "Non défini"
                        }
                        Text("Budget : $budgetLabel • Secteur : ${contact.sector.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                        if (contact.desiredPropertyType.isNotBlank() || contact.desiredSurfaceMin > 0 || contact.desiredRoomsMin > 0) {
                            Text(
                                "Recherche : ${contact.desiredPropertyType.ifBlank { "Bien" }} • ≥ ${contact.desiredSurfaceMin} m² • ≥ ${contact.desiredRoomsMin} ch.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (contact.financingMode.isNotBlank() || contact.projectTimeline.isNotBlank()) {
                            Text(
                                "Financement : ${contact.financingMode.ifBlank { "—" }} • Délai : ${contact.projectTimeline.ifBlank { "—" }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (contact.lastContactedAt.isNotBlank() || contact.nextFollowUpAt.isNotBlank()) {
                            Text(
                                "Dernier contact : ${contact.lastContactedAt.ifBlank { "—" }} • Prochaine relance : ${contact.nextFollowUpAt.ifBlank { "Aucune" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldDeep
                            )
                        }
                    }
                }

                // RGPD & CNIL Compliance Card
                val retentionExpired = remember(contact.rgpdRetentionUntil) {
                    RgpdComplianceManager.isRetentionExpired(contact.rgpdRetentionUntil)
                }
                Surface(
                    color = if (retentionExpired) ErrorContainer else IvoryWarm,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (retentionExpired) ErrorRed else BorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "Conformité RGPD",
                                tint = if (retentionExpired) ErrorRed else EmeraldDeep,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Conformité RGPD (CNIL)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (retentionExpired) ErrorRed else EmeraldDeep
                            )
                        }
                        Text(
                            text = "Base légale : ${contact.rgpdLegalBasis.ifBlank { RgpdComplianceManager.LEGAL_BASIS_PRECONTRACT }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ObsidianInk
                        )
                        Text(
                            text = "Consentement : ${
                                if (contact.rgpdConsentGranted) {
                                    "Accordé" + if (contact.rgpdConsentDate.isNotBlank()) " (${contact.rgpdConsentDate})" else ""
                                } else {
                                    "Non confirmé"
                                }
                            } • Conservation : ${contact.rgpdRetentionUntil.ifBlank { "36 mois" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                        if (retentionExpired) {
                            Text(
                                text = "Durée de conservation légale dépassée — effacement ou renouvellement requis.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val json = onExportRgpdJson()
                                    if (!json.isNullOrBlank()) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_SUBJECT, "Export RGPD — ${contact.fullName}")
                                            putExtra(Intent.EXTRA_TEXT, json)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Exporter les données RGPD (Art. 15 / 20)")
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("crm_contact_rgpd_export_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Export RGPD (JSON)")
                            }
                            if (isDirector) {
                                OutlinedButton(
                                    onClick = { showEraseRgpdConfirm = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                    modifier = Modifier.testTag("crm_contact_rgpd_erase_button")
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Effacer (Art. 17)")
                                }
                            }
                        }
                    }
                }

                // Linked Property & Reassignment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { propertyMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.HomeWork, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(contact.linkedPropertyTitle.ifBlank { "Associer un bien" }, maxLines = 1)
                        }
                        DropdownMenu(expanded = propertyMenuOpen, onDismissRequest = { propertyMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Aucun bien associé") },
                                onClick = { propertyMenuOpen = false; onLinkProperty(null) }
                            )
                            properties.forEach { prop ->
                                DropdownMenuItem(
                                    text = { Text("${prop.title} (${MontyMarketData.formatEuro(prop.price)})") },
                                    onClick = { propertyMenuOpen = false; onLinkProperty(prop) }
                                )
                            }
                        }
                    }

                    if (isDirector && activeMembers.size > 1) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(onClick = { agentMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Agent : ${contact.assignedToName}", maxLines = 1)
                            }
                            DropdownMenu(expanded = agentMenuOpen, onDismissRequest = { agentMenuOpen = false }) {
                                activeMembers.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text("${m.displayName} (${m.agencyRole.labelFr})") },
                                        onClick = { agentMenuOpen = false; onReassign(m) }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderSubtle)

                // Log an exchange (Appel / SMS / Email / Note)
                Text("Enregistrer un échange ou une note", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CrmCatalog.EXCHANGE_CHANNELS.forEach { ch ->
                        val selected = quickChannel == ch
                        Surface(
                            color = if (selected) EmeraldDeep else IvoryWarm,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { quickChannel = ch }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val icon = when (ch) {
                                    "Appel" -> Icons.Default.Call
                                    "SMS" -> Icons.Default.Sms
                                    "Email" -> Icons.Default.Email
                                    else -> Icons.AutoMirrored.Filled.NoteAdd
                                }
                                Icon(icon, contentDescription = null, tint = if (selected) GoldChampagne else SlateMuted, modifier = Modifier.size(14.dp))
                                Text(ch, style = MaterialTheme.typography.labelSmall, color = if (selected) IvoryWarm else ObsidianInk)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = quickNote,
                    onValueChange = { quickNote = it },
                    label = { Text("Compte-rendu d'échange / note interne") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Statut :", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    CrmCatalog.CONTACT_STATUSES.forEach { st ->
                        val sel = statusOverride == st
                        Surface(
                            color = if (sel) EmeraldDeep else IvoryWarm,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { statusOverride = st }
                        ) {
                            Text(st, style = MaterialTheme.typography.labelSmall, color = if (sel) IvoryWarm else ObsidianInk, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
                Button(
                    onClick = {
                        if (quickNote.isNotBlank()) {
                            onAddExchange(quickChannel, quickNote, statusOverride)
                            quickNote = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ajouter à l'historique des échanges")
                }

                if (linkedTasks.isNotEmpty()) {
                    Text("Tâches & relances associées (${linkedTasks.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                    linkedTasks.forEach { t ->
                        Surface(color = IvoryWarm, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "• ${t.dueDate} ${t.dueTime} [${t.channel}] ${t.title} — ${t.status}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                // Exchange history list
                Text("Historique complet (${contact.exchangeLogs.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Tous", "Appel", "SMS", "Email", "Note", "Statut").forEach { f ->
                        val sel = exchangeFilter == f
                        Surface(
                            color = if (sel) EmeraldDeep else IvoryWarm,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { exchangeFilter = f }
                        ) {
                            Text(f, style = MaterialTheme.typography.labelSmall, color = if (sel) IvoryWarm else ObsidianInk, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
                val visibleLogs = contact.exchangeLogs.filter { exchangeFilter == "Tous" || it.channel == exchangeFilter }
                if (visibleLogs.isEmpty()) {
                    Text("Aucun échange enregistré dans ce filtre.", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                } else {
                    visibleLogs.forEach { entry ->
                        Surface(
                            color = IvoryWarm,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "${entry.channel} • ${entry.dateTime} • ${entry.authorName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldDeep
                                )
                                Text(entry.summary, style = MaterialTheme.typography.bodySmall, color = ObsidianInk)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )

    if (showEraseRgpdConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseRgpdConfirm = false },
            title = { Text("Effacement RGPD (Article 17)", fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = {
                Text(
                    "Voulez-vous supprimer définitivement la fiche CRM de « ${contact.fullName} » ainsi que ses données personnelles ? Cette action est irréversible et journalisée."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseRgpdConfirm = false
                        onEraseRgpd()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = IvoryWarm),
                    modifier = Modifier.testTag("crm_contact_rgpd_erase_confirm_button")
                ) {
                    Text("Confirmer l'effacement")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseRgpdConfirm = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
fun CrmContactFormDialog(
    initialContact: CrmContact?,
    isDirector: Boolean,
    activeMembers: List<AgencyMember>,
    properties: List<PropertyItem>,
    onDismiss: () -> Unit,
    onSave: (
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        postalAddress: String,
        contactType: String,
        status: String,
        priority: String,
        budgetMin: Long,
        budgetMax: Long,
        sector: String,
        desiredPropertyType: String,
        desiredSurfaceMin: Long,
        desiredRoomsMin: Long,
        financingMode: String,
        projectTimeline: String,
        source: String,
        notes: String,
        linkedProperty: PropertyItem?,
        assignedMember: AgencyMember?,
        followUpDate: String?,
        followUpTime: String?
    ) -> Unit
) {
    var firstName by remember { mutableStateOf(initialContact?.firstName.orEmpty().ifBlank { initialContact?.fullName?.substringBefore(" ").orEmpty() }) }
    var lastName by remember { mutableStateOf(initialContact?.lastName.orEmpty().ifBlank { initialContact?.fullName?.substringAfter(" ", "").orEmpty() }) }
    var phone by remember { mutableStateOf(initialContact?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initialContact?.email.orEmpty()) }
    var postalAddress by remember { mutableStateOf(initialContact?.postalAddress.orEmpty()) }
    var contactType by remember { mutableStateOf(initialContact?.contactType ?: "Acquéreur") }
    var status by remember { mutableStateOf(initialContact?.status ?: "Nouveau") }
    var priority by remember { mutableStateOf(initialContact?.priority ?: "Normal") }
    var budgetMinText by remember { mutableStateOf(if ((initialContact?.budgetMin ?: 0L) > 0) initialContact!!.budgetMin.toString() else "") }
    var budgetMaxText by remember { mutableStateOf(if ((initialContact?.budgetMax ?: 0L) > 0) initialContact!!.budgetMax.toString() else "400000") }
    var sector by remember { mutableStateOf(initialContact?.sector ?: "Montpellier") }
    var desiredPropType by remember { mutableStateOf(initialContact?.desiredPropertyType ?: "Appartement") }
    var desiredSurfaceText by remember { mutableStateOf(if ((initialContact?.desiredSurfaceMin ?: 0L) > 0) initialContact!!.desiredSurfaceMin.toString() else "") }
    var desiredRoomsText by remember { mutableStateOf(if ((initialContact?.desiredRoomsMin ?: 0L) > 0) initialContact!!.desiredRoomsMin.toString() else "") }
    var financingMode by remember { mutableStateOf(initialContact?.financingMode ?: "Crédit immobilier") }
    var projectTimeline by remember { mutableStateOf(initialContact?.projectTimeline ?: "Sous 3 mois") }
    var source by remember { mutableStateOf(initialContact?.source ?: "Manuel") }
    var notes by remember { mutableStateOf(initialContact?.notes.orEmpty()) }
    var selectedProperty by remember { mutableStateOf(properties.firstOrNull { it.id == initialContact?.linkedPropertyId }) }
    var selectedMember by remember { mutableStateOf(activeMembers.firstOrNull { it.uid == initialContact?.assignedToUid } ?: activeMembers.firstOrNull()) }
    var scheduleInitialFollowUp by remember { mutableStateOf(initialContact == null) }
    var followUpDate by remember { mutableStateOf("2026-11-22") }
    var followUpTime by remember { mutableStateOf("10:00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialContact == null) "Nouveau contact CRM" else "Modifier la fiche contact",
                fontWeight = FontWeight.Bold,
                color = EmeraldDeep
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("1. Identité", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Nom *") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Adresse e-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = postalAddress, onValueChange = { postalAddress = it }, label = { Text("Adresse postale") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                ChipSelectorRow("Origine", CrmCatalog.SOURCES, source) { source = it }

                Text("2. Qualification", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                ChipSelectorRow("Type", CrmCatalog.CONTACT_TYPES, contactType) { contactType = it }
                ChipSelectorRow("Statut", CrmCatalog.CONTACT_STATUSES, status) { status = it }
                ChipSelectorRow("Priorité", CrmCatalog.PRIORITIES, priority) { priority = it }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = budgetMinText,
                        onValueChange = { budgetMinText = it.filter(Char::isDigit) },
                        label = { Text("Budget min (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = budgetMaxText,
                        onValueChange = { budgetMaxText = it.filter(Char::isDigit) },
                        label = { Text("Budget max (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(value = sector, onValueChange = { sector = it }, label = { Text("Secteurs recherchés") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChipSelectorRow("Bien recherché", CrmCatalog.PROPERTY_TYPES, desiredPropType) { desiredPropType = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = desiredSurfaceText,
                        onValueChange = { desiredSurfaceText = it.filter(Char::isDigit) },
                        label = { Text("Surface min (m²)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = desiredRoomsText,
                        onValueChange = { desiredRoomsText = it.filter(Char::isDigit) },
                        label = { Text("Chambres min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                ChipSelectorRow("Financement", CrmCatalog.FINANCING_MODES, financingMode) { financingMode = it }
                ChipSelectorRow("Délai", CrmCatalog.TIMELINES, projectTimeline) { projectTimeline = it }

                Text("3. Suivi & Bien associé", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                if (properties.isNotEmpty()) {
                    var propMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { propMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Bien associé : ${selectedProperty?.title ?: "Aucun"}")
                        }
                        DropdownMenu(expanded = propMenu, onDismissRequest = { propMenu = false }) {
                            DropdownMenuItem(text = { Text("Aucun") }, onClick = { selectedProperty = null; propMenu = false })
                            properties.forEach { p ->
                                DropdownMenuItem(text = { Text("${p.title} (${MontyMarketData.formatEuro(p.price)})") }, onClick = { selectedProperty = p; propMenu = false })
                            }
                        }
                    }
                }
                if (initialContact == null && isDirector && activeMembers.size > 1) {
                    var agentMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { agentMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Agent responsable : ${selectedMember?.displayName ?: "Moi"}")
                        }
                        DropdownMenu(expanded = agentMenu, onDismissRequest = { agentMenu = false }) {
                            activeMembers.forEach { m ->
                                DropdownMenuItem(text = { Text(m.displayName) }, onClick = { selectedMember = m; agentMenu = false })
                            }
                        }
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes internes") }, modifier = Modifier.fillMaxWidth())

                if (initialContact == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { scheduleInitialFollowUp = !scheduleInitialFollowUp }) {
                        Checkbox(checked = scheduleInitialFollowUp, onCheckedChange = { scheduleInitialFollowUp = it })
                        Text("Programmer une première relance", style = MaterialTheme.typography.bodySmall)
                    }
                    if (scheduleInitialFollowUp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = followUpDate, onValueChange = { followUpDate = it }, label = { Text("Date (AAAA-MM-JJ)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = followUpTime, onValueChange = { followUpTime = it }, label = { Text("Heure (HH:MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (firstName.isNotBlank() || lastName.isNotBlank()) {
                        onSave(
                            firstName,
                            lastName,
                            email,
                            phone,
                            postalAddress,
                            contactType,
                            status,
                            priority,
                            budgetMinText.toLongOrNull() ?: 0L,
                            budgetMaxText.toLongOrNull() ?: 0L,
                            sector,
                            desiredPropType,
                            desiredSurfaceText.toLongOrNull() ?: 0L,
                            desiredRoomsText.toLongOrNull() ?: 0L,
                            financingMode,
                            projectTimeline,
                            source,
                            notes,
                            selectedProperty,
                            selectedMember,
                            if (initialContact == null && scheduleInitialFollowUp) followUpDate else null,
                            if (initialContact == null && scheduleInitialFollowUp) followUpTime else null
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
            ) {
                Text(if (initialContact == null) "Créer le contact" else "Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun CrmExcelImportWizardDialog(
    table: ParsedSpreadsheetTable,
    mapping: Map<Int, MontyImportField>,
    preview: ExcelImportPreview?,
    isDirector: Boolean,
    teamSize: Int,
    onMapColumn: (Int, MontyImportField) -> Unit,
    onDismiss: () -> Unit,
    onConfirmImport: (Boolean, Boolean) -> Unit
) {
    var distribute by remember { mutableStateOf(isDirector && teamSize > 1) }
    var autoTasks by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Assistant d'import Excel / CSV", fontWeight = FontWeight.Bold, color = EmeraldDeep)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Fichier : ${table.fileName.ifBlank { table.formatDetected }} (${table.dataRows.size} lignes détectées)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )

                Text("1. Association des colonnes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                table.headers.forEachIndexed { colIdx, header ->
                    var menuOpen by remember { mutableStateOf(false) }
                    val currentField = mapping[colIdx] ?: MontyImportField.IGNORE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = header.take(22),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            OutlinedButton(onClick = { menuOpen = true }) {
                                Text(currentField.labelFr, style = MaterialTheme.typography.labelSmall)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                MontyImportField.entries.forEach { field ->
                                    DropdownMenuItem(
                                        text = { Text(field.labelFr) },
                                        onClick = {
                                            menuOpen = false
                                            onMapColumn(colIdx, field)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (preview != null) {
                    HorizontalDivider(color = BorderSubtle)
                    Text("2. Prévisualisation & Détection des doublons", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(color = SuccessContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Text("${preview.validDrafts.size} valides", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                        }
                        Surface(color = WarningContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Text("${preview.duplicateRows.size} doublons", style = MaterialTheme.typography.labelSmall, color = WarningAmber, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                        }
                        Surface(color = ErrorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Text("${preview.errorRows.size} erreurs", style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                        }
                    }

                    preview.validDrafts.take(3).forEach { d ->
                        Text("✓ Ligne ${d.rowNumber} : ${d.fullName} (${d.phone.ifBlank { d.email }})", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                    }
                    preview.duplicateRows.take(3).forEach { dup ->
                        Text("⚠ Ligne ${dup.rowNumber} ignorée : ${dup.label} — ${dup.reason}", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
                    }
                    preview.errorRows.take(3).forEach { err ->
                        Text("✕ Ligne ${err.rowNumber} : ${err.reason}", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                    }
                }

                if (isDirector && teamSize > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { distribute = !distribute }) {
                        Checkbox(checked = distribute, onCheckedChange = { distribute = it })
                        Text("Répartir entre les $teamSize collaborateurs actifs", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { autoTasks = !autoTasks }) {
                    Checkbox(checked = autoTasks, onCheckedChange = { autoTasks = it })
                    Text("Programmer une relance de premier contact", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmImport(distribute, autoTasks) },
                enabled = (preview?.validDrafts?.isNotEmpty() == true),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
            ) {
                Text("Importer (${preview?.validDrafts?.size ?: 0})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun CrmImportReportDialog(
    report: ExcelImportExecutionResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rapport d'importation Excel", fontWeight = FontWeight.Bold, color = EmeraldDeep) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("• Contacts importés en base : ${report.importedCount}", color = SuccessGreen, fontWeight = FontWeight.Bold)
                Text("• Doublons ignorés (tél / e-mail) : ${report.duplicateSkippedCount}", color = WarningAmber)
                Text("• Lignes en erreur ignorées : ${report.errorCount}", color = if (report.errorCount > 0) ErrorRed else SlateMuted)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
            ) { Text("Terminer") }
        }
    )
}

@Composable
private fun ChipSelectorRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = SlateMuted)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val isSel = selected == option
                Surface(
                    color = if (isSel) EmeraldDeep else IvoryWarm,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(1.dp, if (isSel) EmeraldDeep else BorderSubtle, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(option) }
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) IvoryWarm else ObsidianInk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
