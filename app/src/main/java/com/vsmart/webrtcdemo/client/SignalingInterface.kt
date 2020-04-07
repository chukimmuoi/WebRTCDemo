package com.vsmart.webrtcdemo.client

import org.json.JSONObject

interface SignalingInterface {
    fun onRemoteHangUp(msg: String)

    fun onOfferReceived(data: JSONObject)

    fun onAnswerReceived(data: JSONObject)

    fun onIceCandidateReceived(data: JSONObject)

    fun onTryToStart()

    fun onCreatedRoom()

    fun onJoinedRoom()

    fun onNewPeerJoined()
}