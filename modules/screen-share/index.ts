import ScreenShareModule from './src/ScreenShareModule';

export async function startHttpServer(): Promise<string> {
  return await ScreenShareModule.startServerAsync();
}

export async function isAccessibilityServiceEnabled(): Promise<boolean> {
  return await ScreenShareModule.isAccessibilityServiceEnabled();
}


export function openAccessibilitySettings() {

  ScreenShareModule.openAccessibilitySettings();
}