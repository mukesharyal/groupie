package expo.modules.screenshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.InetAddress
import java.net.NetworkInterface

class ScreenShareModule : Module() {
  private var pendingIp: String? = null

  override fun definition() = ModuleDefinition {
    Name("ScreenShare")

    AsyncFunction("startServerAsync") {
      val service = ScreenShareService.instance
      val activity = appContext.currentActivity ?: throw Exception("Activity not found")
      
      if (service == null) {
          throw Exception("ACCESSIBILITY_SERVICE_NOT_ENABLED")
      }

      // 1. Get the IP address first
      val ip = getLocalIpAddress() ?: throw Exception("COULD_NOT_GET_IP_ADDRESS")
      pendingIp = ip // Store it temporarily to use after permission is granted

      // 2. Prepare the MediaProjection request
      val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val permissionIntent = manager.createScreenCaptureIntent()
      
      // 3. Launch the native permission dialog
      activity.startActivityForResult(permissionIntent, 1001)
      
      // Return the URL to the frontend so it can display a QR code or link
      return@AsyncFunction "http://$ip:8080"
    }

    OnActivityResult { _, payload ->
      if (payload.requestCode == 1001) {
          if (payload.resultCode == Activity.RESULT_OK) {
              val data = payload.data
              val ip = pendingIp
              
              if (data != null && ip != null) {
                  // SUCCESS: Start hardware AND server now
                  ScreenShareService.instance?.onScreenCapturePermissionGranted(data, ip)
              }
          } else {
              // User denied permission
              pendingIp = null
          }
      }
    }
  }

  private fun getLocalIpAddress(): String? {
    return try {
      NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filter { !it.isLoopbackAddress && it.hostAddress.indexOf(':') < 0 }
        .map { it.hostAddress }
        .firstOrNull()
    } catch (e: Exception) { null }
  }
}