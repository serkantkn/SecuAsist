package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "intercoms",
    foreignKeys = [ForeignKey(
        entity = Villa::class,
        parentColumns = ["villaId"],
        childColumns = ["villaId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Intercom(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val villaId: Int,
    val location: String,
    val ipAddress: String,
    var isFaulty: Boolean = false,
    var faultDate: Date? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    var deviceId: String? = "Bilinmiyor"
)