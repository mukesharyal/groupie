import { EventSubscription } from 'expo-modules-core';
import ScreenShareModule from './src/ScreenShareModule';

export async function isAccessibilityServiceEnabled(): Promise<boolean> {
  return await ScreenShareModule.isAccessibilityServiceEnabled();
}

export function openAccessibilitySettings() {
  return ScreenShareModule.openAccessibilitySettings();
}

export async function startHttpServer(): Promise<string> {
  return await ScreenShareModule.startServerAsync();
}

export async function stopScreenShare(): Promise<boolean> {
  return await ScreenShareModule.stopScreenShare();
}

export async function isSharing(): Promise<boolean> {
  return await ScreenShareModule.isSharing();
}


// In modules/screen-share/index.ts
export async function getServerUrl(): Promise<string | null> {
  return await ScreenShareModule.getServerUrl();
}

/**
 * Modern Expo Modules way to add listeners.
 * We use the addListener method directly on the module instance.
 */
export function addStopListener(listener: () => void): EventSubscription {
  // We cast to 'any' or a specific type to bypass the 'never' type error 
  // while still using the modern underlying API.
  return (ScreenShareModule as any).addListener('onScreenShareStopped', listener);
}

