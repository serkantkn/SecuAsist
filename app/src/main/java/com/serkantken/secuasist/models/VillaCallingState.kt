package com.serkantken.secuasist.models

enum class VillaCallingState {
    IN_PROGRESS, // Şu an işlemde olan / aranan villa
    CALLED,      // Aranması tamamlanmış villa
    PENDING      // Henüz aranmamış, sırada bekleyen villa
}