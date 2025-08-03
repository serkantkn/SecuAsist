package com.serkantken.secuasist.models

data class DisplayableVilla(
    val villa: Villa,
    val ownerName: String?, // Villa sahibinin adı (CallingActivity'de doldurulacak)
    val state: VillaCallingState
)
