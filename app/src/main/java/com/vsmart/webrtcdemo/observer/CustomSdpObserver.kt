package com.vsmart.webrtcdemo.observer

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

class CustomSdpObserver
constructor(var logTag: String) : SdpObserver {

    init {
        logTag = this.javaClass.name
    }

    override fun onCreateSuccess(sessionDescription: SessionDescription) {
        Log.d(
            logTag,
            "onCreateSuccess() called with: sessionDescription = [$sessionDescription]"
        )
    }

    override fun onSetSuccess() {
        Log.d(
            logTag,
            "onSetSuccess() called"
        )
    }

    override fun onCreateFailure(s: String) {
        Log.d(
            logTag,
            "onCreateFailure() called with: s = [$s]"
        )
    }

    override fun onSetFailure(s: String) {
        Log.d(
            logTag,
            "onSetFailure() called with: s = [$s]"
        )
    }
}