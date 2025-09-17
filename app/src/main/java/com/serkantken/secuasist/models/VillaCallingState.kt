package com.serkantken.secuasist.models

enum class VillaCallingState {
    PENDING,        // Sırada
    CALLED_SUCCESS, // Arandı - Ulaşıldı (Başarılı)
    CALLED_FAILED,  // Arandı - Ulaşılamadı (Başarısız)
    DONE_AT_HOME    // Arandı - Evden Teslim Alındı (Bu özel bir başarı durumu)
    ,
    IN_PROGRESS,
    CALLED
}