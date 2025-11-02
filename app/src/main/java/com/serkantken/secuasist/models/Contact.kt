package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val contactId: Int = 0,
    val contactName: String?,
    val contactPhone: String?,
    var lastCallTimestamp: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    var deviceId: String? = "Bilinmiyor"
)
