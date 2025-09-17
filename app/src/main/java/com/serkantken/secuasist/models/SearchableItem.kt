package com.serkantken.secuasist.models // veya sizin paket adınız

interface SearchableItem {
    fun getDisplayId(): String? // Villa No veya Şirket ID gibi benzersiz bir tanımlayıcı
    fun getDisplayName(): String? // Listede gösterilecek ana metin (Villa No, Şirket Adı)
}