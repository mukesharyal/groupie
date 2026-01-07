package expo.modules.screenshare

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import org.webrtc.*

class CaptureManager(
    private val context: Context,
    private val rootEglBase: EglBase,
    private val videoSource: VideoSource
) {
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    
    // Flag to control frame flow
    private var isPaused = false

    /**
     * A proxy observer that intercepts frames before they reach the video source.
     */
    private val proxyObserver = object : CapturerObserver {
        override fun onCapturerStarted(success: Boolean) {
            videoSource.capturerObserver.onCapturerStarted(success)
        }

        override fun onCapturerStopped() {
            videoSource.capturerObserver.onCapturerStopped()
        }

        override fun onFrameCaptured(frame: VideoFrame) {
            // If paused, we simply drop the frame and don't pass it to the source.
            // This causes the remote peer to stay on the last successfully processed frame.
            if (!isPaused) {
                videoSource.capturerObserver.onFrameCaptured(frame)
            }
        }
    }

    fun startCapture(data: Intent) {
        if (screenCapturer != null) return

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
        screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {})

        try {
            // Initialize with our proxy instead of the direct videoSource.capturerObserver
            screenCapturer?.initialize(surfaceTextureHelper, context, proxyObserver)

            val metrics = context.resources.displayMetrics
            screenCapturer?.startCapture(metrics.widthPixels, metrics.heightPixels, 30)
            isPaused = false
        } catch (e: Exception) {
            Log.e("CaptureManager", "Failed to start capture: ${e.localizedMessage}")
            stopCapture()
        }
    }

    fun pauseCapture() {
        isPaused = true
        Log.d("CaptureManager", "Capture gated: Frames are being dropped (Freeze frame).")
    }

    fun continueCapture() {
        isPaused = false
        Log.d("CaptureManager", "Capture ungated: Frames are flowing.")
    }

    fun stopCapture() {
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
        } catch (e: Exception) {
            Log.e("CaptureManager", "Error during stopCapture: ${e.localizedMessage}")
        }
    }
}