package com.vsmart.webrtcdemo.client

import android.annotation.SuppressLint
import android.util.Log
import com.vsmart.webrtcdemo.data.constant.IConstants
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.net.URISyntaxException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SignallingSocket {
    private val TAG = SignallingSocket::class.java.name

    private var roomName: String = ""
    private lateinit var socket: Socket
    private lateinit var callback: SignalingInterface

    var isChannelReady = false
    var isInitiator    = false
    var isStarted      = false

    //This piece of code should not go into production!!
    //This will help in cases where the node server is running in non-https server and you want to ignore the warnings
//    @SuppressLint("TrustAllX509TrustManager")
//    private val trustAllCerts =
//        arrayOf<TrustManager>(object : X509TrustManager {
//            override fun getAcceptedIssuers(): Array<X509Certificate> {
//                return arrayOf()
//            }
//
//            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
//
//            }
//
//            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
//
//            }
//        })

    companion object {
        private var instance: SignallingSocket? = null

        fun getInstance() : SignallingSocket {
            if (instance == null) {
                instance = SignallingSocket()
            }
            instance?.let {
                if (it.roomName.isNullOrEmpty())
                    it.roomName = "vivek17"
            }

            return instance!!
        }
    }

    fun init(signalingInterface: SignalingInterface) {
        this.callback = signalingInterface

        try {
//            val sslContext = SSLContext.getInstance("TLS")
//            sslContext.init(null, trustAllCerts, null)
//            IO.setDefaultHostnameVerifier { _, _ -> true }
//            IO.setDefaultSSLContext(sslContext)

            socket = IO.socket(IConstants.IO_SOCKET)
            socket.connect()
            Log.d(TAG, "init() called")

            if (!roomName.isNullOrEmpty()) {
                emitInitStatement(roomName)
            }

            //room created event.
            socket.on(IConstants.EVENT_CREATED) { args ->
                Log.d(
                    TAG,
                    "created call(${IConstants.EVENT_CREATED}) called with: args = [" + Arrays.toString(args) + "]"
                )
                isInitiator = true
                callback.onCreatedRoom()
            }

            //room is full event
            socket.on(IConstants.EVENT_FULL) { args ->
                Log.d(
                    TAG,
                    "full call(${IConstants.EVENT_FULL}) called with: args = [" + Arrays.toString(args) + "]"
                )
            }

            //peer joined event
            socket.on(IConstants.EVENT_JOIN) { args ->
                Log.d(
                    TAG,
                    "join call(${IConstants.EVENT_JOIN}) called with: args = [" + Arrays.toString(args) + "]"
                )
                isChannelReady = true
                callback.onNewPeerJoined()
            }

            //when you joined a chat room successfully
            socket.on(IConstants.EVENT_JOINED) { args ->
                Log.d(
                    TAG,
                    "joined call(${IConstants.EVENT_JOINED}) called with: args = [" + Arrays.toString(args) + "]"
                )
                isChannelReady = true
                callback.onJoinedRoom()
            }

            //log event
            socket.on(IConstants.EVENT_LOG) { args ->
                Log.d(
                    TAG,
                    "log call(${IConstants.EVENT_LOG}) called with: args = [" + Arrays.toString(args) + "]"
                )
            }

            //bye event
            socket.on(IConstants.EVENT_BYE) { args ->
                Log.d(
                    TAG,
                    "log call(${IConstants.EVENT_BYE}) called with: args = [" + Arrays.toString(args) + "]"
                )
                callback.onRemoteHangUp(args[0] as String)
            }

            //messages - SDP and ICE candidates are transferred through this
            socket.on(IConstants.EVENT_MESSAGE) { args ->
                Log.d(
                    TAG,
                    "message call(${IConstants.EVENT_MESSAGE}) called with: args = [" + Arrays.toString(args) + "]"
                )
                if (args[0] is String) {
                    val data = args[0] as String
                    Log.d(TAG, "String received :: $data")
                    if (data.equals(IConstants.RESULT_GOT_USER_MEDIA, ignoreCase = true)) {
                        callback.onTryToStart()
                    }
                    if (data.equals(IConstants.RESULT_BYE, ignoreCase = true)) {
                        callback.onRemoteHangUp(data)
                    }
                } else if (args[0] is JSONObject) {
                    try {
                        val data = args[0] as JSONObject
                        Log.d(TAG, "Json Received :: $data")
                        val type = data.getString(IConstants.RESULT_TYPE)
                        if (type.equals(IConstants.RESULT_OFFER, ignoreCase = true)) {
                            callback.onOfferReceived(data)
                        } else if (type.equals(IConstants.RESULT_ANSWER, ignoreCase = true) && isStarted) {
                            callback.onAnswerReceived(data)
                        } else if (type.equals(IConstants.RESULT_CANDIDATE, ignoreCase = true) && isStarted) {
                            callback.onIceCandidateReceived(data)
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    private fun emitInitStatement(roomName: String) {
        Log.d(
            TAG,
            "emitInitStatement() called with: event = [create or join], room name = [$roomName]"
        )
        socket.emit(IConstants.EVENT_CREATE_OR_JOIN, roomName)
    }

    fun emitMessage(message: String) {
        Log.d(TAG, "emitMessage() called with: message = [$message]")
        socket.emit(IConstants.EVENT_MESSAGE, message)
    }

    fun emitMessage(message: SessionDescription) {
        Log.d(TAG, "emitMessage() called with: message = [$message]")
        try {
            val obj = JSONObject()
            obj.put(IConstants.RESULT_TYPE, message.type.canonicalForm())
            obj.put(IConstants.RESULT_SDP, message.description)

            Log.d(TAG, "emitMessage() called with: message = [$obj]")
            socket.emit(IConstants.EVENT_MESSAGE, obj)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun emitIceCandidate(iceCandidate: IceCandidate) {
        try {
            val obj = JSONObject()
            obj.put(IConstants.RESULT_TYPE, IConstants.TYPE_CANDIDATE)
            obj.put(IConstants.RESULT_LABEL, iceCandidate.sdpMLineIndex)
            obj.put(IConstants.RESULT_ID, iceCandidate.sdpMid)
            obj.put(IConstants.RESULT_CANDIDATE, iceCandidate.sdp)

            Log.d(TAG, "emitIceCandidate() called with: message = [$obj]")
            socket.emit(IConstants.EVENT_MESSAGE, obj)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        socket.emit(IConstants.EVENT_BYE, roomName)
        socket.disconnect()
        socket.close()
    }
}