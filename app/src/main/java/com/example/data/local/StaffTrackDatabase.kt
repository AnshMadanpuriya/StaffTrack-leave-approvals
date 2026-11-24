package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedLeaveEntity::class,
        CachedAuditEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StaffTrackDatabase : RoomDatabase() {
    abstract fun staffTrackDao(): StaffTrackDao

    companion object {
        @Volatile
        private var instance: StaffTrackDatabase? = null

        fun getInstance(context: Context): StaffTrackDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StaffTrackDatabase::class.java,
                    "stafftrack_sql.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
