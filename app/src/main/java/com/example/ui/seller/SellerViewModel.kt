package com.example.ui.seller

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DvfValuationReport
import com.example.data.DvfValuationService
import com.example.data.GeoCommuteService
import com.example.data.MontyAiService
import com.example.data.SaleTimelineEngine
import com.example.data.SaleTimelineEstimate
import com.example.data.SellerDocumentVault
import com.example.data.SellerListingAiDraft
import com.example.data.SellerRepository
import com.example.data.model.ClientDossier
import com.example.data.model.DocumentContributorRole
import com.example.data.model.DvfValuationCriteria
import com.example.data.model.DvfValuationSnapshot
import com.example.data.model.PropertyItem
import com.example.data.model.PropertyKind
import com.example.data.model.ResidenceType
import com.example.data.model.SellerDocumentCatalog
import com.example.data.model.SellerPropertyDossier
import com.example.data.model.SellerSaleDocument
import com.example.security.AppSecurityManager
import com.example.security.RateLimitChannel
import com.example.security.RateLimitDecision
import com.example.ui.DataUiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface DvfUiState {
    data object Idle : DvfUiState
    data object Loading : DvfUiState
    data class Ready(val report: DvfValuationReport) : DvfUiState
    data class Error(val message: String) : DvfUiState
}

class SellerViewModel(
    private val repository: SellerRepository,
    private val dvfService: DvfValuationService,
    private val aiService: MontyAiService,
    private val currentUserId: String,
    private val onNotify: (String) -> Unit,
    private val onSyncPropertyToPortfolio: (suspend (PropertyItem) -> Result<String>)? = null
) : ViewModel() {

    private val _selectedPropertyId = MutableStateFlow<String?>(null)
    val selectedPropertyId: StateFlow<String?> = _selectedPropertyId.asStateFlow()

    private val _contributorRole = MutableStateFlow(DocumentContributorRole.VENDEUR)
    val contributorRole: StateFlow<DocumentContributorRole> = _contributorRole.asStateFlow()

    private val _dvfUiState = MutableStateFlow<DvfUiState>(DvfUiState.Idle)
    val dvfUiState: StateFlow<DvfUiState> = _dvfUiState.asStateFlow()

    private val _isSummarizingAi = MutableStateFlow(false)
    val isSummarizingAi: StateFlow<Boolean> = _isSummarizingAi.asStateFlow()

    private val _isGeneratingListingAi = MutableStateFlow(false)
    val isGeneratingListingAi: StateFlow<Boolean> = _isGeneratingListingAi.asStateFlow()

    private val _saleTimelineEstimate = MutableStateFlow<SaleTimelineEstimate?>(null)
    val saleTimelineEstimate: StateFlow<SaleTimelineEstimate?> = _saleTimelineEstimate.asStateFlow()

    val dossiersState: StateFlow<DataUiState<List<SellerPropertyDossier>>> =
        repository.observeSellerDossiers(currentUserId)
            .map<List<SellerPropertyDossier>, DataUiState<List<SellerPropertyDossier>>> { DataUiState.Success(it) }
            .catch { error ->
                val fs = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                emit(DataUiState.Error(fs?.localizedMessage ?: error.localizedMessage ?: "Erreur espace vendeur"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val activeDossier: StateFlow<SellerPropertyDossier?> = combine(
        dossiersState,
        _selectedPropertyId
    ) { state, propId ->
        val list = (state as? DataUiState.Success)?.data.orEmpty()
        list.firstOrNull { it.propertyId == propId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    fun selectProperty(property: PropertyItem) {
        _selectedPropertyId.value = property.id
        _dvfUiState.value = DvfUiState.Idle
        ensureDossierForProperty(property)
    }

    fun setContributorRole(role: DocumentContributorRole) {
        _contributorRole.value = role
    }

    fun ensureDossierForProperty(property: PropertyItem) {
        _selectedPropertyId.value = property.id
        val currentList = (dossiersState.value as? DataUiState.Success)?.data.orEmpty()
        if (currentList.any { it.propertyId == property.id }) return

        viewModelScope.launch {
            repository.ensureDossierForProperty(currentUserId, property)
                .onFailure { onNotify(it.localizedMessage ?: "Initialisation dossier vendeur impossible.") }
        }
    }

    fun updatePropertyAndResidenceConfig(
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        newKind: PropertyKind,
        newResidence: ResidenceType,
        sellerName: String,
        sellerEmail: String,
        sellerPhone: String,
        mandateRef: String,
        mandateType: String = dossier.mandateType,
        mandateDate: String = dossier.mandateDate,
        agencyFeePercent: Double = dossier.agencyFeePercent,
        listingUrl: String = dossier.listingUrl,
        advisorEstimatedEuro: Long = dossier.advisorEstimatedEuro,
        advisorValuationNotes: String = dossier.advisorValuationNotes,
        syndicName: String,
        annualCoproCharges: Long,
        alurWorkFundEuro: Long,
        packPriceEuro: Long,
        paymentLinkUrl: String,
        rgpdConsentGranted: Boolean = dossier.rgpdConsentGranted,
        rgpdConsentDate: String = dossier.rgpdConsentDate
    ) {
        viewModelScope.launch {
            repository.updatePropertyAndResidenceConfig(
                userId = currentUserId,
                dossier = dossier,
                property = property,
                newKind = newKind,
                newResidence = newResidence,
                sellerName = sellerName,
                sellerEmail = sellerEmail,
                sellerPhone = sellerPhone,
                mandateRef = mandateRef,
                mandateType = mandateType,
                mandateDate = mandateDate,
                agencyFeePercent = agencyFeePercent,
                listingUrl = listingUrl,
                advisorEstimatedEuro = advisorEstimatedEuro,
                advisorValuationNotes = advisorValuationNotes,
                syndicName = syndicName,
                annualCoproCharges = annualCoproCharges,
                alurWorkFundEuro = alurWorkFundEuro,
                packPriceEuro = packPriceEuro,
                paymentLinkUrl = paymentLinkUrl,
                rgpdConsentGranted = rgpdConsentGranted,
                rgpdConsentDate = rgpdConsentDate
            ).onSuccess {
                val alurMsg = if (newResidence.alurRequired) "Checklist Loi ALUR activée et conservée." else "Mode hors copropriété activé."
                onNotify("Dossier mis à jour. $alurMsg")
            }.onFailure {
                onNotify(it.localizedMessage ?: "Mise à jour refusée.")
            }
        }
    }

    fun updateDossierMandateAndValuation(
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        mandateRef: String,
        mandateType: String,
        mandateDate: String,
        agencyFeePercent: Double,
        listingUrl: String,
        advisorEstimatedEuro: Long,
        advisorValuationNotes: String,
        dvfSnapshot: DvfValuationSnapshot? = dossier.dvfValuation
    ) {
        viewModelScope.launch {
            repository.updateDossierMandateAndValuation(
                userId = currentUserId,
                dossier = dossier,
                property = property,
                mandateRef = mandateRef,
                mandateType = mandateType,
                mandateDate = mandateDate,
                agencyFeePercent = agencyFeePercent,
                listingUrl = listingUrl,
                advisorEstimatedEuro = advisorEstimatedEuro,
                advisorValuationNotes = advisorValuationNotes,
                dvfSnapshot = dvfSnapshot
            ).onSuccess {
                onNotify("Mandat & avis de valeur enregistrés.")
            }.onFailure {
                onNotify(it.localizedMessage ?: "Mise à jour mandat impossible.")
            }
        }
    }

    fun attachFileToDocumentSlot(
        context: Context,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        slot: SellerSaleDocument,
        uri: Uri,
        actorDisplayName: String
    ) {
        val importRes = SellerDocumentVault.importDocumentFromUri(context, dossier.propertyId, uri)
        val stored = importRes.getOrElse { err ->
            onNotify(err.localizedMessage ?: "Lecture du document impossible.")
            return
        }
        val role = _contributorRole.value
        val updatedSlot = slot.copy(
            status = if (slot.status == "Vérifié") "Vérifié" else "Fourni",
            uploadedByRole = role.code,
            uploadedByName = actorDisplayName.ifBlank { role.label },
            fileName = stored.fileName,
            mimeType = stored.mimeType,
            sizeBytes = stored.sizeBytes,
            localVaultPath = stored.relativeVaultPath
        )
        viewModelScope.launch {
            repository.upsertDocumentSlot(currentUserId, dossier, property, updatedSlot)
                .onSuccess {
                    onNotify("Document « ${stored.fileName} » déposé (${role.label}).")
                }
                .onFailure {
                    onNotify(it.localizedMessage ?: "Sauvegarde refusée.")
                }
        }
    }

    fun updateDocumentDetails(
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        slot: SellerSaleDocument,
        newStatus: String,
        newSummaryText: String
    ) {
        val role = _contributorRole.value
        val updatedSlot = slot.copy(
            status = newStatus,
            summaryText = newSummaryText.trim(),
            uploadedByRole = slot.uploadedByRole.ifBlank { role.code }
        )
        viewModelScope.launch {
            repository.upsertDocumentSlot(currentUserId, dossier, property, updatedSlot)
                .onSuccess { onNotify("Pièce « ${slot.title} » enregistrée ($newStatus).") }
                .onFailure { onNotify(it.localizedMessage ?: "Modification refusée.") }
        }
    }

    fun addCustomDocument(
        context: Context,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        title: String,
        category: String,
        summaryText: String,
        uri: Uri?,
        actorDisplayName: String
    ) {
        val stored = uri?.let {
            SellerDocumentVault.importDocumentFromUri(context, dossier.propertyId, it)
                .getOrElse { err ->
                    onNotify(err.localizedMessage ?: "Lecture du fichier impossible.")
                    return
                }
        }
        val role = _contributorRole.value
        viewModelScope.launch {
            repository.addCustomDocument(
                userId = currentUserId,
                dossier = dossier,
                property = property,
                title = title,
                category = category,
                contributorRole = role,
                contributorName = actorDisplayName,
                summaryText = summaryText,
                storedFile = stored
            ).onSuccess {
                onNotify("Pièce ajoutée au dossier vendeur.")
            }.onFailure {
                onNotify(it.localizedMessage ?: "Ajout impossible.")
            }
        }
    }

    fun generateExecutiveSummary(dossier: SellerPropertyDossier, property: PropertyItem?) {
        if (_isSummarizingAi.value) return
        _isSummarizingAi.value = true
        viewModelScope.launch {
            val aiText = runCatching {
                aiService.summarizeSellerDossierWithAi(dossier, property)
            }.getOrNull()
            val finalSummary = aiText?.takeIf { it.isNotBlank() }
                ?: SellerDocumentCatalog.buildFreeBuyerSummary(dossier, property)

            repository.saveDossier(
                currentUserId,
                dossier.copy(executiveSummary = finalSummary),
                property
            ).onSuccess {
                onNotify("Résumé acquéreur généré et publié.")
            }.onFailure {
                onNotify(it.localizedMessage ?: "Enregistrement impossible.")
            }
            _isSummarizingAi.value = false
        }
    }

    fun runDvfValuation(
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        addressQuery: String,
        surfaceM2: Int,
        dpe: String,
        conditionLabel: String,
        hasExteriorOrParking: Boolean = true,
        criteria: DvfValuationCriteria = DvfValuationCriteria()
    ) {
        _dvfUiState.value = DvfUiState.Loading
        val docs = dossier.documents.filter { it.status != "Non applicable" }
        val providedRatio = if (docs.isEmpty()) 0f else docs.count { it.isProvided }.toFloat() / docs.size.toFloat()

        viewModelScope.launch {
            val res = dvfService.evaluatePropertyWithDvf(
                addressQuery = addressQuery,
                propertyKind = dossier.resolvedKind,
                surfaceM2 = surfaceM2,
                dpe = dpe,
                conditionLabel = conditionLabel,
                hasExteriorOrParking = hasExteriorOrParking,
                providedDocsRatio = providedRatio,
                criteria = criteria
            )
            res.onSuccess { report ->
                _dvfUiState.value = DvfUiState.Ready(report)
                repository.saveDvfValuationSnapshot(currentUserId, dossier, property, report.snapshot)
                onNotify("Avis de valeur DVF calculé (Score ${report.snapshot.valuationScore}/100).")
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Erreur lors de l'interrogation DVF."
                _dvfUiState.value = DvfUiState.Error(msg)
                onNotify(msg)
            }
        }
    }

    fun submitCallbackRequest(
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        sellerName: String,
        sellerPhone: String,
        sellerEmail: String,
        preferredSlot: String,
        projectHorizon: String,
        notes: String,
        rgpdConsentGranted: Boolean = true
    ) {
        if (sellerPhone.isBlank() && sellerEmail.isBlank()) {
            onNotify("Renseignez au moins un numéro de téléphone ou un e-mail.")
            return
        }
        viewModelScope.launch {
            repository.addSellerCallbackRequest(
                userId = currentUserId,
                dossier = dossier,
                property = property,
                sellerName = sellerName,
                sellerPhone = sellerPhone,
                sellerEmail = sellerEmail,
                preferredSlot = preferredSlot,
                projectHorizon = projectHorizon,
                notes = notes,
                rgpdConsentGranted = rgpdConsentGranted
            ).onSuccess {
                onNotify("Demande de rappel envoyée au conseiller pour approfondir l'avis de valeur.")
            }.onFailure {
                onNotify(it.localizedMessage ?: "Impossible d'envoyer la demande.")
            }
        }
    }

    fun updateCallbackStatus(
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        requestId: String,
        newStatus: String
    ) {
        viewModelScope.launch {
            repository.updateCallbackRequestStatus(currentUserId, dossier, property, requestId, newStatus)
                .onSuccess { onNotify("Statut de rappel : $newStatus") }
                .onFailure { onNotify(it.localizedMessage ?: "Mise à jour refusée.") }
        }
    }

    fun submitBuyerDocumentOrder(
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        buyerClient: ClientDossier,
        buyerEmailOverride: String,
        paymentMethodLabel: String,
        markPaidImmediately: Boolean
    ) {
        viewModelScope.launch {
            repository.recordBuyerOrder(
                userId = currentUserId,
                dossier = dossier,
                property = property,
                buyerClientId = buyerClient.id,
                buyerName = buyerClient.name,
                buyerEmail = buyerEmailOverride.ifBlank { buyerClient.email },
                paymentMethodLabel = paymentMethodLabel,
                markPaidImmediately = markPaidImmediately
            ).onSuccess {
                if (markPaidImmediately) {
                    onNotify("Pack complet débloqué ! Vous pouvez consulter et télécharger les documents.")
                } else {
                    onNotify("Demande enregistrée. Le conseiller validera l'envoi dès réception du règlement.")
                }
            }.onFailure {
                onNotify(it.localizedMessage ?: "Commande refusée.")
            }
        }
    }

    fun confirmBuyerOrderDelivery(
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        orderId: String
    ) {
        viewModelScope.launch {
            repository.markOrderPaidAndDelivered(currentUserId, dossier, property, orderId)
                .onSuccess { onNotify("Paiement confirmé — Pack débloqué pour l'acquéreur.") }
                .onFailure { onNotify(it.localizedMessage ?: "Action refusée.") }
        }
    }

    fun recomputeSaleTimeline(
        dossier: SellerPropertyDossier?,
        sectorName: String,
        propertyKind: PropertyKind,
        askingPriceEuro: Long,
        surfaceM2: Int,
        dpe: String,
        conditionLabel: String,
        criteria: DvfValuationCriteria = DvfValuationCriteria(),
        photoCount: Int = 0
    ): SaleTimelineEstimate {
        val docs = dossier?.documents.orEmpty().filter { it.status != "Non applicable" }
        val providedRatio = if (docs.isEmpty()) 0f else docs.count { it.isProvided }.toFloat() / docs.size.toFloat()
        val estimate = SaleTimelineEngine.estimate(
            sectorName = sectorName,
            propertyKind = propertyKind,
            askingPriceEuro = askingPriceEuro,
            surfaceM2 = surfaceM2,
            dpe = dpe,
            conditionLabel = conditionLabel,
            criteria = criteria,
            dvfSnapshot = dossier?.dvfValuation,
            photoCount = photoCount,
            providedDocsRatio = providedRatio,
            mandateType = dossier?.mandateType.orEmpty()
        )
        _saleTimelineEstimate.value = estimate
        return estimate
    }

    fun attachListingPhotos(
        context: Context,
        propertyId: String,
        uris: List<Uri>,
        existingPaths: List<String>,
        onUpdated: (List<String>) -> Unit
    ) {
        if (uris.isEmpty()) return
        val remaining = (6 - existingPaths.size).coerceAtLeast(0)
        if (remaining == 0) {
            onNotify("Limite de 6 photos par annonce atteinte.")
            return
        }
        val added = mutableListOf<String>()
        uris.take(remaining).forEach { uri ->
            SellerDocumentVault.importListingPhotoFromUri(context, propertyId, uri)
                .onSuccess { added += it.relativeVaultPath }
                .onFailure { onNotify(it.localizedMessage ?: "Photo non importée.") }
        }
        if (added.isNotEmpty()) {
            onUpdated((existingPaths + added).take(6))
            onNotify("${added.size} photo(s) ajoutée(s) à l'annonce.")
        }
    }

    fun generateListingWithPhotosAndLocation(
        context: Context,
        rawTitle: String,
        publisherType: String,
        propertyType: String,
        sector: String,
        addressHint: String,
        hideExactAddress: Boolean,
        priceEuro: Long,
        surfaceM2: Long,
        rooms: Long,
        bedrooms: Long,
        dpe: String,
        conditionLabel: String,
        exterior: String,
        parking: String,
        transports: String,
        environment: String,
        highlightsText: String,
        photoPaths: List<String>,
        onResult: (SellerListingAiDraft) -> Unit
    ) {
        if (_isGeneratingListingAi.value) return
        val limitDecision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.AI_PROPERTY_DRAFT)
        if (limitDecision is RateLimitDecision.Blocked) {
            onNotify(limitDecision.reason)
            return
        }
        _isGeneratingListingAi.value = true
        viewModelScope.launch {
            try {
                val bitmaps = photoPaths.take(4).mapNotNull {
                    SellerDocumentVault.loadDownscaledPhotoBitmap(context, it, maxDimPx = 1024)
                }
                val draft = aiService.generateSellerListingWithPhotosAndLocation(
                    rawTitle = rawTitle,
                    publisherType = publisherType,
                    propertyType = propertyType,
                    sector = sector,
                    addressHint = addressHint,
                    hideExactAddress = hideExactAddress,
                    priceEuro = priceEuro,
                    surfaceM2 = surfaceM2,
                    rooms = rooms,
                    bedrooms = bedrooms,
                    dpe = dpe,
                    conditionLabel = conditionLabel,
                    exterior = exterior,
                    parking = parking,
                    transports = transports,
                    environment = environment,
                    highlightsText = highlightsText,
                    photos = bitmaps
                )
                bitmaps.forEach { it.recycle() }
                onResult(draft)
                onNotify("Annonce rédigée par l'IA — Relisez et validez chaque fait avant diffusion.")
            } catch (e: TimeoutCancellationException) {
                onNotify("Rédaction IA expirée (60s). Réessayez.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNotify(e.localizedMessage ?: "Échec de la rédaction IA.")
            } finally {
                _isGeneratingListingAi.value = false
            }
        }
    }

    fun submitSellerListing(
        baseProperty: PropertyItem?,
        dossier: SellerPropertyDossier?,
        title: String,
        propertyType: String,
        sector: String,
        address: String,
        hideExactAddress: Boolean,
        priceEuro: Long,
        surfaceM2: Long,
        rooms: Long,
        bedrooms: Long,
        dpe: String,
        conditionLabel: String,
        exterior: String,
        parking: String,
        transports: String,
        environment: String,
        highlightsText: String,
        description: String,
        photoPaths: List<String>,
        ownerConfirmedAccuracy: Boolean,
        legalPublicationConsent: Boolean,
        targetPublicationStatus: String,
        saleEstimate: SaleTimelineEstimate?,
        onSavedPropertyId: (String) -> Unit
    ) {
        if (title.isBlank()) {
            onNotify("Saisissez un titre d'annonce.")
            return
        }
        if (priceEuro < 10000L || surfaceM2 < 5L) {
            onNotify("Indiquez un prix et une surface valides.")
            return
        }
        if (targetPublicationStatus != "BROUILLON" && (!ownerConfirmedAccuracy || !legalPublicationConsent)) {
            onNotify("Cochez les deux confirmations légales avant d'envoyer l'annonce.")
            return
        }
        val syncFn = onSyncPropertyToPortfolio
        if (syncFn == null) {
            onNotify("Synchronisation catalogue indisponible.")
            return
        }
        viewModelScope.launch {
            val isAdvisor = _contributorRole.value == DocumentContributorRole.AGENT
            val publisherType = baseProperty?.publisherType
                ?.takeIf { it == "AGENCE" || it == "PARTICULIER" }
                ?: if (isAdvisor) "AGENCE" else "PARTICULIER"
            val effectiveStatus = when {
                targetPublicationStatus == "BROUILLON" -> "BROUILLON"
                publisherType == "AGENCE" && isAdvisor && targetPublicationStatus == "PUBLIEE" -> "PUBLIEE"
                else -> "A_VERIFIER"
            }
            val propId = baseProperty?.id?.takeIf { it.isNotBlank() }
                ?: "prop-${UUID.randomUUID().toString().take(8)}"
            val geo = GeoCommuteService.resolveOfflineAddressFallback(address.ifBlank { sector })
            val lat = baseProperty?.latitude?.takeIf { it != 0.0 } ?: geo?.latitude ?: 0.0
            val lon = baseProperty?.longitude?.takeIf { it != 0.0 } ?: geo?.longitude ?: 0.0
            val strengths = highlightsText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(15)
            val statusLabel = when (effectiveStatus) {
                "PUBLIEE" -> if (publisherType == "PARTICULIER") "Annonce Particulier — Vérifiée" else "Vérifiée — Publiée"
                "A_VERIFIER" -> "En attente de modération"
                else -> "Brouillon vendeur"
            }

            val finalProp = (baseProperty ?: PropertyItem()).copy(
                id = propId,
                userId = currentUserId,
                title = title.trim(),
                sector = sector.ifBlank { "Montpellier (34000)" },
                address = address.ifBlank { sector },
                type = propertyType,
                price = priceEuro,
                surface = surfaceM2,
                rooms = rooms.coerceAtLeast(1L),
                bedrooms = bedrooms.coerceAtLeast(0L),
                dpe = dpe.ifBlank { "NC" },
                generalCondition = conditionLabel,
                exterior = exterior,
                parking = parking,
                transports = transports,
                environment = environment,
                strengths = strengths,
                description = description.trim(),
                listingStatus = statusLabel,
                source = if (publisherType == "PARTICULIER") "particulier" else "vérifiée",
                publisherType = publisherType,
                publisherName = dossier?.sellerName?.ifBlank { _contributorRole.value.label } ?: _contributorRole.value.label,
                publicationStatus = effectiveStatus,
                hideExactAddress = hideExactAddress,
                ownerConfirmedAccuracy = ownerConfirmedAccuracy,
                legalPublicationConsent = legalPublicationConsent,
                photoVaultPaths = photoPaths.take(6),
                photoCount = photoPaths.size.coerceIn(0, 6),
                saleDelayMinDays = saleEstimate?.lowDays ?: 0,
                saleDelayLikelyDays = saleEstimate?.likelyDays ?: 0,
                saleDelayMaxDays = saleEstimate?.highDays ?: 0,
                saleDelayConfidence = saleEstimate?.confidenceLabel.orEmpty(),
                saleDelaySummary = saleEstimate?.displayRange.orEmpty(),
                latitude = lat,
                longitude = lon
            )

            syncFn(finalProp).onSuccess { savedId ->
                val ensuredDossier = dossier ?: repository.ensureDossierForProperty(currentUserId, finalProp.copy(id = savedId)).getOrNull()
                if (ensuredDossier != null) {
                    repository.saveDossier(
                        currentUserId,
                        ensuredDossier.copy(propertyTitle = finalProp.title),
                        finalProp.copy(id = savedId)
                    )
                }
                _selectedPropertyId.value = savedId
                onSavedPropertyId(savedId)
                onNotify(
                    when (effectiveStatus) {
                        "PUBLIEE" -> "Annonce publiée dans le catalogue Acquéreur !"
                        "A_VERIFIER" -> "Annonce envoyée en modération conseiller avant publication."
                        else -> "Brouillon d'annonce sauvegardé."
                    }
                )
            }.onFailure {
                onNotify(it.localizedMessage ?: "Publication impossible.")
            }
        }
    }
}
