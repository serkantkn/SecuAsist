package com.serkantken.secuasist.models

import java.io.Serializable

data class ContactFromPhone(
    val phoneContactId: String, // Rehberdeki orijinal ID
    val displayName: String,
    val phoneOptions: MutableList<PhoneOptionForContact> = mutableListOf()
) : Serializable