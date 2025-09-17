package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "CompanyDeliverers", // Tablo adını çoğul yapıyorum, genel bir pratiktir.
    primaryKeys = ["companyId", "contactId"],
    foreignKeys = [
        ForeignKey(
            entity = CargoCompany::class,
            parentColumns = ["companyId"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE // Şirket silinirse, ilgili dağıtıcı atamaları da silinsin.
        ),
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["contactId"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE // Kişi silinirse, ilgili dağıtıcı atamaları da silinsin.
        )
    ]
)
data class CompanyDelivererCrossRef(
    val companyId: Int,
    val contactId: Int // Bu, "Contact" tablosundaki bir kişinin ID'si olacak.
)