package com.example.data

import com.example.data.model.AgencyAuditLog
import com.example.data.model.AgencyInvite
import com.example.data.model.AgencyMember
import com.example.data.model.AgencyOrg
import com.example.data.model.AgencyRole
import com.example.data.model.ContactExchangeEntry
import com.example.data.model.CrmContact
import com.example.data.model.FollowUpTask
import com.example.data.model.PropertyItem
import com.example.data.model.SaasPlan
import com.example.data.model.computeViewerUids
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.security.AppSecurityManager
import com.example.security.InputSanitizer
import com.example.security.RateLimitChannel
import com.example.security.RateLimitDecision
import com.example.security.RgpdComplianceManager
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SaasAgencyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Connexion Google requise.")

    private fun currentDisplayName(): String {
        val user = auth.currentUser
        val raw = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Conseiller Monty"
        return InputSanitizer.sanitizeText(raw, 120)
    }

    private fun currentEmail(): String =
        InputSanitizer.sanitizeText(auth.currentUser?.email.orEmpty(), 160)

    private fun enforceWriteRateLimit() {
        val decision = AppSecurityManager.rateLimiter.checkAndConsume(RateLimitChannel.FIRESTORE_WRITE)
        if (decision is RateLimitDecision.Blocked) {
            throw SecurityException(decision.reason)
        }
    }

    private fun agenciesRef() = db.collection("agencies")
    private fun invitesRef() = db.collection("agency_invites")
    private fun membersRef(agencyId: String) = agenciesRef().document(agencyId).collection("members")
    private fun contactsRef(agencyId: String) = agenciesRef().document(agencyId).collection("contacts")
    private fun tasksRef(agencyId: String) = agenciesRef().document(agencyId).collection("tasks")
    private fun auditLogsRef(agencyId: String) = agenciesRef().document(agencyId).collection("audit_logs")

    fun observeMyAgencies(uid: String): Flow<List<AgencyOrg>> {
        val path = "agencies"
        return agenciesRef()
            .whereArrayContains("memberUids", uid)
            .limit(20)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AgencyOrg::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeMembers(agencyId: String, uid: String): Flow<List<AgencyMember>> {
        val path = "agencies/$agencyId/members"
        return membersRef(agencyId)
            .whereArrayContains("viewerUids", uid)
            .limit(50)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AgencyMember::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(compareBy<AgencyMember> { !it.agencyRole.isManagerOrAdmin() }.thenBy { it.displayName })
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeContacts(agencyId: String, uid: String): Flow<List<CrmContact>> {
        val path = "agencies/$agencyId/contacts"
        return contactsRef(agencyId)
            .whereArrayContains("viewerUids", uid)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CrmContact::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeTasks(agencyId: String, uid: String): Flow<List<FollowUpTask>> {
        val path = "agencies/$agencyId/tasks"
        return tasksRef(agencyId)
            .whereArrayContains("viewerUids", uid)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FollowUpTask::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareBy<FollowUpTask> { it.status == "Terminée" }
                        .thenBy { it.dueDate }
                        .thenBy { it.dueTime }
                )
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeAuditLogs(agencyId: String, uid: String): Flow<List<AgencyAuditLog>> {
        val path = "agencies/$agencyId/audit_logs"
        return auditLogsRef(agencyId)
            .whereArrayContains("viewerUids", uid)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AgencyAuditLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun bootstrapDefaultAgencyIfEmpty(uid: String): Result<Unit> {
        return try {
            requireUid()
            val existing = agenciesRef().whereArrayContains("memberUids", uid).limit(1).get().await()
            if (existing.isEmpty) {
                createAgency(
                    name = "Agence ${currentDisplayName().substringBefore(" ")} Immo",
                    plan = SaasPlan.AGENCY,
                    city = "Montpellier"
                ).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "agencies")
            Result.failure(e)
        }
    }

    suspend fun createAgency(
        name: String,
        plan: SaasPlan,
        city: String
    ): Result<String> {
        val agencyId = "ag-${UUID.randomUUID().toString().take(8)}"
        val path = "agencies/$agencyId"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            val cleanName = InputSanitizer.sanitizeText(name, 120)
            require(cleanName.length >= 2) { "Le nom de l'agence doit contenir au moins 2 caractères." }
            val cleanCity = InputSanitizer.sanitizeText(city, 80).ifBlank { "Montpellier" }
            val actorName = currentDisplayName()
            val defaultCode = "MNT-${UUID.randomUUID().toString().take(6).uppercase(Locale.ROOT)}"

            val agency = AgencyOrg(
                id = agencyId,
                name = cleanName,
                plan = plan.code,
                subscriptionStatus = "ACTIVE",
                ownerUid = uid,
                city = cleanCity,
                inviteCode = defaultCode,
                directorUids = listOf(uid),
                memberUids = listOf(uid)
            )
            val directorMember = AgencyMember(
                agencyId = agencyId,
                uid = uid,
                email = currentEmail(),
                displayName = actorName,
                role = AgencyRole.DIRECTOR.code,
                status = "ACTIVE",
                viewerUids = listOf(uid)
            )
            val auditLog = AgencyAuditLog(
                agencyId = agencyId,
                actorUid = uid,
                actorName = actorName,
                actionType = "AGENCY_CREATED",
                targetLabel = cleanName,
                detail = "Création espace ${plan.label} à $cleanCity (aucune donnée fictive injectée)",
                viewerUids = listOf(uid)
            )

            val batch = db.batch()
            batch.set(agenciesRef().document(agencyId), agency.toCreateMap(uid))
            batch.set(membersRef(agencyId).document(uid), directorMember.toCreateMap())
            batch.set(auditLogsRef(agencyId).document("log-${UUID.randomUUID().toString().take(8)}"), auditLog.toCreateMap())
            batch.commit().await()
            Result.success(agencyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAgencySettings(
        agency: AgencyOrg,
        newName: String,
        newPlan: SaasPlan,
        newCity: String
    ): Result<Unit> {
        val path = "agencies/${agency.id}"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id))
            val cleanName = InputSanitizer.sanitizeText(newName, 120)
            require(cleanName.length >= 2) { "Nom d'agence invalide." }
            val cleanCity = InputSanitizer.sanitizeText(newCity, 80).ifBlank { "Montpellier" }

            val batch = db.batch()
            batch.update(
                agenciesRef().document(agency.id),
                mapOf(
                    "name" to cleanName,
                    "plan" to newPlan.code,
                    "city" to cleanCity,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = currentDisplayName(),
                actionType = "PLAN_UPDATED",
                targetLabel = cleanName,
                detail = "Abonnement mis à jour : ${newPlan.label} ($cleanCity)",
                viewerUids = agency.directorUids
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun inviteCollaborator(
        agency: AgencyOrg,
        targetEmail: String,
        displayName: String
    ): Result<String> {
        val inviteCode = "MNT-${UUID.randomUUID().toString().take(6).uppercase(Locale.ROOT)}"
        val path = "agency_invites/$inviteCode"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id))
            val cleanName = InputSanitizer.sanitizeText(displayName, 120)
            require(cleanName.isNotBlank()) { "Le nom du collaborateur est requis." }
            val cleanEmail = InputSanitizer.sanitizeText(targetEmail, 160).lowercase(Locale.ROOT)
            require(cleanEmail.contains("@")) { "Adresse e-mail Google valide requise pour l'invitation." }

            val invite = AgencyInvite(
                inviteCode = inviteCode,
                agencyId = agency.id,
                agencyName = agency.name,
                targetEmail = cleanEmail,
                targetName = cleanName,
                role = AgencyRole.AGENT.code,
                directorUid = uid,
                used = false
            )
            val invitedSeat = AgencyMember(
                agencyId = agency.id,
                uid = "",
                email = cleanEmail,
                displayName = cleanName,
                role = AgencyRole.AGENT.code,
                status = "INVITED",
                inviteCode = inviteCode,
                viewerUids = agency.directorUids
            )
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = currentDisplayName(),
                actionType = "MEMBER_INVITED",
                targetLabel = cleanName,
                detail = "Invitation envoyée ($cleanEmail) — Code $inviteCode",
                viewerUids = agency.directorUids
            )

            invitesRef().document(inviteCode).set(invite.toCreateMap()).await()
            val batch = db.batch()
            batch.set(membersRef(agency.id).document("inv-$inviteCode"), invitedSeat.toCreateMap())
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(inviteCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinAgencyByInviteCode(rawCode: String): Result<String> {
        val code = InputSanitizer.sanitizeText(rawCode, 64).uppercase(Locale.ROOT)
        val path = "agency_invites/$code"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(code)) { "Code d'invitation invalide." }
            val inviteSnap = invitesRef().document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("Code d'invitation introuvable : $code"))
            }
            val invite = inviteSnap.toObject(AgencyInvite::class.java)
                ?: return Result.failure(IllegalStateException("Invitation illisible."))
            if (invite.used) {
                return Result.failure(IllegalStateException("Ce code d'invitation a déjà été utilisé."))
            }
            val myEmail = currentEmail().lowercase(Locale.ROOT)
            if (invite.targetEmail.isNotBlank() && myEmail != invite.targetEmail.lowercase(Locale.ROOT)) {
                return Result.failure(
                    SecurityException("Ce code est réservé à l'adresse ${InputSanitizer.maskEmailForDisplay(invite.targetEmail)}.")
                )
            }

            val agencyId = invite.agencyId
            val actorName = currentDisplayName()
            val memberViewerUids = listOf(invite.directorUid, uid).distinct()
            val newMember = AgencyMember(
                agencyId = agencyId,
                uid = uid,
                email = myEmail,
                displayName = actorName,
                role = AgencyRole.AGENT.code,
                status = "ACTIVE",
                inviteCode = code,
                viewerUids = memberViewerUids
            )
            val log = AgencyAuditLog(
                agencyId = agencyId,
                actorUid = uid,
                actorName = actorName,
                actionType = "MEMBER_JOINED",
                targetLabel = actorName,
                detail = "Collaborateur rejoint via le code $code",
                viewerUids = memberViewerUids
            )

            val batch = db.batch()
            batch.update(
                invitesRef().document(code),
                mapOf(
                    "used" to true,
                    "claimedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(membersRef(agencyId).document(uid), newMember.toCreateMap())
            batch.update(
                agenciesRef().document(agencyId),
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(auditLogsRef(agencyId).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(agencyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addContact(
        agency: AgencyOrg,
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
        followUpTime: String?,
        rgpdConsentGranted: Boolean = false,
        rgpdLegalBasis: String = RgpdComplianceManager.LEGAL_BASIS_CONSENT
    ): Result<String> {
        val contactId = "ctc-${UUID.randomUUID().toString().take(8)}"
        val path = "agencies/${agency.id}/contacts/$contactId"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id))
            val cleanFirst = InputSanitizer.sanitizeText(firstName, 80)
            val cleanLast = InputSanitizer.sanitizeText(lastName, 80)
            val fullName = listOf(cleanFirst, cleanLast).filter { it.isNotBlank() }.joinToString(" ")
            require(fullName.isNotBlank()) { "Le prénom ou le nom du contact est obligatoire." }

            val actorName = currentDisplayName()
            val targetUid = assignedMember?.uid?.takeIf { it.isNotBlank() } ?: uid
            val targetName = assignedMember?.displayName?.takeIf { it.isNotBlank() } ?: actorName
            val viewerUids = computeViewerUids(agency.directorUids, targetUid)
            val nowStamp = nowDateTimeLabel()
            val cleanBasis = InputSanitizer.sanitizeText(rgpdLegalBasis, 80).ifBlank { RgpdComplianceManager.LEGAL_BASIS_CONSENT }
            val consentDate = if (rgpdConsentGranted) todayIso() else ""
            val retentionUntil = RgpdComplianceManager.computeRetentionUntilIso(cleanBasis)

            val nextFollowUpLabel = if (!followUpDate.isNullOrBlank()) {
                "${followUpDate.take(10)} ${followUpTime.orEmpty().ifBlank { "10:00" }.take(5)}"
            } else ""

            val initialLog = ContactExchangeEntry(
                id = "ex-${UUID.randomUUID().toString().take(8)}",
                dateTime = nowStamp,
                channel = "Statut",
                authorName = actorName,
                summary = "Création fiche ($contactType • $status • Priorité $priority • RGPD ${if (rgpdConsentGranted) "OK" else "à confirmer"})"
            )

            val contact = CrmContact(
                agencyId = agency.id,
                createdByUid = uid,
                assignedToUid = targetUid,
                assignedToName = targetName,
                firstName = cleanFirst,
                lastName = cleanLast,
                fullName = fullName,
                email = InputSanitizer.sanitizeText(email, 160),
                phone = InputSanitizer.sanitizeText(phone, 40),
                postalAddress = InputSanitizer.sanitizeText(postalAddress, 220),
                contactType = contactType,
                status = InputSanitizer.sanitizeText(status, 60).ifBlank { "Nouveau" },
                priority = CrmContact.normalizePriority(priority),
                budgetMin = budgetMin.coerceIn(0L, 50_000_000L),
                budgetMax = budgetMax.coerceIn(0L, 50_000_000L),
                sector = InputSanitizer.sanitizeText(sector, 160),
                desiredPropertyType = InputSanitizer.sanitizeText(desiredPropertyType, 80),
                desiredSurfaceMin = desiredSurfaceMin.coerceIn(0L, 5000L),
                desiredRoomsMin = desiredRoomsMin.coerceIn(0L, 30L),
                financingMode = InputSanitizer.sanitizeText(financingMode, 80),
                projectTimeline = InputSanitizer.sanitizeText(projectTimeline, 80),
                source = InputSanitizer.sanitizeText(source, 60).ifBlank { "Manuel" },
                notes = InputSanitizer.sanitizeText(notes, 2500),
                lastContactedAt = nowStamp,
                nextFollowUpAt = nextFollowUpLabel,
                linkedPropertyId = linkedProperty?.id.orEmpty(),
                linkedPropertyTitle = linkedProperty?.title.orEmpty(),
                archived = false,
                exchangeLogs = listOf(initialLog),
                viewerUids = viewerUids,
                rgpdConsentGranted = rgpdConsentGranted,
                rgpdConsentDate = consentDate,
                rgpdLegalBasis = cleanBasis,
                rgpdRetentionUntil = retentionUntil
            )

            val batch = db.batch()
            batch.set(contactsRef(agency.id).document(contactId), contact.toCreateMap())
            if (!followUpDate.isNullOrBlank()) {
                val timeClean = followUpTime.orEmpty().ifBlank { "10:00" }.take(5)
                val task = FollowUpTask(
                    agencyId = agency.id,
                    contactId = contactId,
                    contactName = fullName,
                    createdByUid = uid,
                    assignedToUid = targetUid,
                    assignedToName = targetName,
                    title = "Relance qualification : $fullName",
                    channel = if (phone.isNotBlank()) "Appel" else "Email",
                    dueDate = followUpDate.take(10),
                    dueTime = timeClean,
                    priority = if (priority == "Urgent") "Urgente" else if (priority == "Élevé") "Haute" else "Normale",
                    status = "À faire",
                    autoGenerated = true,
                    viewerUids = viewerUids
                )
                batch.set(tasksRef(agency.id).document("tsk-${UUID.randomUUID().toString().take(8)}"), task.toCreateMap())
            }
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = actorName,
                actionType = "CONTACT_CREATED",
                targetLabel = fullName,
                detail = "$contactType ($status) • Assigné à $targetName",
                viewerUids = viewerUids
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(contactId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateContactFull(
        agency: AgencyOrg,
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
        linkedProperty: PropertyItem?,
        rgpdConsentGranted: Boolean = existingContact.rgpdConsentGranted,
        rgpdLegalBasis: String = existingContact.rgpdLegalBasis
    ): Result<Unit> {
        val path = "agencies/${agency.id}/contacts/${existingContact.id}"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id) && InputSanitizer.isValidDocumentId(existingContact.id))
            val cleanFirst = InputSanitizer.sanitizeText(firstName, 80)
            val cleanLast = InputSanitizer.sanitizeText(lastName, 80)
            val fullName = listOf(cleanFirst, cleanLast).filter { it.isNotBlank() }.joinToString(" ")
                .ifBlank { existingContact.fullName }
            val cleanStatus = InputSanitizer.sanitizeText(status, 60).ifBlank { existingContact.status }
            val actorName = currentDisplayName()
            val cleanBasis = InputSanitizer.sanitizeText(rgpdLegalBasis, 80).ifBlank { RgpdComplianceManager.LEGAL_BASIS_CONSENT }
            val consentDate = if (rgpdConsentGranted) {
                existingContact.rgpdConsentDate.ifBlank { todayIso() }
            } else {
                ""
            }
            val retentionUntil = existingContact.rgpdRetentionUntil.ifBlank {
                RgpdComplianceManager.computeRetentionUntilIso(cleanBasis)
            }

            val nextExchanges = if (cleanStatus != existingContact.status) {
                val entry = ContactExchangeEntry(
                    id = "ex-${UUID.randomUUID().toString().take(8)}",
                    dateTime = nowDateTimeLabel(),
                    channel = "Statut",
                    authorName = actorName,
                    summary = "Statut modifié : ${existingContact.status} -> $cleanStatus"
                )
                (listOf(entry) + existingContact.exchangeLogs).take(60)
            } else {
                existingContact.exchangeLogs.take(60)
            }

            val batch = db.batch()
            batch.update(
                contactsRef(agency.id).document(existingContact.id),
                mapOf(
                    "firstName" to cleanFirst,
                    "lastName" to cleanLast,
                    "fullName" to fullName.take(140),
                    "email" to InputSanitizer.sanitizeText(email, 160),
                    "phone" to InputSanitizer.sanitizeText(phone, 40),
                    "postalAddress" to InputSanitizer.sanitizeText(postalAddress, 220),
                    "contactType" to CrmContact.normalizeContactType(contactType),
                    "status" to cleanStatus,
                    "priority" to CrmContact.normalizePriority(priority),
                    "budgetMin" to budgetMin.coerceIn(0L, 50_000_000L),
                    "budgetMax" to budgetMax.coerceIn(0L, 50_000_000L),
                    "sector" to InputSanitizer.sanitizeText(sector, 160),
                    "desiredPropertyType" to InputSanitizer.sanitizeText(desiredPropertyType, 80),
                    "desiredSurfaceMin" to desiredSurfaceMin.coerceIn(0L, 5000L),
                    "desiredRoomsMin" to desiredRoomsMin.coerceIn(0L, 30L),
                    "financingMode" to InputSanitizer.sanitizeText(financingMode, 80),
                    "projectTimeline" to InputSanitizer.sanitizeText(projectTimeline, 80),
                    "source" to InputSanitizer.sanitizeText(source, 60),
                    "notes" to InputSanitizer.sanitizeText(notes, 2500),
                    "linkedPropertyId" to (linkedProperty?.id ?: existingContact.linkedPropertyId).take(128),
                    "linkedPropertyTitle" to (linkedProperty?.title ?: existingContact.linkedPropertyTitle).take(160),
                    "exchangeLogs" to nextExchanges.map { it.toMap() },
                    "rgpdConsentGranted" to rgpdConsentGranted,
                    "rgpdConsentDate" to consentDate,
                    "rgpdLegalBasis" to cleanBasis,
                    "rgpdRetentionUntil" to retentionUntil,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = actorName,
                actionType = "CONTACT_UPDATED",
                targetLabel = fullName,
                detail = "Fiche contact mise à jour ($cleanStatus • Priorité $priority)",
                viewerUids = existingContact.viewerUids.ifEmpty { agency.directorUids }
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addContactExchange(
        agency: AgencyOrg,
        contact: CrmContact,
        channel: String,
        summary: String,
        updateStatusTo: String? = null
    ): Result<Unit> {
        val path = "agencies/${agency.id}/contacts/${contact.id}"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id) && InputSanitizer.isValidDocumentId(contact.id))
            val cleanSummary = InputSanitizer.sanitizeText(summary, 800)
            require(cleanSummary.isNotBlank()) { "Le contenu de l'échange ne peut pas être vide." }
            val actorName = currentDisplayName()
            val nowStamp = nowDateTimeLabel()

            val entry = ContactExchangeEntry(
                id = "ex-${UUID.randomUUID().toString().take(8)}",
                dateTime = nowStamp,
                channel = channel.take(30),
                authorName = actorName,
                summary = cleanSummary
            )
            val logs = mutableListOf(entry)
            val nextStatus = updateStatusTo?.takeIf { it.isNotBlank() && it != contact.status }
            if (nextStatus != null) {
                logs.add(
                    ContactExchangeEntry(
                        id = "ex-${UUID.randomUUID().toString().take(8)}",
                        dateTime = nowStamp,
                        channel = "Statut",
                        authorName = actorName,
                        summary = "Statut modifié : ${contact.status} -> $nextStatus"
                    )
                )
            }
            val mergedLogs = (logs + contact.exchangeLogs).take(60)
            val updatedNotes = if (channel == "Note") {
                listOf(cleanSummary, contact.notes).filter { it.isNotBlank() }.joinToString("\n• ").take(2500)
            } else {
                contact.notes
            }

            val batch = db.batch()
            batch.update(
                contactsRef(agency.id).document(contact.id),
                mapOf(
                    "status" to (nextStatus ?: contact.status),
                    "notes" to updatedNotes,
                    "lastContactedAt" to nowStamp,
                    "exchangeLogs" to mergedLogs.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val audit = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = actorName,
                actionType = "CONTACT_EXCHANGE",
                targetLabel = contact.fullName,
                detail = "$channel : ${cleanSummary.take(80)}",
                viewerUids = contact.viewerUids.ifEmpty { agency.directorUids }
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), audit.toCreateMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleArchiveContact(agency: AgencyOrg, contact: CrmContact): Result<Unit> {
        val path = "agencies/${agency.id}/contacts/${contact.id}"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id) && InputSanitizer.isValidDocumentId(contact.id))
            val nextArchived = !contact.archived
            val actorName = currentDisplayName()
            val entry = ContactExchangeEntry(
                id = "ex-${UUID.randomUUID().toString().take(8)}",
                dateTime = nowDateTimeLabel(),
                channel = "Statut",
                authorName = actorName,
                summary = if (nextArchived) "Fiche archivée" else "Fiche restaurée depuis les archives"
            )
            val mergedLogs = (listOf(entry) + contact.exchangeLogs).take(60)

            val batch = db.batch()
            batch.update(
                contactsRef(agency.id).document(contact.id),
                mapOf(
                    "archived" to nextArchived,
                    "exchangeLogs" to mergedLogs.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = actorName,
                actionType = if (nextArchived) "CONTACT_ARCHIVED" else "CONTACT_RESTORED",
                targetLabel = contact.fullName,
                detail = if (nextArchived) "Contact archivé" else "Contact restauré",
                viewerUids = contact.viewerUids.ifEmpty { agency.directorUids }
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun linkContactToProperty(
        agency: AgencyOrg,
        contact: CrmContact,
        property: PropertyItem?
    ): Result<Unit> {
        val path = "agencies/${agency.id}/contacts/${contact.id}"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id) && InputSanitizer.isValidDocumentId(contact.id))
            val actorName = currentDisplayName()
            val propId = property?.id.orEmpty()
            val propTitle = property?.title.orEmpty()
            val entry = ContactExchangeEntry(
                id = "ex-${UUID.randomUUID().toString().take(8)}",
                dateTime = nowDateTimeLabel(),
                channel = "Note",
                authorName = actorName,
                summary = if (property != null) "Bien associé : ${property.title} (${MontyMarketData.formatEuro(property.price)})" else "Association bien retirée"
            )
            val mergedLogs = (listOf(entry) + contact.exchangeLogs).take(60)

            val batch = db.batch()
            batch.update(
                contactsRef(agency.id).document(contact.id),
                mapOf(
                    "linkedPropertyId" to propId,
                    "linkedPropertyTitle" to propTitle,
                    "exchangeLogs" to mergedLogs.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = actorName,
                actionType = "CONTACT_PROPERTY_LINKED",
                targetLabel = contact.fullName,
                detail = if (property != null) "Associé au bien : ${property.title}" else "Bien dissocié",
                viewerUids = contact.viewerUids.ifEmpty { agency.directorUids }
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun reassignContact(
        agency: AgencyOrg,
        contact: CrmContact,
        assignee: AgencyMember
    ): Result<Unit> {
        val path = "agencies/${agency.id}/contacts/${contact.id}"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id) && InputSanitizer.isValidDocumentId(contact.id))
            val targetUid = assignee.uid.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("Ce collaborateur doit d'abord activer son invitation Google."))
            val newViewers = computeViewerUids(agency.directorUids, targetUid)

            val batch = db.batch()
            batch.update(
                contactsRef(agency.id).document(contact.id),
                mapOf(
                    "assignedToUid" to targetUid,
                    "assignedToName" to assignee.displayName.take(120),
                    "viewerUids" to newViewers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = currentDisplayName(),
                actionType = "CONTACT_REASSIGNED",
                targetLabel = contact.fullName,
                detail = "Réassigné de ${contact.assignedToName} à ${assignee.displayName}",
                viewerUids = (newViewers + contact.assignedToUid).distinct().take(25)
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun importContactsBatch(
        agency: AgencyOrg,
        preview: ExcelImportPreview,
        activeMembers: List<AgencyMember>,
        distributeAcrossTeam: Boolean,
        autoCreateFollowUps: Boolean
    ): Result<ExcelImportExecutionResult> {
        val path = "agencies/${agency.id}/contacts"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id))
            val limited = preview.validDrafts.take(100)
            if (limited.isEmpty()) {
                return Result.success(
                    ExcelImportExecutionResult(
                        importedCount = 0,
                        duplicateSkippedCount = preview.duplicateRows.size,
                        errorCount = preview.errorRows.size
                    )
                )
            }

            val assignees = if (distributeAcrossTeam) {
                activeMembers.filter { it.status == "ACTIVE" && it.uid.isNotBlank() }
            } else {
                emptyList()
            }
            val actorName = currentDisplayName()
            val nowStamp = nowDateTimeLabel()
            val today = todayIso()
            val batch = db.batch()

            limited.forEachIndexed { idx, draft ->
                val targetMember = if (assignees.isNotEmpty()) assignees[idx % assignees.size] else null
                val targetUid = targetMember?.uid ?: uid
                val targetName = targetMember?.displayName ?: actorName
                val viewerUids = computeViewerUids(agency.directorUids, targetUid)
                val cId = "ctc-${UUID.randomUUID().toString().take(8)}"
                val initialLog = ContactExchangeEntry(
                    id = "ex-${UUID.randomUUID().toString().take(8)}",
                    dateTime = nowStamp,
                    channel = "Statut",
                    authorName = actorName,
                    summary = "Importé (${preview.formatDetected})"
                )

                val contact = CrmContact(
                    agencyId = agency.id,
                    createdByUid = uid,
                    assignedToUid = targetUid,
                    assignedToName = targetName,
                    firstName = draft.firstName,
                    lastName = draft.lastName,
                    fullName = draft.fullName,
                    email = draft.email,
                    phone = draft.phone,
                    postalAddress = draft.postalAddress,
                    contactType = draft.contactType,
                    status = draft.status,
                    priority = draft.priority,
                    budgetMin = draft.budgetMin,
                    budgetMax = draft.budgetMax,
                    sector = draft.sector,
                    desiredPropertyType = draft.desiredPropertyType,
                    desiredSurfaceMin = draft.desiredSurfaceMin,
                    desiredRoomsMin = draft.desiredRoomsMin,
                    financingMode = draft.financingMode,
                    projectTimeline = draft.projectTimeline,
                    source = draft.source,
                    notes = draft.notes,
                    lastContactedAt = nowStamp,
                    nextFollowUpAt = if (autoCreateFollowUps && idx < 25) "$today 10:00" else "",
                    exchangeLogs = listOf(initialLog),
                    viewerUids = viewerUids,
                    rgpdConsentGranted = false,
                    rgpdConsentDate = "",
                    rgpdLegalBasis = RgpdComplianceManager.LEGAL_BASIS_LEGITIMATE,
                    rgpdRetentionUntil = RgpdComplianceManager.computeRetentionUntilIso(RgpdComplianceManager.LEGAL_BASIS_LEGITIMATE)
                )
                batch.set(contactsRef(agency.id).document(cId), contact.toCreateMap())

                if (autoCreateFollowUps && idx < 25) {
                    val task = FollowUpTask(
                        agencyId = agency.id,
                        contactId = cId,
                        contactName = draft.fullName,
                        createdByUid = uid,
                        assignedToUid = targetUid,
                        assignedToName = targetName,
                        title = "Premier contact post-import : ${draft.fullName}",
                        channel = if (draft.phone.isNotBlank()) "Appel" else "Email",
                        dueDate = today,
                        dueTime = "10:00",
                        priority = if (draft.budgetMax >= 400_000L || draft.priority == "Urgent") "Haute" else "Normale",
                        status = "À faire",
                        autoGenerated = true,
                        viewerUids = viewerUids
                    )
                    batch.set(tasksRef(agency.id).document("tsk-${UUID.randomUUID().toString().take(8)}"), task.toCreateMap())
                }
            }

            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = actorName,
                actionType = "EXCEL_IMPORT",
                targetLabel = "${limited.size} contacts importés",
                detail = "${limited.size} importés, ${preview.duplicateRows.size} doublons ignorés, ${preview.errorRows.size} erreurs",
                viewerUids = (agency.directorUids + uid).distinct().take(25)
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(
                ExcelImportExecutionResult(
                    importedCount = limited.size,
                    duplicateSkippedCount = preview.duplicateRows.size,
                    errorCount = preview.errorRows.size
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addFollowUpTask(
        agency: AgencyOrg,
        contact: CrmContact?,
        title: String,
        channel: String,
        dueDate: String,
        dueTime: String,
        priority: String,
        assignee: AgencyMember?
    ): Result<String> {
        val taskId = "tsk-${UUID.randomUUID().toString().take(8)}"
        val path = "agencies/${agency.id}/tasks/$taskId"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id))
            val cleanTitle = InputSanitizer.sanitizeText(title, 200)
            require(cleanTitle.isNotBlank()) { "Le titre de la relance est requis." }
            val cleanDate = InputSanitizer.sanitizeText(dueDate, 40).ifBlank { todayIso() }
            val cleanTime = InputSanitizer.sanitizeText(dueTime, 10).ifBlank { "10:00" }
            val targetUid = assignee?.uid?.takeIf { it.isNotBlank() } ?: contact?.assignedToUid?.takeIf { it.isNotBlank() } ?: uid
            val targetName = assignee?.displayName?.takeIf { it.isNotBlank() } ?: contact?.assignedToName?.takeIf { it.isNotBlank() } ?: currentDisplayName()
            val viewerUids = computeViewerUids(agency.directorUids, targetUid)

            val task = FollowUpTask(
                agencyId = agency.id,
                contactId = contact?.id.orEmpty(),
                contactName = contact?.fullName ?: "Prospect général",
                createdByUid = uid,
                assignedToUid = targetUid,
                assignedToName = targetName,
                title = cleanTitle,
                channel = channel,
                dueDate = cleanDate,
                dueTime = cleanTime,
                priority = priority,
                status = "À faire",
                autoGenerated = false,
                viewerUids = viewerUids
            )
            val batch = db.batch()
            batch.set(tasksRef(agency.id).document(taskId), task.toCreateMap())
            if (contact != null && InputSanitizer.isValidDocumentId(contact.id)) {
                batch.update(
                    contactsRef(agency.id).document(contact.id),
                    mapOf(
                        "nextFollowUpAt" to "$cleanDate $cleanTime",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = currentDisplayName(),
                actionType = "TASK_CREATED",
                targetLabel = task.contactName,
                detail = "$channel le $cleanDate à $cleanTime ($priority) • $cleanTitle",
                viewerUids = viewerUids
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTaskStatus(
        agency: AgencyOrg,
        task: FollowUpTask,
        newStatus: String
    ): Result<Unit> {
        val path = "agencies/${agency.id}/tasks/${task.id}"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id) && InputSanitizer.isValidDocumentId(task.id))
            val batch = db.batch()
            batch.update(
                tasksRef(agency.id).document(task.id),
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = currentDisplayName(),
                actionType = "TASK_UPDATED",
                targetLabel = task.contactName,
                detail = "Relance « ${task.title.take(60)} » -> $newStatus",
                viewerUids = task.viewerUids.ifEmpty { agency.directorUids }
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun eraseContactRgpd(agency: AgencyOrg, contact: CrmContact): Result<Unit> {
        val path = "agencies/${agency.id}/contacts/${contact.id}"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id) && InputSanitizer.isValidDocumentId(contact.id))
            val batch = db.batch()
            batch.delete(contactsRef(agency.id).document(contact.id))
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = currentDisplayName(),
                actionType = "RGPD_ERASURE",
                targetLabel = "Contact effacé (Art. 17 RGPD)",
                detail = "Effacement définitif exécuté à la demande de la personne concernée (aucune PII conservée).",
                viewerUids = agency.directorUids
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun purgeExpiredRgpdContacts(agency: AgencyOrg, contacts: List<CrmContact>): Result<Int> {
        val today = todayIso()
        val expired = contacts.filter { it.isRgpdRetentionExpired(today) }.take(50)
        if (expired.isEmpty()) return Result.success(0)
        val path = "agencies/${agency.id}/contacts"
        return try {
            val uid = requireUid()
            enforceWriteRateLimit()
            require(InputSanitizer.isValidDocumentId(agency.id))
            val batch = db.batch()
            expired.forEach { c ->
                if (InputSanitizer.isValidDocumentId(c.id)) {
                    batch.delete(contactsRef(agency.id).document(c.id))
                }
            }
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = currentDisplayName(),
                actionType = "RGPD_RETENTION_PURGE",
                targetLabel = "${expired.size} fiches expirées",
                detail = "Purge automatique Art. 5.1.e RGPD (conservation CNIL dépassée).",
                viewerUids = agency.directorUids
            )
            batch.set(auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}"), log.toCreateMap())
            batch.commit().await()
            Result.success(expired.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordExcelExport(agency: AgencyOrg, format: String, rowCount: Int) {
        runCatching {
            val uid = requireUid()
            val log = AgencyAuditLog(
                agencyId = agency.id,
                actorUid = uid,
                actorName = currentDisplayName(),
                actionType = "EXCEL_EXPORT",
                targetLabel = "$rowCount contacts ($format)",
                detail = "Export CRM réalisé au format $format",
                viewerUids = computeViewerUids(agency.directorUids, uid)
            )
            auditLogsRef(agency.id).document("log-${UUID.randomUUID().toString().take(8)}")
                .set(log.toCreateMap())
                .await()
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

    private fun todayIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())

    private fun nowDateTimeLabel(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date())
}
