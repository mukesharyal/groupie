package expo.modules.screenshare

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.webrtc.*

class ScreenShareService : AccessibilityService() {
    private val rootEglBase by lazy { EglBase.create() }
    
    private var rtcManager: WebRTCManager? = null
    private var captureManager: CaptureManager? = null
    private var signalingServer: SignalingServer? = null
    private var videoTrack: VideoTrack? = null

    companion object {
        var instance: ScreenShareService? = null
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "screen_share_channel"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    /**
     * STEP 1: Triggered after user grants Screen Capture Permission.
     */
    fun onScreenCapturePermissionGranted(data: Intent, ip: String) {
        startServiceForeground()

        // 1. Initialize WebRTC Manager with logic to close server on success 
        // and reset session on loss.
        rtcManager = WebRTCManager(
            context = this,
            rootEglBase = rootEglBase,
            onConnectionEstablished = {
                // P2P tunnel is live. We no longer need the HTTP/WebSocket server.
                Log.d("ScreenShareService", "P2P Connected. Shutting down signaling server.")
                signalingServer?.stop()
            },
            onConnectionLost = {
                // Connection failed or closed. Clean up resources for next use.
                Log.d("ScreenShareService", "Connection lost. Resetting session.")
                stopCaptureSession()
            },
            onSignalReady = { signalJson ->
                signalingServer?.broadcast(signalJson)
            }
        )

        // 2. Create the Video Source and Track
        val videoSource = rtcManager?.createVideoSource(true) ?: return
        videoTrack = rtcManager?.createVideoTrack("SCREEN_VIDEO_TRACK", videoSource)

        // 3. Start hardware capture
        captureManager = CaptureManager(this, rootEglBase, videoSource)
        captureManager?.startCapture(data)

        // 4. STEP 2: Start the Signaling Server
        startServer(ip)
    }

    /**
     * Starts the Ktor server and waits for a WebSocket connection.
     */
    private fun startServer(ip: String) {
        try {
            val html = assets.open("index.html").bufferedReader().use { it.readText() }
            
            signalingServer = SignalingServer(
                onMessageReceived = { message -> 
                    rtcManager?.handleRemoteSignal(message) 
                },
                onNewConnection = {
                    // Start negotiation once the browser opens the WebSocket
                    videoTrack?.let { rtcManager?.startNegotiation(it) }
                }
            )
            signalingServer?.start(ip, html)
        } catch (e: Exception) {
            Log.e("ScreenShareService", "Server start error: ${e.localizedMessage}")
        }
    }

    /**
     * Cleans up the current WebRTC and Capture session resources 
     * while leaving the AccessibilityService running.
     */
    private fun stopCaptureSession() {
        captureManager?.stopCapture()
        signalingServer?.stop()
        rtcManager?.dispose()
        
        captureManager = null
        signalingServer = null
        rtcManager = null
        videoTrack = null
        
        // Optional: Update notification to show sharing has stopped
        updateNotificationStatus("Screen Sharing Stopped")
    }

    private fun startServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen Share", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        updateNotificationStatus("Screen Sharing Active")
    }

    private fun updateNotificationStatus(text: String) {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(text)
                .setContentText("Broadcasting screen via WebRTC")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle(text)
                .build()
        }
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopCaptureSession()
        rootEglBase.release()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
}