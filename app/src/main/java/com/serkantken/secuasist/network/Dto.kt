package com.serkantken.secuasist.network

import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaContact

// Tüm verileri almak için
data class FullSyncPayload(
    val villas: List<VillaDto> = emptyList(),
    val contacts: List<ContactDto> = emptyList(),
    val villaContacts: List<VillaContactDto> = emptyList(),
    val cargoCompanies: List<CargoCompanyDto> = emptyList(),
    val cargos: List<CargoDto> = emptyList()
)

// Villas tablosu için JSON modeli
data class VillaDto(
    val villaId: Int? = null,
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
    val contactId: String? = null,
    val contactName: String?,
    val contactPhone: String?
)

// VillaContacts için JSON modeli (Sadece bağlantı oluşturmak için kullanılacak)
data class VillaContactDto(
    val villaId: Int,
    val contactId: String,
    val isRealOwner: Boolean = false,
    val contactType: String?,
    val notes: String? = null
)

// Cameras tablosu için JSON modeli
// Cameras tablosu için JSON modeli
data class CameraDto(
    val cameraId: String? = null,
    val cameraName: String,
    val cameraIp: String,
    val isWorking: Boolean = true,
    val lastChecked: Long? = null,
    val notes: String? = null
)

data class IntercomDto(
    val intercomId: String? = null,
    val villaId: Int,
    val intercomName: String,
    val isWorking: Boolean = true,
    val lastChecked: Long? = null,
    val notes: String? = null
)

fun CameraDto.toCamera(): com.serkantken.secuasist.models.Camera {
    return com.serkantken.secuasist.models.Camera(
        cameraId = this.cameraId ?: java.util.UUID.randomUUID().toString(),
        cameraName = this.cameraName,
        cameraIp = this.cameraIp,
        isWorking = this.isWorking,
        lastChecked = this.lastChecked ?: System.currentTimeMillis(),
        notes = this.notes
    )
}

fun IntercomDto.toIntercom(): com.serkantken.secuasist.models.Intercom {
    return com.serkantken.secuasist.models.Intercom(
        intercomId = this.intercomId ?: java.util.UUID.randomUUID().toString(),
        villaId = this.villaId,
        intercomName = this.intercomName,
        isWorking = this.isWorking,
        lastChecked = this.lastChecked ?: System.currentTimeMillis(),
        notes = this.notes
    )
}

// CargoCompanies tablosu için JSON modeli
data class CargoCompanyDto(
    val companyId: Int? = null,
    val companyName: String?,
    val isCargoInOperation: Int = 0,
    val contacts: List<ContactDto>? = null // İlişkili kişileri içerecek (şoförler vb.)
)

// CompanyContacts için JSON modeli (Sadece bağlantı oluşturmak için kullanılacak)
data class CompanyContactDto(
    val companyId: Int,
    val contactId: String,
    val role: String? = null,
    val isPrimaryContact: Int = 0
)

// Cargos tablosu için JSON modeli
data class CargoDto(
    val companyId: Int,
    val villaId: Int,
    val whoCalled: Int?, // ContactId olacak (Eğer bu artık String ise güncellenmeli. DB şemada Int kalmıştı, server.py'de güncelledim mi? Evet server.py Text değil hala Integer görünüyor whoCalled için. DİKKAT)
    val isCalled: Int = 0,
    val isMissed: Int = 0,
    val date: String, // ISO 8601 formatı
    val callDate: String? = null,
    val callAttemptCount: Int = 0
)

data class VillaContactDeleteDto(
    val villaId: Int,
    val contactId: String
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
        contactId = this.contactId ?: java.util.UUID.randomUUID().toString(),
        contactName = this.contactName,
        contactPhone = this.contactPhone,
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