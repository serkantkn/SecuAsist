package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "Intercoms")
data class Intercom(
    @PrimaryKey
    val intercomId: String = UUID.randomUUID().toString(),
    val villaId: Int,
    val intercomName: String, // e.g., "Bahçe Kapısı", "Bina Girişi"
    val isWorking: Int = 1,
    val lastChecked: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String? = null
)
