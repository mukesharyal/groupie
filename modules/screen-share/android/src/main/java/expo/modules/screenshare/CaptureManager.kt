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

    fun startCapture(data: Intent) {
        if (screenCapturer != null) return

        // 1. Create the SurfaceTextureHelper on a dedicated thread with shared EGL context
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)

        // 2. Create the ScreenCapturer
        screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d("CaptureManager", "MediaProjection stopped by system or user.")
            }
        })

        try {
            // 3. Initialize with the observer from our VideoSource
            screenCapturer?.initialize(
                surfaceTextureHelper,
                context,
                videoSource.capturerObserver
            )

            // 4. Start capturing at the device's current resolution
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            
            screenCapturer?.startCapture(width, height, 30)
            
            Log.d("CaptureManager", "Screen capture started: ${width}x${height}")
        } catch (e: Exception) {
            Log.e("CaptureManager", "Failed to start capture: ${e.localizedMessage}")
            stopCapture() 
        }
    }

    /**
     * Stops the capture and releases hardware resources.
     */
    fun stopCapture() {
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            
            Log.d("CaptureManager", "Capture resources released.")
        } catch (e: Exception) {
            Log.e("CaptureManager", "Error during stopCapture: ${e.localizedMessage}")
        }
    }
}