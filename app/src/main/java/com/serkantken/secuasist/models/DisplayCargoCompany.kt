package com.serkantken.secuasist.models

data class DisplayCargoCompany(
    val company: CargoCompany,
    val hasUncalledCargos: Boolean
)