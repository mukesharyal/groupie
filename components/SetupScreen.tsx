import { ActivityIndicator, StyleSheet, Text, TouchableOpacity, View } from "react-native";

export default function SetupScreen({ onStart, loading }: { onStart: () => void, loading: boolean }) {
  return (
    <View style={styles.center}>
      <Text style={styles.title}>Ready to Share</Text>
      <Text style={styles.hint}>Ensure your PC is on the same Wi-Fi network or connected to your Hotspot.</Text>
      
      <TouchableOpacity style={styles.startBtn} onPress={onStart} disabled={loading}>
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.btnText}>Start Server</Text>}
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: "center", alignItems: "center", padding: 20 },
  title: { fontSize: 24, fontWeight: "700", marginBottom: 10 },
  hint: { color: "#8E8E93", textAlign: "center", marginBottom: 40 },
  startBtn: { backgroundColor: "#4630EB", paddingHorizontal: 60, paddingVertical: 20, borderRadius: 30 },
  btnText: { color: "#FFF", fontSize: 18, fontWeight: "600" }
});