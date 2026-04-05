package com.serkantken.secuasist.services

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CallStateInfo(
    val call: Call,
    val state: Int,
    val number: String? = null
)

object CallManager {
    var inCallService: InCallService? = null

    private val _currentCall = MutableStateFlow<CallStateInfo?>(null)
    val currentCall: StateFlow<CallStateInfo?> = _currentCall.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
            stopTimer()
        } else {
            val number = call.details?.handle?.schemeSpecificPart
            val oldState = _currentCall.value?.state
            val newState = call.state
            _currentCall.value = CallStateInfo(call, newState, number)
            
            if (newState == Call.STATE_ACTIVE && oldState != Call.STATE_ACTIVE) {
                startTimer()
            } else if (newState == Call.STATE_DISCONNECTED) {
                stopTimer()
            }
        }
    }

    private fun startTimer() {
        if (timerJob != null) return
        _callDuration.value = 0L
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                _callDuration.value++
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
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
