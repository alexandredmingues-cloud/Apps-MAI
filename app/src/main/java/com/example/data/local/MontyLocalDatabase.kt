package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AcquereurEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MontyLocalDatabase : RoomDatabase() {
    abstract fun acquereurDao(): AcquereurDao

    companion object {
        @Volatile
        private var INSTANCE: MontyLocalDatabase? = null

        fun getInstance(context: Context): MontyLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MontyLocalDatabase::class.java,
                    "monty_immo_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
