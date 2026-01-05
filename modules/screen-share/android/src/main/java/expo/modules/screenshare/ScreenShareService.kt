package expo.modules.screenshare

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.webrtc.*

class ScreenShareService : AccessibilityService(), PointerListener {
    private val rootEglBase by lazy { EglBase.create() }
    
    private var rtcManager: WebRTCManager? = null
    private var captureManager: CaptureManager? = null
    private var signalingServer: SignalingServer? = null
    private var videoTrack: VideoTrack? = null

    // --- Gesture State Management ---
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activePaths = mutableMapOf<Int, Path>()
    private val lastX = mutableMapOf<Int, Float>()
    private val lastY = mutableMapOf<Int, Float>()
    
    // Interval for "flushing" segments. 
    // Stroke duration must be > interval to ensure the OS doesn't lift the finger between segments.
    private val FLUSH_INTERVAL_MS = 80L 
    private val STROKE_DURATION_MS = 120L 

    companion object {
        var instance: ScreenShareService? = null
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "screen_share_channel"
    }

    override fun onPointerEventReceived(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            val normX = json.getDouble("x").toFloat()
            val normY = json.getDouble("y").toFloat()
            val pointerId = json.getInt("id")

            val metrics = resources.displayMetrics
            val x = normX * metrics.widthPixels
            val y = normY * metrics.heightPixels

            handleGestureLogic(type, x, y, pointerId)
        } catch (e: Exception) {
            Log.e("ScreenShareService", "Error parsing pointer data: ${e.message}")
        }
    }

    private fun handleGestureLogic(type: String, x: Float, y: Float, id: Int) {
        when (type) {
            "down" -> {
                // Clear any leftover timers
                mainHandler.removeCallbacksAndMessages("gesture_flush_$id")
                
                val path = Path()
                path.moveTo(x, y)
                activePaths[id] = path
                lastX[id] = x
                lastY[id] = y
                
                // Immediately dispatch the 'down' to trigger touch feedback/longpress
                dispatchStrokeSegment(path, 10, true)
                
                scheduleStrokeFlush(id)
            }
            "move" -> {
                activePaths[id]?.lineTo(x, y)
                lastX[id] = x
                lastY[id] = y
            }
            "up" -> {
                mainHandler.removeCallbacksAndMessages("gesture_flush_$id")
                val path = activePaths.remove(id)
                val lx = lastX.remove(id) ?: x
                val ly = lastY.remove(id) ?: y
                
                if (path != null) {
                    path.lineTo(x, y)
                    // willContinue = false tells Android to lift the virtual finger
                    dispatchStrokeSegment(path, 50, false)
                }
            }
        }
    }

    private fun scheduleStrokeFlush(id: Int) {
        // Use a unique token for the handler to prevent clearing other pointers' timers
        val token = "gesture_flush_$id"
        mainHandler.postAtTime(object : Runnable {
            override fun run() {
                val path = activePaths[id] ?: return 
                
                // Send current segment and keep finger down
                dispatchStrokeSegment(path, STROKE_DURATION_MS, true)
                
                // Reset path for the next chunk starting from the current position
                val newPath = Path()
                val lx = lastX[id] ?: 0f
                val ly = lastY[id] ?: 0f
                newPath.moveTo(lx, ly)
                activePaths[id] = newPath
                
                mainHandler.postDelayed(this, token, FLUSH_INTERVAL_MS)
            }
        }, token, System.currentTimeMillis() + FLUSH_INTERVAL_MS)
    }

    private fun dispatchStrokeSegment(path: Path, duration: Long, willContinue: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val gestureBuilder = GestureDescription.Builder()
        
        val stroke = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // willContinue is supported on Android 8.0+ (API 26)
            GestureDescription.StrokeDescription(path, 0, duration, willContinue)
        } else {
            // Fallback for Android 7.x
            GestureDescription.StrokeDescription(path, 0, duration)
        }
        
        gestureBuilder.addStroke(stroke)
        
        // This is non-blocking. Returns immediately.
        val success = dispatchGesture(gestureBuilder.build(), null, null)
        if (!success) {
            Log.w("ScreenShareService", "Gesture segment rejected (possibly overlapping)")
        }
    }

    // --- Service Lifecycle & Setup ---

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    fun onScreenCapturePermissionGranted(data: Intent, ip: String) {
        startServiceForeground()
        rtcManager = WebRTCManager(
            context = this,
            rootEglBase = rootEglBase,
            pointerListener = this, 
            onConnectionEstablished = {
                Log.d("ScreenShareService", "P2P Connected.")
                signalingServer?.stop()
            },
            onConnectionLost = {
                Log.d("ScreenShareService", "Connection lost.")
                stopCaptureSession()
            },
            onSignalReady = { signalJson ->
                signalingServer?.broadcast(signalJson)
            }
        )

        val videoSource = rtcManager?.createVideoSource(true) ?: return
        videoTrack = rtcManager?.createVideoTrack("SCREEN_VIDEO_TRACK", videoSource)
        captureManager = CaptureManager(this, rootEglBase, videoSource)
        captureManager?.startCapture(data)
        startServer(ip)
    }

    private fun startServer(ip: String) {
        try {
            val html = assets.open("index.html").bufferedReader().use { it.readText() }
            signalingServer = SignalingServer(
                onMessageReceived = { message -> rtcManager?.handleRemoteSignal(message) },
                onNewConnection = { videoTrack?.let { rtcManager?.startNegotiation(it) } }
            )
            signalingServer?.start(ip, html)
        } catch (e: Exception) {
            Log.e("ScreenShareService", "Server start error: ${e.localizedMessage}")
        }
    }

    private fun stopCaptureSession() {
        captureManager?.stopCapture()
        signalingServer?.stop()
        rtcManager?.dispose()
        captureManager = null
        signalingServer = null
        rtcManager = null
        videoTrack = null
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
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(text)
                .build()
        }
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopCaptureSession()
        rootEglBase.release()
        mainHandler.removeCallbacksAndMessages(null)
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
}