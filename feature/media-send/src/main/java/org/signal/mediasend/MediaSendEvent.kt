/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import org.signal.mediasend.edit.MediaEditScreenEvent
import org.signal.mediasend.select.MediaSelectScreenEvent

interface MediaSendEventHandler {
  fun onMediaSelectScreenEvent(mediaSelectScreenEvent: MediaSelectScreenEvent)
  fun onMediaEditScreenEvent(mediaEditScreenEvent: MediaEditScreenEvent)

  object Empty : MediaSendEventHandler {
    override fun onMediaSelectScreenEvent(mediaSelectScreenEvent: MediaSelectScreenEvent) = Unit
    override fun onMediaEditScreenEvent(mediaEditScreenEvent: MediaEditScreenEvent) = Unit
  }
}

/**
 * Commands sent from the ViewModel to the UI layer (HUD).
 *
 * These are one-shot events that don't belong in persistent state.
 */
sealed interface HudCommand {

  /** Show the dialog to allow the user to add a message */
  data class ShowAddAMessageDialog(
    val message: String,
    val startWithEmojiKeyboard: Boolean,
    val isViewOnceAvailable: Boolean
  ) : HudCommand
}
