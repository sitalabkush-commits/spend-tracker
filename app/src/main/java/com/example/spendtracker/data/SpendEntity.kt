package com.example.spendtracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One parsed expense. Amount is stored in minor units (paise) to avoid float errors.
 * [smsHash] is unique so the same SMS is never stored twice (live receive + inbox scan).
 */
@Entity(tableName = "spends", indices = [Index(value = ["smsHash"], unique = true)])
data class SpendEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val merchant: String,
    val source: String,      // PhonePe / Paytm / Bank
    val timestamp: Long,     // epoch millis of the SMS
    val smsHash: String
)
