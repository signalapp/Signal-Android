/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import org.signal.core.models.media.Media
import org.signal.mediasend.edit.image.BrushTool
import org.signal.mediasend.edit.video.VideoTrimData

sealed interface MediaEditScreenEvent {
  data class RemoveMedia(val media: Media) : MediaEditScreenEvent
  data class FocusedMediaChanged(val media: Media) : MediaEditScreenEvent
  data class ReorderSelectedMedia(val fromIndex: Int, val toIndex: Int) : MediaEditScreenEvent
  data class AddMessageClick(val startWithEmojiKeyboard: Boolean = false) : MediaEditScreenEvent
  data object StickerClick : MediaEditScreenEvent
  data object NextClick : MediaEditScreenEvent
  data class ScheduleSendClick(val option: ScheduleSendOption) : MediaEditScreenEvent
  data object NavigateBack : MediaEditScreenEvent
  data object NavigateToGallery : MediaEditScreenEvent
  data object ToggleMediaQuality : MediaEditScreenEvent
  data class BrushWidthChanged(val tool: BrushTool, val fraction: Float) : MediaEditScreenEvent
  data object SaveMedia : MediaEditScreenEvent
  data class VideoTrimChanged(val videoTrimData: VideoTrimData, val editingComplete: Boolean) : MediaEditScreenEvent
  data class VideoSeek(val positionUs: Long, val editingComplete: Boolean) : MediaEditScreenEvent
}
