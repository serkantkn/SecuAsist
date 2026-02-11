package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "CameraVisibleVillas",
    primaryKeys = ["cameraId", "villaId"],
    foreignKeys = [
        ForeignKey(
            entity = Camera::class,
            parentColumns = ["cameraId"],
            childColumns = ["cameraId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Villa::class,
            parentColumns = ["villaId"],
            childColumns = ["villaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["villaId"])]
)
data class CameraVisibleVillaCrossRef(
    val cameraId: String,
    val villaId: Int,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String? = null
)
