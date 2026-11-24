package com.example.data

import com.example.data.model.BuyerChatMessage
import com.example.data.model.ClientDossier
import com.example.data.model.OfferRecord
import com.example.data.model.PropertyFeedback
import com.example.data.model.PropertyItem
import com.example.data.model.TimelineEvent
import com.example.data.model.VisitRecord
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.security.AppSecurityManager
import com.example.security.InputSanitizer
import com.example.security.RateLimitChannel
import com.example.security.RateLimitDecision
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MontyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(expectedUserId: String): String {
        require(InputSanitizer.isValidDocumentId(expectedUserId)) { "Identifiant utilisateur invalide." }
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
        return uid
    }

    private fun enforceWriteRateLimit() {
        val decision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.FIRESTORE_WRITE)
        if (decision is RateLimitDecision.Blocked) {
            throw SecurityException(decision.reason)
        }
    }

    private fun clientsRef(uid: String) = db.collection("users").document(uid).collection("clients")
    private fun propertiesRef(uid: String) = db.collection("users").document(uid).collection("properties")
    private fun visitsRef(uid: String) = db.collection("users").document(uid).collection("visits")
    private fun offersRef(uid: String) = db.collection("users").document(uid).collection("offers")
    private fun buyerChatsRef(uid: String) = db.collection("users").document(uid).collection("buyer_chats")

    fun observeClients(userId: String): Flow<List<ClientDossier>> {
        val path = "users/$userId/clients"
        return clientsRef(userId)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ClientDossier::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeProperties(userId: String): Flow<List<PropertyItem>> {
        val path = "users/$userId/properties"
        return propertiesRef(userId)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PropertyItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeVisits(userId: String): Flow<List<VisitRecord>> {
        val path = "users/$userId/visits"
        return visitsRef(userId)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    VisitRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.date }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeOffers(userId: String): Flow<List<OfferRecord>> {
        val path = "users/$userId/offers"
        return offersRef(userId)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    OfferRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.date }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeBuyerChats(userId: String): Flow<List<BuyerChatMessage>> {
        val path = "users/$userId/buyer_chats"
        return buyerChatsRef(userId)
            .limit(150)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    BuyerChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    private fun logStreamError(error: Throwable, op: OperationType, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    suspend fun seedWorkspaceIfEmpty(userId: String): Result<Unit> {
        val path = "users/$userId/clients"
        return try {
            val uid = requireUserId(userId)
            val existingClients = clientsRef(uid).limit(1).get().await()
            val existingProps = propertiesRef(uid).limit(1).get().await()
            if (existingClients.isEmpty && existingProps.isEmpty) {
                val batch = db.batch()
                MontyMarketData.initialClients(uid).forEach { (id, dossier) ->
                    batch.set(clientsRef(uid).document(id), dossier.toCreateMap(uid))
                }
                MontyMarketData.initialProperties(uid).forEach { (id, prop) ->
                    batch.set(propertiesRef(uid).document(id), prop.toCreateMap(uid))
                }
                MontyMarketData.initialVisits(uid).forEach { (id, visit) ->
                    batch.set(visitsRef(uid).document(id), visit.toCreateMap(uid))
                }
                MontyMarketData.initialOffers(uid).forEach { (id, offer) ->
                    batch.set(offersRef(uid).document(id), offer.toCreateMap(uid))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addClient(userId: String, dossier: ClientDossier): Result<String> {
        val docId = "cli-${UUID.randomUUID().toString().take(8)}"
        val path = "users/$userId/clients/$docId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            val sanitizedName = InputSanitizer.sanitizeText(dossier.name, 120)
            require(sanitizedName.isNotBlank()) { "Le nom du dossier est obligatoire." }
            val cleanDossier = dossier.copy(
                name = sanitizedName,
                phone = InputSanitizer.sanitizeText(dossier.phone, 40),
                email = InputSanitizer.sanitizeText(dossier.email, 120),
                situation = InputSanitizer.sanitizeText(dossier.situation, 300),
                privateNotes = InputSanitizer.sanitizeText(dossier.privateNotes, 3000),
                sectors = InputSanitizer.sanitizeStringList(dossier.sectors, 15, 100),
                essentialCriteria = InputSanitizer.sanitizeStringList(dossier.essentialCriteria, 20, 80)
            )
            clientsRef(uid).document(docId).set(cleanDossier.toCreateMap(uid)).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateClientStage(userId: String, clientId: String, stage: Int): Result<Unit> {
        val path = "users/$userId/clients/$clientId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(clientId))
            clientsRef(uid).document(clientId).update(
                mapOf(
                    "projectStage" to stage.coerceIn(0, 5).toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun togglePortalAccess(userId: String, clientId: String, active: Boolean): Result<Unit> {
        val path = "users/$userId/clients/$clientId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(clientId))
            clientsRef(uid).document(clientId).update(
                mapOf(
                    "portalActive" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivateNotes(userId: String, clientId: String, notes: String): Result<Unit> {
        val path = "users/$userId/clients/$clientId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(clientId))
            val sanitizedNotes = InputSanitizer.sanitizeText(notes, 3000)
            clientsRef(uid).document(clientId).update(
                mapOf(
                    "privateNotes" to sanitizedNotes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeClientUpdates(userId: String, clientId: String): Result<Unit> {
        val path = "users/$userId/clients/$clientId"
        return try {
            val uid = requireUserId(userId)
            require(InputSanitizer.isValidDocumentId(clientId))
            clientsRef(uid).document(clientId).update(
                mapOf(
                    "criteriaModifiedByClient" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveBuyerCriteria(
        userId: String,
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
        updateLabel: String,
        searchCenterAddress: String = client.searchCenterAddress,
        searchCenterLat: Double = client.searchCenterLat,
        searchCenterLon: Double = client.searchCenterLon,
        searchMode: String = client.searchMode,
        searchRadiusKm: Int = client.searchRadiusKm,
        searchMaxTravelMinutes: Int = client.searchMaxTravelMinutes,
        searchTransportMode: String = client.searchTransportMode,
        rgpdConsentGranted: Boolean = client.rgpdConsentGranted,
        rgpdConsentDate: String = client.rgpdConsentDate
    ): Result<Unit> {
        val path = "users/$userId/clients/${client.id}"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(client.id))
            val cleanMessage = InputSanitizer.sanitizeText(clientMessageToAdvisor, 1000)
            val cleanType = InputSanitizer.sanitizeText(propertyType, 60)
            val cleanDelai = InputSanitizer.sanitizeText(timelineDelai, 100)
            val cleanSectors = InputSanitizer.sanitizeStringList(sectors, 15, 100)
            val cleanEssentials = InputSanitizer.sanitizeStringList(essentialCriteria, 20, 80)
            val cleanCenterAddress = InputSanitizer.sanitizeText(searchCenterAddress, 220)
            val resolvedMode = SearchPerimeterMode.fromCode(searchMode).code
            val resolvedTransport = TransportMode.fromCode(searchTransportMode).code
            val newTimeline = listOf(
                TimelineEvent(
                    date = "Aujourd'hui ($updateLabel)",
                    type = "criteres",
                    title = "Mise à jour des critères depuis le Portail Client",
                    detail = "Budget ${MontyMarketData.formatEuro(budgetMax)} • Min. $minSurface m² • $minRooms ch."
                )
            ) + client.timeline
            clientsRef(uid).document(client.id).update(
                mapOf(
                    "budgetMax" to budgetMax.coerceIn(10000L, 50000000L),
                    "downPayment" to downPayment.coerceIn(0L, 50000000L),
                    "sectors" to cleanSectors,
                    "propertyType" to cleanType,
                    "minSurface" to minSurface.coerceIn(0L, 2000L),
                    "minRooms" to minRooms.coerceIn(0L, 20L),
                    "essentialCriteria" to cleanEssentials,
                    "secondaryCriteria" to client.secondaryCriteria.take(20),
                    "timelineDelai" to cleanDelai,
                    "clientMessageToAdvisor" to cleanMessage,
                    "criteriaModifiedByClient" to true,
                    "lastClientUpdateLabel" to "Aujourd'hui à ${InputSanitizer.sanitizeText(updateLabel, 20)}",
                    "timeline" to newTimeline.take(60).map { it.toMap() },
                    "searchCenterAddress" to cleanCenterAddress,
                    "searchCenterLat" to searchCenterLat.coerceIn(-90.0, 90.0),
                    "searchCenterLon" to searchCenterLon.coerceIn(-180.0, 180.0),
                    "searchMode" to resolvedMode,
                    "searchRadiusKm" to searchRadiusKm.coerceIn(1, 100),
                    "searchMaxTravelMinutes" to searchMaxTravelMinutes.coerceIn(5, 120),
                    "searchTransportMode" to resolvedTransport,
                    "rgpdConsentGranted" to rgpdConsentGranted,
                    "rgpdConsentDate" to InputSanitizer.sanitizeText(rgpdConsentDate, 30),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateClientRgpdConsent(
        userId: String,
        clientId: String,
        granted: Boolean,
        consentDate: String
    ): Result<Unit> {
        val path = "users/$userId/clients/$clientId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(clientId))
            clientsRef(uid).document(clientId).update(
                mapOf(
                    "rgpdConsentGranted" to granted,
                    "rgpdConsentDate" to InputSanitizer.sanitizeText(consentDate, 30),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun eraseClientAndRelatedData(
        userId: String,
        clientId: String,
        chatIds: List<String>,
        visitIds: List<String>,
        offerIds: List<String>
    ): Result<Unit> {
        val path = "users/$userId/clients/$clientId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(clientId))
            val batch = db.batch()
            chatIds.filter { InputSanitizer.isValidDocumentId(it) }.take(150).forEach { id ->
                batch.delete(buyerChatsRef(uid).document(id))
            }
            visitIds.filter { InputSanitizer.isValidDocumentId(it) }.take(100).forEach { id ->
                batch.delete(visitsRef(uid).document(id))
            }
            offerIds.filter { InputSanitizer.isValidDocumentId(it) }.take(100).forEach { id ->
                batch.delete(offersRef(uid).document(id))
            }
            batch.delete(clientsRef(uid).document(clientId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBuyerFavorite(
        userId: String,
        client: ClientDossier,
        property: PropertyItem
    ): Result<Boolean> {
        val path = "users/$userId/clients/${client.id}"
        return try {
            val uid = requireUserId(userId)
            val isCurrentlyFav = client.selectedPropertyIds.contains(property.id)
            val nextSelected = if (isCurrentlyFav) {
                client.selectedPropertyIds.filterNot { it == property.id }
            } else {
                (client.selectedPropertyIds + property.id).take(50)
            }
            val nextTimeline = if (!isCurrentlyFav) {
                listOf(
                    TimelineEvent(
                        date = "Aujourd'hui",
                        type = "selection",
                        title = "Sélection client : ${property.title}",
                        detail = "Ajouté aux favoris depuis le portail client (${MontyMarketData.formatEuro(property.price)})."
                    )
                ) + client.timeline
            } else {
                client.timeline
            }
            clientsRef(uid).document(client.id).update(
                mapOf(
                    "selectedPropertyIds" to nextSelected,
                    "criteriaModifiedByClient" to true,
                    "timeline" to nextTimeline.take(60).map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(!isCurrentlyFav)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBuyerFeedback(
        userId: String,
        client: ClientDossier,
        propertyId: String,
        feedback: PropertyFeedback
    ): Result<Unit> {
        val path = "users/$userId/clients/${client.id}"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(client.id) && InputSanitizer.isValidDocumentId(propertyId))
            val sanitizedFeedback = feedback.copy(
                rating = feedback.rating.coerceIn(1L, 5L),
                status = InputSanitizer.sanitizeText(feedback.status, 60),
                comment = InputSanitizer.sanitizeText(feedback.comment, 600)
            )
            val nextFeedback = client.propertyFeedback.toMutableMap().apply {
                put(propertyId, sanitizedFeedback)
            }
            clientsRef(uid).document(client.id).update(
                mapOf(
                    "propertyFeedback" to nextFeedback.mapValues { it.value.toMap() },
                    "criteriaModifiedByClient" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addProperty(userId: String, property: PropertyItem): Result<String> {
        val docId = "prop-${UUID.randomUUID().toString().take(8)}"
        val path = "users/$userId/properties/$docId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            val cleanTitle = InputSanitizer.sanitizeText(property.title, 160)
            require(cleanTitle.isNotBlank()) { "Le titre du bien est obligatoire." }
            val safeUrl = InputSanitizer.sanitizeHttpUrl(property.listingUrl)
            val resolvedPortal = if (safeUrl.isNotBlank()) {
                InputSanitizer.detectListingPortal(safeUrl)
            } else {
                InputSanitizer.sanitizeText(property.listingPortal, 60)
            }
            val (fallbackLat, fallbackLon) = GeoCommuteService.resolveCoordinatesForProperty(property)
            val cleanProperty = property.copy(
                title = cleanTitle,
                sector = InputSanitizer.sanitizeText(property.sector, 100),
                address = InputSanitizer.sanitizeText(property.address, 200),
                exterior = InputSanitizer.sanitizeText(property.exterior, 160),
                parking = InputSanitizer.sanitizeText(property.parking, 160),
                strengths = InputSanitizer.sanitizeStringList(property.strengths, 15, 100),
                weaknesses = InputSanitizer.sanitizeStringList(property.weaknesses, 15, 100),
                description = InputSanitizer.sanitizeText(property.description, 1500),
                mandateRef = InputSanitizer.sanitizeText(property.mandateRef, 64),
                mandateType = InputSanitizer.sanitizeText(property.mandateType, 40),
                mandateDate = InputSanitizer.sanitizeText(property.mandateDate, 30),
                agencyFeePercent = property.agencyFeePercent.coerceIn(0.0, 25.0),
                listingUrl = safeUrl,
                listingPortal = resolvedPortal,
                latitude = if (property.latitude != 0.0) property.latitude else fallbackLat,
                longitude = if (property.longitude != 0.0) property.longitude else fallbackLon
            )
            propertiesRef(uid).document(docId).set(cleanProperty.toCreateMap(uid)).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePropertyMandateAndValuation(
        userId: String,
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
    ): Result<String> {
        val path = "users/$userId/properties/$propertyId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(propertyId))
            val safeUrl = InputSanitizer.sanitizeHttpUrl(listingUrl)
            val portal = InputSanitizer.detectListingPortal(safeUrl)
            propertiesRef(uid).document(propertyId).update(
                mapOf(
                    "price" to price.coerceIn(10000L, 50000000L),
                    "listingStatus" to InputSanitizer.sanitizeText(listingStatus, 80).ifBlank { "Vérifiée" },
                    "mandateRef" to InputSanitizer.sanitizeText(mandateRef, 64),
                    "mandateType" to InputSanitizer.sanitizeText(mandateType, 40),
                    "mandateDate" to InputSanitizer.sanitizeText(mandateDate, 30),
                    "agencyFeePercent" to agencyFeePercent.coerceIn(0.0, 25.0),
                    "listingUrl" to safeUrl,
                    "listingPortal" to portal,
                    "dvfEstimatedEuro" to dvfEstimatedEuro.coerceIn(0L, 50000000L),
                    "dvfEstimatedLowEuro" to dvfEstimatedLowEuro.coerceIn(0L, 50000000L),
                    "dvfEstimatedHighEuro" to dvfEstimatedHighEuro.coerceIn(0L, 50000000L),
                    "dvfScore" to dvfScore.coerceIn(0, 100),
                    "dvfCriteriaSummary" to InputSanitizer.sanitizeText(dvfCriteriaSummary, 400),
                    "advisorValuationNotes" to InputSanitizer.sanitizeText(advisorValuationNotes, 1500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(portal)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertSellerListingProperty(
        userId: String,
        property: PropertyItem
    ): Result<String> {
        val docId = property.id.takeIf { InputSanitizer.isValidDocumentId(it) }
            ?: "prop-${UUID.randomUUID().toString().take(8)}"
        val path = "users/$userId/properties/$docId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            val cleanTitle = InputSanitizer.sanitizeText(property.title, 160)
            require(cleanTitle.isNotBlank()) { "Le titre de l'annonce est obligatoire." }
            val safeUrl = InputSanitizer.sanitizeHttpUrl(property.listingUrl)
            val resolvedPortal = if (safeUrl.isNotBlank()) {
                InputSanitizer.detectListingPortal(safeUrl)
            } else {
                InputSanitizer.sanitizeText(property.listingPortal, 60)
            }
            val (fallbackLat, fallbackLon) = GeoCommuteService.resolveCoordinatesForProperty(property)
            val resolvedPublisherType = if (property.publisherType == "PARTICULIER") "PARTICULIER" else "AGENCE"
            val resolvedPubStatus = when (property.publicationStatus) {
                "BROUILLON", "A_VERIFIER", "PUBLIEE" -> property.publicationStatus
                else -> "A_VERIFIER"
            }
            val cleanPhotos = InputSanitizer.sanitizeStringList(property.photoVaultPaths, 6, 260)
            val cleanProperty = property.copy(
                id = docId,
                userId = uid,
                title = cleanTitle,
                sector = InputSanitizer.sanitizeText(property.sector, 100).ifBlank { "Montpellier (34000)" },
                address = InputSanitizer.sanitizeText(property.address, 200),
                type = InputSanitizer.sanitizeText(property.type, 60).ifBlank { "Appartement" },
                dpe = InputSanitizer.sanitizeText(property.dpe, 5).ifBlank { "NC" },
                ges = InputSanitizer.sanitizeText(property.ges, 5).ifBlank { "NC" },
                listingStatus = InputSanitizer.sanitizeText(property.listingStatus, 80),
                source = if (resolvedPublisherType == "PARTICULIER") "particulier" else "vérifiée",
                exterior = InputSanitizer.sanitizeText(property.exterior, 160),
                parking = InputSanitizer.sanitizeText(property.parking, 160),
                environment = InputSanitizer.sanitizeText(property.environment, 300),
                transports = InputSanitizer.sanitizeText(property.transports, 200),
                generalCondition = InputSanitizer.sanitizeText(property.generalCondition, 200),
                strengths = InputSanitizer.sanitizeStringList(property.strengths, 15, 100),
                weaknesses = InputSanitizer.sanitizeStringList(property.weaknesses, 15, 100),
                description = InputSanitizer.sanitizeText(property.description, 1500),
                mandateRef = InputSanitizer.sanitizeText(property.mandateRef, 64),
                mandateType = InputSanitizer.sanitizeText(property.mandateType, 40),
                mandateDate = InputSanitizer.sanitizeText(property.mandateDate, 30),
                agencyFeePercent = property.agencyFeePercent.coerceIn(0.0, 25.0),
                listingUrl = safeUrl,
                listingPortal = resolvedPortal,
                latitude = if (property.latitude != 0.0) property.latitude else fallbackLat,
                longitude = if (property.longitude != 0.0) property.longitude else fallbackLon,
                publisherType = resolvedPublisherType,
                publisherName = InputSanitizer.sanitizeText(property.publisherName, 120),
                publicationStatus = resolvedPubStatus,
                photoVaultPaths = cleanPhotos,
                photoCount = maxOf(property.photoCount, cleanPhotos.size).coerceIn(0, 6),
                saleDelayConfidence = InputSanitizer.sanitizeText(property.saleDelayConfidence, 80),
                saleDelaySummary = InputSanitizer.sanitizeText(property.saleDelaySummary, 260),
                cadastralRef = InputSanitizer.sanitizeText(property.cadastralRef, 80),
                pluZone = InputSanitizer.sanitizeText(property.pluZone, 60)
            )

            val docRef = propertiesRef(uid).document(docId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "title" to cleanProperty.title,
                        "sector" to cleanProperty.sector,
                        "address" to cleanProperty.address,
                        "type" to cleanProperty.type,
                        "price" to cleanProperty.price.coerceIn(10000L, 50000000L),
                        "surface" to cleanProperty.surface.coerceIn(5L, 5000L),
                        "rooms" to cleanProperty.rooms.coerceIn(1L, 50L),
                        "bedrooms" to cleanProperty.bedrooms.coerceIn(0L, 30L),
                        "dpe" to cleanProperty.dpe,
                        "ges" to cleanProperty.ges,
                        "listingStatus" to cleanProperty.listingStatus,
                        "source" to cleanProperty.source,
                        "exterior" to cleanProperty.exterior,
                        "parking" to cleanProperty.parking,
                        "environment" to cleanProperty.environment,
                        "transports" to cleanProperty.transports,
                        "generalCondition" to cleanProperty.generalCondition,
                        "strengths" to cleanProperty.strengths,
                        "weaknesses" to cleanProperty.weaknesses,
                        "description" to cleanProperty.description,
                        "mandateRef" to cleanProperty.mandateRef,
                        "mandateType" to cleanProperty.mandateType,
                        "mandateDate" to cleanProperty.mandateDate,
                        "agencyFeePercent" to cleanProperty.agencyFeePercent,
                        "listingUrl" to cleanProperty.listingUrl,
                        "listingPortal" to cleanProperty.listingPortal,
                        "latitude" to cleanProperty.latitude.coerceIn(-90.0, 90.0),
                        "longitude" to cleanProperty.longitude.coerceIn(-180.0, 180.0),
                        "publisherType" to cleanProperty.publisherType,
                        "publisherName" to cleanProperty.publisherName,
                        "publicationStatus" to cleanProperty.publicationStatus,
                        "hideExactAddress" to cleanProperty.hideExactAddress,
                        "ownerConfirmedAccuracy" to cleanProperty.ownerConfirmedAccuracy,
                        "legalPublicationConsent" to cleanProperty.legalPublicationConsent,
                        "photoVaultPaths" to cleanProperty.photoVaultPaths,
                        "photoCount" to cleanProperty.photoCount,
                        "saleDelayMinDays" to cleanProperty.saleDelayMinDays.coerceIn(0, 1000),
                        "saleDelayLikelyDays" to cleanProperty.saleDelayLikelyDays.coerceIn(0, 1000),
                        "saleDelayMaxDays" to cleanProperty.saleDelayMaxDays.coerceIn(0, 1000),
                        "saleDelayConfidence" to cleanProperty.saleDelayConfidence,
                        "saleDelaySummary" to cleanProperty.saleDelaySummary,
                        "cadastralRef" to cleanProperty.cadastralRef,
                        "pluZone" to cleanProperty.pluZone,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(cleanProperty.toCreateMap(uid)).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePropertyPublicationStatus(
        userId: String,
        propertyId: String,
        publicationStatus: String,
        listingStatus: String
    ): Result<Unit> {
        val path = "users/$userId/properties/$propertyId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(propertyId))
            val safeStatus = when (publicationStatus) {
                "BROUILLON", "A_VERIFIER", "PUBLIEE" -> publicationStatus
                else -> "A_VERIFIER"
            }
            propertiesRef(uid).document(propertyId).update(
                mapOf(
                    "publicationStatus" to safeStatus,
                    "listingStatus" to InputSanitizer.sanitizeText(listingStatus, 80),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addVisit(userId: String, visit: VisitRecord): Result<String> {
        val docId = "vis-${UUID.randomUUID().toString().take(8)}"
        val path = "users/$userId/visits/$docId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(visit.clientId) && InputSanitizer.isValidDocumentId(visit.propertyId))
            val cleanVisit = visit.copy(
                clientImpressions = InputSanitizer.sanitizeText(visit.clientImpressions, 1000),
                positives = InputSanitizer.sanitizeText(visit.positives, 600),
                negatives = InputSanitizer.sanitizeText(visit.negatives, 600),
                technicalNotes = InputSanitizer.sanitizeText(visit.technicalNotes, 600),
                nextAction = InputSanitizer.sanitizeText(visit.nextAction, 600)
            )
            visitsRef(uid).document(docId).set(cleanVisit.toCreateMap(uid)).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addOffer(userId: String, offer: OfferRecord): Result<String> {
        val docId = "off-${UUID.randomUUID().toString().take(8)}"
        val path = "users/$userId/offers/$docId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(offer.clientId) && InputSanitizer.isValidDocumentId(offer.propertyId))
            val cleanOffer = offer.copy(
                sellerContext = InputSanitizer.sanitizeText(offer.sellerContext, 600),
                clientFinancing = InputSanitizer.sanitizeText(offer.clientFinancing, 600),
                suspensiveConditions = InputSanitizer.sanitizeText(offer.suspensiveConditions, 600),
                arguments = InputSanitizer.sanitizeStringList(offer.arguments, 15, 200)
            )
            offersRef(uid).document(docId).set(cleanOffer.toCreateMap(uid)).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addBuyerChatMessage(
        userId: String,
        clientId: String,
        role: String,
        content: String,
        topic: String = ""
    ): Result<String> {
        val docId = "msg-${UUID.randomUUID().toString().take(8)}"
        val path = "users/$userId/buyer_chats/$docId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(clientId))
            val sanitizedContent = InputSanitizer.sanitizeText(content, 6000)
            require(sanitizedContent.isNotBlank()) { "Le message ne peut pas être vide." }
            val msg = BuyerChatMessage(
                userId = uid,
                clientId = clientId,
                role = role,
                content = sanitizedContent,
                topic = InputSanitizer.sanitizeText(topic, 80)
            )
            buyerChatsRef(uid).document(docId).set(msg.toCreateMap(uid)).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearBuyerChatForClient(userId: String, messageIds: List<String>): Result<Unit> {
        val path = "users/$userId/buyer_chats"
        return try {
            val uid = requireUserId(userId)
            if (messageIds.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            messageIds.filter { InputSanitizer.isValidDocumentId(it) }.take(200).forEach { id ->
                batch.delete(buyerChatsRef(uid).document(id))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
