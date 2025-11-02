package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "CargoCompanies")
data class CargoCompany(
    @PrimaryKey(autoGenerate = true)
    val companyId: Int = 0,
    val companyName: String?, // UNIQUE olacak
    val isCargoInOperation: Int = 0, // 0: Yok, 1: Var
    val updatedAt: Long = System.currentTimeMillis(),
    var deviceId: String? = "Bilinmiyor"
) : SearchableItem {
    override fun getDisplayId(): String? {
        return companyName
    }

    override fun getDisplayName(): String? {
        return companyName
    }
}