import { openAccessibilitySettings } from "@/modules/screen-share";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import React from "react";
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { useTheme } from "../constants/colors";

import * as Updates from 'expo-updates';

export default function WelcomeScreen() {
  const theme = useTheme();

  return (
    <ScrollView 
      contentContainerStyle={[styles.container, { backgroundColor: theme.background }]} 
      bounces={false}
    >
      {/* Header Section */}
      <View style={styles.header}>
        <View style={[styles.iconCircle, { backgroundColor: theme.card }]}>
          <MaterialCommunityIcons name="shield-check" size={60} color={theme.tint} />
        </View>
        <Text style={[styles.title, { color: theme.text }]}>Permission Required</Text>
      </View>

      {/* Feature List Card */}
      <View style={[styles.card, { backgroundColor: theme.card }]}>
        <FeatureItem 
          theme={theme}
          icon="gesture-tap" 
          title="Gesture Control" 
          desc="Tap, swipe, and scroll your device from another device." 
        />
      </View>

      {/* Disclosure Box */}
      <View style={styles.disclosureSection}>
        <Text style={[styles.disclosureText, { color: theme.text }]}>
          This app uses Android's <Text style={styles.bold}>Accessibility Service</Text>. 
          It is essential for <Text style={styles.bold}>simulating touch inputs</Text>.
          We do <Text style={styles.bold}>not</Text> collect or share any data.
        </Text>
      </View>

      {/* iOS Action Buttons */}
      <View style={styles.buttonWrapper}>
        <TouchableOpacity 
          style={[styles.primaryButton, { backgroundColor: theme.tint }]} 
          onPress={openAccessibilitySettings}
          activeOpacity={0.7}
        >
          <Text style={styles.primaryButtonText}>Enable in Settings</Text>
        </TouchableOpacity>

        <TouchableOpacity 
          style={styles.secondaryButton} 
          onPress={() => Updates.reloadAsync()}
        >
          <Text style={[styles.secondaryButtonText, { color: theme.tint }]}>
            I've already enabled it
          </Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

function FeatureItem({ icon, title, desc, theme }: any) {
  return (
    <View style={styles.featureRow}>
      <View style={[styles.featureIconBox, { backgroundColor: theme.iconBg }]}>
        <MaterialCommunityIcons name={icon} size={24} color={theme.tint} />
      </View>
      <View style={styles.featureTextColumn}>
        <Text style={[styles.featureTitle, { color: theme.text }]}>{title}</Text>
        <Text style={[styles.featureDesc, { color: theme.textSecondary }]}>{desc}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    padding: 24,
    alignItems: "center",
  },
  header: {
    alignItems: "center",
    marginTop: 60,
    marginBottom: 40,
  },
  iconCircle: {
    width: 110,
    height: 110,
    borderRadius: 28, // iOS Squircle
    justifyContent: "center",
    alignItems: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 12,
    elevation: 5,
    marginBottom: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 17,
    marginTop: 6,
  },
  card: {
    width: "100%",
    borderRadius: 14,
    padding: 16,
    marginBottom: 32,
  },
  featureRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
  },
  featureIconBox: {
    width: 44,
    height: 44,
    borderRadius: 10,
    justifyContent: "center",
    alignItems: "center",
    marginRight: 16,
  },
  featureTextColumn: {
    flex: 1,
  },
  featureTitle: {
    fontSize: 17,
    fontWeight: "600",
  },
  featureDesc: {
    fontSize: 14,
    marginTop: 2,
    lineHeight: 18,
  },
  divider: {
    height: 0.5,
    marginLeft: 60,
  },
  disclosureSection: {
    width: "100%",
    paddingHorizontal: 4,
    marginBottom: 48,
  },
  disclosureHeader: {
    fontSize: 13,
    fontWeight: "600",
    marginBottom: 8,
  },
  disclosureText: {
    fontSize: 15,
    lineHeight: 22,
  },
  bold: {
    fontWeight: "600",
  },
  buttonWrapper: {
    width: "100%",
    paddingBottom: 20,
  },
  primaryButton: {
    width: "100%",
    height: 52,
    borderRadius: 26,
    justifyContent: "center",
    alignItems: "center",
  },
  primaryButtonText: {
    color: "#FFF",
    fontSize: 17,
    fontWeight: "600",
  },
  secondaryButton: {
    marginTop: 16,
    width: "100%",
    height: 44,
    justifyContent: "center",
    alignItems: "center",
  },
  secondaryButtonText: {
    fontSize: 17,
    fontWeight: "400",
  },
});