package com.example.data

import com.example.data.model.BuyerDocumentOrder
import com.example.data.model.DocumentContributorRole
import com.example.data.model.DvfValuationSnapshot
import com.example.data.model.PropertyItem
import com.example.data.model.PropertyKind
import com.example.data.model.ResidenceType
import com.example.data.model.SellerCallbackRequest
import com.example.data.model.SellerDocumentCatalog
import com.example.data.model.SellerPropertyDossier
import com.example.data.model.SellerSaleDocument
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SellerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(expectedUserId: String): String {
        require(InputSanitizer.isValidDocumentId(expectedUserId)) { "Identifiant utilisateur invalide." }
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Session Google requise pour accéder à l'espace vendeur.")
        check(uid == expectedUserId) { "Session modifiée ; opération refusée." }
        return uid
    }

    private fun enforceWriteRateLimit() {
        val decision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.FIRESTORE_WRITE)
        if (decision is RateLimitDecision.Blocked) {
            throw SecurityException(decision.reason)
        }
    }

    private fun sellerDossiersRef(uid: String) =
        db.collection("users").document(uid).collection("seller_dossiers")

    fun observeSellerDossiers(userId: String): Flow<List<SellerPropertyDossier>> {
        val path = "users/$userId/seller_dossiers"
        return sellerDossiersRef(userId)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SellerPropertyDossier::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError != null || error !is CancellationException) {
                    (fsError ?: error as? Exception)?.let {
                        if (auth.currentUser?.uid == userId) {
                            handleFirestoreError(it, OperationType.LIST, path)
                        }
                    }
                }
                throw error
            }
    }

    suspend fun ensureDossierForProperty(
        userId: String,
        property: PropertyItem
    ): Result<SellerPropertyDossier> {
        val dossierId = "seller_${property.id}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val path = "users/$userId/seller_dossiers/$dossierId"
        return try {
            val uid = requireUserId(userId)
            val snap = sellerDossiersRef(uid).document(dossierId).get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    SellerPropertyDossier::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) {
                    return Result.success(existing)
                }
            }
            enforceWriteRateLimit()
            val kind = PropertyKind.fromLabel(property.type)
            val residence = if (kind == PropertyKind.APPARTEMENT) {
                ResidenceType.COPROPRIETE
            } else {
                ResidenceType.MAISON_INDIVIDUELLE
            }
            val initialDocs = SellerDocumentCatalog.buildChecklist(kind, residence, property)
            val draft = SellerPropertyDossier(
                id = dossierId,
                userId = uid,
                propertyId = property.id,
                propertyTitle = property.title.ifBlank { "Bien à vendre" },
                propertyKind = kind.label,
                residenceType = residence.label,
                annualCoproCharges = property.monthlyCharges * 12L,
                packPriceEuro = 29L,
                documents = initialDocs
            )
            val summary = SellerDocumentCatalog.buildFreeBuyerSummary(draft, property)
            val toSave = draft.copy(executiveSummary = summary)
            sellerDossiersRef(uid).document(dossierId).set(toSave.toCreateMap(uid)).await()
            Result.success(toSave)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveDossier(
        userId: String,
        dossier: SellerPropertyDossier,
        property: PropertyItem?
    ): Result<SellerPropertyDossier> {
        val docId = dossier.id.ifBlank { "seller_${dossier.propertyId}" }
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(120)
        val path = "users/$userId/seller_dossiers/$docId"
        return try {
            val uid = requireUserId(userId)
            enforceWriteRateLimit()
            val updated = dossier.copy(
                id = docId,
                userId = uid,
                executiveSummary = dossier.executiveSummary.ifBlank {
                    SellerDocumentCatalog.buildFreeBuyerSummary(dossier, property)
                }
            )
            val ref = sellerDossiersRef(uid).document(docId)
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(updated.toUpdateMap()).await()
            } else {
                ref.set(updated.toCreateMap(uid)).await()
            }
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePropertyAndResidenceConfig(
        userId: String,
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
    ): Result<SellerPropertyDossier> {
        val mergedDocs = SellerDocumentCatalog.buildChecklist(
            propertyKind = newKind,
            residenceType = newResidence,
            property = property,
            existingDocs = dossier.documents
        )
        val safeListingUrl = InputSanitizer.sanitizeHttpUrl(listingUrl)
        val detectedPortal = InputSanitizer.detectListingPortal(safeListingUrl)
        val resolvedConsentDate = if (rgpdConsentGranted && rgpdConsentDate.isBlank()) {
            SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
        } else {
            InputSanitizer.sanitizeText(rgpdConsentDate, 30)
        }
        val next = dossier.copy(
            propertyKind = newKind.label,
            residenceType = newResidence.label,
            sellerName = sellerName.trim(),
            sellerEmail = sellerEmail.trim(),
            sellerPhone = sellerPhone.trim(),
            mandateRef = mandateRef.trim(),
            mandateType = mandateType.trim(),
            mandateDate = mandateDate.trim(),
            agencyFeePercent = agencyFeePercent.coerceIn(0.0, 25.0),
            listingUrl = safeListingUrl,
            listingPortal = detectedPortal,
            advisorEstimatedEuro = advisorEstimatedEuro.coerceAtLeast(0L),
            advisorValuationNotes = InputSanitizer.sanitizeText(advisorValuationNotes, 1500),
            syndicName = syndicName.trim(),
            annualCoproCharges = annualCoproCharges.coerceAtLeast(0L),
            alurWorkFundEuro = alurWorkFundEuro.coerceAtLeast(0L),
            packPriceEuro = packPriceEuro.coerceIn(0L, 5000L),
            paymentLinkUrl = InputSanitizer.sanitizeHttpUrl(paymentLinkUrl),
            rgpdConsentGranted = rgpdConsentGranted,
            rgpdConsentDate = resolvedConsentDate,
            documents = mergedDocs
        )
        val refreshed = next.copy(
            executiveSummary = SellerDocumentCatalog.buildFreeBuyerSummary(next, property)
        )
        return saveDossier(userId, refreshed, property)
    }

    suspend fun updateDossierMandateAndValuation(
        userId: String,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        mandateRef: String,
        mandateType: String,
        mandateDate: String,
        agencyFeePercent: Double,
        listingUrl: String,
        advisorEstimatedEuro: Long,
        advisorValuationNotes: String,
        dvfSnapshot: DvfValuationSnapshot?
    ): Result<SellerPropertyDossier> {
        val safeUrl = InputSanitizer.sanitizeHttpUrl(listingUrl)
        val portal = InputSanitizer.detectListingPortal(safeUrl)
        val next = dossier.copy(
            mandateRef = InputSanitizer.sanitizeText(mandateRef, 64),
            mandateType = InputSanitizer.sanitizeText(mandateType, 40),
            mandateDate = InputSanitizer.sanitizeText(mandateDate, 30),
            agencyFeePercent = agencyFeePercent.coerceIn(0.0, 25.0),
            listingUrl = safeUrl,
            listingPortal = portal,
            advisorEstimatedEuro = advisorEstimatedEuro.coerceIn(0L, 50000000L),
            advisorValuationNotes = InputSanitizer.sanitizeText(advisorValuationNotes, 1500),
            dvfValuation = dvfSnapshot ?: dossier.dvfValuation
        )
        return saveDossier(
            userId,
            next.copy(executiveSummary = SellerDocumentCatalog.buildFreeBuyerSummary(next, property)),
            property
        )
    }

    suspend fun upsertDocumentSlot(
        userId: String,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        updatedDoc: SellerSaleDocument
    ): Result<SellerPropertyDossier> {
        val nowLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date())
        val stamped = updatedDoc.copy(updatedAtLabel = nowLabel)
        val existingIdx = dossier.documents.indexOfFirst { it.id == stamped.id }
        val nextDocs = if (existingIdx >= 0) {
            dossier.documents.toMutableList().apply { set(existingIdx, stamped) }
        } else {
            (dossier.documents + stamped).take(40)
        }
        val next = dossier.copy(documents = nextDocs)
        return saveDossier(
            userId,
            next.copy(executiveSummary = SellerDocumentCatalog.buildFreeBuyerSummary(next, property)),
            property
        )
    }

    suspend fun addCustomDocument(
        userId: String,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        title: String,
        category: String,
        contributorRole: DocumentContributorRole,
        contributorName: String,
        summaryText: String,
        storedFile: VaultStoredFileInfo?
    ): Result<SellerPropertyDossier> {
        val uidSuffix = UUID.randomUUID().toString().take(8)
        val nowLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date())
        val newDoc = SellerSaleDocument(
            id = "doc_custom_$uidSuffix",
            code = "CUSTOM_$uidSuffix",
            title = title.trim().ifBlank { "Document complémentaire" },
            category = category,
            required = false,
            status = if (storedFile != null) "Fourni" else "Manquant",
            uploadedByRole = contributorRole.code,
            uploadedByName = contributorName.ifBlank { contributorRole.label },
            fileName = storedFile?.fileName.orEmpty(),
            mimeType = storedFile?.mimeType.orEmpty(),
            sizeBytes = storedFile?.sizeBytes ?: 0L,
            localVaultPath = storedFile?.relativeVaultPath.orEmpty(),
            summaryText = summaryText.trim(),
            updatedAtLabel = nowLabel
        )
        return upsertDocumentSlot(userId, dossier, property, newDoc)
    }

    suspend fun saveDvfValuationSnapshot(
        userId: String,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        snapshot: DvfValuationSnapshot
    ): Result<SellerPropertyDossier> {
        val next = dossier.copy(dvfValuation = snapshot)
        return saveDossier(
            userId,
            next.copy(executiveSummary = SellerDocumentCatalog.buildFreeBuyerSummary(next, property)),
            property
        )
    }

    suspend fun addSellerCallbackRequest(
        userId: String,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        sellerName: String,
        sellerPhone: String,
        sellerEmail: String,
        preferredSlot: String,
        projectHorizon: String,
        notes: String,
        rgpdConsentGranted: Boolean = true
    ): Result<SellerPropertyDossier> {
        val nowLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date())
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
        val req = SellerCallbackRequest(
            requestId = "cb_${UUID.randomUUID().toString().take(8)}",
            sellerName = sellerName.trim().ifBlank { dossier.sellerName.ifBlank { "Propriétaire vendeur" } },
            sellerPhone = sellerPhone.trim(),
            sellerEmail = sellerEmail.trim(),
            preferredSlot = preferredSlot,
            projectHorizon = projectHorizon,
            notes = notes.trim(),
            status = "Demandée",
            createdAtLabel = nowLabel,
            rgpdConsentGranted = rgpdConsentGranted
        )
        val next = dossier.copy(
            sellerName = dossier.sellerName.ifBlank { req.sellerName },
            sellerPhone = dossier.sellerPhone.ifBlank { req.sellerPhone },
            sellerEmail = dossier.sellerEmail.ifBlank { req.sellerEmail },
            rgpdConsentGranted = dossier.rgpdConsentGranted || rgpdConsentGranted,
            rgpdConsentDate = if (rgpdConsentGranted && dossier.rgpdConsentDate.isBlank()) todayIso else dossier.rgpdConsentDate,
            callbackRequests = (listOf(req) + dossier.callbackRequests).take(30)
        )
        return saveDossier(userId, next, property)
    }

    suspend fun updateCallbackRequestStatus(
        userId: String,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        requestId: String,
        newStatus: String
    ): Result<SellerPropertyDossier> {
        val nextCallbacks = dossier.callbackRequests.map { cb ->
            if (cb.requestId == requestId) cb.copy(status = newStatus) else cb
        }
        return saveDossier(userId, dossier.copy(callbackRequests = nextCallbacks), property)
    }

    suspend fun recordBuyerOrder(
        userId: String,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        buyerClientId: String,
        buyerName: String,
        buyerEmail: String,
        paymentMethodLabel: String,
        markPaidImmediately: Boolean
    ): Result<SellerPropertyDossier> {
        val nowLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date())
        val order = BuyerDocumentOrder(
            orderId = "ord_${UUID.randomUUID().toString().take(8)}",
            buyerClientId = buyerClientId,
            buyerName = buyerName.trim(),
            buyerEmail = buyerEmail.trim(),
            amountEuro = dossier.packPriceEuro,
            status = if (markPaidImmediately) "PAID" else "PENDING_PAYMENT",
            paymentMethodLabel = paymentMethodLabel,
            createdAtLabel = nowLabel,
            deliveredAtLabel = if (markPaidImmediately) nowLabel else ""
        )
        val filtered = dossier.buyerOrders.filterNot {
            it.buyerClientId == buyerClientId && it.status == "PENDING_PAYMENT"
        }
        val next = dossier.copy(buyerOrders = (listOf(order) + filtered).take(50))
        return saveDossier(userId, next, property)
    }

    suspend fun markOrderPaidAndDelivered(
        userId: String,
        dossier: SellerPropertyDossier,
        property: PropertyItem?,
        orderId: String
    ): Result<SellerPropertyDossier> {
        val nowLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date())
        val nextOrders = dossier.buyerOrders.map { ord ->
            if (ord.orderId == orderId) {
                ord.copy(status = "DELIVERED", deliveredAtLabel = nowLabel)
            } else {
                ord
            }
        }
        return saveDossier(userId, dossier.copy(buyerOrders = nextOrders), property)
    }
}
