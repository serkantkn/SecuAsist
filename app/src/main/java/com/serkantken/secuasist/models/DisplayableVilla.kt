package com.serkantken.secuasist.models

data class DisplayableVilla(
    val cargoId: Int,
    val villa: Villa,
    val ownerName: String?, // Villa sahibinin adı (CallingActivity'de doldurulacak)
    var state: VillaCallingState
)
