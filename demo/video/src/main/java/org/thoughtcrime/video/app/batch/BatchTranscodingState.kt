/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.batch

sealed class BatchTranscodingState {
  data object Idle : BatchTranscodingState()

  data class InProgress(
    val currentVideoName: String,
    val currentProfileName: String,
    val currentTranscodePercent: Int,
    val completedCount: Int,
    val totalCount: Int
  ) : BatchTranscodingState()

  data class Completed(
    val totalTranscoded: Int,
    val totalFailed: Int,
    val results: List<BatchTranscodeResult>
  ) : BatchTranscodingState()

  data class Failed(val error: String) : BatchTranscodingState()

  data object Cancelled : BatchTranscodingState()
}

data class BatchTranscodeResult(
  val videoName: String,
  val originalUri: android.net.Uri,
  val profileName: String,
  val success: Boolean,
  val error: String?,
  val originalSize: Long,
  val outputSize: Long,
  val outputUri: android.net.Uri? = null
)
