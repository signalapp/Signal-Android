/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.net.Uri
import org.signal.core.models.media.Media
import org.signal.mediasend.edit.MediaEditScreenCallback
import org.signal.mediasend.select.MediaSelectScreenCallback

/**
 * Interface for communicating user intent back up to the view-model.
 */
interface MediaSendCallback : MediaEditScreenCallback, MediaSelectScreenCallback {

  /** Called when the user navigates to a different position. */
  fun onPageChanged(position: Int) {}

  /** Called when the user edits video trim data. */
  fun onVideoEdited(uri: Uri, isEdited: Boolean) {}

  /** Called when message text changes. */
  fun onMessageChanged(text: CharSequence?) {}

  object Empty : MediaSendCallback, MediaEditScreenCallback by MediaEditScreenCallback.Empty, MediaSelectScreenCallback by MediaSelectScreenCallback.Empty {
    override fun setFocusedMedia(media: Media) = Unit
  }
}

/**
 * Commands sent from the ViewModel to the UI layer (HUD).
 *
 * These are one-shot events that don't belong in persistent state.
 */
sealed interface HudCommand {
  /** Start camera capture flow. */
  data object StartCamera : HudCommand

  /** Open the media selector/gallery. */
  data object OpenGallery : HudCommand

  /** Resume previously paused video. */
  data object ResumeVideo : HudCommand

  /** Show a transient error message. */
  data class ShowError(val message: String) : HudCommand
}
