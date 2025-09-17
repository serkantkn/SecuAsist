package com.serkantken.secuasist.models // veya sizin paket adınız

import java.io.Serializable // Gerekirse

data class CargoReport(
    val cargoId: Int,
    val companyName: String?, // CargoCompany'den
    val villaNo: String?,     // Villa'dan
    val whoCalledName: String?, // Contact'tan (whoCalled ID ile eşleşen)
    val callDate: String?,      // Cargo'dan
    val callStatus: String,    // Cargo'daki isMissed'dan türetilecek (örn: "Başarılı", "Ulaşılamadı")
    val callingDeviceName: String?, // Cargo'dan
    val callAttemptCount: Int?   // Cargo'dan
) : Serializable // Eğer bu nesneyi bir yerden bir yere aktaracaksanız