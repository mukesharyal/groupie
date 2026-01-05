package expo.modules.screenshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.InetAddress
import java.net.NetworkInterface

class ScreenShareModule : Module() {
  private var pendingIp: String? = null

  override fun definition() = ModuleDefinition {
    Name("ScreenShare")

    // --- CHECK ACCESSIBILITY PERMISSION ---
    AsyncFunction("isAccessibilityServiceEnabled") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
      
      // Get list of enabled services
      val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
      
      // Check if our ScreenShareService is in that list
      return@AsyncFunction enabledServices.any { 
        it.resolveInfo.serviceInfo.packageName == context.packageName && 
        it.resolveInfo.serviceInfo.name == ScreenShareService::class.java.canonicalName 
      }
    }

    // --- OPEN SETTINGS FOR USER ---
    Function("openAccessibilitySettings") {
      val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      appContext.currentActivity?.startActivity(intent)
    }

    AsyncFunction("startServerAsync") {
      val service = ScreenShareService.instance
      val activity = appContext.currentActivity ?: throw Exception("Activity not found")
      
      if (service == null) {
          throw Exception("ACCESSIBILITY_SERVICE_NOT_ENABLED")
      }

      val ip = getLocalIpAddress() ?: throw Exception("COULD_NOT_GET_IP_ADDRESS")
      pendingIp = ip 

      val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val permissionIntent = manager.createScreenCaptureIntent()
      
      activity.startActivityForResult(permissionIntent, 1001)
      
      return@AsyncFunction "http://$ip:8080"
    }

    OnActivityResult { _, payload ->
      if (payload.requestCode == 1001) {
          if (payload.resultCode == Activity.RESULT_OK) {
              val data = payload.data
              val ip = pendingIp
              
              if (data != null && ip != null) {
                  ScreenShareService.instance?.onScreenCapturePermissionGranted(data, ip)
              }
          } else {
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