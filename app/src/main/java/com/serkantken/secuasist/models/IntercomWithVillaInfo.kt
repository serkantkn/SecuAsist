package com.serkantken.secuasist.models

import androidx.room.Embedded

data class IntercomWithVillaInfo(
    @Embedded
    val intercom: Intercom,
    val villaNo: Int,
    val isVillaEmpty: Int,
    val isVillaUnderConstruction: Int
)