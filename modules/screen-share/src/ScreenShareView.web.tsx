import * as React from 'react';

import { ScreenShareViewProps } from './ScreenShare.types';

export default function ScreenShareView(props: ScreenShareViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
