import { useTheme } from "@/constants/colors";
import * as ScreenShare from "@/modules/screen-share";
import React, { useEffect, useState } from "react";
import { AppState, AppStateStatus, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

// Import our screen components
import ControlScreen from "../components/ControlScreen";
import SetupScreen from "../components/SetupScreen";
import WelcomeScreen from "../components/WelcomeScreen";

export default function Index() {
  const insets = useSafeAreaInsets();
  const theme = useTheme();
  
  const [isAccessibilityEnabled, setIsAccessibilityEnabled] = useState(false);
  const [serverUrl, setServerUrl] = useState<string | null>(null);
  const [isStarting, setIsStarting] = useState(false);

  // 1. Initial Check
  useEffect(() => {
    checkPermissions();
  }, []);

  // 2. Re-check permissions when user returns from System Settings
  useEffect(() => {
    const subscription = AppState.addEventListener("change", (nextAppState: AppStateStatus) => {
      if (nextAppState === "active") {
        checkPermissions();
      }
    });

    return () => subscription.remove();
  }, []);

  const checkPermissions = async () => {
    const enabled = await ScreenShare.isAccessibilityServiceEnabled();
    setIsAccessibilityEnabled(enabled);
  };

  const handleStart = async () => {
    setIsStarting(true);
    try {
      const url = await ScreenShare.startHttpServer();
      setServerUrl(url);
    } catch (e) {
      console.error("Failed to start server:", e);
    } finally {
      setIsStarting(false);
    }
  };

  const handleStop = async () => {
    try {
      await ScreenShare.stopHttpServer();
      setServerUrl(null);
    } catch (e) {
      console.error("Failed to stop server:", e);
    }
  };

  // --- NAVIGATION LOGIC ---
  let content;
  if (!isAccessibilityEnabled) {
    content = <WelcomeScreen onCheckPermissions={checkPermissions} />;
  } else if (!serverUrl) {
    content = <SetupScreen onStart={handleStart} loading={isStarting} />;
  } else {
    content = <ControlScreen url={serverUrl} onStop={handleStop} />;
  }

  return (
    /* Outermost view: Background color bleeds into Status/Nav bars */
    <View style={[styles.outerContainer, { backgroundColor: theme.background }]}>
      
      {/* Inner view: Respects notches/pill indicators */}
      <View style={[
        styles.innerContainer, 
        { 
          paddingTop: insets.top, 
          paddingBottom: insets.bottom,
          paddingLeft: insets.left,
          paddingRight: insets.right 
        }
      ]}>
        {content}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  outerContainer: {
    flex: 1,
  },
  innerContainer: {
    flex: 1,
  },
});