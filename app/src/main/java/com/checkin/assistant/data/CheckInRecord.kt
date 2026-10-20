package com.checkin.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_in_records")
data class CheckInRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val status: String,   // SUCCESS / FAILED / NEED_RELOGIN
    val message: String,
    val mode: String      // AUTO / MANUAL
)
