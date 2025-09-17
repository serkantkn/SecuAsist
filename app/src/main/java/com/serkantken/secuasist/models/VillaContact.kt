package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "VillaContacts",
    foreignKeys = [
        ForeignKey(entity = Villa::class,
            parentColumns = ["villaId"],
            childColumns = ["villaId"],
            onDelete = ForeignKey.CASCADE), // Villa silinirse ilgili bağlantılar da silinsin
        ForeignKey(entity = Contact::class,
            parentColumns = ["contactId"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE) // Kişi silinirse ilgili bağlantılar da silinsin
    ],
    primaryKeys = ["villaId", "contactId"] // Benzersizlik için composite primary key
)
data class VillaContact(
    // val id: Int = 0, // Eğer composite primary key kullanıyorsak bu alana gerek kalmaz
    val villaId: Int,
    val contactId: Int,
    val isRealOwner: Boolean = false,
    val contactType: String?,
    val notes: String?,
    var orderIndex: Int = 0
)