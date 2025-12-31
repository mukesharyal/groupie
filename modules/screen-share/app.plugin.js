const { withAndroidManifest } = require('@expo/config-plugins');

module.exports = function withMyAccessibilityService(config) {
  return withAndroidManifest(config, (config) => {
    const manifest = config.modResults.manifest;
    const mainApplication = manifest.application[0];

    // 1. Add required Permissions to the top-level manifest
    manifest['uses-permission'] = manifest['uses-permission'] || [];
    const permissions = [
      { '$': { 'android:name': 'android.permission.FOREGROUND_SERVICE' } },
      { '$': { 'android:name': 'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION' } }
    ];
    manifest['uses-permission'].push(...permissions);

    // 2. Add/Update the Service entry
    mainApplication.service = mainApplication.service || [];
    mainApplication.service.push({
      '$': {
        'android:name': 'expo.modules.screenshare.ScreenShareService',
        'android:permission': 'android.permission.BIND_ACCESSIBILITY_SERVICE',
        'android:exported': 'true',
        // CRITICAL: This prevents the crash during screen recording
        'android:foregroundServiceType': 'mediaProjection'
      },
      'intent-filter': [{
        'action': [{ '$': { 'android:name': 'android.accessibilityservice.AccessibilityService' } }]
      }],
      'meta-data': [{
        '$': {
          'android:name': 'android.accessibilityservice',
          'android:resource': '@xml/screen_share_config'
        }
      }]
    });

    return config;
  });
};