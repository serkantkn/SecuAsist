package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "CompanyContacts",
    foreignKeys = [
        ForeignKey(entity = CargoCompany::class,
            parentColumns = ["companyId"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Contact::class,
            parentColumns = ["contactId"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE)
    ],
    primaryKeys = ["companyId", "contactId"] // UNIQUE kısıtlamasına karşılık composite primary key
)
data class CompanyContact(
    // val id: Int = 0, // Composite primary key olduğu için bu alana gerek kalmaz
    val companyId: Int,
    val contactId: Int,
    val role: String?, // Örn: 'Driver', 'Representative'
    val isPrimaryContact: Int = 0 // 0: Hayır, 1: Evet
)