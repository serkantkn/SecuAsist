package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Villas")
data class Villa(
    @PrimaryKey(autoGenerate = true)
    val villaId: Int = 0,
    val villaNo: Int,
    val villaNotes: String?,
    val villaStreet: String?,
    val villaNavigationA: String?,
    val villaNavigationB: String?,
    val isVillaUnderConstruction: Int = 0,
    val isVillaSpecial: Int = 0,
    val isVillaRental: Int = 0,
    val isVillaCallFromHome: Int = 0,
    val isVillaCallForCargo: Int = 0,
    val isVillaEmpty: Int = 0
)