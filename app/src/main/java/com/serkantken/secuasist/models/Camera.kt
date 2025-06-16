package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "Cameras",
    foreignKeys = [
        ForeignKey(entity = Villa::class,
            parentColumns = ["villaId"],
            childColumns = ["villaId"],
            onDelete = ForeignKey.CASCADE)
    ])
data class Camera(
    @PrimaryKey(autoGenerate = true)
    val cameraId: Int = 0,
    val villaId: Int,
    val cameraIpAddress: String,
    val cameraNotes: String?,
    val isActive: Int = 1 // 0: Pasif, 1: Aktif
)