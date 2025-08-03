package com.serkantken.secuasist.network

import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaContact

// Temel bir WebSocket mesaj yapısı için
data class WebSocketMessage<T>(
    val type: String, // "add_villa", "add_contact", "add_cargo" vb.
    val data: T       // Mesajın içeriği (örneğin VillaDto, CargoDto)
)

// Villas tablosu için JSON modeli
data class VillaDto(
    val villaId: Int? = null, // Opsiyonel ve null olabilir
    val villaNo: Int,
    val villaNotes: String? = null,
    val villaStreet: String? = null,
    val villaNavigationA: String? = null,
    val villaNavigationB: String? = null,
    val isVillaUnderConstruction: Int = 0,
    val isVillaSpecial: Int = 0,
    val isVillaRental: Int = 0,
    val isVillaCallFromHome: Int = 0,
    val isVillaCallForCargo: Int = 0,
    val isVillaEmpty: Int = 0,
    val contacts: List<ContactDto>? = null, // İlişkili kişileri içerecek
    val cameras: List<CameraDto>? = null    // İlişkili kameraları içerecek
)

// Contacts tablosu için JSON modeli (Python tarafındaki Kisiler tablosuyla uyumlu)
data class ContactDto(
    val contactId: Int? = null, // YENİ: ID alanı eklendi
    val contactName: String?,
    val contactPhone: String?
)

// VillaContacts için JSON modeli (Sadece bağlantı oluşturmak için kullanılacak)
data class VillaContactDto(
    val villaId: Int,
    val contactId: Int,
    val isRealOwner: Int = 0,
    val contactType: String,
    val notes: String? = null
)

// Cameras tablosu için JSON modeli
data class CameraDto(
    val villaId: Int,
    val cameraIpAddress: String,
    val cameraNotes: String? = null,
    val isActive: Int = 1
)

// CargoCompanies tablosu için JSON modeli
data class CargoCompanyDto(
    val companyId: Int? = null,
    val companyName: String,
    val isCargoInOperation: Int = 0,
    val contacts: List<ContactDto>? = null // İlişkili kişileri içerecek (şoförler vb.)
)

// CompanyContacts için JSON modeli (Sadece bağlantı oluşturmak için kullanılacak)
data class CompanyContactDto(
    val companyId: Int,
    val contactId: Int,
    val role: String? = null,
    val isPrimaryContact: Int = 0
)

// Cargos tablosu için JSON modeli
data class CargoDto(
    val companyId: Int,
    val villaId: Int,
    val whoCalled: Int?, // ContactId olacak
    val isCalled: Int = 0,
    val isMissed: Int = 0,
    val date: String, // ISO 8601 formatı
    val callDate: String? = null,
    val callAttemptCount: Int = 0
)

data class VillaContactDeleteDto(
    val villaId: Int,
    val contactId: Int
)

fun VillaDto.toVilla(): Villa {
    return Villa(
        villaId = this.villaId ?: 0, // Sunucudan null gelirse yeni bir villa olduğunu varsayarız
        villaNo = this.villaNo,
        villaNotes = this.villaNotes,
        villaStreet = this.villaStreet,
        villaNavigationA = this.villaNavigationA,
        villaNavigationB = this.villaNavigationB,
        isVillaUnderConstruction = this.isVillaUnderConstruction ?: 0,
        isVillaSpecial = this.isVillaSpecial ?: 0,
        isVillaRental = this.isVillaRental ?: 0,
        isVillaCallFromHome = this.isVillaCallFromHome ?: 0,
        isVillaCallForCargo = this.isVillaCallForCargo ?: 0,
        isVillaEmpty = this.isVillaEmpty ?: 0
    )
}

fun ContactDto.toContact(): Contact {
    return Contact(
        contactId = this.contactId ?: 0,
        contactName = this.contactName,
        contactPhone = this.contactPhone
    )
}

fun VillaContactDto.toVillaContact(): VillaContact {
    return VillaContact(
        villaId = this.villaId,
        contactId = this.contactId,
        isRealOwner = this.isRealOwner,
        contactType = this.contactType,
        notes = this.notes
    )
}

fun CargoCompanyDto.toCargoCompany(): CargoCompany {
    return CargoCompany(
        companyId = this.companyId ?: 0,
        companyName = this.companyName
        // Diğer alanlar varsa burada atanmalı
    )
}