package com.serkantken.secuasist.models

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class CameraWithVillas(
    @Embedded val camera: Camera,
    @Relation(
        parentColumn = "cameraId",
        entityColumn = "villaId",
        associateBy = Junction(CameraVisibleVillaCrossRef::class)
    )
    val visibleVillas: List<Villa>
)
