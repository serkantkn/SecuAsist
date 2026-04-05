package com.serkantken.secuasist.ui.viewmodels

import android.app.Application
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantken.secuasist.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CallLogEntry(
    val id: Long,
    val formattedNumber: String,
    val contactName: String?,
    val timestamp: Long,
    val durationSeconds: Long,
    val type: Int // CallLog.Calls.INCOMING_TYPE, OUTGOING_TYPE, MISSED_TYPE, REJECTED_TYPE
)

class CallLogViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val contactDao = db.contactDao()
    
    private val _callLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callLogs: StateFlow<List<CallLogEntry>> = _callLogs.asStateFlow()

    fun loadCallLogs() {
        viewModelScope.launch {
            val logs = withContext(Dispatchers.IO) {
                fetchLogs()
            }
            _callLogs.value = logs
        }
    }

    private suspend fun fetchLogs(): List<CallLogEntry> {
        val result = mutableListOf<CallLogEntry>()
        val context = getApplication<Application>().applicationContext
        
        try {
            // Requires Permissions runtime wrapper
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

                var limit = 0
                while (it.moveToNext() && limit < 100) {
                    limit++
                    val number = it.getString(numIdx) ?: "Bilinmeyen Numara"
                    var name = it.getString(nameIdx)
                    
                    // Cross check with internal db if native name missing
                    if (name.isNullOrEmpty() && number != "Bilinmeyen Numara") {
                        val cleanNum = number.replace(Regex("[^0-9]"), "")
                        if (cleanNum.isNotEmpty()) {
                            val localContact = contactDao.getContactByPhoneNumber(number) 
                                ?: contactDao.getContactByPhoneNumber(cleanNum)
                            name = localContact?.contactName
                        }
                    }

                    result.add(
                        CallLogEntry(
                            id = it.getLong(idIdx),
                            formattedNumber = number,
                            contactName = name,
                            timestamp = it.getLong(dateIdx),
                            durationSeconds = it.getLong(durIdx),
                            type = it.getInt(typeIdx)
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace() // Un-granted permission
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
