/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.mediasend.MediaSendFlowState

sealed interface MediaSelectScreenEvent {

  /** The parent flow's state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: MediaSendFlowState) : MediaSelectScreenEvent {
    // The parent's state carries the message the user is typing and every item they have picked. Only the parts this
    // screen reads are worth logging, and they are the only parts safe to.
    override fun toString(): String = "ParentStateChanged(selectedMedia=${parentState.selectedMedia.size}, isSelectionRejected=${parentState.isSelectionRejected})"
  }

  /** The screen has stopped what the refusal was meant to stop, so the flow can drop it. */
  data object SelectionRejectionShown : MediaSelectScreenEvent

  data class FolderClick(val mediaFolder: MediaFolder?) : MediaSelectScreenEvent
  data class MediaClick(val media: Media) : MediaSelectScreenEvent

  /** A run of media covered by a drag across the grid. Batched, since a drag can cross many tiles in a single frame. */
  data class MediaSelected(val media: Set<Media>) : MediaSelectScreenEvent

  /** A run of media that a drag has retracted back over, undoing its own selection. */
  data class MediaUnselected(val media: Set<Media>) : MediaSelectScreenEvent

  data class SetFocusedMedia(val media: Media) : MediaSelectScreenEvent
  data class ReorderSelectedMedia(val fromIndex: Int, val toIndex: Int) : MediaSelectScreenEvent
  data object NavigateToEdit : MediaSelectScreenEvent
  data object NavigateToCamera : MediaSelectScreenEvent

  /** Re-read the gallery and the current permission level, e.g. after coming back from app settings. */
  data object Refresh : MediaSelectScreenEvent

  /** The up-front "Allow access" ask, made when we cannot read anything at all. */
  data object RequestMediaPermissions : MediaSelectScreenEvent

  /** Re-ask while holding selected-photos access, so the user can widen what we can see. */
  data object SelectMorePhotos : MediaSelectScreenEvent
}
