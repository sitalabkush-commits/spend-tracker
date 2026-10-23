package com.example.spendtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpendDao {

    /** Returns -1 if the SMS was already stored (duplicate hash). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SpendEntity): Long

    @Query("SELECT * FROM spends WHERE timestamp >= :from ORDER BY timestamp DESC")
    fun observeSince(from: Long): Flow<List<SpendEntity>>

    /** Sum of spends from [from] until now (used for Today / Month cards). */
    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM spends WHERE timestamp >= :from")
    fun observeTotalSince(from: Long): Flow<Long>
}
