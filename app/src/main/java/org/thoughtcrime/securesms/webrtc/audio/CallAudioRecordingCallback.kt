/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.webrtc.audio

import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import androidx.annotation.RequiresApi
import org.signal.core.util.logging.Log

/**
 * Logs every recording config change and reports when the platform starts or stops silencing our recorder.
 */
@RequiresApi(31)
internal class CallAudioRecordingCallback(
  private val tag: String,
  private val describeState: () -> String,
  private val onSilencedChanged: (Boolean) -> Unit
) : AudioManager.AudioRecordingCallback() {

  private var isMicrophoneSilenced = false

  override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
    val silenced = configs.any { it.isClientSilenced }
    if (silenced != isMicrophoneSilenced) {
      isMicrophoneSilenced = silenced
      onSilencedChanged(silenced)
    }

    if (configs.isEmpty()) {
      Log.i(tag, "AudioRecordingCallback: no active recordings ${describeState()}")
    } else {
      for (config in configs) {
        val description = "AudioRecordingCallback: silenced: ${config.isClientSilenced} source: ${CallAudioLogging.getAudioSourceName(config.audioSource)} device: ${CallAudioLogging.describeRecordingDevice(config)} ${describeState()}"
        if (config.isClientSilenced) {
          Log.w(tag, description)
        } else {
          Log.i(tag, description)
        }
      }
    }
  }
}
