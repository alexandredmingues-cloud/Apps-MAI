package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdvisorAiAction
import com.example.data.GeocodedAddressResult
import com.example.data.MontyAiService
import com.example.data.MontyMarketData
import com.example.data.MontyRepository
import com.example.data.PropertyGeoUrbanismReport
import com.example.data.UrbanismCadastreService
import com.example.data.model.AdvisorTab
import com.example.data.model.AutoAiRelevanceResult
import com.example.data.model.BuyerChatMessage
import com.example.data.model.BuyerTab
import com.example.data.model.ClientDossier
import com.example.data.model.CriteriaOptimizationSuggestion
import com.example.data.model.FinanceMetrics
import com.example.data.model.HcsfCapacityInput
import com.example.data.model.HcsfCapacityResult
import com.example.data.model.OfferRecord
import com.example.data.model.PropertyDraftEnhancement
import com.example.data.model.PropertyFeedback
import com.example.data.model.PropertyItem
import com.example.data.model.TimelineEvent
import com.example.data.model.VisitRecord
import com.example.data.model.WorkspaceMode
import com.example.security.AppSecurityManager
import com.example.security.RateLimitChannel
import com.example.security.RateLimitDecision
import com.example.security.RgpdComplianceManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface AiGenState {
    data object Idle : AiGenState
    data object Loading : AiGenState
    data class Streaming(val text: String) : AiGenState
    data class Success(val text: String) : AiGenState
    data class Error(val message: String) : AiGenState
}

sealed interface AutoAiUiState {
    data object Idle : AutoAiUiState
    data object Analyzing : AutoAiUiState
    data class Ready(val result: AutoAiRelevanceResult) : AutoAiUiState
    data class Error(val message: String) : AutoAiUiState
}

data class SimulatorInput(
    val price: Long = 429000L,
    val downPayment: Long = 95000L,
    val notaryRatePercent: Double = 7.8,
    val durationYears: Int = 25,
    val interestRatePercent: Double = 3.35,
    val insuranceRatePercent: Double = 0.32,
    val monthlyIncome: Long = 6800L,
    val existingCredits: Long = 0L,
    val monthlyCharges: Long = 320L
)

class MontyViewModel(
    private val repository: MontyRepository,
    private val aiService: MontyAiService,
    private val currentUserId: String
) : ViewModel() {

    private val _workspace = MutableStateFlow(WorkspaceMode.BUYER)
    val workspace: StateFlow<WorkspaceMode> = _workspace.asStateFlow()

    private val _advisorTab = MutableStateFlow(AdvisorTab.DASHBOARD)
    val advisorTab: StateFlow<AdvisorTab> = _advisorTab.asStateFlow()

    private val _buyerTab = MutableStateFlow(BuyerTab.EXPLORER)
    val buyerTab: StateFlow<BuyerTab> = _buyerTab.asStateFlow()

    private val _selectedClientId = MutableStateFlow("cli-1")
    val selectedClientId: StateFlow<String> = _selectedClientId.asStateFlow()

    private val _comparePropertyIds = MutableStateFlow(listOf("prop-1", "prop-2", "prop-5"))
    val comparePropertyIds: StateFlow<List<String>> = _comparePropertyIds.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _simulatorInput = MutableStateFlow(SimulatorInput())
    val simulatorInput: StateFlow<SimulatorInput> = _simulatorInput.asStateFlow()

    val financeMetrics: StateFlow<FinanceMetrics> = _simulatorInput
        .map { input ->
            MontyMarketData.computeFinanceMetrics(
                price = input.price,
                downPayment = input.downPayment,
                notaryRatePercent = input.notaryRatePercent,
                durationYears = input.durationYears,
                interestRatePercent = input.interestRatePercent,
                insuranceRatePercent = input.insuranceRatePercent,
                monthlyIncome = input.monthlyIncome,
                existingCredits = input.existingCredits,
                monthlyCharges = input.monthlyCharges
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), MontyMarketData.computeFinanceMetrics(429000L, 95000L, 7.8, 25, 3.35, 0.32, 6800L, 0L, 320L))

    private val _showCapacityScreen = MutableStateFlow(false)
    val showCapacityScreen: StateFlow<Boolean> = _showCapacityScreen.asStateFlow()

    private val _hcsfCapacityInput = MutableStateFlow(HcsfCapacityInput())
    val hcsfCapacityInput: StateFlow<HcsfCapacityInput> = _hcsfCapacityInput.asStateFlow()

    val hcsfCapacityResult: StateFlow<HcsfCapacityResult> = _hcsfCapacityInput
        .map { MontyMarketData.computeHcsfBorrowingCapacity(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            MontyMarketData.computeHcsfBorrowingCapacity(HcsfCapacityInput())
        )

    private val _advisorAiState = MutableStateFlow<AiGenState>(AiGenState.Idle)
    val advisorAiState: StateFlow<AiGenState> = _advisorAiState.asStateFlow()

    private val _buyerAiState = MutableStateFlow<AiGenState>(AiGenState.Idle)
    val buyerAiState: StateFlow<AiGenState> = _buyerAiState.asStateFlow()

    private val _aiAutoEnhanceEnabled = MutableStateFlow(true)
    val aiAutoEnhanceEnabled: StateFlow<Boolean> = _aiAutoEnhanceEnabled.asStateFlow()

    private val _autoAiState = MutableStateFlow<AutoAiUiState>(AutoAiUiState.Idle)
    val autoAiState: StateFlow<AutoAiUiState> = _autoAiState.asStateFlow()

    private val _forceRelevanceNonce = MutableStateFlow(0)
    private val relevanceCache = mutableMapOf<String, AutoAiRelevanceResult>()

    private val urbanismService = UrbanismCadastreService()
    private val _mapUrbanismReport = MutableStateFlow<PropertyGeoUrbanismReport?>(null)
    val mapUrbanismReport: StateFlow<PropertyGeoUrbanismReport?> = _mapUrbanismReport.asStateFlow()

    private val _isLoadingUrbanism = MutableStateFlow(false)
    val isLoadingUrbanism: StateFlow<Boolean> = _isLoadingUrbanism.asStateFlow()

    fun getUrbanismService(): UrbanismCadastreService = urbanismService

    val clientsState: StateFlow<DataUiState<List<ClientDossier>>> = repository.observeClients(currentUserId)
        .map<List<ClientDossier>, DataUiState<List<ClientDossier>>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreFlowError(error, "clients")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val propertiesState: StateFlow<DataUiState<List<PropertyItem>>> = repository.observeProperties(currentUserId)
        .map<List<PropertyItem>, DataUiState<List<PropertyItem>>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreFlowError(error, "properties")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val visitsState: StateFlow<DataUiState<List<VisitRecord>>> = repository.observeVisits(currentUserId)
        .map<List<VisitRecord>, DataUiState<List<VisitRecord>>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreFlowError(error, "visits")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val offersState: StateFlow<DataUiState<List<OfferRecord>>> = repository.observeOffers(currentUserId)
        .map<List<OfferRecord>, DataUiState<List<OfferRecord>>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreFlowError(error, "offers")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val buyerChatsState: StateFlow<DataUiState<List<BuyerChatMessage>>> = repository.observeBuyerChats(currentUserId)
        .map<List<BuyerChatMessage>, DataUiState<List<BuyerChatMessage>>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreFlowError(error, "buyer_chats")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val activeClient: StateFlow<ClientDossier?> = combine(clientsState, _selectedClientId) { state, selId ->
        val list = (state as? DataUiState.Success)?.data.orEmpty()
        list.firstOrNull { it.id == selId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    init {
        viewModelScope.launch {
            repository.seedWorkspaceIfEmpty(currentUserId)
                .onFailure { e -> showToast("Initialisation cloud : ${e.localizedMessage}") }
        }
        viewModelScope.launch {
            activeClient.collect { client ->
                if (client != null) {
                    _simulatorInput.value = _simulatorInput.value.copy(
                        downPayment = client.downPayment,
                        monthlyIncome = client.monthlyIncome,
                        existingCredits = client.existingCredits
                    )
                    _hcsfCapacityInput.value = _hcsfCapacityInput.value.copy(
                        monthlyNetIncome = client.monthlyIncome,
                        existingMonthlyCredits = client.existingCredits,
                        downPayment = client.downPayment
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                combine(_aiAutoEnhanceEnabled, activeClient, propertiesState, ::Triple),
                combine(visitsState, offersState, _forceRelevanceNonce, ::Triple)
            ) { (enabled, client, propsState), (vState, oState, nonce) ->
                val props = (propsState as? DataUiState.Success<List<PropertyItem>>)?.data.orEmpty()
                val visits = (vState as? DataUiState.Success<List<VisitRecord>>)?.data.orEmpty()
                val offers = (oState as? DataUiState.Success<List<OfferRecord>>)?.data.orEmpty()
                RelevanceInput(enabled, client, props, visits, offers, nonce)
            }
                .distinctUntilChanged { old, new ->
                    old.enabled == new.enabled &&
                        old.nonce == new.nonce &&
                        old.signature() == new.signature()
                }
                .collectLatest { input ->
                    val client = input.client
                    if (!input.enabled || client == null || input.properties.isEmpty()) {
                        _autoAiState.value = AutoAiUiState.Idle
                        return@collectLatest
                    }
                    val sig = input.signature()
                    val cached = relevanceCache[sig]
                    if (cached != null && input.nonce == 0) {
                        _autoAiState.value = AutoAiUiState.Ready(cached)
                        return@collectLatest
                    }
                    val limitDecision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.AI_AUTO_RELEVANCE)
                    if (limitDecision is RateLimitDecision.Blocked) {
                        _autoAiState.value = AutoAiUiState.Error(limitDecision.reason)
                        return@collectLatest
                    }
                    _autoAiState.value = AutoAiUiState.Analyzing
                    try {
                        val result = aiService.analyzePortfolioRelevance(
                            signature = sig,
                            client = client,
                            properties = input.properties,
                            visits = input.visits,
                            offers = input.offers
                        )
                        relevanceCache[sig] = result
                        _autoAiState.value = AutoAiUiState.Ready(result)
                    } catch (e: TimeoutCancellationException) {
                        Log.e(TAG, "Auto relevance analysis timed out", e)
                        _autoAiState.value = AutoAiUiState.Error("Analyse IA automatique expirée (60s).")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto relevance analysis failed", e)
                        _autoAiState.value = AutoAiUiState.Error(
                            e.localizedMessage ?: "Analyse IA automatique indisponible."
                        )
                    }
                }
        }
    }

    private data class RelevanceInput(
        val enabled: Boolean,
        val client: ClientDossier?,
        val properties: List<PropertyItem>,
        val visits: List<VisitRecord>,
        val offers: List<OfferRecord>,
        val nonce: Int
    ) {
        fun signature(): String {
            if (client == null) return "none"
            val propsSig = properties.joinToString(",") { "${it.id}:${it.price}:${it.dpe}" }
            return "${client.id}|${client.budgetMax}|${client.downPayment}|${client.minSurface}|${client.minRooms}|${client.sectors.joinToString()}|${client.essentialCriteria.joinToString()}|${client.selectedPropertyIds.joinToString()}|$propsSig|${visits.size}|${offers.size}"
        }
    }

    private fun <T> unwrapFirestoreFlowError(error: Throwable, collectionName: String): DataUiState<T> {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $collectionName", rootError)
        return DataUiState.Error(rootError.localizedMessage ?: "Erreur Firestore ($collectionName)")
    }

    fun setWorkspace(mode: WorkspaceMode) {
        _workspace.value = mode
    }

    fun setAdvisorTab(tab: AdvisorTab) {
        _advisorTab.value = tab
    }

    fun setBuyerTab(tab: BuyerTab) {
        _buyerTab.value = tab
    }

    fun selectClient(clientId: String) {
        _selectedClientId.value = clientId
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun updateSimulator(transform: (SimulatorInput) -> SimulatorInput) {
        _simulatorInput.value = transform(_simulatorInput.value)
    }

    fun openCapacityScreen() {
        _showCapacityScreen.value = true
    }

    fun closeCapacityScreen() {
        _showCapacityScreen.value = false
    }

    fun updateHcsfCapacityInput(transform: (HcsfCapacityInput) -> HcsfCapacityInput) {
        _hcsfCapacityInput.value = transform(_hcsfCapacityInput.value)
    }

    fun resetHcsfFromClient(client: ClientDossier?) {
        if (client == null) return
        _hcsfCapacityInput.value = HcsfCapacityInput(
            monthlyNetIncome = client.monthlyIncome,
            existingMonthlyCredits = client.existingCredits,
            downPayment = client.downPayment
        )
        showToast("Paramètres HCSF synchronisés depuis le dossier ${client.name.substringBefore("—").trim()}.")
    }

    fun applyHcsfToSimulator(input: HcsfCapacityInput, result: HcsfCapacityResult) {
        _simulatorInput.value = _simulatorInput.value.copy(
            price = result.maxPropertyPriceNet.coerceAtLeast(10000L),
            downPayment = input.downPayment,
            notaryRatePercent = input.notaryRatePercent,
            durationYears = input.durationYears,
            interestRatePercent = input.interestRatePercent,
            insuranceRatePercent = input.insuranceRatePercent,
            monthlyIncome = input.monthlyNetIncome,
            existingCredits = input.existingMonthlyCredits
        )
        _showCapacityScreen.value = false
        showToast("Capacité HCSF (${MontyMarketData.formatEuro(result.maxPropertyPriceNet)}) appliquée au simulateur.")
    }

    fun applyHcsfToClientBudget(
        client: ClientDossier,
        input: HcsfCapacityInput,
        result: HcsfCapacityResult
    ) {
        val nextBudget = result.maxPropertyPriceNet.coerceAtLeast(10000L)
        _simulatorInput.value = _simulatorInput.value.copy(
            price = nextBudget,
            downPayment = input.downPayment,
            notaryRatePercent = input.notaryRatePercent,
            durationYears = input.durationYears,
            interestRatePercent = input.interestRatePercent,
            insuranceRatePercent = input.insuranceRatePercent,
            monthlyIncome = input.monthlyNetIncome,
            existingCredits = input.existingMonthlyCredits
        )
        viewModelScope.launch {
            val nowLabel = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
            repository.saveBuyerCriteria(
                userId = currentUserId,
                client = client,
                budgetMax = nextBudget,
                downPayment = input.downPayment,
                sectors = client.sectors,
                propertyType = client.propertyType,
                minSurface = client.minSurface,
                minRooms = client.minRooms,
                essentialCriteria = client.essentialCriteria,
                timelineDelai = client.timelineDelai,
                clientMessageToAdvisor = "Capacité HCSF ${input.maxDebtRatioPercent}% validée : ${MontyMarketData.formatEuro(nextBudget)} (${input.durationYears} ans, apport ${MontyMarketData.formatEuro(input.downPayment)}).",
                updateLabel = nowLabel
            ).onSuccess {
                _showCapacityScreen.value = false
                showToast("Budget max du dossier mis à jour (${MontyMarketData.formatEuro(nextBudget)}).")
            }.onFailure { e ->
                showToast("Erreur : ${e.localizedMessage}")
            }
        }
    }

    fun loadPropertyInSimulator(property: PropertyItem) {
        _simulatorInput.value = _simulatorInput.value.copy(
            price = property.price,
            monthlyCharges = property.monthlyCharges + (property.propertyTax / 12L)
        )
        showToast("Simulation chargée pour ${property.title}")
    }

    fun toggleCompareProperty(propertyId: String) {
        val current = _comparePropertyIds.value
        if (current.contains(propertyId)) {
            if (current.size > 1) {
                _comparePropertyIds.value = current.filterNot { it == propertyId }
            }
        } else {
            if (current.size >= 4) {
                showToast("Comparateur limité à 4 biens simultanés.")
            } else {
                _comparePropertyIds.value = current + propertyId
            }
        }
    }

    // --- Mutations Conseiller ---

    fun addClient(
        name: String,
        phone: String,
        email: String,
        situation: String,
        budgetMax: Long,
        downPayment: Long,
        monthlyIncome: Long,
        sector: String,
        propertyType: String,
        minSurface: Long,
        minRooms: Long,
        essentialsCsv: String,
        timelineDelai: String,
        privateNotes: String
    ) {
        viewModelScope.launch {
            val tokenPrefix = name.uppercase().filter { it.isLetter() }.take(4).padEnd(4, 'X')
            val token = "MNT-$tokenPrefix-${(1000..9999).random()}"
            val dossier = ClientDossier(
                userId = currentUserId,
                name = name.trim(),
                phone = phone.ifBlank { "06 00 00 00 00" },
                email = email.ifBlank { "client@exemple.fr" },
                situation = situation.ifBlank { "Projet résidentiel Montpellier" },
                status = "Actif — Qualification validée",
                projectStage = 1L,
                budgetMax = budgetMax,
                downPayment = downPayment,
                monthlyIncome = monthlyIncome,
                bankApproval = "En cours de validation courtier",
                sectors = listOf(sector),
                propertyType = propertyType,
                minSurface = minSurface,
                minRooms = minRooms,
                essentialCriteria = essentialsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                secondaryCriteria = listOf("Calme absolu", "Proche Tramway"),
                timelineDelai = timelineDelai,
                portalActive = true,
                portalToken = token,
                privateNotes = privateNotes.ifBlank { "Dossier créé dans Mon Agent Immo." },
                timeline = listOf(
                    TimelineEvent("Aujourd'hui", "contact", "Création du dossier & portail privé", "Jeton sécurisé : $token")
                )
            )
            repository.addClient(currentUserId, dossier)
                .onSuccess { newId ->
                    _selectedClientId.value = newId
                    showToast("Dossier « ${dossier.name} » créé.")
                }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    fun updateClientStage(clientId: String, stage: Int) {
        viewModelScope.launch {
            repository.updateClientStage(currentUserId, clientId, stage)
                .onSuccess { showToast("Avancement du dossier mis à jour.") }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    fun togglePortalAccess(client: ClientDossier) {
        viewModelScope.launch {
            val next = !client.portalActive
            repository.togglePortalAccess(currentUserId, client.id, next)
                .onSuccess {
                    showToast(if (next) "Lien portail réactivé." else "Accès portail révoqué.")
                }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    fun savePrivateNotes(clientId: String, notes: String) {
        viewModelScope.launch {
            repository.updatePrivateNotes(currentUserId, clientId, notes)
                .onSuccess { showToast("Notes confidentielles enregistrées.") }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    fun acknowledgeClientUpdates(clientId: String) {
        viewModelScope.launch {
            repository.acknowledgeClientUpdates(currentUserId, clientId)
                .onSuccess { showToast("Modifications client marquées comme vues.") }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    fun addProperty(
        title: String,
        sector: String,
        address: String,
        type: String,
        price: Long,
        surface: Long,
        rooms: Long,
        bedrooms: Long,
        dpe: String,
        monthlyCharges: Long,
        propertyTax: Long,
        estimatedWorks: Long,
        exterior: String,
        parking: String,
        strengthsCsv: String,
        weaknessesCsv: String
    ) {
        viewModelScope.launch {
            val prop = PropertyItem(
                userId = currentUserId,
                title = title.trim(),
                sector = sector,
                address = address.ifBlank { sector },
                type = type,
                price = price,
                surface = surface,
                rooms = rooms,
                bedrooms = bedrooms,
                floor = "Étage intermédiaire",
                dpe = dpe,
                ges = "B",
                monthlyCharges = monthlyCharges,
                propertyTax = propertyTax,
                estimatedWorks = estimatedWorks,
                listingStatus = "Saisie manuelle — Vérifiée",
                source = "manuelle",
                exterior = exterior,
                parking = parking,
                environment = "Environnement résidentiel qualifié à Montpellier.",
                transports = "Accès Tramway / Axes principaux",
                schoolsShops = "Commerces et écoles à proximité",
                generalCondition = "Bon état général",
                strengths = strengthsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                weaknesses = weaknessesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                description = "Bien qualifié sur $sector offrant $surface m² et $bedrooms chambres."
            )
            repository.addProperty(currentUserId, prop)
                .onSuccess { showToast("Bien « ${prop.title} » ajouté au catalogue.") }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    fun addVisit(
        clientId: String,
        propertyId: String,
        date: String,
        timeSlot: String,
        status: String,
        rating: Double?,
        impressions: String,
        positives: String,
        negatives: String,
        technicalNotes: String,
        nextAction: String
    ) {
        viewModelScope.launch {
            val visit = VisitRecord(
                userId = currentUserId,
                date = date,
                timeSlot = timeSlot,
                clientId = clientId,
                propertyId = propertyId,
                status = status,
                overallRating = if (status == "Réalisée") rating else null,
                clientImpressions = impressions,
                positives = positives,
                negatives = negatives,
                technicalNotes = technicalNotes,
                nextAction = nextAction,
                sharedWithClient = true
            )
            repository.addVisit(currentUserId, visit)
                .onSuccess { showToast("Visite enregistrée et partagée.") }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    fun addOffer(
        client: ClientDossier,
        property: PropertyItem,
        offeredPrice: Long,
        sellerContext: String,
        suspensiveConditions: String,
        mainArgument: String
    ) {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
            val offer = OfferRecord(
                userId = currentUserId,
                date = today,
                clientId = client.id,
                propertyId = property.id,
                askingPrice = property.price,
                offeredPrice = offeredPrice,
                estimatedWorks = property.estimatedWorks,
                status = "Préparée — Prête à signer",
                sellerContext = sellerContext,
                clientFinancing = client.bankApproval,
                suspensiveConditions = suspensiveConditions,
                arguments = listOf(
                    mainArgument,
                    "Apport personnel mobilisable immédiatement : ${MontyMarketData.formatEuro(client.downPayment)}."
                )
            )
            repository.addOffer(currentUserId, offer)
                .onSuccess { showToast("Offre d'achat enregistrée.") }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    // --- Mutations Portail Client / Buyer's Portal ---

    fun saveBuyerCriteria(
        client: ClientDossier,
        budgetMax: Long,
        downPayment: Long,
        sectors: List<String>,
        propertyType: String,
        minSurface: Long,
        minRooms: Long,
        essentialCriteria: List<String>,
        timelineDelai: String,
        clientMessageToAdvisor: String,
        searchCenterAddress: String = client.searchCenterAddress,
        searchCenterLat: Double = client.searchCenterLat,
        searchCenterLon: Double = client.searchCenterLon,
        searchMode: String = client.searchMode,
        searchRadiusKm: Int = client.searchRadiusKm,
        searchMaxTravelMinutes: Int = client.searchMaxTravelMinutes,
        searchTransportMode: String = client.searchTransportMode,
        rgpdConsentGranted: Boolean = client.rgpdConsentGranted,
        rgpdConsentDate: String = client.rgpdConsentDate
    ) {
        viewModelScope.launch {
            val nowLabel = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
            val resolvedConsentDate = if (rgpdConsentGranted && rgpdConsentDate.isBlank()) {
                RgpdComplianceManager.todayIso()
            } else {
                rgpdConsentDate
            }
            repository.saveBuyerCriteria(
                userId = currentUserId,
                client = client,
                budgetMax = budgetMax,
                downPayment = downPayment,
                sectors = sectors.ifEmpty { client.sectors },
                propertyType = propertyType,
                minSurface = minSurface,
                minRooms = minRooms,
                essentialCriteria = essentialCriteria,
                timelineDelai = timelineDelai,
                clientMessageToAdvisor = clientMessageToAdvisor,
                updateLabel = nowLabel,
                searchCenterAddress = searchCenterAddress,
                searchCenterLat = searchCenterLat,
                searchCenterLon = searchCenterLon,
                searchMode = searchMode,
                searchRadiusKm = searchRadiusKm,
                searchMaxTravelMinutes = searchMaxTravelMinutes,
                searchTransportMode = searchTransportMode,
                rgpdConsentGranted = rgpdConsentGranted,
                rgpdConsentDate = resolvedConsentDate
            ).onSuccess {
                showToast("Critères enregistrés et transmis à votre conseiller.")
                _buyerTab.value = BuyerTab.EXPLORER
            }.onFailure { e ->
                showToast("Erreur : ${e.localizedMessage}")
            }
        }
    }

    fun updateClientRgpdConsent(clientId: String, granted: Boolean) {
        viewModelScope.launch {
            val consentDate = if (granted) RgpdComplianceManager.todayIso() else ""
            repository.updateClientRgpdConsent(currentUserId, clientId, granted, consentDate)
                .onSuccess {
                    showToast(
                        if (granted) "Consentement RGPD enregistré ($consentDate)."
                        else "Consentement RGPD retiré."
                    )
                }
                .onFailure { e -> showToast("Erreur RGPD : ${e.localizedMessage}") }
        }
    }

    fun eraseClientRgpdData(
        client: ClientDossier,
        chats: List<BuyerChatMessage>,
        visits: List<VisitRecord>,
        offers: List<OfferRecord>
    ) {
        viewModelScope.launch {
            val chatIds = chats.filter { it.clientId == client.id }.map { it.id }
            val visitIds = visits.filter { it.clientId == client.id }.map { it.id }
            val offerIds = offers.filter { it.clientId == client.id }.map { it.id }
            repository.eraseClientAndRelatedData(
                userId = currentUserId,
                clientId = client.id,
                chatIds = chatIds,
                visitIds = visitIds,
                offerIds = offerIds
            ).onSuccess {
                showToast("Dossier et données personnelles effacés (Art. 17 RGPD).")
            }.onFailure { e ->
                showToast("Effacement impossible : ${e.localizedMessage}")
            }
        }
    }

    suspend fun upsertSellerListingPropertySync(property: PropertyItem): Result<String> {
        return repository.upsertSellerListingProperty(currentUserId, property)
    }

    fun validatePropertyPublication(property: PropertyItem) {
        viewModelScope.launch {
            val nextStatus = if (property.publicationStatus == "PUBLIEE") "A_VERIFIER" else "PUBLIEE"
            val nextListingStatus = if (nextStatus == "PUBLIEE") {
                if (property.publisherType == "PARTICULIER") "Annonce Particulier — Vérifiée"
                else "Vérifiée — Publiée"
            } else {
                "En attente de modération"
            }
            repository.updatePropertyPublicationStatus(
                userId = currentUserId,
                propertyId = property.id,
                publicationStatus = nextStatus,
                listingStatus = nextListingStatus
            ).onSuccess {
                showToast(
                    if (nextStatus == "PUBLIEE") "Annonce « ${property.title} » publiée dans le catalogue."
                    else "Annonce « ${property.title} » repassée en vérification."
                )
            }.onFailure { e ->
                val isDenied = (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                if (isDenied && property.publisherType == "PARTICULIER" && nextStatus == "PUBLIEE") {
                    showToast("Publication Particulier réservée aux comptes modérateurs (claim staff requis).")
                } else {
                    showToast("Modération impossible : ${e.localizedMessage}")
                }
            }
        }
    }

    fun updatePropertyMandateAndValuation(
        propertyId: String,
        price: Long,
        listingStatus: String,
        mandateRef: String,
        mandateType: String,
        mandateDate: String,
        agencyFeePercent: Double,
        listingUrl: String,
        dvfEstimatedEuro: Long,
        dvfEstimatedLowEuro: Long,
        dvfEstimatedHighEuro: Long,
        dvfScore: Int,
        dvfCriteriaSummary: String,
        advisorValuationNotes: String
    ) {
        viewModelScope.launch {
            repository.updatePropertyMandateAndValuation(
                userId = currentUserId,
                propertyId = propertyId,
                price = price,
                listingStatus = listingStatus,
                mandateRef = mandateRef,
                mandateType = mandateType,
                mandateDate = mandateDate,
                agencyFeePercent = agencyFeePercent,
                listingUrl = listingUrl,
                dvfEstimatedEuro = dvfEstimatedEuro,
                dvfEstimatedLowEuro = dvfEstimatedLowEuro,
                dvfEstimatedHighEuro = dvfEstimatedHighEuro,
                dvfScore = dvfScore,
                dvfCriteriaSummary = dvfCriteriaSummary,
                advisorValuationNotes = advisorValuationNotes
            ).onSuccess {
                showToast("Mandat, estimation & lien d'annonce mis à jour.")
            }.onFailure { e ->
                showToast("Mise à jour refusée : ${e.localizedMessage}")
            }
        }
    }

    fun inspectMapLocation(lat: Double, lon: Double, addressHint: String = "") {
        viewModelScope.launch {
            _isLoadingUrbanism.value = true
            try {
                val report = urbanismService.fetchGeoUrbanismReport(lat, lon, addressHint)
                _mapUrbanismReport.value = report
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Erreur urbanisme : ${e.localizedMessage}")
            } finally {
                _isLoadingUrbanism.value = false
            }
        }
    }

    fun clearUrbanismReport() {
        _mapUrbanismReport.value = null
    }

    suspend fun searchBanAddresses(query: String): Result<List<GeocodedAddressResult>> {
        return urbanismService.searchBanSuggestions(query)
    }

    suspend fun fetchOsmTileBytes(z: Int, x: Int, y: Int): ByteArray? {
        return urbanismService.fetchOsmTileBytes(z, x, y)
    }

    fun toggleBuyerFavorite(client: ClientDossier, property: PropertyItem) {
        viewModelScope.launch {
            repository.toggleBuyerFavorite(currentUserId, client, property)
                .onSuccess { added ->
                    showToast(
                        if (added) "« ${property.title} » ajouté à votre sélection."
                        else "« ${property.title} » retiré de votre sélection."
                    )
                }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    fun updateBuyerFeedback(
        client: ClientDossier,
        propertyId: String,
        rating: Long,
        status: String,
        comment: String
    ) {
        viewModelScope.launch {
            repository.updateBuyerFeedback(
                userId = currentUserId,
                client = client,
                propertyId = propertyId,
                feedback = PropertyFeedback(rating, status, comment)
            ).onSuccess {
                showToast("Appréciation enregistrée pour votre conseiller.")
            }.onFailure { e ->
                showToast("Erreur : ${e.localizedMessage}")
            }
        }
    }

    fun submitBuyerVisitRequest(
        client: ClientDossier,
        property: PropertyItem,
        requestedDate: String,
        timeSlot: String,
        message: String
    ) {
        viewModelScope.launch {
            val visit = VisitRecord(
                userId = currentUserId,
                date = requestedDate,
                timeSlot = timeSlot,
                clientId = client.id,
                propertyId = property.id,
                status = "Demande client à confirmer",
                overallRating = null,
                clientImpressions = message.ifBlank { "Demande de visite initiée depuis le portail client." },
                nextAction = "Confirmer le créneau ($timeSlot) avec ${client.name}.",
                sharedWithClient = true
            )
            repository.addVisit(currentUserId, visit)
                .onSuccess {
                    if (!client.selectedPropertyIds.contains(property.id)) {
                        repository.toggleBuyerFavorite(currentUserId, client, property)
                    }
                    showToast("Demande de visite transmise à votre conseiller.")
                }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    // --- IA Gemini (Firebase AI Logic) ---

    fun generateAdvisorAi(
        action: AdvisorAiAction,
        client: ClientDossier,
        property: PropertyItem,
        allProperties: List<PropertyItem>,
        visits: List<VisitRecord>,
        customInstruction: String
    ) {
        val limitDecision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.AI_ADVISOR)
        if (limitDecision is RateLimitDecision.Blocked) {
            _advisorAiState.value = AiGenState.Error(limitDecision.reason)
            showToast(limitDecision.reason)
            return
        }
        viewModelScope.launch {
            _advisorAiState.value = AiGenState.Loading
            var lastText = ""
            try {
                aiService.streamAdvisorAnalysis(
                    action = action,
                    client = client,
                    property = property,
                    allProperties = allProperties,
                    visits = visits,
                    customInstruction = customInstruction
                ).collect { chunked ->
                    lastText = chunked
                    _advisorAiState.value = AiGenState.Streaming(chunked)
                }
                _advisorAiState.value = AiGenState.Success(lastText)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Advisor AI timed out", e)
                _advisorAiState.value = AiGenState.Error("Délai d'analyse dépassé (60s). Veuillez réessayer.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Advisor AI failed", e)
                _advisorAiState.value = AiGenState.Error(e.localizedMessage ?: "Échec de la génération Gemini.")
            }
        }
    }

    fun askBuyerAssistant(
        question: String,
        topic: String,
        client: ClientDossier,
        favoriteProperties: List<PropertyItem>,
        history: List<BuyerChatMessage>
    ) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return

        val limitDecision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.AI_BUYER_CHAT)
        if (limitDecision is RateLimitDecision.Blocked) {
            _buyerAiState.value = AiGenState.Error(limitDecision.reason)
            showToast(limitDecision.reason)
            return
        }

        viewModelScope.launch {
            _buyerAiState.value = AiGenState.Loading
            repository.addBuyerChatMessage(
                userId = currentUserId,
                clientId = client.id,
                role = "user",
                content = trimmed,
                topic = topic
            ).onFailure { e ->
                _buyerAiState.value = AiGenState.Error("Impossible d'enregistrer votre question : ${e.localizedMessage}")
                return@launch
            }

            var finalReply = ""
            try {
                aiService.streamBuyerAssistantReply(
                    question = trimmed,
                    client = client,
                    favoriteProperties = favoriteProperties,
                    history = history
                ).collect { partial ->
                    finalReply = partial
                    _buyerAiState.value = AiGenState.Streaming(partial)
                }
                repository.addBuyerChatMessage(
                    userId = currentUserId,
                    clientId = client.id,
                    role = "assistant",
                    content = finalReply,
                    topic = topic
                )
                _buyerAiState.value = AiGenState.Idle
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Buyer Assistant timed out", e)
                _buyerAiState.value = AiGenState.Error("Délai de réponse dépassé (60s). Réessayez.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Buyer Assistant failed", e)
                _buyerAiState.value = AiGenState.Error(e.localizedMessage ?: "L'assistant IA n'a pas pu répondre.")
            }
        }
    }

    fun clearBuyerChat(messages: List<BuyerChatMessage>) {
        viewModelScope.launch {
            repository.clearBuyerChatForClient(currentUserId, messages.map { it.id })
                .onSuccess { showToast("Historique de conversation effacé.") }
                .onFailure { e -> showToast("Erreur : ${e.localizedMessage}") }
        }
    }

    fun toggleAiAutoEnhance(enabled: Boolean) {
        _aiAutoEnhanceEnabled.value = enabled
        showToast(
            if (enabled) "Amélioration automatique IA activée."
            else "Tri manuel par critères bruts activé."
        )
    }

    fun refreshAutoRelevance() {
        relevanceCache.clear()
        _forceRelevanceNonce.value += 1
    }

    fun applyAiCriteriaOptimization(
        client: ClientDossier,
        optimization: CriteriaOptimizationSuggestion
    ) {
        val nextBudget = if (optimization.suggestedBudgetMax > 0L) optimization.suggestedBudgetMax else client.budgetMax
        val nextSectors = if (optimization.suggestedSectors.isNotEmpty()) optimization.suggestedSectors else client.sectors
        val nextMinSurface = if (optimization.suggestedMinSurface > 0L) optimization.suggestedMinSurface else client.minSurface

        viewModelScope.launch {
            val nowLabel = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
            repository.saveBuyerCriteria(
                userId = currentUserId,
                client = client,
                budgetMax = nextBudget,
                downPayment = client.downPayment,
                sectors = nextSectors,
                propertyType = client.propertyType,
                minSurface = nextMinSurface,
                minRooms = client.minRooms,
                essentialCriteria = client.essentialCriteria,
                timelineDelai = client.timelineDelai,
                clientMessageToAdvisor = "Optimisation IA appliquée : ${optimization.title}",
                updateLabel = nowLabel
            ).onSuccess {
                showToast("Critères optimisés par l'IA et enregistrés.")
            }.onFailure { e ->
                showToast("Erreur : ${e.localizedMessage}")
            }
        }
    }

    suspend fun enhancePropertyDraft(
        rawTitle: String,
        sector: String,
        type: String,
        price: Long,
        surface: Long,
        bedrooms: Long,
        dpe: String,
        exterior: String,
        parking: String,
        rawStrengths: String,
        rawWeaknesses: String,
        activeClient: ClientDossier?
    ): Result<PropertyDraftEnhancement> {
        val limitDecision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.AI_PROPERTY_DRAFT)
        if (limitDecision is RateLimitDecision.Blocked) {
            return Result.failure(SecurityException(limitDecision.reason))
        }
        return try {
            val enhanced = aiService.enhancePropertyListing(
                rawTitle = rawTitle,
                sector = sector,
                type = type,
                price = price,
                surface = surface,
                bedrooms = bedrooms,
                dpe = dpe,
                exterior = exterior,
                parking = parking,
                rawStrengths = rawStrengths,
                rawWeaknesses = rawWeaknesses,
                activeClient = activeClient
            )
            Result.success(enhanced)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Property draft enhancement failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "MontyVM"
    }
}
