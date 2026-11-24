package com.example.ui.saas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExcelCsvCodec
import com.example.data.ExcelImportExecutionResult
import com.example.data.ExcelImportPreview
import com.example.data.MontyImportField
import com.example.data.ParsedSpreadsheetTable
import com.example.data.SaasAgencyRepository
import com.example.data.model.AgencyAuditLog
import com.example.data.model.AgencyMember
import com.example.data.model.AgencyOrg
import com.example.data.model.CrmContact
import com.example.data.model.CrmDashboardMetrics
import com.example.data.model.FollowUpTask
import com.example.data.model.PropertyItem
import com.example.data.model.SaasPlan
import com.example.data.model.computeCrmDashboardMetrics
import com.example.security.AppSecurityManager
import com.example.security.RateLimitChannel
import com.example.security.RateLimitDecision
import com.example.security.RgpdComplianceManager
import com.example.ui.DataUiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CrmSortMode(val label: String) {
    CREATED_DESC("Création (récents)"),
    CREATED_ASC("Création (anciens)"),
    NEXT_FOLLOW_UP("Prochaine relance")
}

data class CrmFilterState(
    val query: String = "",
    val type: String = "Tous",
    val status: String = "Tous",
    val agentUid: String = "Tous",
    val source: String = "Toutes",
    val priority: String = "Toutes",
    val showArchived: Boolean = false,
    val sortMode: CrmSortMode = CrmSortMode.CREATED_DESC
)

@OptIn(ExperimentalCoroutinesApi::class)
class SaasViewModel(
    private val repository: SaasAgencyRepository,
    private val currentUserId: String,
    private val onNotify: (String) -> Unit
) : ViewModel() {

    private val _selectedAgencyId = MutableStateFlow<String?>(null)
    private val _filterState = MutableStateFlow(CrmFilterState())
    val filterState: StateFlow<CrmFilterState> = _filterState.asStateFlow()

    private val _lastInviteCodeGenerated = MutableStateFlow<String?>(null)
    val lastInviteCodeGenerated: StateFlow<String?> = _lastInviteCodeGenerated.asStateFlow()

    private val _importTable = MutableStateFlow<ParsedSpreadsheetTable?>(null)
    val importTable: StateFlow<ParsedSpreadsheetTable?> = _importTable.asStateFlow()

    private val _importMapping = MutableStateFlow<Map<Int, MontyImportField>>(emptyMap())
    val importMapping: StateFlow<Map<Int, MontyImportField>> = _importMapping.asStateFlow()

    private val _lastImportReport = MutableStateFlow<ExcelImportExecutionResult?>(null)
    val lastImportReport: StateFlow<ExcelImportExecutionResult?> = _lastImportReport.asStateFlow()

    val agenciesState: StateFlow<DataUiState<List<AgencyOrg>>> = repository.observeMyAgencies(currentUserId)
        .map<List<AgencyOrg>, DataUiState<List<AgencyOrg>>> { DataUiState.Success(it) }
        .catch { e -> emit(unwrapError(e, "agences")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val activeAgency: StateFlow<AgencyOrg?> = combine(agenciesState, _selectedAgencyId) { state, selId ->
        val list = (state as? DataUiState.Success)?.data.orEmpty()
        list.firstOrNull { it.id == selId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val membersState: StateFlow<DataUiState<List<AgencyMember>>> =
        observeForActiveAgency(
            emptyValue = emptyList(),
            stream = { agencyId -> repository.observeMembers(agencyId, currentUserId) },
            label = "collaborateurs"
        )

    val contactsState: StateFlow<DataUiState<List<CrmContact>>> =
        observeForActiveAgency(
            emptyValue = emptyList(),
            stream = { agencyId -> repository.observeContacts(agencyId, currentUserId) },
            label = "contacts"
        )

    val tasksState: StateFlow<DataUiState<List<FollowUpTask>>> =
        observeForActiveAgency(
            emptyValue = emptyList(),
            stream = { agencyId -> repository.observeTasks(agencyId, currentUserId) },
            label = "relances"
        )

    val auditLogsState: StateFlow<DataUiState<List<AgencyAuditLog>>> =
        observeForActiveAgency(
            emptyValue = emptyList(),
            stream = { agencyId -> repository.observeAuditLogs(agencyId, currentUserId) },
            label = "historique"
        )

    val isDirector: StateFlow<Boolean> = combine(activeAgency, membersState) { agency, mState ->
        val members = (mState as? DataUiState.Success)?.data.orEmpty()
        val myMember = members.firstOrNull { it.uid == currentUserId }
        if (myMember != null) {
            myMember.agencyRole.isManagerOrAdmin()
        } else {
            agency?.directorUids?.contains(currentUserId) == true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val crmDashboardMetrics: StateFlow<CrmDashboardMetrics> = combine(contactsState, tasksState) { cState, tState ->
        val contacts = (cState as? DataUiState.Success)?.data.orEmpty()
        val tasks = (tState as? DataUiState.Success)?.data.orEmpty()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
        val nowTime = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
        computeCrmDashboardMetrics(contacts, tasks, today, nowTime)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CrmDashboardMetrics())

    val filteredContacts: StateFlow<List<CrmContact>> = combine(contactsState, _filterState) { cState, f ->
        val all = (cState as? DataUiState.Success)?.data.orEmpty()
        val q = f.query.trim().lowercase(Locale.ROOT)
        val filtered = all.filter { c ->
            val archivedMatch = f.showArchived || !c.archived
            val typeMatch = f.type == "Tous" || c.contactType == f.type
            val statusMatch = f.status == "Tous" || c.status == f.status
            val agentMatch = f.agentUid == "Tous" || c.assignedToUid == f.agentUid
            val sourceMatch = f.source == "Toutes" || c.source == f.source
            val priorityMatch = f.priority == "Toutes" || c.priority == f.priority
            val queryMatch = q.isEmpty() ||
                c.fullName.lowercase(Locale.ROOT).contains(q) ||
                c.phone.lowercase(Locale.ROOT).contains(q) ||
                c.email.lowercase(Locale.ROOT).contains(q) ||
                c.sector.lowercase(Locale.ROOT).contains(q)
            archivedMatch && typeMatch && statusMatch && agentMatch && sourceMatch && priorityMatch && queryMatch
        }

        when (f.sortMode) {
            CrmSortMode.CREATED_DESC -> filtered.sortedByDescending { it.createdAt?.seconds ?: 0L }
            CrmSortMode.CREATED_ASC -> filtered.sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            CrmSortMode.NEXT_FOLLOW_UP -> filtered.sortedWith(
                compareBy<CrmContact> { it.nextFollowUpAt.isBlank() }.thenBy { it.nextFollowUpAt }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val importPreview: StateFlow<ExcelImportPreview?> = combine(
        _importTable,
        _importMapping,
        contactsState
    ) { table, mapping, cState ->
        if (table == null) {
            null
        } else {
            val existing = (cState as? DataUiState.Success)?.data.orEmpty()
            ExcelCsvCodec.buildImportPreview(table, mapping, existing)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    init {
        viewModelScope.launch {
            repository.bootstrapDefaultAgencyIfEmpty(currentUserId)
                .onFailure { onNotify("Initialisation agence : ${it.localizedMessage}") }
        }
    }

    private fun <T> observeForActiveAgency(
        emptyValue: T,
        stream: (String) -> Flow<T>,
        label: String
    ): StateFlow<DataUiState<T>> {
        return activeAgency
            .flatMapLatest { agency ->
                if (agency == null) {
                    flowOf<DataUiState<T>>(DataUiState.Success(emptyValue))
                } else {
                    stream(agency.id)
                        .map<T, DataUiState<T>> { DataUiState.Success(it) }
                        .catch { e -> emit(unwrapError(e, label)) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)
    }

    fun selectAgency(agencyId: String) {
        _selectedAgencyId.value = agencyId
    }

    fun updateFilters(transform: (CrmFilterState) -> CrmFilterState) {
        _filterState.value = transform(_filterState.value)
    }

    fun resetFilters() {
        _filterState.value = CrmFilterState()
    }

    fun createAgency(name: String, plan: SaasPlan, city: String) {
        viewModelScope.launch {
            repository.createAgency(name, plan, city)
                .onSuccess { newId ->
                    _selectedAgencyId.value = newId
                    onNotify("Espace « $name » créé (${plan.label}).")
                }
                .onFailure { onNotify(it.localizedMessage ?: "Création impossible.") }
        }
    }

    fun updateAgencyPlan(newName: String, newPlan: SaasPlan, newCity: String) {
        val agency = activeAgency.value ?: return
        viewModelScope.launch {
            repository.updateAgencySettings(agency, newName, newPlan, newCity)
                .onSuccess { onNotify("Formule mise à jour : ${newPlan.label}.") }
                .onFailure { onNotify(it.localizedMessage ?: "Mise à jour refusée.") }
        }
    }

    fun inviteCollaborator(email: String, displayName: String) {
        val agency = activeAgency.value ?: return
        if (agency.saasPlan != SaasPlan.AGENCY) {
            onNotify("Passez en Formule Agence pour inviter des collaborateurs.")
            return
        }
        viewModelScope.launch {
            repository.inviteCollaborator(agency, email, displayName)
                .onSuccess { code ->
                    _lastInviteCodeGenerated.value = code
                    onNotify("Invitation prête pour $displayName — Code : $code")
                }
                .onFailure { onNotify(it.localizedMessage ?: "Invitation impossible.") }
        }
    }

    fun joinAgencyByCode(code: String) {
        viewModelScope.launch {
            repository.joinAgencyByInviteCode(code)
                .onSuccess { agencyId ->
                    _selectedAgencyId.value = agencyId
                    onNotify("Vous avez rejoint l'agence avec succès.")
                }
                .onFailure { onNotify(it.localizedMessage ?: "Code d'invitation refusé.") }
        }
    }

    fun addContact(
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
    ) {
        val agency = activeAgency.value ?: return
        viewModelScope.launch {
            repository.addContact(
                agency = agency,
                firstName = firstName,
                lastName = lastName,
                email = email,
                phone = phone,
                postalAddress = postalAddress,
                contactType = contactType,
                status = status,
                priority = priority,
                budgetMin = budgetMin,
                budgetMax = budgetMax,
                sector = sector,
                desiredPropertyType = desiredPropertyType,
                desiredSurfaceMin = desiredSurfaceMin,
                desiredRoomsMin = desiredRoomsMin,
                financingMode = financingMode,
                projectTimeline = projectTimeline,
                source = source,
                notes = notes,
                linkedProperty = linkedProperty,
                assignedMember = assignedMember,
                followUpDate = followUpDate,
                followUpTime = followUpTime
            ).onSuccess {
                onNotify("Contact enregistré dans le CRM.")
            }.onFailure {
                onNotify(it.localizedMessage ?: "Impossible d'enregistrer le contact.")
            }
        }
    }

    fun updateContactFull(
        existingContact: CrmContact,
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
        linkedProperty: PropertyItem?
    ) {
        val agency = activeAgency.value ?: return
        viewModelScope.launch {
            repository.updateContactFull(
                agency = agency,
                existingContact = existingContact,
                firstName = firstName,
                lastName = lastName,
                email = email,
                phone = phone,
                postalAddress = postalAddress,
                contactType = contactType,
                status = status,
                priority = priority,
                budgetMin = budgetMin,
                budgetMax = budgetMax,
                sector = sector,
                desiredPropertyType = desiredPropertyType,
                desiredSurfaceMin = desiredSurfaceMin,
                desiredRoomsMin = desiredRoomsMin,
                financingMode = financingMode,
                projectTimeline = projectTimeline,
                source = source,
                notes = notes,
                linkedProperty = linkedProperty
            ).onSuccess {
                onNotify("Fiche contact mise à jour.")
            }.onFailure {
                onNotify(it.localizedMessage ?: "Mise à jour refusée.")
            }
        }
    }

    fun addContactExchange(
        contact: CrmContact,
        channel: String,
        summary: String,
        updateStatusTo: String? = null
    ) {
        val agency = activeAgency.value ?: return
        viewModelScope.launch {
            repository.addContactExchange(agency, contact, channel, summary, updateStatusTo)
                .onSuccess { onNotify("Échange ($channel) ajouté à la fiche.") }
                .onFailure { onNotify(it.localizedMessage ?: "Ajout impossible.") }
        }
    }

    fun toggleArchiveContact(contact: CrmContact) {
        val agency = activeAgency.value ?: return
        viewModelScope.launch {
            repository.toggleArchiveContact(agency, contact)
                .onSuccess {
                    onNotify(if (!contact.archived) "Contact archivé." else "Contact restauré.")
                }
                .onFailure { onNotify(it.localizedMessage ?: "Opération refusée.") }
        }
    }

    fun linkContactToProperty(contact: CrmContact, property: PropertyItem?) {
        val agency = activeAgency.value ?: return
        viewModelScope.launch {
            repository.linkContactToProperty(agency, contact, property)
                .onSuccess {
                    onNotify(if (property != null) "Bien « ${property.title} » associé." else "Bien dissocié.")
                }
                .onFailure { onNotify(it.localizedMessage ?: "Association impossible.") }
        }
    }

    fun reassignContact(contact: CrmContact, assignee: AgencyMember) {
        val agency = activeAgency.value ?: return
        viewModelScope.launch {
            repository.reassignContact(agency, contact, assignee)
                .onSuccess { onNotify("${contact.fullName} affecté à ${assignee.displayName}.") }
                .onFailure { onNotify(it.localizedMessage ?: "Réaffectation refusée.") }
        }
    }

    fun prepareSpreadsheetImport(bytes: ByteArray, fileName: String) {
        val parsed = runCatching { ExcelCsvCodec.inspectRawTable(bytes, fileName) }
            .getOrElse { err ->
                onNotify("Lecture fichier impossible : ${err.localizedMessage}")
                return
            }
        if (parsed.dataRows.isEmpty()) {
            onNotify("Le fichier ne contient aucune ligne exploitable.")
            return
        }
        _importTable.value = parsed
        _importMapping.value = parsed.suggestedMapping
    }

    fun updateImportColumnMapping(columnIndex: Int, targetField: MontyImportField) {
        _importMapping.value = _importMapping.value.toMutableMap().apply {
            put(columnIndex, targetField)
        }
    }

    fun cancelSpreadsheetImport() {
        _importTable.value = null
        _importMapping.value = emptyMap()
    }

    fun confirmSpreadsheetImport(distributeAcrossTeam: Boolean, autoCreateFollowUps: Boolean) {
        val agency = activeAgency.value ?: return
        val preview = importPreview.value ?: return
        val members = (membersState.value as? DataUiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            repository.importContactsBatch(
                agency = agency,
                preview = preview,
                activeMembers = members,
                distributeAcrossTeam = distributeAcrossTeam,
                autoCreateFollowUps = autoCreateFollowUps
            ).onSuccess { execResult ->
                _importTable.value = null
                _importMapping.value = emptyMap()
                _lastImportReport.value = execResult
            }.onFailure {
                onNotify(it.localizedMessage ?: "Échec de l'import.")
            }
        }
    }

    fun dismissImportReport() {
        _lastImportReport.value = null
    }

    fun buildExportCsvBytes(): ByteArray? {
        val agency = activeAgency.value ?: return null
        val contacts = filteredContacts.value
        val bytes = ExcelCsvCodec.exportContactsToCsv(contacts)
        viewModelScope.launch { repository.recordExcelExport(agency, "CSV Excel", contacts.size) }
        return bytes
    }

    fun buildExportXlsxBytes(): ByteArray? {
        val agency = activeAgency.value ?: return null
        val contacts = filteredContacts.value
        val bytes = ExcelCsvCodec.exportContactsToXlsx(contacts, agency.name)
        viewModelScope.launch { repository.recordExcelExport(agency, "Excel .xlsx", contacts.size) }
        return bytes
    }

    fun addFollowUpTask(
        contact: CrmContact?,
        title: String,
        channel: String,
        dueDate: String,
        dueTime: String,
        priority: String,
        assignee: AgencyMember?
    ) {
        val agency = activeAgency.value ?: return
        viewModelScope.launch {
            repository.addFollowUpTask(agency, contact, title, channel, dueDate, dueTime, priority, assignee)
                .onSuccess { onNotify("Relance programmée ($dueDate à $dueTime).") }
                .onFailure { onNotify(it.localizedMessage ?: "Création impossible.") }
        }
    }

    fun updateTaskStatus(task: FollowUpTask, newStatus: String) {
        val agency = activeAgency.value ?: return
        viewModelScope.launch {
            repository.updateTaskStatus(agency, task, newStatus)
                .onSuccess { onNotify("Relance marquée « $newStatus ».") }
                .onFailure { onNotify(it.localizedMessage ?: "Action refusée.") }
        }
    }

    fun eraseContactRgpd(contact: CrmContact) {
        val agency = activeAgency.value ?: return
        if (!isDirector.value) {
            onNotify("Droit à l'effacement RGPD réservé au responsable d'agence.")
            return
        }
        viewModelScope.launch {
            repository.eraseContactRgpd(agency, contact)
                .onSuccess { onNotify("Fiche contact effacée définitivement (Art. 17 RGPD).") }
                .onFailure { onNotify(it.localizedMessage ?: "Effacement RGPD refusé.") }
        }
    }

    fun purgeExpiredContactsRgpd() {
        val agency = activeAgency.value ?: return
        if (!isDirector.value) {
            onNotify("Purge RGPD réservée au responsable d'agence.")
            return
        }
        val contacts = (contactsState.value as? DataUiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            repository.purgeExpiredRgpdContacts(agency, contacts)
                .onSuccess { count ->
                    if (count > 0) {
                        onNotify("$count contact(s) expiré(s) purgé(s) selon la politique CNIL.")
                    } else {
                        onNotify("Aucune fiche CRM en dépassement de durée légale RGPD.")
                    }
                }
                .onFailure { onNotify(it.localizedMessage ?: "Purge RGPD impossible.") }
        }
    }

    fun buildContactRgpdExportJson(contact: CrmContact): String? {
        val decision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.SHARE_EXPORT)
        if (decision is RateLimitDecision.Blocked) {
            onNotify(decision.reason)
            return null
        }
        val tasks = (tasksState.value as? DataUiState.Success)?.data.orEmpty()
        val actorName = activeAgency.value?.name?.ifBlank { "Agence" } ?: "Agence"
        return RgpdComplianceManager.buildContactRgpdJson(contact, tasks, actorName)
    }

    private fun unwrapError(error: Throwable, label: String): DataUiState.Error {
        val fs = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        val msg = fs?.localizedMessage ?: error.localizedMessage ?: "Erreur $label"
        return DataUiState.Error(msg)
    }
}
