package com.serkantken.secuasist.utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.serkantken.secuasist.models.VillaContact
import java.lang.reflect.Type

class VillaContactDeserializer : JsonDeserializer<VillaContact> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): VillaContact? {
        val jsonObject = json?.asJsonObject ?: return null
        val isRealOwnerAsInt = jsonObject.get("isRealOwner")?.asInt
        val isRealOwnerBoolean = isRealOwnerAsInt == 1
        val villaId = jsonObject.get("villaId")?.asInt ?: 0
        val contactId = jsonObject.get("contactId")?.asInt ?: 0
        val contactType = jsonObject.get("contactType")?.asString
        val notes = jsonObject.get("notes")?.asString
        val orderIndex = jsonObject.get("orderIndex")?.asInt ?: 0

        return VillaContact(
            villaId = villaId,
            contactId = contactId,
            isRealOwner = isRealOwnerBoolean,
            contactType = contactType,
            notes = notes,
            orderIndex = orderIndex
        )
    }
}