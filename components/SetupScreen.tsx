import { useTheme } from "@/constants/colors";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import React from "react";
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity, View } from "react-native";

interface SetupScreenProps {
  onStart: () => void;
  loading: boolean;
}

export default function SetupScreen({ onStart, loading }: SetupScreenProps) {
  const theme = useTheme();

  return (
    <View style={styles.container}>
      <View style={styles.topSection}>
        {/* Main Branding/Icon */}
        <View style={[styles.iconContainer, { backgroundColor: theme.card }]}>
          <MaterialCommunityIcons name="cellphone-link" size={64} color={theme.tint} />
        </View>
        <Text style={[styles.title, { color: theme.text }]}>Ready to Connect</Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          Use one of these methods to establish connection.
        </Text>
      </View>

      {/* Connection Requirement Cards */}
      <View style={styles.cardContainer}>
        <View style={[styles.card, { backgroundColor: theme.card }]}>
          <View style={[styles.badge, { backgroundColor: theme.iconBg }]}>
            <MaterialCommunityIcons name="wifi" size={20} color={theme.tint} />
          </View>
          <View style={styles.cardTextContainer}>
            <Text style={[styles.cardTitle, { color: theme.text }]}>Same Wi-Fi Network</Text>
            <Text style={[styles.cardDesc, { color: theme.textSecondary }]}>
              Connect both your devices to the same Wi-Fi.
            </Text>
          </View>
        </View>

        <View style={styles.orContainer}>
          <View style={[styles.line, { backgroundColor: theme.separator }]} />
          <Text style={[styles.orText, { color: theme.textSecondary }]}>OR</Text>
          <View style={[styles.line, { backgroundColor: theme.separator }]} />
        </View>

        <View style={[styles.card, { backgroundColor: theme.card }]}>
          <View style={[styles.badge, { backgroundColor: theme.iconBg }]}>
            <MaterialCommunityIcons name="access-point" size={20} color={theme.tint} />
          </View>
          <View style={styles.cardTextContainer}>
            <Text style={[styles.cardTitle, { color: theme.text }]}>Mobile Hotspot</Text>
            <Text style={[styles.cardDesc, { color: theme.textSecondary }]}>
              Connect your device directly to this phone's hotspot.
            </Text>
          </View>
        </View>
      </View>

      {/* Action Area */}
      <View style={styles.footer}>
        <TouchableOpacity 
          style={[styles.startButton, { backgroundColor: theme.tint }]} 
          onPress={onStart}
          disabled={loading}
          activeOpacity={0.8}
        >
          {loading ? (
            <ActivityIndicator color="#FFF" />
          ) : (
            <Text style={styles.startButtonText}>Start Sharing</Text>
          )}
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
  topSection: {
    alignItems: "center",
  },
  iconContainer: {
    width: 120,
    height: 120,
    borderRadius: 30, // iOS Squircle
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 24,
    // Soft Shadow
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.1,
    shadowRadius: 20,
    elevation: 5,
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 16,
    textAlign: "center",
    marginTop: 8,
    paddingHorizontal: 20,
  },
  cardContainer: {
    width: "100%",
  },
  card: {
    flexDirection: "row",
    padding: 20,
    borderRadius: 20,
    alignItems: "center",
  },
  badge: {
    width: 40,
    height: 40,
    borderRadius: 12,
    justifyContent: "center",
    alignItems: "center",
    marginRight: 16,
  },
  cardTextContainer: {
    flex: 1,
  },
  cardTitle: {
    fontSize: 17,
    fontWeight: "600",
    marginBottom: 2,
  },
  cardDesc: {
    fontSize: 14,
    lineHeight: 20,
  },
  orContainer: {
    flexDirection: "row",
    alignItems: "center",
    marginVertical: 16,
    paddingHorizontal: 10,
  },
  line: {
    flex: 1,
    height: 1,
  },
  orText: {
    marginHorizontal: 16,
    fontSize: 12,
    fontWeight: "700",
    textTransform: "uppercase",
    letterSpacing: 1,
  },
  footer: {
    width: "100%",
  },
  startButton: {
    width: "100%",
    height: 56,
    borderRadius: 28,
    justifyContent: "center",
    alignItems: "center",
  },
  startButtonText: {
    color: "#FFF",
    fontSize: 18,
    fontWeight: "600",
  },
});