package com.serkantken.secuasist.utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.serkantken.secuasist.models.VillaContact
import java.lang.reflect.Type

class VillaContactDeserializer : JsonDeserializer<VillaContact> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): VillaContact? {
        val jsonObject = json?.asJsonObject ?: return null

        fun getStringSafe(key: String): String? {
            val el = jsonObject.get(key)
            return if (el == null || el is JsonNull) null else el.asString
        }

        fun getIntSafe(key: String): Int {
            val el = jsonObject.get(key)
            return if (el == null || el is JsonNull) 0 else el.asInt
        }

        val isRealOwnerBoolean = getIntSafe("isRealOwner") == 1
        val villaId = getIntSafe("villaId")
        val contactId = getIntSafe("contactId")
        val contactType = getStringSafe("contactType")
        val notes = getStringSafe("notes")
        val orderIndex = getIntSafe("orderIndex")

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