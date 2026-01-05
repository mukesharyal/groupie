package expo.modules.screenshare

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.webrtc.*

/**
 * Interface to communicate received gestures to the Accessibility Service
 */
interface PointerListener {
    fun onPointerEventReceived(message: String)
}

class WebRTCManager(
    context: Context,
    private val rootEglBase: EglBase,
    private val pointerListener: PointerListener, // Injected listener
    private val onConnectionEstablished: () -> Unit,
    private val onConnectionLost: () -> Unit,
    private val onSignalReady: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    
    private var dataChannel: DataChannel? = null

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun startNegotiation(videoTrack: VideoTrack) {
        peerConnection?.close()
        peerConnection?.dispose()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val json = JSONObject().apply {
                        put("type", "candidate")
                        put("candidate", it.sdp)
                        put("sdpMid", it.sdpMid)
                        put("sdpMLineIndex", it.sdpMLineIndex)
                    }
                    onSignalReady(json.toString())
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d("WebRTC", "Peer Connection State: $newState")
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> onConnectionEstablished()
                    PeerConnection.PeerConnectionState.FAILED, 
                    PeerConnection.PeerConnectionState.DISCONNECTED,
                    PeerConnection.PeerConnectionState.CLOSED -> onConnectionLost()
                    else -> {}
                }
            }

            override fun onDataChannel(dc: DataChannel?) {
                // If the remote side creates a data channel, we attach hooks to it
                Log.d("WebRTC", "Remote DataChannel received")
                dataChannel = dc
                setupDataChannelHooks()
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        // --- DATA CHANNEL SETUP (Local Creation) ---
        val dcInit = DataChannel.Init().apply {
            ordered = true 
        }
        dataChannel = peerConnection?.createDataChannel("messagingChannel", dcInit)
        setupDataChannelHooks()

        peerConnection?.addTrack(videoTrack, listOf("SCREEN_STREAM"))

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                val json = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", sdp?.description)
                }
                onSignalReady(json.toString())
            }
        }, MediaConstraints())
    }

    private fun setupDataChannelHooks() {
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            
            override fun onStateChange() {
                Log.d("WebRTC", "DataChannel State: ${dataChannel?.state()}")
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                // Extract the bytes from the WebRTC Buffer
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val message = String(bytes)

                // Pass the raw JSON string to the PointerListener on the Main Thread
                // This is safer because dispatchGesture usually requires the Main/UI thread.
                mainHandler.post {
                    pointerListener.onPointerEventReceived(message)
                }
            }
        })
    }

    fun handleRemoteSignal(message: String) {
        val json = JSONObject(message)
        mainHandler.post {
            val pc = peerConnection ?: return@post 
            when (json.optString("type")) {
                "answer" -> {
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"))
                    pc.setRemoteDescription(SimpleSdpObserver(), sdp)
                }
                "candidate" -> {
                    val candidate = IceCandidate(
                        json.getString("sdpMid"), 
                        json.getInt("sdpMLineIndex"), 
                        json.getString("candidate")
                    )
                    pc.addIceCandidate(candidate)
                }
            }
        }
    }

    fun createVideoSource(isScreencast: Boolean): VideoSource = factory.createVideoSource(isScreencast)
    fun createVideoTrack(id: String, source: VideoSource): VideoTrack = factory.createVideoTrack(id, source)

    fun dispose() {
        dataChannel?.unregisterObserver()
        dataChannel?.dispose()
        dataChannel = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        factory.dispose()
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(msg: String?) { Log.e("WebRTC", "SDP Create Error: $msg") }
        override fun onSetFailure(msg: String?) { Log.e("WebRTC", "SDP Set Error: $msg") }
    }
}