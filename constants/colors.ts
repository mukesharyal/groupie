import { useColorScheme } from 'react-native';

export const Theme = {
  light: {
    background: '#F2F2F7', // iOS System Gray 6
    card: '#FFFFFF',
    text: '#000000',
    textSecondary: '#6C757D',
    tint: '#007AFF', // App Store Blue
    separator: '#C6C6C8',
    iconBg: '#F0F7FF',
  },
  dark: {
    background: '#000000',
    card: '#1C1C1E', // iOS Dark Gray
    text: '#FFFFFF',
    textSecondary: '#8E8E93',
    tint: '#0A84FF', // Brighter iOS Blue for dark mode
    separator: '#38383A',
    iconBg: '#2C2C2E',
  },
};

// A helper hook to use the theme easily
export function useTheme() {
  const scheme = useColorScheme() ?? 'light';
  return Theme[scheme];
}