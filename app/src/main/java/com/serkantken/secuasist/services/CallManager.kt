package com.serkantken.secuasist.services

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallStateInfo(
    val call: Call,
    val state: Int,
    val number: String? = null
)

object CallManager {
    var inCallService: InCallService? = null

    private val _currentCall = MutableStateFlow<CallStateInfo?>(null)
    val currentCall: StateFlow<CallStateInfo?> = _currentCall.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeaker = MutableStateFlow(false)
    val isSpeaker: StateFlow<Boolean> = _isSpeaker.asStateFlow()

    fun updateAudioState(audioState: CallAudioState?) {
        if (audioState == null) return
        _isMuted.value = audioState.isMuted
        _isSpeaker.value = audioState.route == CallAudioState.ROUTE_SPEAKER
    }

    fun updateCall(call: Call?) {
        if (call == null) {
            _currentCall.value = null
        } else {
            val number = call.details?.handle?.schemeSpecificPart
            _currentCall.value = CallStateInfo(call, call.state, number)
        }
    }

    fun answerCall() {
        val call = _currentCall.value?.call ?: return
        if (call.state == Call.STATE_RINGING) {
            call.answer(call.details.videoState)
        }
    }

    fun rejectCall() {
        val call = _currentCall.value?.call ?: return
        if (call.state == Call.STATE_RINGING) {
            call.reject(false, null)
        }
    }

    fun toggleMute() {
        inCallService?.setMuted(!_isMuted.value)
    }

    fun toggleSpeaker() {
        val newRoute = if (_isSpeaker.value) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        inCallService?.setAudioRoute(newRoute)
    }

    fun playDtmfTone(char: Char) {
        _currentCall.value?.call?.playDtmfTone(char)
    }

    fun stopDtmfTone() {
        _currentCall.value?.call?.stopDtmfTone()
    }

    fun disconnectCall() {
        _currentCall.value?.call?.disconnect()
    }
}
