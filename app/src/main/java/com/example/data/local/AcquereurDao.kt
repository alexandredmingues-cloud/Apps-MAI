package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AcquereurDao {
    @Query("SELECT * FROM acquereurs ORDER BY updatedAtMillis DESC")
    fun observeAllAcquereurs(): Flow<List<AcquereurEntity>>

    @Query("SELECT COUNT(*) FROM acquereurs")
    suspend fun countAcquereurs(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcquereur(acquereur: AcquereurEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(acquereurs: List<AcquereurEntity>)

    @Update
    suspend fun updateAcquereur(acquereur: AcquereurEntity)

    @Query("UPDATE acquereurs SET status = :newStatus, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: Long,
        newStatus: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM acquereurs WHERE id = :id")
    suspend fun deleteAcquereurById(id: Long)

    @Query("DELETE FROM acquereurs")
    suspend fun deleteAllAcquereurs()
}
