import { registerWebModule, NativeModule } from 'expo';

import { ChangeEventPayload } from './ScreenShare.types';

type ScreenShareModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
}

class ScreenShareModule extends NativeModule<ScreenShareModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
};

export default registerWebModule(ScreenShareModule, 'ScreenShareModule');
