import { requireNativeView } from 'expo';
import * as React from 'react';

import { ScreenShareViewProps } from './ScreenShare.types';

const NativeView: React.ComponentType<ScreenShareViewProps> =
  requireNativeView('ScreenShare');

export default function ScreenShareView(props: ScreenShareViewProps) {
  return <NativeView {...props} />;
}
