package org.thoughtcrime.securesms.components.webrtc;

public enum WebRtcLocalRenderState {
  GONE,
  SMALL_RECTANGLE,
  SMALLER_RECTANGLE,
  LARGE,
  LARGE_NO_VIDEO,
  EXPANDED;

  public boolean isAnySmall() {
    return this == SMALL_RECTANGLE || this == SMALLER_RECTANGLE;
  }
}
