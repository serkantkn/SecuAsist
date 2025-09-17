package com.serkantken.secuasist.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.io.Serializable

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
    var whoCalled: Int?, // NULLable olabilir, eğer arama yapılmadıysa veya kişi silindiyse
    var isCalled: Int = 0,
    var isMissed: Int = 0,
    val date: String, // ISO 8601 string: YYYY-MM-DD HH:MM:SS
    var callDate: String?, // ISO 8601 string, NULLable
    var callAttemptCount: Int = 0,
    var callingDeviceName: String? = null,
    @ColumnInfo(index = true) // Bu ID üzerinden sorgulama yapılacaksa index eklemek performansı artırabilir
    var delivererContactId: Int? = null // Dağıtıcı Contact ID'si, nullable
): Serializable