/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import org.signal.core.models.media.Media

/**
 * One-shot side effects for the host of [MediaViewerScreen].
 */
sealed interface MediaViewerAction {
  data object Finish : MediaViewerAction

  /** Finish, running the shared element exit transition when still on the entry page. */
  data object FinishAfterTransition : MediaViewerAction

  data class ShowToast(@StringRes val messageResId: Int) : MediaViewerAction

  data class ShareMedia(val uri: Uri, val contentType: String?) : MediaViewerAction

  data class ShowForwardSheet(val threadId: Long, val uri: Uri, val contentType: String?) : MediaViewerAction

  data class SaveMedia(val uri: Uri, val contentType: String, val date: Long) : MediaViewerAction

  data class OpenEditor(val media: Media) : MediaViewerAction

  /** Open the conversation at the message's position and finish. */
  data class JumpToMessage(val intent: Intent) : MediaViewerAction

  data object PauseVoiceNotes : MediaViewerAction

  data object OpenStorageSettings : MediaViewerAction
}
