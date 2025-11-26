package org.thoughtcrime.securesms.components.webrtc

enum class WebRtcLocalRenderState {
  GONE,
  SMALL_RECTANGLE,
  SMALLER_RECTANGLE,
  LARGE,
  LARGE_NO_VIDEO,
  EXPANDED,
  FOCUSED;

  val isAnySmall: Boolean
    get() = this == SMALL_RECTANGLE || this == SMALLER_RECTANGLE
}
