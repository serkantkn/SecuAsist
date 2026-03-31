package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "Cameras")
data class Camera(
    @PrimaryKey
    val cameraId: String = UUID.randomUUID().toString(),
    // villaId removed - Many-to-Many relation now
    val cameraName: String,
    val cameraIp: String,
    val isWorking: Int = 1,
    val lastChecked: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String? = null
)