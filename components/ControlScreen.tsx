import { StyleSheet, Text, TouchableOpacity, View } from "react-native";

export default function ControlScreen({ url, onStop }: { url: string, onStop: () => void }) {
  return (
    <View style={styles.activeContainer}>
      <View style={styles.pulse} />
      <Text style={styles.activeTitle}>Sharing Screen</Text>
      <Text style={styles.url}>{url}</Text>
      
      <TouchableOpacity style={styles.stopBtn} onPress={onStop}>
        <Text style={styles.stopText}>End Session</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  activeContainer: { flex: 1, justifyContent: "center", alignItems: "center", backgroundColor: "#fff" },
  activeTitle: { fontSize: 22, fontWeight: "bold", color: "#FF3B30", marginBottom: 10 },
  url: { fontSize: 16, color: "#007AFF", marginBottom: 50 },
  stopBtn: { borderWidth: 2, borderColor: "#FF3B30", paddingHorizontal: 40, paddingVertical: 15, borderRadius: 12 },
  stopText: { color: "#FF3B30", fontWeight: "bold" },
  pulse: { width: 20, height: 20, borderRadius: 10, backgroundColor: "#FF3B30", marginBottom: 20 }
});