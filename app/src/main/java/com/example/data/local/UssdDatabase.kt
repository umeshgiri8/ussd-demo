package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UssdEntity::class], version = 2, exportSchema = false)
abstract class UssdDatabase : RoomDatabase() {
    abstract fun ussdDao(): UssdDao

    companion object {
        @Volatile
        private var INSTANCE: UssdDatabase? = null

        fun getDatabase(context: Context): UssdDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UssdDatabase::class.java,
                    "ussd_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
