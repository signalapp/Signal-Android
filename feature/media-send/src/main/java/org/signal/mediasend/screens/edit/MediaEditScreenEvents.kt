/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import org.signal.core.models.media.Media
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.screens.edit.image.BrushTool
import org.signal.mediasend.screens.edit.video.VideoTrimData

sealed interface MediaEditScreenEvents {

  /** The parent flow's state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: MediaSendFlowState) : MediaEditScreenEvents {
    // The parent's state carries the message the user is typing and every item they have picked. Only the size of the
    // selection is worth logging, and it is the only part safe to.
    override fun toString(): String = "ParentStateChanged(selectedMedia=${parentState.selectedMedia.size})"
  }

  data class RemoveMedia(val media: Media) : MediaEditScreenEvents
  data class FocusedMediaChanged(val media: Media) : MediaEditScreenEvents
  data class ReorderSelectedMedia(val fromIndex: Int, val toIndex: Int) : MediaEditScreenEvents
  data class AddMessageClick(val startWithEmojiKeyboard: Boolean = false) : MediaEditScreenEvents
  data object StickerClick : MediaEditScreenEvents
  data object NextClick : MediaEditScreenEvents
  data class ScheduleSendClick(val option: ScheduleSendOption) : MediaEditScreenEvents
  data object NavigateBack : MediaEditScreenEvents
  data object NavigateToGallery : MediaEditScreenEvents
  data class SetMediaQuality(val quality: SentMediaQuality) : MediaEditScreenEvents
  data object ToggleViewOnce : MediaEditScreenEvents
  data class BrushWidthChanged(val tool: BrushTool, val fraction: Float) : MediaEditScreenEvents
  data class ToggleBlurFaces(val enabled: Boolean) : MediaEditScreenEvents
  data object SaveMedia : MediaEditScreenEvents
  data class VideoTrimChanged(val videoTrimData: VideoTrimData, val editingComplete: Boolean) : MediaEditScreenEvents
  data class VideoSeek(val positionUs: Long, val editingComplete: Boolean) : MediaEditScreenEvents
  data object ToggleVideoMuted : MediaEditScreenEvents
}
