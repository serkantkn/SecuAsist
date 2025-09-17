package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "cameras")
data class Camera(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val ipAddress: String,
    var isFaulty: Boolean = false,
    var faultDate: Date? = null // Arızalı değilse null olacak
)