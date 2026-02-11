package com.serkantken.secuasist.models

import androidx.room.Embedded
import androidx.room.Relation

data class CargoWithDetails(
    @Embedded val cargo: Cargo,
    
    @Relation(
        parentColumn = "villaId",
        entityColumn = "villaId"
    )
    val villa: Villa,

    @Relation(
        parentColumn = "companyId",
        entityColumn = "companyId"
    )
    val company: CargoCompany
)
