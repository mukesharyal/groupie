import { useTheme } from "@/constants/colors";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import React from "react";
import { Share, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import QRCode from "react-native-qrcode-svg";

interface ControlScreenProps {
  url: string;
  onStop: () => void;
}

export default function ControlScreen({ url, onStop }: ControlScreenProps) {
  const theme = useTheme();

  const handleShare = async () => {
    try {
      await Share.share({
        message: `Connect to my screen: ${url}`,
      });
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <View style={styles.container}>
      {/* Status Header */}
      <View style={styles.header}>
        <View style={[styles.liveBadge, { backgroundColor: "#34C75920" }]}>
          <View style={styles.pulseDot} />
          <Text style={styles.liveText}>LIVE</Text>
        </View>
        <Text style={[styles.title, { color: theme.text }]}>Mirroring Active</Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          Scan the code or type the address into your browser.
        </Text>
      </View>

      {/* QR Code Section */}
      <View style={styles.qrSection}>
        <View style={[styles.qrContainer, { backgroundColor: "#FFF", borderColor: theme.separator }]}>
          <QRCode 
            value={url}
            size={200}
            color="#000"
            backgroundColor="#FFF"
            quietZone={10}
          />
        </View>
        
        {/* Address Display */}
        <TouchableOpacity 
          style={[styles.addressBar, { backgroundColor: theme.card }]} 
          onPress={handleShare}
          activeOpacity={0.7}
        >
          <Text style={[styles.urlText, { color: theme.tint }]}>{url}</Text>
          <MaterialCommunityIcons name="share-variant" size={20} color={theme.tint} />
        </TouchableOpacity>
      </View>

      {/* Footer Action */}
      <View style={styles.footer}>
        <TouchableOpacity 
          style={styles.stopButton} 
          onPress={onStop}
          activeOpacity={0.8}
        >
          <Text style={styles.stopButtonText}>End Sharing</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 24,
    paddingTop: 40,
    paddingBottom: 20,
    justifyContent: "space-between",
  },
  header: {
    alignItems: "center",
  },
  liveBadge: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    marginBottom: 16,
  },
  pulseDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: "#34C759",
    marginRight: 8,
  },
  liveText: {
    color: "#34C759",
    fontWeight: "800",
    fontSize: 12,
    letterSpacing: 1,
  },
  title: {
    fontSize: 26,
    fontWeight: "700",
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 15,
    textAlign: "center",
    marginTop: 8,
    paddingHorizontal: 30,
    lineHeight: 22,
  },
  qrSection: {
    alignItems: "center",
    width: "100%",
  },
  qrContainer: {
    padding: 16,
    borderRadius: 24,
    borderWidth: 1,
    // iOS Shadow
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.1,
    shadowRadius: 20,
    elevation: 8,
    marginBottom: 30,
  },
  addressBar: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: 16,
    gap: 12,
  },
  urlText: {
    fontSize: 17,
    fontWeight: "600",
    fontFamily: "monospace", // Makes IP addresses easier to read
  },
  footer: {
    width: "100%",
  },
  stopButton: {
    width: "100%",
    height: 56,
    borderRadius: 28,
    backgroundColor: "#FF3B30", // iOS Destructive Red
    justifyContent: "center",
    alignItems: "center",
  },
  stopButtonText: {
    color: "#FFF",
    fontSize: 18,
    fontWeight: "600",
  },
});