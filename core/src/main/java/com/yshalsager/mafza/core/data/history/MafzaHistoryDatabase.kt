package com.yshalsager.mafza.core.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RunHistoryEntity::class, StepHistoryEntity::class, CommandAuditEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(HistoryTypeConverters::class)
abstract class MafzaHistoryDatabase : RoomDatabase() {
    abstract fun run_history_dao(): RunHistoryDao

    companion object {
        private const val DATABASE_NAME = "mafza_history.db"

        fun create(context: Context): MafzaHistoryDatabase {
            return Room.databaseBuilder(context, MafzaHistoryDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
