package com.example.ui.saas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ExcelImportExecutionResult
import com.example.data.ExcelImportPreview
import com.example.data.MontyImportField
import com.example.data.MontyMarketData
import com.example.data.ParsedSpreadsheetTable
import com.example.data.model.AgencyMember
import com.example.data.model.AgencyOrg
import com.example.data.model.CrmCatalog
import com.example.data.model.CrmContact
import com.example.data.model.CrmDashboardMetrics
import com.example.data.model.FollowUpTask
import com.example.data.model.PropertyItem
import com.example.security.RgpdComplianceManager
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrmContactsAndTasksTab(
    agency: AgencyOrg?,
    isDirector: Boolean,
    members: List<AgencyMember>,
    allContacts: List<CrmContact>,
    filteredContacts: List<CrmContact>,
    tasks: List<FollowUpTask>,
    properties: List<PropertyItem>,
    dashboardMetrics: CrmDashboardMetrics,
    filterState: CrmFilterState,
    importTable: ParsedSpreadsheetTable?,
    importMapping: Map<Int, MontyImportField>,
    importPreview: ExcelImportPreview?,
    lastImportReport: ExcelImportExecutionResult?,
    onUpdateFilters: ((CrmFilterState) -> CrmFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onAddContact: (String, String, String, String, String, String, String, String, Long, Long, String, String, Long, Long, String, String, String, String, PropertyItem?, AgencyMember?, String?, String?) -> Unit,
    onUpdateContactFull: (CrmContact, String, String, String, String, String, String, String, String, Long, Long, String, String, Long, Long, String, String, String, String, PropertyItem?) -> Unit,
    onAddContactExchange: (CrmContact, String, String, String?) -> Unit,
    onToggleArchiveContact: (CrmContact) -> Unit,
    onLinkProperty: (CrmContact, PropertyItem?) -> Unit,
    onReassignContact: (CrmContact, AgencyMember) -> Unit,
    onPrepareExcelImport: (ByteArray, String) -> Unit,
    onUpdateImportColumnMapping: (Int, MontyImportField) -> Unit,
    onCancelExcelImport: () -> Unit,
    onConfirmExcelImport: (Boolean, Boolean) -> Unit,
    onDismissImportReport: () -> Unit,
    onBuildExportCsv: () -> ByteArray?,
    onBuildExportXlsx: () -> ByteArray?,
    onAddTask: (CrmContact?, String, String, String, String, String, AgencyMember?) -> Unit,
    onUpdateTaskStatus: (FollowUpTask, String) -> Unit,
    onBuildContactRgpdJson: (CrmContact) -> String? = { null },
    onEraseContactRgpd: (CrmContact) -> Unit = {},
    onPurgeExpiredRgpd: () -> Unit = {},
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var subTabIndex by remember { mutableIntStateOf(0) } // 0 = Contacts, 1 = Relances
    var showCreateContactDialog by remember { mutableStateOf(false) }
    var selectedContactIdForDetail by remember { mutableStateOf<String?>(null) }
    var editingContact by remember { mutableStateOf<CrmContact?>(null) }
    var scheduleTaskForContact by remember { mutableStateOf<CrmContact?>(null) }
    var showStandaloneTaskDialog by remember { mutableStateOf(false) }
    var showPurgeRgpdConfirm by remember { mutableStateOf(false) }
    var taskFilter by remember { mutableStateOf("Toutes") }

    val expiredRgpdCount = remember(allContacts) {
        allContacts.count { RgpdComplianceManager.isRetentionExpired(it.rgpdRetentionUntil) }
    }

    val selectedContact = remember(allContacts, selectedContactIdForDetail) {
        allContacts.firstOrNull { it.id == selectedContactIdForDetail }
    }

    val openExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Fichier illisible")
                    val name = uri.lastPathSegment.orEmpty()
                    name to bytes
                }
            }
            loaded.onSuccess { (name, bytes) -> onPrepareExcelImport(bytes, name) }
                .onFailure { onShowToast("Erreur lecture fichier : ${it.localizedMessage}") }
        }
    }

    val exportXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = onBuildExportXlsx() ?: return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            }
            saved.onSuccess { onShowToast("Export Excel (.xlsx) enregistré (${filteredContacts.size} contacts).") }
                .onFailure { onShowToast("Erreur écriture Excel : ${it.localizedMessage}") }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = onBuildExportCsv() ?: return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            }
            saved.onSuccess { onShowToast("Export CSV (.csv) enregistré (${filteredContacts.size} contacts).") }
                .onFailure { onShowToast("Erreur écriture CSV : ${it.localizedMessage}") }
        }
    }

    val activeMembers = remember(members) { members.filter { it.status == "ACTIVE" && it.uid.isNotBlank() } }
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date()) }
    val nowTime = remember { SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date()) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Dynamic Real-Data CRM Dashboard
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = agency?.name ?: "CRM Monty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IvoryWarm
                        )
                        Text(
                            text = "${agency?.saasPlan?.label ?: "Monty SaaS"} • Tableau de bord temps réel",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldChampagne
                        )
                    }
                    Surface(color = EmeraldMid, shape = RoundedCornerShape(10.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GoldChampagne, modifier = Modifier.size(14.dp))
                            Text(
                                text = if (isDirector) "DIRECTION / ADMIN" else "AGENT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IvoryWarm
                            )
                        }
                    }
                }

                // 8 Dynamic KPIs (2 rows of 4)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CrmKpiTile("Total", dashboardMetrics.totalContacts.toString(), Modifier.weight(1f))
                    CrmKpiTile("Nouveaux", dashboardMetrics.newContacts.toString(), Modifier.weight(1f))
                    CrmKpiTile("À qualifier", dashboardMetrics.toQualifyContacts.toString(), Modifier.weight(1f))
                    CrmKpiTile("Actifs", dashboardMetrics.activeProspects.toString(), Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CrmKpiTile("Rel. jour", dashboardMetrics.todayFollowUps.toString(), Modifier.weight(1f))
                    CrmKpiTile(
                        label = "En retard",
                        value = dashboardMetrics.overdueFollowUps.toString(),
                        modifier = Modifier.weight(1f),
                        alert = dashboardMetrics.overdueFollowUps > 0
                    )
                    CrmKpiTile("Convertis", dashboardMetrics.convertedContacts.toString(), Modifier.weight(1f))
                    CrmKpiTile("Conv. %", "${dashboardMetrics.conversionRatePercent}%", Modifier.weight(1f))
                }
            }
        }

        // 2. Sub-tabs: Contacts vs Relances
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Contacts CRM (${filteredContacts.size})",
                "Relances (${tasks.count { it.status != "Terminée" }})"
            ).forEachIndexed { idx, label ->
                val selected = subTabIndex == idx
                Surface(
                    color = if (selected) EmeraldDeep else Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .border(1.dp, if (selected) GoldAntique else BorderSubtle, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { subTabIndex = idx }
                        .testTag("crm_subtab_$idx")
                ) {
                    Box(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (selected) IvoryWarm else ObsidianInk)
                    }
                }
            }
        }

        // 3. Import / Export Excel Toolbar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Importation & Exportation Excel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            openExcelLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel"
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                        modifier = Modifier.testTag("crm_import_excel_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Importer Excel / CSV")
                    }
                    OutlinedButton(
                        onClick = { exportXlsxLauncher.launch("monty_contacts_filtres.xlsx") },
                        modifier = Modifier.testTag("crm_export_xlsx_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exporter .xlsx (${filteredContacts.size})")
                    }
                    OutlinedButton(
                        onClick = { exportCsvLauncher.launch("monty_contacts_filtres.csv") },
                        modifier = Modifier.testTag("crm_export_csv_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exporter .csv")
                    }
                    if (isDirector) {
                        OutlinedButton(
                            onClick = { showPurgeRgpdConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (expiredRgpdCount > 0) ErrorRed else EmeraldDeep
                            ),
                            modifier = Modifier.testTag("crm_purge_rgpd_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (expiredRgpdCount > 0) "Purger RGPD expirés ($expiredRgpdCount)"
                                else "Purger RGPD expirés"
                            )
                        }
                    }
                }
            }
        }

        if (subTabIndex == 0) {
            // Search + Add Contact
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = filterState.query,
                    onValueChange = { q -> onUpdateFilters { it.copy(query = q) } },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Nom, téléphone, e-mail, secteur...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("crm_search_input")
                )
                Button(
                    onClick = { showCreateContactDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAntique, contentColor = ObsidianInk),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("crm_add_contact_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nouveau contact")
                }
            }

            // Multi-criteria Filter & Sort Bar
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterDropdownChip("Type : ${filterState.type}", listOf("Tous") + CrmCatalog.CONTACT_TYPES) { v ->
                    onUpdateFilters { it.copy(type = v) }
                }
                FilterDropdownChip("Statut : ${filterState.status}", listOf("Tous") + CrmCatalog.CONTACT_STATUSES) { v ->
                    onUpdateFilters { it.copy(status = v) }
                }
                FilterDropdownChip("Priorité : ${filterState.priority}", listOf("Toutes") + CrmCatalog.PRIORITIES) { v ->
                    onUpdateFilters { it.copy(priority = v) }
                }
                FilterDropdownChip("Origine : ${filterState.source}", listOf("Toutes") + CrmCatalog.SOURCES) { v ->
                    onUpdateFilters { it.copy(source = v) }
                }
                if (activeMembers.size > 1) {
                    val agentLabel = if (filterState.agentUid == "Tous") "Tous" else activeMembers.firstOrNull { it.uid == filterState.agentUid }?.displayName ?: "Agent"
                    FilterDropdownChip("Agent : $agentLabel", listOf("Tous") + activeMembers.map { it.displayName }) { pickedName ->
                        val uid = if (pickedName == "Tous") "Tous" else activeMembers.firstOrNull { it.displayName == pickedName }?.uid ?: "Tous"
                        onUpdateFilters { it.copy(agentUid = uid) }
                    }
                }
                FilterDropdownChip("Tri : ${filterState.sortMode.label}", CrmSortMode.entries.map { it.label }, leadingSort = true) { label ->
                    val mode = CrmSortMode.entries.firstOrNull { it.label == label } ?: CrmSortMode.CREATED_DESC
                    onUpdateFilters { it.copy(sortMode = mode) }
                }
                Surface(
                    color = if (filterState.showArchived) EmeraldDeep else Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onUpdateFilters { it.copy(showArchived = !it.showArchived) } }
                ) {
                    Text(
                        text = if (filterState.showArchived) "Archivés : Oui" else "Archivés : Non",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (filterState.showArchived) IvoryWarm else ObsidianInk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
                TextButton(onClick = onResetFilters) { Text("Réinitialiser") }
            }

            if (filteredContacts.isEmpty()) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(36.dp))
                        Text("Aucun contact dans cette vue", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                        Text(
                            text = "Ajoutez votre premier contact ou importez un fichier Excel / CSV pour alimenter le CRM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                }
            } else {
                filteredContacts.forEach { contact ->
                    CrmContactCard(
                        contact = contact,
                        isDirector = isDirector,
                        activeMembers = activeMembers,
                        onOpenDetail = { selectedContactIdForDetail = contact.id },
                        onReassign = { m -> onReassignContact(contact, m) },
                        onPlanFollowUp = { scheduleTaskForContact = contact },
                        onArchiveToggle = { onToggleArchiveContact(contact) }
                    )
                }
            }
        } else {
            // Relances & Overdue alerts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Toutes", "En retard", "Aujourd'hui", "À faire", "Terminées").forEach { f ->
                        val sel = taskFilter == f
                        Surface(
                            color = if (sel) EmeraldDeep else Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .border(1.dp, if (sel) EmeraldDeep else BorderSubtle, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { taskFilter = f }
                        ) {
                            Text(f, style = MaterialTheme.typography.labelSmall, color = if (sel) IvoryWarm else ObsidianInk, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { showStandaloneTaskDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne),
                    modifier = Modifier.testTag("crm_add_task_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Relance")
                }
            }

            val visibleTasks = tasks.filter { t ->
                when (taskFilter) {
                    "En retard" -> t.isOverdue(todayIso, nowTime)
                    "Aujourd'hui" -> t.isDueToday(todayIso)
                    "À faire" -> t.status != "Terminée"
                    "Terminées" -> t.status == "Terminée"
                    else -> true
                }
            }

            if (visibleTasks.isEmpty()) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(36.dp))
                        Text("Aucune relance dans ce filtre", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                visibleTasks.forEach { task ->
                    FollowUpTaskCard(
                        task = task,
                        isOverdue = task.isOverdue(todayIso, nowTime),
                        onAdvanceStatus = { nextStatus -> onUpdateTaskStatus(task, nextStatus) }
                    )
                }
            }
        }
    }

    if (showCreateContactDialog) {
        CrmContactFormDialog(
            initialContact = null,
            isDirector = isDirector,
            activeMembers = activeMembers,
            properties = properties,
            onDismiss = { showCreateContactDialog = false },
            onSave = { fn, ln, em, ph, addr, tp, st, pr, bMin, bMax, sec, propTp, surf, rms, fin, dl, src, nt, prop, mbr, fDate, fTime ->
                onAddContact(fn, ln, em, ph, addr, tp, st, pr, bMin, bMax, sec, propTp, surf, rms, fin, dl, src, nt, prop, mbr, fDate, fTime)
                showCreateContactDialog = false
            }
        )
    }

    val editTarget = editingContact
    if (editTarget != null) {
        CrmContactFormDialog(
            initialContact = editTarget,
            isDirector = isDirector,
            activeMembers = activeMembers,
            properties = properties,
            onDismiss = { editingContact = null },
            onSave = { fn, ln, em, ph, addr, tp, st, pr, bMin, bMax, sec, propTp, surf, rms, fin, dl, src, nt, prop, _, _, _ ->
                onUpdateContactFull(editTarget, fn, ln, em, ph, addr, tp, st, pr, bMin, bMax, sec, propTp, surf, rms, fin, dl, src, nt, prop)
                editingContact = null
            }
        )
    }

    if (selectedContact != null) {
        CrmContactDetailDialog(
            contact = selectedContact,
            tasks = tasks,
            properties = properties,
            isDirector = isDirector,
            activeMembers = activeMembers,
            onDismiss = { selectedContactIdForDetail = null },
            onEdit = { editingContact = selectedContact },
            onArchiveToggle = { onToggleArchiveContact(selectedContact) },
            onAddExchange = { ch, summary, status -> onAddContactExchange(selectedContact, ch, summary, status) },
            onPlanFollowUp = { scheduleTaskForContact = selectedContact },
            onLinkProperty = { prop -> onLinkProperty(selectedContact, prop) },
            onReassign = { m -> onReassignContact(selectedContact, m) },
            onExportRgpdJson = { onBuildContactRgpdJson(selectedContact) },
            onEraseRgpd = { onEraseContactRgpd(selectedContact) }
        )
    }

    if (showPurgeRgpdConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeRgpdConfirm = false },
            title = { Text("Purge RGPD (Politique CNIL)", fontWeight = FontWeight.Bold, color = EmeraldDeep) },
            text = {
                Text(
                    if (expiredRgpdCount > 0) {
                        "$expiredRgpdCount fiche(s) contact ont dépassé leur durée légale de conservation. Confirmer la purge définitive ?"
                    } else {
                        "Vérifier et purger les éventuelles fiches CRM ayant dépassé leur échéance légale de conservation ?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPurgeRgpdConfirm = false
                        onPurgeExpiredRgpd()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = IvoryWarm),
                    modifier = Modifier.testTag("crm_purge_rgpd_confirm_button")
                ) {
                    Text("Lancer la purge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeRgpdConfirm = false }) { Text("Annuler") }
            }
        )
    }

    val taskTarget = scheduleTaskForContact
    if (taskTarget != null || showStandaloneTaskDialog) {
        NewFollowUpTaskDialog(
            contact = taskTarget,
            isDirector = isDirector,
            activeMembers = activeMembers,
            onDismiss = {
                scheduleTaskForContact = null
                showStandaloneTaskDialog = false
            },
            onConfirm = { title, channel, dueDate, dueTime, priority, assignee ->
                onAddTask(taskTarget, title, channel, dueDate, dueTime, priority, assignee)
                scheduleTaskForContact = null
                showStandaloneTaskDialog = false
            }
        )
    }

    if (importTable != null) {
        CrmExcelImportWizardDialog(
            table = importTable,
            mapping = importMapping,
            preview = importPreview,
            isDirector = isDirector,
            teamSize = activeMembers.size,
            onMapColumn = onUpdateImportColumnMapping,
            onDismiss = onCancelExcelImport,
            onConfirmImport = onConfirmExcelImport
        )
    }

    if (lastImportReport != null) {
        CrmImportReportDialog(report = lastImportReport, onDismiss = onDismissImportReport)
    }
}

@Composable
private fun CrmKpiTile(label: String, value: String, modifier: Modifier = Modifier, alert: Boolean = false) {
    Surface(
        color = if (alert) Color(0xFF7F1D1D) else EmeraldMid,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (alert) Color(0xFFFEE2E2) else GoldChampagne
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = IvoryWarm,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FilterDropdownChip(
    label: String,
    options: List<String>,
    leadingSort: Boolean = false,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .clickable { open = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (leadingSort) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(14.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = ObsidianInk)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { open = false; onSelect(opt) })
            }
        }
    }
}

@Composable
private fun CrmContactCard(
    contact: CrmContact,
    isDirector: Boolean,
    activeMembers: List<AgencyMember>,
    onOpenDetail: () -> Unit,
    onReassign: (AgencyMember) -> Unit,
    onPlanFollowUp: () -> Unit,
    onArchiveToggle: () -> Unit
) {
    var assignMenuExpanded by remember { mutableStateOf(false) }
    val priorityColors = when (contact.priority) {
        "Urgent" -> ErrorContainer to ErrorRed
        "Élevé" -> WarningContainer to WarningAmber
        "Faible" -> IvoryWarm to SlateMuted
        else -> SuccessContainer to SuccessGreen
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (contact.archived) IvoryWarm else Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenDetail() }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (contact.archived) "${contact.fullName} (Archivé)" else contact.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ObsidianInk
                    )
                    Text(
                        text = "${contact.contactType} • ${contact.sector.ifBlank { "Secteur libre" }} • ${if (contact.budgetMax > 0) MontyMarketData.formatEuro(contact.budgetMax) else "Budget à préciser"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = priorityColors.first, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = contact.priority,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = priorityColors.second,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(color = SuccessContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = contact.status,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (contact.phone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(14.dp))
                        Text(contact.phone, style = MaterialTheme.typography.labelSmall, color = ObsidianInk)
                    }
                }
                if (contact.email.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(14.dp))
                        Text(contact.email, style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    }
                }
            }

            if (contact.linkedPropertyTitle.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.HomeWork, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(14.dp))
                    Text("Bien associé : ${contact.linkedPropertyTitle}", style = MaterialTheme.typography.labelSmall, color = EmeraldDeep)
                }
            }
            if (contact.nextFollowUpAt.isNotBlank()) {
                Text("Prochaine relance : ${contact.nextFollowUpAt}", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
            }

            HorizontalDivider(color = BorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = isDirector && activeMembers.isNotEmpty()) { assignMenuExpanded = true }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.AssignmentInd, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(15.dp))
                        Text(
                            text = contact.assignedToName,
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldDeep,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    DropdownMenu(expanded = assignMenuExpanded, onDismissRequest = { assignMenuExpanded = false }) {
                        activeMembers.forEach { m ->
                            DropdownMenuItem(text = { Text(m.displayName) }, onClick = { assignMenuExpanded = false; onReassign(m) })
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPlanFollowUp) {
                        Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Relance")
                    }
                    TextButton(onClick = onOpenDetail) { Text("Fiche") }
                    TextButton(onClick = onArchiveToggle) {
                        Text(if (contact.archived) "Restaurer" else "Archiver", color = SlateMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUpTaskCard(
    task: FollowUpTask,
    isOverdue: Boolean,
    onAdvanceStatus: (String) -> Unit
) {
    val isDone = task.status == "Terminée"
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isOverdue) Color(0xFFFFF5F5) else if (isDone) IvoryWarm else Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isOverdue) ErrorRed else BorderSubtle, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = {
                    val next = when (task.status) {
                        "À faire" -> "En cours"
                        "En cours" -> "Terminée"
                        else -> "À faire"
                    }
                    onAdvanceStatus(next)
                },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Changer état relance",
                    tint = if (isDone) SuccessGreen else if (isOverdue) ErrorRed else GoldAntique,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (isDone) SlateMuted else ObsidianInk)
                Text(
                    text = "${task.contactName} • ${task.channel} • ${task.dueDate} à ${task.dueTime.ifBlank { "10:00" }} • ${task.assignedToName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isOverdue) {
                        Surface(color = ErrorContainer, shape = RoundedCornerShape(8.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(12.dp))
                                Text("En retard", style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Surface(color = WarningContainer, shape = RoundedCornerShape(8.dp)) {
                        Text("Priorité ${task.priority}", style = MaterialTheme.typography.labelSmall, color = WarningAmber, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Surface(color = if (isDone) SuccessContainer else IvoryWarm, shape = RoundedCornerShape(8.dp)) {
                        Text(task.status, style = MaterialTheme.typography.labelSmall, color = if (isDone) SuccessGreen else EmeraldDeep, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NewFollowUpTaskDialog(
    contact: CrmContact?,
    isDirector: Boolean,
    activeMembers: List<AgencyMember>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, AgencyMember?) -> Unit
) {
    var title by remember { mutableStateOf(if (contact != null) "Relancer ${contact.fullName}" else "") }
    var channel by remember { mutableStateOf("Appel") }
    var priority by remember { mutableStateOf("Haute") }
    var dueDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())) }
    var dueTime by remember { mutableStateOf("14:30") }
    var assignee by remember { mutableStateOf(activeMembers.firstOrNull { it.uid == contact?.assignedToUid } ?: activeMembers.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Programmer une relance", fontWeight = FontWeight.Bold, color = EmeraldDeep) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Action à mener *") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Date (AAAA-MM-JJ)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = dueTime, onValueChange = { dueTime = it }, label = { Text("Heure (HH:MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Canal", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Appel", "Email", "SMS", "Visite").forEach { ch ->
                        Surface(
                            color = if (channel == ch) EmeraldDeep else IvoryWarm,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { channel = ch }
                        ) {
                            Text(ch, style = MaterialTheme.typography.labelSmall, color = if (channel == ch) IvoryWarm else ObsidianInk, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                Text("Priorité", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Haute", "Urgente", "Normale", "Faible").forEach { p ->
                        Surface(
                            color = if (priority == p) EmeraldDeep else IvoryWarm,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { priority = p }
                        ) {
                            Text(p, style = MaterialTheme.typography.labelSmall, color = if (priority == p) IvoryWarm else ObsidianInk, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                if (isDirector && activeMembers.size > 1) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Pour : ${assignee?.displayName ?: "Moi"}")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            activeMembers.forEach { m ->
                                DropdownMenuItem(text = { Text(m.displayName) }, onClick = { assignee = m; menuOpen = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title, channel, dueDate, dueTime, priority, assignee) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
            ) { Text("Programmer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
