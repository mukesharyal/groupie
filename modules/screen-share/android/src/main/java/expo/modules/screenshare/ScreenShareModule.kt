package expo.modules.screenshare

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import java.net.NetworkInterface

class ScreenShareModule : Module() {
  private var pendingIp: String? = null
  private var pendingPromise: Promise? = null

  // Listener to catch the signal from the Service when sharing stops
  private val stopReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == "EXPO_SCREENSHARE_STOPPED") {
        sendEvent("onScreenShareStopped")
      }
    }
  }

  override fun definition() = ModuleDefinition {
    Name("ScreenShare")

    // Define the event name for JavaScript
    Events("onScreenShareStopped")

    // Register the broadcast receiver when the module is created
    OnCreate {
      val context = appContext.reactContext
      val filter = IntentFilter("EXPO_SCREENSHARE_STOPPED")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context?.registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        context?.registerReceiver(stopReceiver, filter)
      }
    }

    // Cleanup the receiver when the module is destroyed
    OnDestroy {
      try {
        appContext.reactContext?.unregisterReceiver(stopReceiver)
      } catch (e: Exception) {
        // Receiver might not be registered
      }
    }


    // NEW FUNCTION: Returns true if the capture manager is currently running
    AsyncFunction("isSharing") {
      val service = ScreenShareService.instance
      return@AsyncFunction service?.isSharingActive() ?: false
    }

    AsyncFunction("getServerUrl") { promise: Promise ->
    val service = ScreenShareService.instance
    if (service != null && service.isSharingActive()) {
        val ip = getLocalIpAddress()
        if (ip != null) {
            promise.resolve("http://$ip:8080")
        } else {
            promise.reject("ERR_NO_IP", "Could not determine IP", null)
        }
    } else {
        promise.resolve(null) // Return null if not sharing
    }
}

    AsyncFunction("isAccessibilityServiceEnabled") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
      val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
      
      return@AsyncFunction enabledServices.any { 
        it.resolveInfo.serviceInfo.packageName == context.packageName && 
        it.resolveInfo.serviceInfo.name == ScreenShareService::class.java.canonicalName 
      }
    }

    Function("openAccessibilitySettings") {
      val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      appContext.currentActivity?.startActivity(intent)
    }

    AsyncFunction("startServerAsync") { promise: Promise ->
      val service = ScreenShareService.instance
      val activity = appContext.currentActivity 
      
      if (activity == null) {
          promise.reject("ERR_NO_ACTIVITY", "Activity not found", null)
          return@AsyncFunction
      }

      if (service == null) {
          promise.reject("ERR_ACCESSIBILITY", "ACCESSIBILITY_SERVICE_NOT_ENABLED", null)
          return@AsyncFunction
      }

      val ip = getLocalIpAddress()
      if (ip == null) {
          promise.reject("ERR_NO_IP", "COULD_NOT_GET_IP_ADDRESS", null)
          return@AsyncFunction
      }

      // --- CRITICAL GATEKEEPER LOGIC ---
      // If the service is already sharing, DON'T ask for permission again.
      // Just return the URL and stop here.
      if (service.isSharingActive()) {
          promise.resolve("http://$ip:8080")
          return@AsyncFunction
      }
      // ---------------------------------

      pendingIp = ip 
      pendingPromise = promise

      val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val permissionIntent = manager.createScreenCaptureIntent()
      
      activity.startActivityForResult(permissionIntent, 1001)
    }

    Function("stopScreenShare") {
      val service = ScreenShareService.instance
      if (service != null) {
        // This triggers the cleanup and the broadcast in the Service
        service.stopCaptureSession()
        return@Function true
      }
      return@Function false
    }

    OnActivityResult { _, payload ->
      if (payload.requestCode == 1001) {
          val promise = pendingPromise
          val ip = pendingIp

          if (payload.resultCode == Activity.RESULT_OK) {
              val data = payload.data
              if (data != null && ip != null) {
                  ScreenShareService.instance?.onScreenCapturePermissionGranted(data, ip)
                  promise?.resolve("http://$ip:8080")
              } else {
                  promise?.reject("ERR_DATA_NULL", "Data or IP was null", null)
              }
          } else {
              promise?.reject("ERR_USER_DENIED", "Screen capture permission was denied", null)
          }

          pendingPromise = null
          pendingIp = null
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