package com.serkantken.secuasist.models

data class SelectableVilla(
    val villa: Villa,
    val defaultContactId: Int?,
    val defaultContactName: String?
)
