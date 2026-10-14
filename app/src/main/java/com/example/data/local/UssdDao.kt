package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UssdDao {
    @Query("SELECT * FROM ussd_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<UssdEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UssdEntity): Long

    @Delete
    suspend fun delete(entity: UssdEntity)

    @Query("DELETE FROM ussd_history")
    suspend fun clearAll()
}
