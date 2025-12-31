import React, { useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Button,
  Clipboard,
  StyleSheet,
  Text,
  TouchableOpacity,
  View
} from "react-native";
import * as ScreenShare from "../modules/screen-share";

export default function Index() {
  const [serverUrl, setServerUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const handleStartServer = async () => {
    setLoading(true);
    setError(null);

    try {
      // This now returns the string from getLocalIpAddress() in your Kotlin code
      const url = await ScreenShare.startHttpServer();
      setServerUrl(url);
    } catch (e: any) {
      if (e.message.includes("ACCESSIBILITY_SERVICE_NOT_ENABLED")) {
        setError("Accessibility Service is not enabled.");
        Alert.alert(
          "Service Disabled",
          "Please enable the ScreenShare service in Android Accessibility settings."
        );
      } else {
        setError("An unexpected error occurred.");
        console.error(e);
      }
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = () => {
    if (serverUrl) {
      Clipboard.setString(serverUrl);
      Alert.alert("Copied!", "URL copied to clipboard.");
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.header}>ScreenShare Control</Text>

      {/* Server Status Card */}
      <View style={[styles.card, serverUrl ? styles.cardSuccess : styles.cardInactive]}>
        <Text style={styles.label}>Server Status:</Text>
        <Text style={styles.statusText}>
          {serverUrl ? "RUNNING" : "STOPPED"}
        </Text>
        
        {serverUrl && (
          <>
            <Text style={styles.addressLabel}>Access the server at:</Text>
            <TouchableOpacity onPress={copyToClipboard}>
              <Text style={styles.urlText}>{serverUrl}</Text>
              <Text style={styles.copyHint}>(Tap to copy)</Text>
            </TouchableOpacity>
          </>
        )}
      </View>

      {error && <Text style={styles.errorText}>{error}</Text>}

      <View style={styles.buttonContainer}>
        {loading ? (
          <ActivityIndicator size="large" color="#4630EB" />
        ) : (
          <Button 
            title={serverUrl ? "Restart Server" : "Start Server"} 
            onPress={handleStartServer} 
            color="#4630EB"
          />
        )}
      </View>

      <Text style={styles.footer}>
        Note: Your PC and Phone must be on the same Wi-Fi network.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F2F2F7",
    padding: 24,
    justifyContent: "center",
  },
  header: {
    fontSize: 28,
    fontWeight: "bold",
    textAlign: "center",
    marginBottom: 40,
    color: "#1C1C1E",
  },
  card: {
    padding: 20,
    borderRadius: 16,
    backgroundColor: "#FFF",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 4,
    alignItems: "center",
  },
  cardSuccess: {
    borderLeftWidth: 6,
    borderLeftColor: "#34C759",
  },
  cardInactive: {
    borderLeftWidth: 6,
    borderLeftColor: "#FF3B30",
  },
  label: {
    fontSize: 14,
    color: "#8E8E93",
    textTransform: "uppercase",
    letterSpacing: 1,
  },
  statusText: {
    fontSize: 22,
    fontWeight: "800",
    marginVertical: 8,
  },
  addressLabel: {
    marginTop: 16,
    fontSize: 14,
    color: "#8E8E93",
  },
  urlText: {
    fontSize: 18,
    color: "#007AFF",
    fontWeight: "600",
    marginTop: 4,
    textDecorationLine: "underline",
  },
  copyHint: {
    fontSize: 10,
    color: "#C7C7CC",
    textAlign: "center",
    marginTop: 4,
  },
  buttonContainer: {
    marginTop: 40,
  },
  errorText: {
    color: "#FF3B30",
    textAlign: "center",
    marginTop: 20,
    fontWeight: "500",
  },
  footer: {
    marginTop: 40,
    textAlign: "center",
    color: "#8E8E93",
    fontSize: 12,
  },
});