package com.serkantken.secuasist.models

import java.io.Serializable

data class PhoneOptionForContact(
    val number: String,
    val typeLabel: String // Örn: "Mobil", "Ev"
) : Serializable
