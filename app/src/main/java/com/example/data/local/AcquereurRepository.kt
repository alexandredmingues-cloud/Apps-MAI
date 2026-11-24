package com.example.data.local

import com.example.data.MontyMarketData
import com.example.security.InputSanitizer
import kotlinx.coroutines.flow.Flow

object AcquereurStatuses {
    val ALL = listOf(
        "Nouveau prospect",
        "Qualification en cours",
        "Actif — Visites en cours",
        "Offre en cours",
        "Sous compromis",
        "En veille"
    )
}

class AcquereurRepository(private val acquereurDao: AcquereurDao) {
    val allAcquereurs: Flow<List<AcquereurEntity>> = acquereurDao.observeAllAcquereurs()

    suspend fun seedIfEmpty() {
        if (acquereurDao.countAcquereurs() == 0) {
            val baseTime = System.currentTimeMillis()
            val seeded = MontyMarketData.initialClients("local").mapIndexed { index, (_, c) ->
                AcquereurEntity(
                    fullName = c.name,
                    phone = c.phone,
                    email = c.email,
                    status = when {
                        c.status.contains("Visites", ignoreCase = true) -> "Actif — Visites en cours"
                        c.status.contains("Offre", ignoreCase = true) -> "Offre en cours"
                        c.status.contains("Nouveau", ignoreCase = true) -> "Nouveau prospect"
                        else -> "Qualification en cours"
                    },
                    budgetMax = c.budgetMax,
                    downPayment = c.downPayment,
                    propertyType = c.propertyType,
                    sectorsSummary = c.sectors.joinToString(", "),
                    minSurface = c.minSurface,
                    minRooms = c.minRooms,
                    essentialCriteria = c.essentialCriteria.joinToString(", "),
                    timelineDelai = c.timelineDelai,
                    bankApproval = c.bankApproval,
                    updatedAtMillis = baseTime - (index * 60_000L)
                )
            } + listOf(
                AcquereurEntity(
                    fullName = "Vidal — Élise & Antoine",
                    phone = "06 58 92 41 17",
                    email = "elise.vidal34@example.fr",
                    status = "Qualification en cours",
                    budgetMax = 385000L,
                    downPayment = 75000L,
                    propertyType = "Rez-de-jardin",
                    sectorsSummary = "Mas Drevon (34070), Croix d'Argent (34070)",
                    minSurface = 80L,
                    minRooms = 3L,
                    essentialCriteria = "Terrasse / Extérieur, Proche Tramway, Calme absolu",
                    timelineDelai = "Sous 4 mois",
                    bankApproval = "Simulation courtier à 3.40% sur 25 ans",
                    updatedAtMillis = baseTime - 240_000L
                )
            )
            acquereurDao.insertAll(seeded)
        }
    }

    suspend fun insert(acquereur: AcquereurEntity): Long =
        acquereurDao.insertAcquereur(sanitizeEntity(acquereur))

    suspend fun update(acquereur: AcquereurEntity) =
        acquereurDao.updateAcquereur(
            sanitizeEntity(acquereur).copy(updatedAtMillis = System.currentTimeMillis())
        )

    suspend fun updateStatus(id: Long, status: String) =
        acquereurDao.updateStatus(id = id, newStatus = InputSanitizer.sanitizeText(status, 80))

    suspend fun deleteById(id: Long) = acquereurDao.deleteAcquereurById(id)

    suspend fun deleteAll() = acquereurDao.deleteAllAcquereurs()

    private fun sanitizeEntity(entity: AcquereurEntity): AcquereurEntity = entity.copy(
        fullName = InputSanitizer.sanitizeText(entity.fullName, 120),
        phone = InputSanitizer.sanitizeText(entity.phone, 40),
        email = InputSanitizer.sanitizeText(entity.email, 120),
        status = InputSanitizer.sanitizeText(entity.status, 80),
        propertyType = InputSanitizer.sanitizeText(entity.propertyType, 60),
        sectorsSummary = InputSanitizer.sanitizeText(entity.sectorsSummary, 300),
        essentialCriteria = InputSanitizer.sanitizeText(entity.essentialCriteria, 400),
        timelineDelai = InputSanitizer.sanitizeText(entity.timelineDelai, 100),
        bankApproval = InputSanitizer.sanitizeText(entity.bankApproval, 250),
        budgetMax = entity.budgetMax.coerceIn(10000L, 50000000L),
        downPayment = entity.downPayment.coerceIn(0L, 50000000L),
        minSurface = entity.minSurface.coerceIn(0L, 2000L),
        minRooms = entity.minRooms.coerceIn(0L, 20L)
    )
}
