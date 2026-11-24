package com.example.ui.advisor

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.AdvisorAiAction
import com.example.data.GeoCommuteService
import com.example.data.GeocodedAddressResult
import com.example.data.PropertyGeoUrbanismReport
import com.example.data.model.AdvisorTab
import com.example.data.model.ClientDossier
import com.example.data.model.FinanceMetrics
import com.example.data.model.HcsfCapacityResult
import com.example.data.model.OfferRecord
import com.example.data.model.PropertyDraftEnhancement
import com.example.data.model.PropertyItem
import com.example.data.model.VisitRecord
import com.example.data.model.WorkspaceMode
import com.example.ui.AiGenState
import com.example.ui.AutoAiUiState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DataUiState
import com.example.ui.SimulatorInput
import com.example.ui.components.MontyBrandHeader
import com.example.ui.components.WorkspaceSwitcherBar
import com.example.ui.saas.AgencyWorkspaceTab
import com.example.ui.saas.CrmContactsAndTasksTab
import com.example.ui.saas.SaasViewModel
import com.example.ui.seller.SellerViewModel
import com.example.ui.seller.SellerWorkspaceContent
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.GoldAntique
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.SlateMuted

@Composable
fun AdvisorWorkspaceScreen(
    clients: List<ClientDossier>,
    activeClient: ClientDossier?,
    properties: List<PropertyItem>,
    visits: List<VisitRecord>,
    offers: List<OfferRecord>,
    activeTab: AdvisorTab,
    comparePropertyIds: List<String>,
    simulatorInput: SimulatorInput,
    financeMetrics: FinanceMetrics,
    hcsfCapacityResult: HcsfCapacityResult,
    advisorAiState: AiGenState,
    autoAiState: AutoAiUiState,
    acquereursViewModel: AcquereursViewModel,
    saasViewModel: SaasViewModel,
    sellerViewModel: SellerViewModel,
    onSelectTab: (AdvisorTab) -> Unit,
    onSwitchMode: (WorkspaceMode) -> Unit,
    onOpenPdfPreview: () -> Unit,
    onOpenCapacityScreen: () -> Unit,
    onRefreshAutoAi: () -> Unit,
    onSelectClient: (String) -> Unit,
    onAddClient: (String, String, String, String, Long, Long, Long, String, String, Long, Long, String, String, String) -> Unit,
    onUpdateClientStage: (String, Int) -> Unit,
    onTogglePortalAccess: (ClientDossier) -> Unit,
    onSavePrivateNotes: (String, String) -> Unit,
    onAcknowledgeClientUpdates: (String) -> Unit,
    onAddProperty: (String, String, String, String, Long, Long, Long, Long, String, Long, Long, Long, String, String, String, String) -> Unit,
    onEnhancePropertyDraft: suspend (String, String, String, Long, Long, Long, String, String, String, String, String, ClientDossier?) -> Result<PropertyDraftEnhancement>,
    onToggleCompareProperty: (String) -> Unit,
    onLoadPropertyInSimulator: (PropertyItem) -> Unit,
    onAddVisit: (String, String, String, String, String, Double?, String, String, String, String, String) -> Unit,
    onAddOffer: (ClientDossier, PropertyItem, Long, String, String, String) -> Unit,
    onUpdateSimulator: ((SimulatorInput) -> SimulatorInput) -> Unit,
    onGenerateAdvisorAi: (AdvisorAiAction, ClientDossier, PropertyItem, String) -> Unit,
    mapUrbanismReport: PropertyGeoUrbanismReport? = null,
    isLoadingUrbanism: Boolean = false,
    onInspectMapLocation: (Double, Double, String) -> Unit = { _, _, _ -> },
    onClearUrbanismReport: () -> Unit = {},
    onSearchBanAddresses: suspend (String) -> Result<List<GeocodedAddressResult>> = { Result.success(emptyList()) },
    onFetchOsmTileBytes: suspend (Int, Int, Int) -> ByteArray? = { _, _, _ -> null },
    onValidatePropertyPublication: (PropertyItem) -> Unit = {},
    onUpdatePropertyMandateAndValuation: (String, Long, String, String, String, String, Double, String, Long, Long, Long, Int, String, String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onShowToast: (String) -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(enabled = activeTab != AdvisorTab.DASHBOARD) {
        onSelectTab(AdvisorTab.DASHBOARD)
    }

    var showNewClientModal by remember { mutableStateOf(false) }
    var showNewPropertyModal by remember { mutableStateOf(false) }
    var showNewVisitModal by remember { mutableStateOf(false) }
    var showNewOfferModal by remember { mutableStateOf(false) }
    var showClientPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = IvoryWarm,
        topBar = {
            Column {
                WorkspaceSwitcherBar(
                    currentMode = WorkspaceMode.ADVISOR,
                    activeClient = activeClient,
                    onSwitchMode = onSwitchMode,
                    onOpenDossierPdf = onOpenPdfPreview,
                    onSignOut = onSignOut
                )
                Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MontyBrandHeader(light = false, subtitle = "Montpellier • Conseiller")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box {
                                    Surface(
                                        color = IvoryWarm,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { showClientPicker = true }
                                            .testTag("active_client_dropdown")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = activeClient?.name?.substringBefore("—")?.trim() ?: "Client",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = EmeraldDeep
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Changer client", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showClientPicker,
                                        onDismissRequest = { showClientPicker = false }
                                    ) {
                                        clients.forEach { cli ->
                                            DropdownMenuItem(
                                                text = { Text(cli.name) },
                                                onClick = {
                                                    onSelectClient(cli.id)
                                                    showClientPicker = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = EmeraldDeep,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showNewClientModal = true }
                                        .testTag("top_new_dossier_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Nouveau dossier", tint = GoldChampagne, modifier = Modifier.size(15.dp))
                                        Text("Dossier", style = MaterialTheme.typography.labelSmall, color = IvoryWarm)
                                    }
                                }
                            }
                        }

                        // Barre de navigation horizontale des 8 modules Conseiller
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AdvisorTab.entries.forEach { tab ->
                                val selected = tab == activeTab
                                val icon = when (tab) {
                                    AdvisorTab.DASHBOARD -> Icons.Default.Dashboard
                                    AdvisorTab.CRM -> Icons.Default.Contacts
                                    AdvisorTab.TEAM -> Icons.Default.Apartment
                                    AdvisorTab.SELLER -> Icons.Default.HomeWork
                                    AdvisorTab.ACQUEREURS -> Icons.Default.PersonSearch
                                    AdvisorTab.CLIENTS -> Icons.Default.Groups
                                    AdvisorTab.PROPERTIES -> Icons.Default.Business
                                    AdvisorTab.MAP -> Icons.Default.Map
                                    AdvisorTab.VISITS -> Icons.Default.CalendarMonth
                                    AdvisorTab.OFFERS -> Icons.Default.Description
                                    AdvisorTab.FINANCE -> Icons.Default.Calculate
                                    AdvisorTab.AI -> Icons.Default.AutoAwesome
                                    AdvisorTab.MARKET -> Icons.AutoMirrored.Filled.TrendingUp
                                }
                                Surface(
                                    color = if (selected) EmeraldDeep else IvoryWarm,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .border(
                                            1.dp,
                                            if (selected) GoldAntique else BorderSubtle,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectTab(tab) }
                                        .testTag("advisor_tab_${tab.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = tab.label,
                                            tint = if (selected) GoldChampagne else SlateMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) IvoryWarm else ObsidianInk
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                when (activeTab) {
                    AdvisorTab.DASHBOARD -> AdvisorDashboardTab(
                        clients = clients,
                        activeClient = activeClient,
                        properties = properties,
                        visits = visits,
                        offers = offers,
                        autoAiState = autoAiState,
                        onRefreshAutoAi = onRefreshAutoAi,
                        onSelectClient = onSelectClient,
                        onAcknowledgeClientUpdate = onAcknowledgeClientUpdates,
                        onOpenBuyerPortal = { onSwitchMode(WorkspaceMode.BUYER) },
                        onOpenCapacityScreen = onOpenCapacityScreen,
                        onNavigateTab = onSelectTab,
                        onOpenNewClientDialog = { showNewClientModal = true }
                    )

                    AdvisorTab.CRM -> {
                        val activeAgency by saasViewModel.activeAgency.collectAsStateWithLifecycle()
                        val isDirector by saasViewModel.isDirector.collectAsStateWithLifecycle()
                        val membersState by saasViewModel.membersState.collectAsStateWithLifecycle()
                        val contactsState by saasViewModel.contactsState.collectAsStateWithLifecycle()
                        val filteredContacts by saasViewModel.filteredContacts.collectAsStateWithLifecycle()
                        val tasksState by saasViewModel.tasksState.collectAsStateWithLifecycle()
                        val crmMetrics by saasViewModel.crmDashboardMetrics.collectAsStateWithLifecycle()
                        val filterState by saasViewModel.filterState.collectAsStateWithLifecycle()
                        val importTable by saasViewModel.importTable.collectAsStateWithLifecycle()
                        val importMapping by saasViewModel.importMapping.collectAsStateWithLifecycle()
                        val importPreview by saasViewModel.importPreview.collectAsStateWithLifecycle()
                        val lastImportReport by saasViewModel.lastImportReport.collectAsStateWithLifecycle()

                        CrmContactsAndTasksTab(
                            agency = activeAgency,
                            isDirector = isDirector,
                            members = (membersState as? DataUiState.Success)?.data.orEmpty(),
                            allContacts = (contactsState as? DataUiState.Success)?.data.orEmpty(),
                            filteredContacts = filteredContacts,
                            tasks = (tasksState as? DataUiState.Success)?.data.orEmpty(),
                            properties = properties,
                            dashboardMetrics = crmMetrics,
                            filterState = filterState,
                            importTable = importTable,
                            importMapping = importMapping,
                            importPreview = importPreview,
                            lastImportReport = lastImportReport,
                            onUpdateFilters = saasViewModel::updateFilters,
                            onResetFilters = saasViewModel::resetFilters,
                            onAddContact = saasViewModel::addContact,
                            onUpdateContactFull = saasViewModel::updateContactFull,
                            onAddContactExchange = saasViewModel::addContactExchange,
                            onToggleArchiveContact = saasViewModel::toggleArchiveContact,
                            onLinkProperty = saasViewModel::linkContactToProperty,
                            onReassignContact = saasViewModel::reassignContact,
                            onPrepareExcelImport = saasViewModel::prepareSpreadsheetImport,
                            onUpdateImportColumnMapping = saasViewModel::updateImportColumnMapping,
                            onCancelExcelImport = saasViewModel::cancelSpreadsheetImport,
                            onConfirmExcelImport = saasViewModel::confirmSpreadsheetImport,
                            onDismissImportReport = saasViewModel::dismissImportReport,
                            onBuildExportCsv = saasViewModel::buildExportCsvBytes,
                            onBuildExportXlsx = saasViewModel::buildExportXlsxBytes,
                            onAddTask = saasViewModel::addFollowUpTask,
                            onUpdateTaskStatus = saasViewModel::updateTaskStatus,
                            onBuildContactRgpdJson = saasViewModel::buildContactRgpdExportJson,
                            onEraseContactRgpd = saasViewModel::eraseContactRgpd,
                            onPurgeExpiredRgpd = saasViewModel::purgeExpiredContactsRgpd,
                            onShowToast = onShowToast
                        )
                    }

                    AdvisorTab.TEAM -> {
                        val agenciesState by saasViewModel.agenciesState.collectAsStateWithLifecycle()
                        val activeAgency by saasViewModel.activeAgency.collectAsStateWithLifecycle()
                        val isDirector by saasViewModel.isDirector.collectAsStateWithLifecycle()
                        val membersState by saasViewModel.membersState.collectAsStateWithLifecycle()
                        val auditLogsState by saasViewModel.auditLogsState.collectAsStateWithLifecycle()
                        val lastInviteCode by saasViewModel.lastInviteCodeGenerated.collectAsStateWithLifecycle()

                        AgencyWorkspaceTab(
                            agencies = (agenciesState as? DataUiState.Success)?.data.orEmpty(),
                            activeAgency = activeAgency,
                            isDirector = isDirector,
                            members = (membersState as? DataUiState.Success)?.data.orEmpty(),
                            auditLogs = (auditLogsState as? DataUiState.Success)?.data.orEmpty(),
                            lastInviteCode = lastInviteCode,
                            onSelectAgency = saasViewModel::selectAgency,
                            onCreateAgency = saasViewModel::createAgency,
                            onUpdateAgencyPlan = saasViewModel::updateAgencyPlan,
                            onInviteCollaborator = saasViewModel::inviteCollaborator,
                            onJoinAgencyByCode = saasViewModel::joinAgencyByCode,
                            onShowToast = onShowToast
                        )
                    }

                    AdvisorTab.SELLER -> {
                        val sellerDossiersState by sellerViewModel.dossiersState.collectAsStateWithLifecycle()
                        val activeSellerDossier by sellerViewModel.activeDossier.collectAsStateWithLifecycle()
                        val contributorRole by sellerViewModel.contributorRole.collectAsStateWithLifecycle()
                        val dvfUiState by sellerViewModel.dvfUiState.collectAsStateWithLifecycle()
                        val isSummarizingAi by sellerViewModel.isSummarizingAi.collectAsStateWithLifecycle()

                        SellerWorkspaceContent(
                            properties = properties,
                            dossiersState = sellerDossiersState,
                            activeDossier = activeSellerDossier,
                            contributorRole = contributorRole,
                            dvfUiState = dvfUiState,
                            isSummarizingAi = isSummarizingAi,
                            sellerViewModel = sellerViewModel,
                            onUpdatePropertyMandateAndValuation = onUpdatePropertyMandateAndValuation,
                            onShowToast = onShowToast
                        )
                    }

                    AdvisorTab.ACQUEREURS -> AcquereursScreen(
                        viewModel = acquereursViewModel,
                        properties = properties,
                        onOpenBuyerPortal = { onSwitchMode(WorkspaceMode.BUYER) },
                        onShowToast = onShowToast
                    )

                    AdvisorTab.CLIENTS -> AdvisorClientsTab(
                        clients = clients,
                        activeClient = activeClient,
                        onSelectClient = onSelectClient,
                        onUpdateStage = onUpdateClientStage,
                        onTogglePortal = onTogglePortalAccess,
                        onSavePrivateNotes = onSavePrivateNotes,
                        onAcknowledgeUpdates = onAcknowledgeClientUpdates,
                        onOpenBuyerPortal = { onSwitchMode(WorkspaceMode.BUYER) },
                        onOpenNewClientDialog = { showNewClientModal = true }
                    )

                    AdvisorTab.PROPERTIES -> AdvisorPropertiesTab(
                        properties = properties,
                        activeClient = activeClient,
                        comparePropertyIds = comparePropertyIds,
                        onToggleCompare = onToggleCompareProperty,
                        onSimulateProperty = { prop ->
                            onLoadPropertyInSimulator(prop)
                            onSelectTab(AdvisorTab.FINANCE)
                        },
                        onOpenSellerDossier = { prop ->
                            sellerViewModel.selectProperty(prop)
                            onSelectTab(AdvisorTab.SELLER)
                        },
                        onValidatePropertyPublication = onValidatePropertyPublication,
                        onOpenPropertyOnMap = { prop ->
                            val (lat, lon) = GeoCommuteService.resolveCoordinatesForProperty(prop)
                            onInspectMapLocation(lat, lon, prop.address.ifBlank { prop.title })
                            onSelectTab(AdvisorTab.MAP)
                        },
                        onOpenNewPropertyDialog = { showNewPropertyModal = true }
                    )

                    AdvisorTab.MAP -> AdvisorMapUrbanismTab(
                        properties = properties,
                        activeClient = activeClient,
                        urbanismReport = mapUrbanismReport,
                        isLoadingUrbanism = isLoadingUrbanism,
                        onInspectLocation = onInspectMapLocation,
                        onClearReport = onClearUrbanismReport,
                        onSearchBanAddresses = onSearchBanAddresses,
                        onFetchOsmTileBytes = onFetchOsmTileBytes,
                        onShowToast = onShowToast
                    )

                    AdvisorTab.VISITS -> AdvisorVisitsTab(
                        visits = visits,
                        clients = clients,
                        properties = properties,
                        onOpenNewVisitDialog = { showNewVisitModal = true }
                    )

                    AdvisorTab.OFFERS -> AdvisorOffersTab(
                        offers = offers,
                        clients = clients,
                        properties = properties,
                        onOpenNewOfferDialog = { showNewOfferModal = true }
                    )

                    AdvisorTab.FINANCE -> AdvisorFinanceTab(
                        simulatorInput = simulatorInput,
                        financeMetrics = financeMetrics,
                        hcsfCapacityResult = hcsfCapacityResult,
                        onUpdateSimulator = onUpdateSimulator,
                        onOpenCapacityScreen = onOpenCapacityScreen
                    )

                    AdvisorTab.AI -> AdvisorAiTab(
                        activeClient = activeClient,
                        properties = properties,
                        visits = visits,
                        aiState = advisorAiState,
                        onGenerate = onGenerateAdvisorAi,
                        onShowToast = onShowToast
                    )

                    AdvisorTab.MARKET -> AdvisorMarketTab()
                }
            }
        }
    }

    if (showNewClientModal) {
        NewClientDialog(
            onDismiss = { showNewClientModal = false },
            onConfirm = { name, phone, email, sit, budget, down, income, sec, type, surf, rooms, ess, delai, notes ->
                onAddClient(name, phone, email, sit, budget, down, income, sec, type, surf, rooms, ess, delai, notes)
                showNewClientModal = false
            }
        )
    }

    if (showNewPropertyModal) {
        NewPropertyDialog(
            onDismiss = { showNewPropertyModal = false },
            onEnhanceDraft = { t, s, ty, p, sf, bd, d, ext, pk, st, wk ->
                onEnhancePropertyDraft(t, s, ty, p, sf, bd, d, ext, pk, st, wk, activeClient)
            },
            onConfirm = { title, sec, addr, type, price, surf, rooms, beds, dpe, chg, tax, works, ext, park, str, weak ->
                onAddProperty(title, sec, addr, type, price, surf, rooms, beds, dpe, chg, tax, works, ext, park, str, weak)
                showNewPropertyModal = false
            }
        )
    }

    if (showNewVisitModal) {
        NewVisitDialog(
            clients = clients,
            properties = properties,
            initialClientId = activeClient?.id.orEmpty(),
            onDismiss = { showNewVisitModal = false },
            onConfirm = { cliId, propId, date, slot, status, rating, imp, pos, neg, tech, next ->
                onAddVisit(cliId, propId, date, slot, status, rating, imp, pos, neg, tech, next)
                showNewVisitModal = false
            }
        )
    }

    if (showNewOfferModal) {
        NewOfferDialog(
            clients = clients,
            properties = properties,
            initialClientId = activeClient?.id.orEmpty(),
            onDismiss = { showNewOfferModal = false },
            onConfirm = { cli, prop, price, seller, cond, arg ->
                onAddOffer(cli, prop, price, seller, cond, arg)
                showNewOfferModal = false
            }
        )
    }
}
