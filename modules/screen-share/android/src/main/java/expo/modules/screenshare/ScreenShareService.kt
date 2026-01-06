package expo.modules.screenshare

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    // --- Simple State Management ---
    private var isCurrentlySharing = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activePaths = mutableMapOf<Int, Path>()
    private val lastX = mutableMapOf<Int, Float>()
    private val lastY = mutableMapOf<Int, Float>()
    
    private val FLUSH_INTERVAL_MS = 80L 
    private val STROKE_DURATION_MS = 120L 

    private val stopActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_SERVICE) {
                stopCaptureSession()
            }
        }
    }

    companion object {
        var instance: ScreenShareService? = null
            private set
            
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "screen_share_channel"
        const val ACTION_STOP_SERVICE = "expo.modules.screenshare.STOP_SERVICE"
    }

    /**
     * Used by the Module to determine the current state.
     */
    fun isSharingActive(): Boolean {
        return isCurrentlySharing
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        val filter = IntentFilter(ACTION_STOP_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopActionReceiver, filter)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    // --- Screen Capture Logic ---

    fun onScreenCapturePermissionGranted(data: Intent, ip: String) {
        // Prevent infinite loops: if we are already sharing, exit immediately
        if (isCurrentlySharing) return
        
        // Toggle the simple boolean first
        isCurrentlySharing = true
        
        startServiceForeground()
        
        rtcManager = WebRTCManager(
            context = this,
            rootEglBase = rootEglBase,
            pointerListener = this, 
            onConnectionEstablished = {
                Log.d("ScreenShareService", "P2P Connected.")
                signalingServer?.broadcast(JSONObject().put("type", "connected").toString())
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
            stopCaptureSession()
        }
    }

    internal fun stopCaptureSession() {
        // Only run if we are actually sharing
        if (!isCurrentlySharing) return
        
        // Toggle immediately
        isCurrentlySharing = false

        captureManager?.stopCapture()
        signalingServer?.stop()
        rtcManager?.dispose()
        
        captureManager = null
        signalingServer = null
        rtcManager = null
        videoTrack = null
        
        // Notify JavaScript via Module broadcast
        sendBroadcast(Intent("EXPO_SCREENSHARE_STOPPED"))
        
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // --- Notification & Lifecycle ---

    private fun startServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen Share", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        updateNotificationStatus("Screen Sharing Active")
    }

    private fun updateNotificationStatus(text: String) {
        val stopIntent = Intent(ACTION_STOP_SERVICE).apply {
            `package` = packageName
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 
            0, 
            stopIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(text)
                .setContentText("Broadcasting screen via WebRTC")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Share", stopPendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(text)
                .setOngoing(true)
                .build()
        }
        startForeground(NOTIFICATION_ID, builder)
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
                mainHandler.removeCallbacksAndMessages("gesture_flush_$id")
                val path = Path()
                path.moveTo(x, y)
                activePaths[id] = path
                lastX[id] = x
                lastY[id] = y
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
                if (path != null) {
                    path.lineTo(x, y)
                    dispatchStrokeSegment(path, 50, false)
                }
            }
        }
    }

    private fun scheduleStrokeFlush(id: Int) {
        val token = "gesture_flush_$id"
        mainHandler.postAtTime(object : Runnable {
            override fun run() {
                val path = activePaths[id] ?: return 
                dispatchStrokeSegment(path, STROKE_DURATION_MS, true)
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
            GestureDescription.StrokeDescription(path, 0, duration, willContinue)
        } else {
            GestureDescription.StrokeDescription(path, 0, duration)
        }
        gestureBuilder.addStroke(stroke)
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    override fun onDestroy() {
        stopCaptureSession()
        rootEglBase.release()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(stopActionReceiver)
        } catch (e: Exception) {}
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
}