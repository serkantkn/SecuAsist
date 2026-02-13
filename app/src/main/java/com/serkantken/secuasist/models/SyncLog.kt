package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actionType: String, // e.g., "ADD_CONTACT", "DELETE_VILLA"
    val payload: String,    // JSON string
    val timestamp: Long = System.currentTimeMillis()
)
