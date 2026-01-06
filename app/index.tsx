import { useTheme } from "@/constants/colors";
import * as ScreenShare from "@/modules/screen-share";
import React, { useEffect, useRef, useState } from "react";
import { ActivityIndicator, AppState, AppStateStatus, StyleSheet, ToastAndroid, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import ControlScreen from "../components/ControlScreen";
import SetupScreen from "../components/SetupScreen";
import WelcomeScreen from "../components/WelcomeScreen";

export default function Index() {
  const insets = useSafeAreaInsets();
  const theme = useTheme();

  const [isAccessibilityEnabled, setIsAccessibilityEnabled] = useState(false);
  const [serverUrl, setServerUrl] = useState<string | null>(null);
  const [isStarting, setIsStarting] = useState(false);
  const [isInitializing, setIsInitializing] = useState(true);

  // Guard to prevent multiple simultaneous native pings
  const isCheckingRef = useRef(false);

  // 1. App Startup: Check permissions and existing sessions
  useEffect(() => {
    const initialize = async () => {
      try {
        await checkPermissions();
        await rehydrateSession();
      } finally {
        setIsInitializing(false);
      }
    };
    initialize();
  }, []);

  // 2. Notification Listener
  useEffect(() => {
    const subscription = ScreenShare.addStopListener(() => {
      setServerUrl(null);
    });
    return () => subscription.remove();
  }, []);

  // 3. Re-check on App Return (Foreground)
  useEffect(() => {
    const subscription = AppState.addEventListener("change", (nextAppState: AppStateStatus) => {
      if (nextAppState === "active" && !isCheckingRef.current) {
        checkPermissions();
        rehydrateSession();
      }
    });
    return () => subscription.remove();
  }, [serverUrl]);

  const checkPermissions = async () => {
    const enabled = await ScreenShare.isAccessibilityServiceEnabled();
    setIsAccessibilityEnabled(!!enabled);
  };

  /**
   * REHYDRATE: Safely checks if a session is running and gets URL.
   * This NEVER triggers a permission dialog.
   */
  const rehydrateSession = async () => {
    if (isCheckingRef.current) return;
    isCheckingRef.current = true;
    try {
      // Check status using the non-intrusive method
      const url = await ScreenShare.getServerUrl();
      if (url) {
        setServerUrl(url);
      } else if (serverUrl) {
        // If state thinks we are sharing but native says no
        setServerUrl(null);
      }
    } catch (e) {
      console.log("Rehydration check skipped:", e);
    } finally {
      isCheckingRef.current = false;
    }
  };

  /**
   * START: Called ONLY when user taps "Start Sharing".
   * This is allowed to trigger the permission dialog.
   */
  const handleStart = async () => {
    // 1. Permission check first
    if (!isAccessibilityEnabled) {
      ToastAndroid.show("Please enable Accessibility Service", ToastAndroid.SHORT);
      return;
    }

    setIsStarting(true);
    try {
      // 2. Check if already running before starting
      const existingUrl = await ScreenShare.getServerUrl();
      if (existingUrl) {
        setServerUrl(existingUrl);
        return;
      }

      // 3. Start fresh session (triggers Android dialog)
      const url = await ScreenShare.startHttpServer();
      if (url) {
        setServerUrl(url);
      }
    } catch (e: any) {
      if (e.message?.includes("denied")) {
        ToastAndroid.show("Permission Denied", ToastAndroid.SHORT);
      }
    } finally {
      setIsStarting(false);
    }
  };

  const handleStop = async () => {
    // Optimistic UI update
    setServerUrl(null);
    try {
      await ScreenShare.stopScreenShare();
    } catch (e) {
      console.error("Stop failed", e);
    }
  };

  if (isInitializing) {
    return (
      <View style={[styles.outerContainer, { backgroundColor: theme.background, justifyContent: 'center' }]}>
        <ActivityIndicator size="large" color={theme.tint} />
      </View>
    );
  }

  return (
    <View style={[styles.outerContainer, { backgroundColor: theme.background }]}>
      <View style={[styles.innerContainer, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
        {!isAccessibilityEnabled ? (
          <WelcomeScreen />
        ) : !serverUrl ? (
          <SetupScreen onStart={handleStart} loading={isStarting} />
        ) : (
          <ControlScreen url={serverUrl} onStop={handleStop} />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  outerContainer: { flex: 1 },
  innerContainer: { flex: 1 },
});