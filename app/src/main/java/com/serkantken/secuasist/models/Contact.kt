package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Contacts")
data class Contact(
    @PrimaryKey
    val contactId: String = java.util.UUID.randomUUID().toString(),
    val contactName: String?,
    val contactPhone: String?,
    var lastCallTimestamp: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    var deviceId: String? = "Bilinmiyor"
)
