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
  data object NavigateToEdit : MediaSelectScreenEvent
}
