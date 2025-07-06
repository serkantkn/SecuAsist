package com.serkantken.secuasist.models

import androidx.room.Embedded
import androidx.room.Relation

data class VillaWithContacts(
    @Embedded val villa: Villa,
    @Relation(
        parentColumn = "villaId",
        entityColumn = "contactId",
        associateBy = androidx.room.Junction(
            value = VillaContact::class,
            parentColumn = "villaId",
            entityColumn = "contactId"
        )
    )
    val contacts: List<Contact>
)