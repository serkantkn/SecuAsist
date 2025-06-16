package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "Cargos",
    foreignKeys = [
        ForeignKey(entity = CargoCompany::class,
            parentColumns = ["companyId"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.RESTRICT), // Şirket silinirse kargo silinmesin, hata versin
        ForeignKey(entity = Villa::class,
            parentColumns = ["villaId"],
            childColumns = ["villaId"],
            onDelete = ForeignKey.RESTRICT), // Villa silinirse kargo silinmesin, hata versin
        ForeignKey(entity = Contact::class,
            parentColumns = ["contactId"],
            childColumns = ["whoCalled"],
            onDelete = ForeignKey.SET_NULL) // Kişi silinirse arayan bilgisi NULL olsun
    ])
data class Cargo(
    @PrimaryKey(autoGenerate = true)
    val cargoId: Int = 0,
    val companyId: Int,
    val villaId: Int,
    val whoCalled: Int?, // NULLable olabilir, eğer arama yapılmadıysa veya kişi silindiyse
    val isCalled: Int = 0,
    val isMissed: Int = 0,
    val date: String, // ISO 8601 string: YYYY-MM-DD HH:MM:SS
    val callDate: String?, // ISO 8601 string, NULLable
    val callAttemptCount: Int = 0
)