package org.thoughtcrime.securesms.components.webrtc

/**
 * Holder class to smooth over the pre/post API 31 calls.
 *
 * @property webRtcAudioOutput audio device type, used by API 30 and below.
 * @property deviceId specific ID for a specific device. Used only by API 31+.
 */
data class WebRtcAudioDevice(val webRtcAudioOutput: WebRtcAudioOutput, val deviceId: Int?)
