/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.select

import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder

sealed interface MediaSelectScreenEvent {
  data class FolderClick(val mediaFolder: MediaFolder?) : MediaSelectScreenEvent
  data class MediaClick(val media: Media) : MediaSelectScreenEvent
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
