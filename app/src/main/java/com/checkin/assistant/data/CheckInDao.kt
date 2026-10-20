package com.checkin.assistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Insert
    suspend fun insert(record: CheckInRecord)

    @Query("SELECT * FROM check_in_records ORDER BY timestamp DESC LIMIT 1")
    fun latest(): Flow<CheckInRecord?>

    @Query("SELECT * FROM check_in_records ORDER BY timestamp DESC")
    fun all(): Flow<List<CheckInRecord>>

    @Query("SELECT COUNT(*) FROM check_in_records WHERE status = 'SUCCESS'")
    fun successCount(): Flow<Int>
}
