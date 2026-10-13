/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import android.net.Uri
import org.signal.mediasend.screens.edit.video.VideoTrimData

/**
 * Side effects that can be emitted by [VideoTrimBarPresenter] that need to be handled by the user of the component.
 * Each names its video, which may no longer be the one in focus by the time it is handled.
 */
sealed interface VideoTrimBarAction {

  /** [editingComplete] is false while the user is still dragging. */
  data class TrimChanged(val uri: Uri, val videoTrimData: VideoTrimData, val editingComplete: Boolean) : VideoTrimBarAction

  /** [editingComplete] is false while the user is still dragging. */
  data class Seek(val uri: Uri, val positionUs: Long, val editingComplete: Boolean) : VideoTrimBarAction
}
