package expo.modules.screenshare

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*
import java.io.InputStream
import java.net.NetworkInterface
import java.util.Collections

class ScreenShareService : AccessibilityService() {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val sessions = Collections.synchronizedSet<DefaultWebSocketServerSession>(LinkedHashSet())

    // WebRTC Core
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    
    // EGL Context for Hardware Acceleration
    private val rootEglBase: EglBase by lazy { EglBase.create() }
    
    // Screen Capture Objects
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    companion object {
        var instance: ScreenShareService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        initWebRTC()
    }

    private fun initWebRTC() {
        // Initialize WebRTC globals
        val options = PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        // Setup Video Encoders/Decoders with EGL Context
        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    /**
     * Called by ScreenShareModule after the user grants MediaProjection permission.
     */
    fun onScreenCapturePermissionGranted(data: Intent) {
        val metrics = resources.displayMetrics
        
        // Helper to handle textures from the screen capture
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
        
        screenCapturer = ScreenCapturerAndroid(data, object : android.media.projection.MediaProjection.Callback() {
            override fun onStop() { 
                Log.d("WebRTC", "Screen projection stopped by system") 
            }
        })

        videoSource = factory?.createVideoSource(true)
        screenCapturer?.initialize(surfaceTextureHelper, this, videoSource?.capturerObserver)
        
        // Start capturing at the device's native resolution
        screenCapturer?.startCapture(metrics.widthPixels, metrics.heightPixels, 30)

        // Create the actual video track that will be sent over WebRTC
        videoTrack = factory?.createVideoTrack("VIDEO_TRACK", videoSource)
        
        Log.d("WebRTC", "Video track created and screen capture started.")
    }

    fun startServer(ip: String) {
        if (server != null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(Netty, port = 8080) {
                    install(WebSockets)
                    routing {
                        get("/address.js") {
                            call.respondText("export const webSocketAddress = 'ws://$ip:8080/signal';", ContentType.Application.JavaScript)
                        }
                        get("/") {
                            val html = assets.open("index.html").bufferedReader().use { it.readText() }
                            call.respondText(html, ContentType.Text.Html)
                        }
                        webSocket("/signal") {
                            sessions.add(this)
                            try {
                                // Start handshake as soon as a client connects to the signaling socket
                                initiateWebRTCOffer()
                                
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        handleIncomingSignal(frame.readText())
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("WebRTC", "WebSocket session error: ${e.message}")
                            } finally {
                                sessions.remove(this)
                            }
                        }
                    }
                }.start(wait = false)
            } catch (e: Exception) {
                Log.e("WebRTC", "Failed to start Ktor server", e)
            }
        }
    }

    private fun handleIncomingSignal(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "answer" -> {
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"))
                    peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
                }
                "candidate" -> {
                    val candidate = IceCandidate(
                        json.getString("sdpMid"),
                        json.getInt("sdpMLineIndex"),
                        json.getString("candidate")
                    )
                    peerConnection?.addIceCandidate(candidate)
                }
            }
        } catch (e: Exception) {
            Log.e("WebRTC", "Error parsing incoming signal", e)
        }
    }

    private fun initiateWebRTCOffer() {
        // Standard STUN server for local/network traversal
        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        
        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val json = JSONObject().apply {
                        put("type", "candidate")
                        put("candidate", it.sdp)
                        put("sdpMid", it.sdpMid)
                        put("sdpMLineIndex", it.sdpMLineIndex)
                    }
                    broadcastSignal(json.toString())
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("WebRTC", "ICE Connection State: $state")
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d("WebRTC", "Connection State: $newState")
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            override fun onTrack(transceiver: RtpTransceiver?) {}
        })

        // Attach the screen track before creating the offer
        videoTrack?.let {
            peerConnection?.addTrack(it, listOf("SCREEN_STREAM"))
            Log.d("WebRTC", "Added video track to PeerConnection")
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                val json = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", sdp?.description)
                }
                broadcastSignal(json.toString())
            }
        }, MediaConstraints())
    }

    private fun broadcastSignal(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            sessions.forEach { 
                try {
                    it.send(Frame.Text(message))
                } catch (e: Exception) {
                    Log.e("WebRTC", "Failed to broadcast signal")
                }
            }
        }
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(msg: String?) { Log.e("WebRTC", "SDP Observer Error: $msg") }
        override fun onSetFailure(msg: String?) { Log.e("WebRTC", "SDP Set Success Error: $msg") }
    }

    override fun onDestroy() {
        server?.stop(1000, 5000)
        screenCapturer?.stopCapture()
        screenCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        videoSource?.dispose()
        peerConnection?.dispose()
        factory?.dispose()
        rootEglBase.release()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
}