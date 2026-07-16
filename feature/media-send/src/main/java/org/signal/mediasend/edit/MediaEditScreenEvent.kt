/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import org.signal.core.models.media.Media
import org.signal.mediasend.edit.video.VideoTrimData

sealed interface MediaEditScreenEvent {
  data class FocusedMediaChanged(val media: Media) : MediaEditScreenEvent
  data class AddMessageClick(val startWithEmojiKeyboard: Boolean = false) : MediaEditScreenEvent
  data object NavigateToSend : MediaEditScreenEvent
  data object NavigateBack : MediaEditScreenEvent
  data class VideoTrimChanged(val videoTrimData: VideoTrimData, val editingComplete: Boolean) : MediaEditScreenEvent
  data class VideoSeek(val positionUs: Long, val editingComplete: Boolean) : MediaEditScreenEvent
}
