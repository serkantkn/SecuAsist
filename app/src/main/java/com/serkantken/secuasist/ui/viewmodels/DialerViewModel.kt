package com.serkantken.secuasist.ui.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.models.Contact
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class DialerViewModel(application: Application) : AndroidViewModel(application) {

    private val contactDao = AppDatabase.getDatabase(application).contactDao()

    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val matchedContacts: StateFlow<List<Contact>> = _dialedNumber
        .flatMapLatest { number ->
            if (number.isBlank()) {
                flowOf(emptyList())
            } else {
                contactDao.searchContactsByPhoneDigits(number)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onDigitPress(digit: String) {
        if (_dialedNumber.value.length < 15) {
            _dialedNumber.value += digit
        }
    }

    fun onDeletePress() {
        val current = _dialedNumber.value
        if (current.isNotEmpty()) {
            _dialedNumber.value = current.dropLast(1)
        }
    }

    fun onLongDeletePress() {
        _dialedNumber.value = ""
    }

    fun clearNumber() {
        _dialedNumber.value = ""
    }

    fun initiateCall(context: Context, phoneNumber: String? = null) {
        val numberToCall = phoneNumber ?: _dialedNumber.value
        if (numberToCall.isNotBlank()) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$numberToCall")
            }
            try {
                context.startActivity(intent)
            } catch (e: SecurityException) {
                // Eğer izin yoksa standart çeviriciye yönlendir
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$numberToCall")
                }
                context.startActivity(dialIntent)
            }
        }
    }
}
