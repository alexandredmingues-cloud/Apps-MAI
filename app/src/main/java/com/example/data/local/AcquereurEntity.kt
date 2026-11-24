package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "acquereurs")
data class AcquereurEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fullName: String,
    val phone: String,
    val email: String,
    val status: String,
    val budgetMax: Long,
    val downPayment: Long,
    val propertyType: String,
    val sectorsSummary: String,
    val minSurface: Long,
    val minRooms: Long,
    val essentialCriteria: String,
    val timelineDelai: String,
    val bankApproval: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun sectorList(): List<String> =
        sectorsSummary.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun essentialList(): List<String> =
        essentialCriteria.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
