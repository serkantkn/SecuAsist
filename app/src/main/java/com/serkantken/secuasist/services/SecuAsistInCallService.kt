package com.serkantken.secuasist.services

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.serkantken.secuasist.ui.screens.CallActivity

class SecuAsistInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d("InCallService", "Call Added: ${call.details?.handle}")
        
        // Listen to state changes of the call
        call.registerCallback(callCallback)
        
        // Update CallManager
        CallManager.inCallService = this
        CallManager.updateAudioState(callAudioState)
        CallManager.updateCall(call)
        
        // Launch CallActivity
        val intent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d("InCallService", "Call Removed")
        
        call.unregisterCallback(callCallback)
        
        if (CallManager.currentCall.value?.call == call) {
            CallManager.inCallService = null
            CallManager.updateCall(null)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        Log.d("InCallService", "Audio State Changed: Muted=${audioState?.isMuted}, Route=${audioState?.route}")
        CallManager.updateAudioState(audioState)
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d("InCallService", "Call State Changed: $state")
            // Re-emit the call to trigger flow collection updates in UI
            if (CallManager.currentCall.value?.call == call) {
                CallManager.updateCall(call) // Will trigger UI refresh
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details?) {
            super.onDetailsChanged(call, details)
            Log.d("InCallService", "Call Details Changed: ${details?.handle}")
            if (CallManager.currentCall.value?.call == call) {
                CallManager.updateCall(call)
            }
        }
    }
}
