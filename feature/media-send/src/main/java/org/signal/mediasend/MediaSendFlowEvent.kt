/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder

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

  data class NavigateToFiles(val mediaFolder: MediaFolder) : MediaSendFlowEvent
  data object NavigateToEdit : MediaSendFlowEvent
  data object NavigateToCamera : MediaSendFlowEvent
}
