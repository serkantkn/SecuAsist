package com.serkantken.secuasist.models

import com.google.gson.JsonElement

data class BaseWebSocketMessage(
    val type: String,
    val payload: JsonElement
)