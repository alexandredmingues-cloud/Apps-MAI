package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.DvfValuationService
import com.example.data.MontyAiService
import com.example.data.MontyRepository
import com.example.data.SaasAgencyRepository
import com.example.data.SellerRepository
import com.example.data.local.AcquereurRepository
import com.example.data.local.MontyLocalDatabase
import com.example.data.model.WorkspaceMode
import com.example.firebase.configureAppCheck
import com.example.security.AppSecurityManager
import com.example.security.RateLimitChannel
import com.example.security.RateLimitDecision
import com.example.security.RgpdComplianceManager
import com.example.security.SecurityPosture
import com.example.ui.DataUiState
import com.example.ui.MontyViewModel
import com.example.ui.advisor.AcquereursViewModel
import com.example.ui.advisor.AdvisorWorkspaceScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.buyer.BuyerPortalScreen
import com.example.ui.components.DossierPdfPreviewDialog
import com.example.ui.finance.HcsfCapacityScreen
import com.example.ui.saas.SaasViewModel
import com.example.ui.seller.SellerViewModel
import com.example.ui.seller.SellerWorkspaceScreen
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.IvoryWarm
import com.example.ui.theme.MontyImmoTheme
import com.example.ui.theme.SlateMuted
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Anti-screen-scraping in release builds; allow virtual display capture in debug preview
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        window.decorView.filterTouchesWhenObscured = true
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            MontyImmoTheme {
                MontyAppRoot()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun MontyAppRoot() {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val securityPosture = remember { AppSecurityManager.inspectRuntimeIntegrity(context) }
    var isAppBackgrounded by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        view.filterTouchesWhenObscured = true
        onDispose { }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> isAppBackgrounded = true
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> isAppBackgrounded = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (securityPosture.shouldBlockExecution) {
        TamperBlockedScreen(posture = securityPosture)
        return
    }

    val auth = remember { Firebase.auth }
    val currentUser by remember(auth) { auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    Box(modifier = Modifier.fillMaxSize()) {
        val user = currentUser
        if (user == null) {
            AuthScreen(onAuthSuccess = { /* AuthStateListener drives navigation */ })
        } else {
            key(user.uid) {
                AuthenticatedMontyWorkspace(userId = user.uid)
            }
        }

        if (isAppBackgrounded) {
            PrivacyCurtainOverlay()
        }
    }
}

@Composable
private fun PrivacyCurtainOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EmeraldDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Écran protégé",
                tint = GoldChampagne,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "MON AGENT IMMO",
                style = MaterialTheme.typography.titleLarge,
                color = IvoryWarm,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Données confidentielles protégées en arrière-plan",
                style = MaterialTheme.typography.bodySmall,
                color = GoldChampagne
            )
        }
    }
}

@Composable
private fun TamperBlockedScreen(posture: SecurityPosture) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EmeraldDeep)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Alerte intégrité",
                    tint = Color(0xFFB91C1C),
                    modifier = Modifier.size(42.dp)
                )
                Text(
                    text = "Intégrité de l'application compromise",
                    style = MaterialTheme.typography.titleLarge,
                    color = EmeraldDeep,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Par sécurité, Mon Agent Immo a bloqué l'exécution car une tentative d'altération ou d'instrumentation a été détectée (${posture.hookFrameworksDetected.joinToString(", ").ifBlank { "débogage non autorisé" }}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedMontyWorkspace(userId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: MontyViewModel = viewModel(
        key = userId,
        factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = MontyRepository(db = db, auth = Firebase.auth)
                val ai = MontyAiService()
                MontyViewModel(repository = repo, aiService = ai, currentUserId = userId)
            }
        }
    )

    val acquereursViewModel: AcquereursViewModel = viewModel(
        key = "acquereurs-$userId",
        factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val localDb = MontyLocalDatabase.getInstance(app)
                val localRepo = AcquereurRepository(localDb.acquereurDao())
                AcquereursViewModel(repository = localRepo)
            }
        }
    )

    val saasViewModel: SaasViewModel = viewModel(
        key = "saas-$userId",
        factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val saasRepo = SaasAgencyRepository(db = db, auth = Firebase.auth)
                SaasViewModel(
                    repository = saasRepo,
                    currentUserId = userId,
                    onNotify = viewModel::showToast
                )
            }
        }
    )

    val sellerViewModel: SellerViewModel = viewModel(
        key = "seller-$userId",
        factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val sellerRepo = SellerRepository(db = db, auth = Firebase.auth)
                val dvfService = DvfValuationService()
                val aiService = MontyAiService()
                SellerViewModel(
                    repository = sellerRepo,
                    dvfService = dvfService,
                    aiService = aiService,
                    currentUserId = userId,
                    onNotify = viewModel::showToast,
                    onSyncPropertyToPortfolio = viewModel::upsertSellerListingPropertySync
                )
            }
        }
    )

    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val advisorTab by viewModel.advisorTab.collectAsStateWithLifecycle()
    val buyerTab by viewModel.buyerTab.collectAsStateWithLifecycle()
    val clientsState by viewModel.clientsState.collectAsStateWithLifecycle()
    val propertiesState by viewModel.propertiesState.collectAsStateWithLifecycle()
    val visitsState by viewModel.visitsState.collectAsStateWithLifecycle()
    val offersState by viewModel.offersState.collectAsStateWithLifecycle()
    val buyerChatsState by viewModel.buyerChatsState.collectAsStateWithLifecycle()
    val activeClient by viewModel.activeClient.collectAsStateWithLifecycle()
    val comparePropertyIds by viewModel.comparePropertyIds.collectAsStateWithLifecycle()
    val simulatorInput by viewModel.simulatorInput.collectAsStateWithLifecycle()
    val financeMetrics by viewModel.financeMetrics.collectAsStateWithLifecycle()
    val showCapacityScreen by viewModel.showCapacityScreen.collectAsStateWithLifecycle()
    val hcsfCapacityInput by viewModel.hcsfCapacityInput.collectAsStateWithLifecycle()
    val hcsfCapacityResult by viewModel.hcsfCapacityResult.collectAsStateWithLifecycle()
    val advisorAiState by viewModel.advisorAiState.collectAsStateWithLifecycle()
    val buyerAiState by viewModel.buyerAiState.collectAsStateWithLifecycle()
    val aiAutoEnhanceEnabled by viewModel.aiAutoEnhanceEnabled.collectAsStateWithLifecycle()
    val autoAiState by viewModel.autoAiState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val sellerDossiersState by sellerViewModel.dossiersState.collectAsStateWithLifecycle()
    val activeSellerDossier by sellerViewModel.activeDossier.collectAsStateWithLifecycle()
    val sellerContributorRole by sellerViewModel.contributorRole.collectAsStateWithLifecycle()
    val dvfUiState by sellerViewModel.dvfUiState.collectAsStateWithLifecycle()
    val isSummarizingSellerAi by sellerViewModel.isSummarizingAi.collectAsStateWithLifecycle()
    val mapUrbanismReport by viewModel.mapUrbanismReport.collectAsStateWithLifecycle()
    val isLoadingUrbanism by viewModel.isLoadingUrbanism.collectAsStateWithLifecycle()
    val crmContactsState by saasViewModel.contactsState.collectAsStateWithLifecycle()
    val localAcquereurs by acquereursViewModel.allAcquereurs.collectAsStateWithLifecycle()

    var showPdfPreview by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val onSignOut: () -> Unit = {
        signOutUser(credentialManager = credentialManager, scope = scope)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val cState = clientsState) {
            is DataUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(IvoryWarm),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = EmeraldDeep,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Chargement de votre espace Mon Agent Immo...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldDeep
                        )
                    }
                }
            }

            is DataUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(IvoryWarm)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Erreur de synchronisation Cloud",
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldDeep
                            )
                            Text(
                                text = cState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMuted
                            )
                            Button(
                                onClick = onSignOut,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = GoldChampagne)
                            ) {
                                Text("Changer de compte Google")
                            }
                        }
                    }
                }
            }

            is DataUiState.Success -> {
                val clients = cState.data
                val properties = (propertiesState as? DataUiState.Success)?.data.orEmpty()
                val visits = (visitsState as? DataUiState.Success)?.data.orEmpty()
                val offers = (offersState as? DataUiState.Success)?.data.orEmpty()
                val buyerChats = (buyerChatsState as? DataUiState.Success)?.data.orEmpty()
                val resolvedClient = activeClient ?: clients.firstOrNull()

                val sellerDossiers = (sellerDossiersState as? DataUiState.Success)?.data.orEmpty()

                if (showCapacityScreen) {
                    HcsfCapacityScreen(
                        input = hcsfCapacityInput,
                        result = hcsfCapacityResult,
                        activeClient = resolvedClient,
                        properties = properties,
                        onUpdateInput = viewModel::updateHcsfCapacityInput,
                        onResetFromClient = { viewModel.resetHcsfFromClient(resolvedClient) },
                        onApplyToSimulator = { viewModel.applyHcsfToSimulator(hcsfCapacityInput, hcsfCapacityResult) },
                        onSaveBudgetToDossier = {
                            resolvedClient?.let {
                                viewModel.applyHcsfToClientBudget(it, hcsfCapacityInput, hcsfCapacityResult)
                            }
                        },
                        onShowToast = viewModel::showToast,
                        onBack = viewModel::closeCapacityScreen
                    )
                } else if (workspace == WorkspaceMode.SELLER) {
                    SellerWorkspaceScreen(
                        properties = properties,
                        activeClient = resolvedClient,
                        dossiersState = sellerDossiersState,
                        activeDossier = activeSellerDossier,
                        contributorRole = sellerContributorRole,
                        dvfUiState = dvfUiState,
                        isSummarizingAi = isSummarizingSellerAi,
                        sellerViewModel = sellerViewModel,
                        onUpdatePropertyMandateAndValuation = viewModel::updatePropertyMandateAndValuation,
                        onSwitchMode = viewModel::setWorkspace,
                        onOpenPdfPreview = { showPdfPreview = true },
                        onShowToast = viewModel::showToast,
                        onSignOut = onSignOut
                    )
                } else if (workspace == WorkspaceMode.BUYER && resolvedClient != null) {
                    BuyerPortalScreen(
                        client = resolvedClient,
                        properties = properties,
                        visits = visits,
                        sellerDossiers = sellerDossiers,
                        buyerChats = buyerChats,
                        activeBuyerTab = buyerTab,
                        buyerAiState = buyerAiState,
                        aiAutoEnhanceEnabled = aiAutoEnhanceEnabled,
                        autoAiState = autoAiState,
                        simulatorInput = simulatorInput,
                        financeMetrics = financeMetrics,
                        hcsfCapacityResult = hcsfCapacityResult,
                        onSelectTab = viewModel::setBuyerTab,
                        onSwitchMode = viewModel::setWorkspace,
                        onOpenPdfPreview = { showPdfPreview = true },
                        onOpenCapacityScreen = viewModel::openCapacityScreen,
                        onToggleAutoAi = viewModel::toggleAiAutoEnhance,
                        onRefreshAutoAi = viewModel::refreshAutoRelevance,
                        onApplyAiOptimization = { opt -> viewModel.applyAiCriteriaOptimization(resolvedClient, opt) },
                        onToggleFavorite = { prop -> viewModel.toggleBuyerFavorite(resolvedClient, prop) },
                        onUpdateFeedback = { propId, rating, status, comment ->
                            viewModel.updateBuyerFeedback(resolvedClient, propId, rating, status, comment)
                        },
                        onSubmitVisitRequest = { prop, date, slot, msg ->
                            viewModel.submitBuyerVisitRequest(resolvedClient, prop, date, slot, msg)
                        },
                        onSaveCriteria = { budgetMax, downPayment, sectors, propType, minSurface, minRooms, essentials, delai, note, searchCenterAddress, searchCenterLat, searchCenterLon, searchMode, searchRadiusKm, searchMaxTravelMinutes, searchTransportMode, rgpdConsentGranted ->
                            viewModel.saveBuyerCriteria(
                                client = resolvedClient,
                                budgetMax = budgetMax,
                                downPayment = downPayment,
                                sectors = sectors,
                                propertyType = propType,
                                minSurface = minSurface,
                                minRooms = minRooms,
                                essentialCriteria = essentials,
                                timelineDelai = delai,
                                clientMessageToAdvisor = note,
                                searchCenterAddress = searchCenterAddress,
                                searchCenterLat = searchCenterLat,
                                searchCenterLon = searchCenterLon,
                                searchMode = searchMode,
                                searchRadiusKm = searchRadiusKm,
                                searchMaxTravelMinutes = searchMaxTravelMinutes,
                                searchTransportMode = searchTransportMode,
                                rgpdConsentGranted = rgpdConsentGranted
                            )
                        },
                        onSearchBanAddresses = viewModel::searchBanAddresses,
                        onUpdateRgpdConsent = { granted ->
                            viewModel.updateClientRgpdConsent(resolvedClient.id, granted)
                        },
                        onBuildFullRgpdExportJson = {
                            val decision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.SHARE_EXPORT)
                            if (decision is RateLimitDecision.Blocked) {
                                viewModel.showToast(decision.reason)
                                null
                            } else {
                                val crmContacts = (crmContactsState as? DataUiState.Success)?.data.orEmpty()
                                RgpdComplianceManager.buildFullWorkspaceRgpdJson(
                                    userUid = userId,
                                    userEmail = Firebase.auth.currentUser?.email.orEmpty(),
                                    clients = clients,
                                    sellerDossiers = sellerDossiers,
                                    crmContacts = crmContacts,
                                    localAcquereurs = localAcquereurs
                                )
                            }
                        },
                        onEraseClientRgpdData = {
                            viewModel.eraseClientRgpdData(resolvedClient, buyerChats, visits, offers)
                        },
                        onAskAssistant = { question, topic ->
                            val favProps = properties.filter { resolvedClient.selectedPropertyIds.contains(it.id) }
                            val clientHistory = buyerChats.filter { it.clientId == resolvedClient.id }
                            viewModel.askBuyerAssistant(
                                question = question,
                                topic = topic,
                                client = resolvedClient,
                                favoriteProperties = favProps,
                                history = clientHistory
                            )
                        },
                        onClearChat = {
                            val clientHistory = buyerChats.filter { it.clientId == resolvedClient.id }
                            viewModel.clearBuyerChat(clientHistory)
                        },
                        onUpdateSimulator = viewModel::updateSimulator,
                        onLoadPropertyInSimulator = viewModel::loadPropertyInSimulator,
                        onSubmitBuyerDocOrder = { dossier, prop, email, method, markPaid ->
                            sellerViewModel.submitBuyerDocumentOrder(
                                dossier = dossier,
                                property = prop,
                                buyerClient = resolvedClient,
                                buyerEmailOverride = email,
                                paymentMethodLabel = method,
                                markPaidImmediately = markPaid
                            )
                        },
                        onShowToast = viewModel::showToast,
                        onSignOut = onSignOut
                    )
                } else {
                    AdvisorWorkspaceScreen(
                        clients = clients,
                        activeClient = resolvedClient,
                        properties = properties,
                        visits = visits,
                        offers = offers,
                        activeTab = advisorTab,
                        comparePropertyIds = comparePropertyIds,
                        simulatorInput = simulatorInput,
                        financeMetrics = financeMetrics,
                        hcsfCapacityResult = hcsfCapacityResult,
                        advisorAiState = advisorAiState,
                        autoAiState = autoAiState,
                        acquereursViewModel = acquereursViewModel,
                        saasViewModel = saasViewModel,
                        sellerViewModel = sellerViewModel,
                        onSelectTab = viewModel::setAdvisorTab,
                        onSwitchMode = viewModel::setWorkspace,
                        onOpenPdfPreview = { showPdfPreview = true },
                        onOpenCapacityScreen = viewModel::openCapacityScreen,
                        onRefreshAutoAi = viewModel::refreshAutoRelevance,
                        onSelectClient = viewModel::selectClient,
                        onAddClient = viewModel::addClient,
                        onUpdateClientStage = viewModel::updateClientStage,
                        onTogglePortalAccess = viewModel::togglePortalAccess,
                        onSavePrivateNotes = viewModel::savePrivateNotes,
                        onAcknowledgeClientUpdates = viewModel::acknowledgeClientUpdates,
                        onAddProperty = viewModel::addProperty,
                        onEnhancePropertyDraft = viewModel::enhancePropertyDraft,
                        onToggleCompareProperty = viewModel::toggleCompareProperty,
                        onLoadPropertyInSimulator = viewModel::loadPropertyInSimulator,
                        onAddVisit = viewModel::addVisit,
                        onAddOffer = viewModel::addOffer,
                        onUpdateSimulator = viewModel::updateSimulator,
                        onGenerateAdvisorAi = { action, client, prop, instruction ->
                            viewModel.generateAdvisorAi(
                                action = action,
                                client = client,
                                property = prop,
                                allProperties = properties,
                                visits = visits,
                                customInstruction = instruction
                            )
                        },
                        mapUrbanismReport = mapUrbanismReport,
                        isLoadingUrbanism = isLoadingUrbanism,
                        onInspectMapLocation = viewModel::inspectMapLocation,
                        onClearUrbanismReport = viewModel::clearUrbanismReport,
                        onSearchBanAddresses = viewModel::searchBanAddresses,
                        onFetchOsmTileBytes = viewModel::fetchOsmTileBytes,
                        onValidatePropertyPublication = viewModel::validatePropertyPublication,
                        onUpdatePropertyMandateAndValuation = viewModel::updatePropertyMandateAndValuation,
                        onShowToast = viewModel::showToast,
                        onSignOut = onSignOut
                    )
                }

                if (showPdfPreview && resolvedClient != null) {
                    DossierPdfPreviewDialog(
                        client = resolvedClient,
                        properties = properties,
                        visits = visits,
                        onDismiss = { showPdfPreview = false },
                        onShareReport = {
                            val decision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.SHARE_EXPORT)
                            if (decision is RateLimitDecision.Blocked) {
                                viewModel.showToast(decision.reason)
                                return@DossierPdfPreviewDialog
                            }
                            val shareText = buildString {
                                appendLine("MON AGENT IMMO — Dossier Acquéreur")
                                appendLine("Client : ${resolvedClient.name}")
                                appendLine("Budget max : ${resolvedClient.budgetMax} €")
                                appendLine("Secteurs : ${resolvedClient.sectors.joinToString(", ")}")
                                appendLine("Biens sélectionnés : ${resolvedClient.selectedPropertyIds.size}")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Dossier Acquéreur — ${resolvedClient.name}")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Partager le dossier"))
                            showPdfPreview = false
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}
