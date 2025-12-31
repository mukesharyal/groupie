import { NativeModule, requireNativeModule } from 'expo';

// Define the interface for your module
declare class ScreenShareModule extends NativeModule {
  startServerAsync(): Promise<string>;
}

// This is the "Modern Way": It loads the module directly from JSI
export default requireNativeModule<ScreenShareModule>('ScreenShare');