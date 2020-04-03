package com.vsmart.webrtcdemo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.*

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO: STEP 01
        //Initialize PeerConnectionFactory globals.
        //Params are context, initAudio,initVideo and videoCodecHwAcceleration
        //PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true);
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(this)
                .setEnableVideoHwAcceleration(true)
                .createInitializationOptions()
        )

        //Create a new PeerConnectionFactory instance.
        //PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        val peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        // TODO: STEP 02
        //Now create a VideoCapturer instance.
        // Callback methods are there if you want to do something! Duh!
        val videoCapturerAndroid = createVideoCapturer()
        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        // More on this later!
        val constraints = MediaConstraints()

        // TODO: STEP 03, 04
        //Create a VideoSource instance
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid)
        val localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        //create an AudioSource instance
        val audioSource = peerConnectionFactory.createAudioSource(constraints)
        val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        //we will start capturing the video from the camera
        //width,height and fps
        videoCapturerAndroid?.startCapture(1000, 1000, 30)

        // TODO: STEP 05
        //create surface renderer, init it and add the renderer to the track
        surfaceRender.setMirror(true)

        val rootEglBase = EglBase.create()
        surfaceRender.init(rootEglBase.eglBaseContext, null)

        localVideoTrack.addRenderer(VideoRenderer(surfaceRender))
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
}
