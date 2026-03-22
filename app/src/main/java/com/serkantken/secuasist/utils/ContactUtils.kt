package com.serkantken.secuasist.utils

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
    val name: String,
    val phone: String,
    val potentialVillaNo: Int?
)

object ContactUtils {
    suspend fun fetchDeviceContacts(context: Context): List<DeviceContact> = withContext(Dispatchers.IO) {
        val contactsMap = mutableMapOf<String, DeviceContact>()
        val contentResolver = context.contentResolver
        
        val villaRegex = Regex("^(\\d+)\\s+(.*)")

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: continue
                val number = it.getString(numberIndex) ?: continue

                val cleanNumber = number.trim()
                val normalized = normalizePhoneNumber(cleanNumber)

                var villaNo: Int? = null
                var parsedName = name

                val match = villaRegex.find(name)
                if (match != null) {
                    val (noStr, restOfName) = match.destructured
                    villaNo = noStr.toIntOrNull()
                    parsedName = restOfName.trim()
                }

                // Deduplicate key: VillaNo + Name + NormalizedNumber
                // If the same person has multiple variations of the same number, we only keep the first one
                val key = "${villaNo ?: "none"}_${parsedName.lowercase()}_$normalized"
                if (!contactsMap.containsKey(key)) {
                    contactsMap[key] = DeviceContact(parsedName, cleanNumber, villaNo)
                }
            }
        }
        return@withContext contactsMap.values.toList()
    }

    fun normalizePhoneNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 10) {
            digits.takeLast(10)
        } else {
            digits
        }
    }

    suspend fun getLastOutgoingCallDate(context: Context, phoneNumber: String): Long? = withContext(Dispatchers.IO) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }

        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            arrayOf(android.provider.CallLog.Calls.DATE),
            "${android.provider.CallLog.Calls.NUMBER} = ? AND ${android.provider.CallLog.Calls.TYPE} = ?",
            arrayOf(phoneNumber, android.provider.CallLog.Calls.OUTGOING_TYPE.toString()),
            "${android.provider.CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val dateIndex = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
                if (dateIndex != -1) {
                    return@withContext it.getLong(dateIndex)
                }
            }
        }
        return@withContext null
    }
}
