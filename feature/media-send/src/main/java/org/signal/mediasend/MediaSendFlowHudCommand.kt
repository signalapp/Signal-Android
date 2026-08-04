/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.os.Parcelable

/**
 * Commands sent from the ViewModel to the UI layer (HUD).
 *
 * These are one-shot events that don't belong in persistent state.
 */
sealed interface MediaSendFlowHudCommand {

  /** Show the dialog to allow the user to add a message */
  data class ShowAddAMessageDialog(
    val message: CharSequence,
    val startWithEmojiKeyboard: Boolean,
    val isViewOnceAvailable: Boolean
  ) : MediaSendFlowHudCommand

  /** Show the app's sticker picker. The pick is handed back via [MediaSendFlowViewModel.onStickerSelected]. */
  data object SelectSticker : MediaSendFlowHudCommand

  /**
   * Show the app's date and time picker for a scheduled send. The pick is handed back via
   * [MediaSendFlowViewModel.onScheduledSendTimeSelected].
   */
  data object PickScheduledSendTime : MediaSendFlowHudCommand

  /**
   * Clear whatever the app requires before a send can be scheduled for [scheduledTime], such as the scheduled messages
   * intro sheet or the exact alarm permission. Once cleared, the app calls
   * [MediaSendFlowViewModel.onScheduledSendConfirmed] to let the send proceed.
   */
  data class ConfirmScheduledSend(val scheduledTime: Long) : MediaSendFlowHudCommand

  data class GoToConversation(val recipientId: MediaRecipientId) : MediaSendFlowHudCommand
  data object GoToLinkedDevices : MediaSendFlowHudCommand
  data class GoToQuickTransfer(val qrData: String) : MediaSendFlowHudCommand
  data object CloseScreen : MediaSendFlowHudCommand

  /**
   * The send was handed off to the caller. [payload] is opaque to this module and is expected to be
   * returned to whoever launched the flow.
   */
  data class FinishWithResult(val payload: Parcelable) : MediaSendFlowHudCommand

  /**
   * The flow performed the send itself, so there is no payload. Callers still need to be told the send
   * succeeded, as opposed to [CloseScreen] which is a cancellation.
   */
  data object FinishWithoutResult : MediaSendFlowHudCommand

  /** The send was blocked by safety number changes for [untrustedRecipientIds]. */
  data class ResolveUntrustedIdentities(val untrustedRecipientIds: List<Long>) : MediaSendFlowHudCommand
}
