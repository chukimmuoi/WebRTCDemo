package com.vsmart.webrtcdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.vsmart.webrtcdemo.client.SignalingInterface
import com.vsmart.webrtcdemo.client.SignallingSocket
import com.vsmart.webrtcdemo.model.IceServer
import com.vsmart.webrtcdemo.model.TurnServer
import com.vsmart.webrtcdemo.observer.CustomPeerConnectionObserver
import com.vsmart.webrtcdemo.observer.CustomSdpObserver
import com.vsmart.webrtcdemo.utils.Utils
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnectionFactory.InitializationOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.UnsupportedEncodingException
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), View.OnClickListener, SignalingInterface {
    private val TAG = MainActivity::class.java.name

    private val rootEglBase by lazy { EglBase.create() }

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        // Initialize PeerConnectionFactory globals.
        val initializationOptions = InitializationOptions
            .builder(this)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        // Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(
            rootEglBase.eglBaseContext,
            true,
            true
        )

        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()
    }

    // Create MediaConstraints - Will be useful for specifying video and audio constraints.
    private val audioConstraints: MediaConstraints by lazy {
        MediaConstraints()
    }
    private val videoConstraints: MediaConstraints by lazy {
        MediaConstraints()
    }
    private val sdpConstraints: MediaConstraints by lazy {
        MediaConstraints()
    }

    var videoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null

    var audioSource: AudioSource?    = null
    var localAudioTrack: AudioTrack? = null

    var surfaceTextureHelper: SurfaceTextureHelper? = null

    var localPeer: PeerConnection?   = null
    var iceServers: List<IceServer> = listOf()

    var gotUserMedia = false
    var peerIceServers: MutableList<PeerConnection.IceServer> = mutableListOf()

    val ALL_PERMISSIONS_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val isCameraNotGranted =
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
        val isRecordAudioNotGranted =
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED

        if (isCameraNotGranted || isRecordAudioNotGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                ),
                ALL_PERMISSIONS_CODE
            )
        } else {
            // all permissions already granted
            start()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == ALL_PERMISSIONS_CODE
            && grantResults.size == 2
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
            && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            // all permissions granted
            start()
        } else {
            finish()
        }
    }

    private fun initVideos() {
        surfaceRenderLocal.init(rootEglBase.eglBaseContext, null)
        surfaceRenderLocal.setZOrderMediaOverlay(true)

        surfaceRenderRemote.init(rootEglBase.eglBaseContext, null)
        surfaceRenderRemote.setZOrderMediaOverlay(true)
    }

    private fun getIceServers() {
        var data = ByteArray(0)
        try {
            data = "chukimmuoi:469d9802-771e-11ea-a827-0242ac110004".toByteArray(Charsets.UTF_8)
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        val authToken = "Basic ${Base64.encodeToString(data, Base64.NO_WRAP)}"

        Utils.getInstance()
            .getRetrofitInstance()
            .getIceCandidates(authToken)
            .enqueue(object : Callback<TurnServer> {
                override fun onResponse(call: Call<TurnServer>, response: Response<TurnServer>) {
                    val body: TurnServer? = response.body()
                    body?.let {
                        iceServers = it.iceServerList.iceServers
                    }
                    for (iceServer in iceServers) {
                        if (iceServer.credential == null) {
                            val peerIceServer =
                                PeerConnection.IceServer.builder(iceServer.url)
                                    .createIceServer()

                            peerIceServers.add(peerIceServer)
                        } else {
                            val peerIceServer =
                                PeerConnection.IceServer.builder(iceServer.url)
                                    .setUsername(iceServer.username)
                                    .setPassword(iceServer.credential)
                                    .createIceServer()

                            peerIceServers.add(peerIceServer)
                        }
                    }

                    Log.d("onApiResponse", "IceServers\n${iceServers}")
                }

                override fun onFailure(call: Call<TurnServer>, t: Throwable) {
                    t.printStackTrace()
                }
            })
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.startCall -> {
                start()
            }
            R.id.initCall -> {
                call()
            }
            R.id.endCall -> {
                hangup()
            }
        }
    }

    private fun start() {
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initVideos()
        getIceServers()

        SignallingSocket.getInstance().init(this)

        // Now create a VideoCapturer instance.
        val videoCapturerAndroid = createVideoCapturer()
        videoCapturerAndroid?.let {
            surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid.isScreencast)
            videoSource?.let {
                videoCapturerAndroid.initialize(surfaceTextureHelper, this, it.capturerObserver)
            }
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        //create an AudioSource instance
        audioSource     = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        videoCapturerAndroid?.startCapture(1024, 720, 30)

        surfaceRenderLocal.visibility = View.VISIBLE

        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack?.addSink(surfaceRenderLocal)

        surfaceRenderLocal.setMirror(true)
        surfaceRenderRemote.setMirror(true)

        gotUserMedia = true
        if (SignallingSocket.getInstance().isInitiator) {
            onTryToStart()
        }
    }

    private fun call() {

    }

    private fun hangup() {
        try {
            localPeer?.let {
                it.close()
            }
            localPeer = null
            SignallingSocket.getInstance().close()
            updateVideoViews(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRemoteHangUp(message: String) {
        showToast("Remote Peer hungup message = $message")
        runOnUiThread { hangup() }
    }

    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    override fun onOfferReceived(data: JSONObject) {
        showToast("Received Offer")
        runOnUiThread {
            if (!SignallingSocket.getInstance().isInitiator
                && !SignallingSocket.getInstance().isStarted) {
                onTryToStart()
            }
            try {
                localPeer?.setRemoteDescription(
                    CustomSdpObserver("localSetRemote"),
                    SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp"))
                )
                doAnswer()
                updateVideoViews(true)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private fun doAnswer() {
        localPeer?.let {
            it.createAnswer(object : CustomSdpObserver("localCreateAns") {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    super.onCreateSuccess(sessionDescription!!)
                    it.setLocalDescription(
                        CustomSdpObserver("localSetLocal"),
                        sessionDescription
                    )
                    SignallingSocket.getInstance().emitMessage(sessionDescription)
                }
            }, MediaConstraints())
        }
    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */
    override fun onAnswerReceived(data: JSONObject) {
        showToast("Received Answer")
        try {
            localPeer?.setRemoteDescription(
                CustomSdpObserver("localSetRemote"),
                SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()),
                    data.getString("sdp")
                )
            )
            updateVideoViews(true)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun updateVideoViews(remoteVisible: Boolean) {
        runOnUiThread {
            var params = surfaceRenderLocal.layoutParams
            if (remoteVisible) {
                params.height = dpToPx(100)
                params.width  = dpToPx(100)
            } else {
                params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            surfaceRenderLocal.layoutParams = params
        }
    }

    /**
     * Remote IceCandidate received
     */
    override fun onIceCandidateReceived(data: JSONObject) {
        try {
            localPeer?.let {
                it.addIceCandidate(
                    IceCandidate(
                        data.getString("id"),
                        data.getInt("label"),
                        data.getString("candidate")
                    )
                )
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    override fun onTryToStart() {
        runOnUiThread {
            if (!SignallingSocket.getInstance().isStarted
                && localVideoTrack != null
                && SignallingSocket.getInstance().isChannelReady) {
                createPeerConnection()
                SignallingSocket.getInstance().isStarted = true
                if (SignallingSocket.getInstance().isInitiator) {
                    doCall()
                }
            }
        }
    }

    /**
     * This method is called when the app is the initiator -
     * We generate the offer and send it over through socket
     * to remote peer
     */
    private fun doCall() {
        sdpConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        sdpConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
        localPeer?.let {
            it.createOffer(object : CustomSdpObserver("localCreateOffer") {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    super.onCreateSuccess(sessionDescription)
                    it.setLocalDescription(
                        CustomSdpObserver("localSetLocalDesc"),
                        sessionDescription
                    )
                    Log.d("onCreateSuccess", "SignallingClient emit ")
                    SignallingSocket.getInstance().emitMessage(sessionDescription)
                }
            }, sdpConstraints)
        }
    }

    /**
     * Creating the local peerconnection instance
     */
    private fun createPeerConnection() {
        val rtcConfig = RTCConfiguration(peerIceServers)
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy =
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        localPeer = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : CustomPeerConnectionObserver("localPeerCreation") {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    super.onIceCandidate(iceCandidate)
                    onIceCandidateReceived(iceCandidate)
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    showToast("Received Remote stream")
                    super.onAddStream(mediaStream)
                    gotRemoteStream(mediaStream)
                }
            })

        addStreamToLocalPeer()
    }

    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingSocket.getInstance().emitIceCandidate(iceCandidate)
    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private fun gotRemoteStream(stream: MediaStream) {
        // We have remote video stream. Add to the renderer.
        val videoTrack = stream.videoTracks[0]
        runOnUiThread {
            try {
                surfaceRenderRemote.visibility = View.VISIBLE
                videoTrack.addSink(surfaceRenderRemote)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Adding the stream to the localpeer
     */
    private fun addStreamToLocalPeer() {
        //creating local media stream
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        stream.addTrack(localVideoTrack)
        localPeer?.addStream(stream)
    }

    /**
     * SignallingCallback - called when the room is created - i.e. you are the initiator
     */
    override fun onCreatedRoom() {
        showToast("You created the room $gotUserMedia")
        if (gotUserMedia) {
            SignallingSocket.getInstance().emitMessage("got user media")
        }
    }

    /**
     * SignallingCallback - called when you join the room - you are a participant
     */
    override fun onJoinedRoom() {
        showToast("You joined the room $gotUserMedia")
        if (gotUserMedia) {
            SignallingSocket.getInstance().emitMessage("got user media")
        }
    }

    override fun onNewPeerJoined() {
        showToast("Remote Peer Joined")
    }

    /**
     * Util Methods
     */
    fun dpToPx(dp: Int): Int {
        val displayMetrics = resources.displayMetrics
        return (dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }

    fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        Logging.d(TAG, "Creating capturer using camera1 API.")
        return createCameraCapturer(Camera1Enumerator(false))
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.")
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.")
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        return null
    }

    override fun onDestroy() {
        SignallingSocket.getInstance().close()
        super.onDestroy()
        surfaceTextureHelper?.let {
            it.dispose()
        }
        surfaceTextureHelper = null
    }
}
