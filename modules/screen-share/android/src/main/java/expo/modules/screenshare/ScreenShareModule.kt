package expo.modules.screenshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

class ScreenShareModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ScreenShare")

    AsyncFunction("startServerAsync") {
      val service = ScreenShareService.instance
      val activity = appContext.currentActivity ?: throw Exception("Activity not found")
      
      if (service != null) {
          val ip = getLocalIpAddress() ?: "localhost"
          
          // Initialize the MediaProjectionManager to request screen capture
          val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
          val permissionIntent = manager.createScreenCaptureIntent()
          
          // Launch the system dialog for screen capture permission
          activity.startActivityForResult(permissionIntent, 1001)
          
          // Start the Ktor server to serve the HTML and handle signaling
          service.startServer(ip)
          
          return@AsyncFunction "http://$ip:8080"
      } else {
          throw Exception("ACCESSIBILITY_SERVICE_NOT_ENABLED")
      }
    }

    // This listener catches the result of the screen capture permission dialog
    OnActivityResult { _, payload ->
      if (payload.requestCode == 1001) {
          if (payload.resultCode == Activity.RESULT_OK) {
              val data: Intent? = payload.data
              if (data != null) {
                  // Pass the intent data to the service to initiate Foreground mode and WebRTC
                  ScreenShareService.instance?.onScreenCapturePermissionGranted(data)
              }
          } else {
              // Handle case where user denied permission
              System.err.println("Screen share permission denied by user.")
          }
      }
    }
  }

  /**
   * Helper function to find the device's local IPv4 address on the Wi-Fi network.
   */
  private fun getLocalIpAddress(): String? {
    try {
      val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
      for (intf in interfaces) {
        val addrs = Collections.list(intf.inetAddresses)
        for (addr in addrs) {
          if (!addr.isLoopbackAddress && addr is InetAddress) {
            val sAddr = addr.hostAddress
            val isIPv4 = sAddr.indexOf(':') < 0
            if (isIPv4) return sAddr
          }
        }
      }
    } catch (ex: Exception) {
      ex.printStackTrace()
    }
    return null
  }
}