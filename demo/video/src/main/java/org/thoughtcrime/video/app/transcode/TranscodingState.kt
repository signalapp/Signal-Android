/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.net.Uri
import java.io.File

sealed class TranscodingState {
  data object Idle : TranscodingState()
  data class InProgress(val percent: Int) : TranscodingState()
  data class Completed(
    val outputUri: Uri,
    val originalFile: File,
    val originalSize: Long,
    val outputSize: Long,
    val settings: TranscodeSettings
  ) : TranscodingState()
  data class Failed(val error: String) : TranscodingState()
  data object Cancelled : TranscodingState()
}

data class TranscodeSettings(
  val isPreset: Boolean,
  val presetName: String?,
  val videoResolution: VideoResolution,
  val videoMegaBitrate: Float,
  val audioKiloBitrate: Int,
  val useHevc: Boolean,
  val enableFastStart: Boolean,
  val enableAudioRemux: Boolean
)
