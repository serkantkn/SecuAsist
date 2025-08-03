package com.serkantken.secuasist.models

import androidx.room.Embedded

data class IntercomWithVillaInfo(
    // @Embedded, Room'a Intercom nesnesinin tüm alanlarını
    // bu sınıfa direkt olarak eklemesini söyler.
    @Embedded
    val intercom: Intercom,

    // Villa'dan sadece ihtiyacımız olan alanları ekliyoruz.
    val villaNo: Int,
    val isVillaEmpty: Int,
    val isVillaUnderConstruction: Int
)