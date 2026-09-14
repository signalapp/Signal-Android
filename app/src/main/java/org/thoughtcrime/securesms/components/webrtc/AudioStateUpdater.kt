package org.thoughtcrime.securesms.components.webrtc

/**
 * This is an interface for [WebRtcAudioPicker31] as a callback for [org.thoughtcrime.securesms.components.webrtc.v2.CallAudioToggleButton]
 */
interface AudioStateUpdater {
  fun updateAudioOutputState(audioOutput: WebRtcAudioOutput)
  fun hidePicker()
}
