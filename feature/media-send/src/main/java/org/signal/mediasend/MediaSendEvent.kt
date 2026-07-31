/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.os.Parcelable
import org.signal.mediasend.capture.MediaCaptureScreenEvent
import org.signal.mediasend.edit.MediaEditScreenEvent
import org.signal.mediasend.select.MediaSelectScreenEvent

interface MediaSendEventHandler {
  fun onMediaSelectScreenEvent(mediaSelectScreenEvent: MediaSelectScreenEvent)
  fun onMediaEditScreenEvent(mediaEditScreenEvent: MediaEditScreenEvent)
  fun onMediaCaptureScreenEvent(mediaCaptureScreenEvent: MediaCaptureScreenEvent)

  object Empty : MediaSendEventHandler {
    override fun onMediaSelectScreenEvent(mediaSelectScreenEvent: MediaSelectScreenEvent) = Unit
    override fun onMediaEditScreenEvent(mediaEditScreenEvent: MediaEditScreenEvent) = Unit
    override fun onMediaCaptureScreenEvent(mediaCaptureScreenEvent: MediaCaptureScreenEvent) = Unit
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
    val message: CharSequence,
    val startWithEmojiKeyboard: Boolean,
    val isViewOnceAvailable: Boolean
  ) : HudCommand

  /** Show the app's sticker picker. The pick is handed back via [MediaSendViewModel.onStickerSelected]. */
  data object SelectSticker : HudCommand

  data class GoToConversation(val recipientId: MediaRecipientId) : HudCommand
  data object GoToLinkedDevices : HudCommand
  data class GoToQuickTransfer(val qrData: String) : HudCommand
  data object CloseScreen : HudCommand

  /**
   * The send was handed off to the caller. [payload] is opaque to this module and is expected to be
   * returned to whoever launched the flow.
   */
  data class FinishWithResult(val payload: Parcelable) : HudCommand

  /**
   * The flow performed the send itself, so there is no payload. Callers still need to be told the send
   * succeeded, as opposed to [CloseScreen] which is a cancellation.
   */
  data object FinishWithoutResult : HudCommand

  /** The send was blocked by safety number changes for [untrustedRecipientIds]. */
  data class ResolveUntrustedIdentities(val untrustedRecipientIds: List<Long>) : HudCommand
}
