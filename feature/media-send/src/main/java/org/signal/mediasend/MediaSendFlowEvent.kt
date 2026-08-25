/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.mediasend.screens.edit.ScheduleSendOption
import org.signal.mediasend.screens.edit.image.BrushTool
import org.signal.mediasend.screens.edit.video.VideoTrimData
import kotlin.time.Duration

/**
 * Changes to the flow itself, raised by the screens within it. A screen owns what only it renders; the selection, the
 * back stack and the snackbar belong to the flow, so a screen asks for those through [MediaSendFlowViewModel.onEvent].
 */
internal sealed interface MediaSendFlowEvent {
  data class AddMedia(val media: Set<Media>) : MediaSendFlowEvent
  data class RemoveMedia(val media: Set<Media>) : MediaSendFlowEvent
  data class SetFocusedMedia(val media: Media) : MediaSendFlowEvent
  data class ReorderSelectedMedia(val fromIndex: Int, val toIndex: Int) : MediaSendFlowEvent
  data class ShowSnackbar(val snackbar: SnackbarEvent) : MediaSendFlowEvent

  /** Whoever was mid-gesture has stopped, so [MediaSendFlowState.isSelectionRejected] has served its purpose. */
  data object SelectionRejectionShown : MediaSendFlowEvent

  /**
   * Media the camera captured and wrote out, which joins the selection and takes the user on to the editor.
   *
   * @param recordingDuration How long the recording ran, or null for a capture that was not recorded.
   */
  data class MediaCaptured(val media: Media, val recordingDuration: Duration? = null) : MediaSendFlowEvent

  /** Data read from a QR code. Every outcome of acting on it leaves the flow, so none of it is a screen's to handle. */
  data class QrCodeScanned(val data: String) : MediaSendFlowEvent

  /** The user asked to leave, which is confirmed first if it would throw a selection away. */
  data object CloseRequested : MediaSendFlowEvent

  //region Edits, which the flow holds so that they survive the editor being swiped away

  data class SetMediaQuality(val quality: SentMediaQuality) : MediaSendFlowEvent
  data class SetBrushWidth(val tool: BrushTool, val fraction: Float) : MediaSendFlowEvent
  data class SetBlurFacesEnabled(val enabled: Boolean) : MediaSendFlowEvent
  data class VideoTrimChanged(val videoTrimData: VideoTrimData, val editingComplete: Boolean) : MediaSendFlowEvent
  data object ToggleViewOnce : MediaSendFlowEvent
  data object ToggleVideoMuted : MediaSendFlowEvent

  //endregion

  //region Requests the flow can only answer by leaving it, through a [MediaSendFlowHudCommand]

  data class AddMessageRequested(val startWithEmojiKeyboard: Boolean) : MediaSendFlowEvent
  data class ScheduleSendRequested(val option: ScheduleSendOption) : MediaSendFlowEvent
  data object StickerRequested : MediaSendFlowEvent

  //endregion

  /** The user is done editing: on to choosing a destination, or straight into the send. */
  data object NextRequested : MediaSendFlowEvent

  data class NavigateToFiles(val mediaFolder: MediaFolder) : MediaSendFlowEvent
  data object NavigateToFolders : MediaSendFlowEvent
  data object NavigateToEdit : MediaSendFlowEvent
  data object NavigateToCamera : MediaSendFlowEvent
  data object NavigateToTextStory : MediaSendFlowEvent

  // Backing out lands somewhere different depending on the screen doing it, so each says which it is.
  data object NavigateBackFromSelect : MediaSendFlowEvent
  data object NavigateBackFromEdit : MediaSendFlowEvent
}
