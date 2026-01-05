import { Theme } from "@/constants/colors";
import * as NavigationBar from "expo-navigation-bar";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import { useColorScheme } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";

export default function RootLayout() {
  const scheme = useColorScheme() ?? 'light';
  const theme = Theme[scheme];

  useEffect(() => {
    // Only use the supported command to flip icon colors (black vs white)
    // The transparency is now handled by the 'edge-to-edge' default
    NavigationBar.setButtonStyleAsync(scheme === 'dark' ? 'light' : 'dark');
  }, [scheme]);

  return (
    <SafeAreaProvider style={{ backgroundColor: theme.background }}>
      {/* On modern Android, 'auto' style + translucent={true} 
        is the most reliable way to blend.
      */}
      <StatusBar 
        style={scheme === "dark" ? "light" : "dark"} 
        translucent 
      />
      
      <Stack
        screenOptions={{
          headerShown: false,
          // This ensures the background color is correct during transitions
          contentStyle: { backgroundColor: theme.background },
        }}
      >
        <Stack.Screen name="index" />
      </Stack>
    </SafeAreaProvider>
  );
}