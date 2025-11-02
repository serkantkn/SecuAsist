package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "Villas")
data class Villa(
    @PrimaryKey(autoGenerate = true)
    val villaId: Int = 0,
    val villaNo: Int,
    var villaNotes: String?,
    var villaStreet: String?,
    var villaNavigationA: String?,
    var villaNavigationB: String?,
    var isVillaUnderConstruction: Int = 0,
    var isVillaSpecial: Int = 0,
    var isVillaRental: Int = 0,
    var isVillaCallFromHome: Int = 0,
    var isVillaCallForCargo: Int = 0,
    var isVillaEmpty: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    var deviceId: String? = "Bilinmiyor"
) : SearchableItem, Serializable {
    override fun getDisplayId(): String? {
        return villaId.toString()
    }

    override fun getDisplayName(): String? {
        return villaNo.toString() // Villa için ana gösterim villa numarası olacak
    }
}