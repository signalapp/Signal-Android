package org.thoughtcrime.securesms.components.webrtc;

import androidx.annotation.NonNull;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

public final class OrientationAwareVideoSink implements VideoSink {

  private final VideoSink delegate;

  public OrientationAwareVideoSink(@NonNull VideoSink delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onFrame(VideoFrame videoFrame) {
    if (videoFrame.getRotatedHeight() < videoFrame.getRotatedWidth()) {
      delegate.onFrame(new VideoFrame(videoFrame.getBuffer(), 270, videoFrame.getTimestampNs()));
    } else {
      delegate.onFrame(videoFrame);
    }
  }
}
