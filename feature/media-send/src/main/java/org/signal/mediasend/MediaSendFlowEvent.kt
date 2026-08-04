/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.os.Parcelable
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.mediasend.screens.capture.MediaCaptureScreenEvent
import org.signal.mediasend.screens.edit.MediaEditScreenEvent

interface MediaSendEventHandler {
  fun onMediaEditScreenEvent(mediaEditScreenEvent: MediaEditScreenEvent)
  fun onMediaCaptureScreenEvent(mediaCaptureScreenEvent: MediaCaptureScreenEvent)
}

/**
 * Changes to the flow itself, raised by the screens within it. A screen owns what only it renders; the selection, the
 * back stack and the snackbar belong to the flow, so a screen asks for those through [MediaSendFlowViewModel.onEvent].
 */
internal sealed interface MediaSendEvent {
  data class AddMedia(val media: Set<Media>) : MediaSendEvent
  data class RemoveMedia(val media: Set<Media>) : MediaSendEvent
  data class SetFocusedMedia(val media: Media) : MediaSendEvent
  data class ReorderSelectedMedia(val fromIndex: Int, val toIndex: Int) : MediaSendEvent
  data class ShowSnackbar(val snackbar: SnackbarEvent) : MediaSendEvent

  /** Whoever was mid-gesture has stopped, so [MediaSendFlowState.isSelectionRejected] has served its purpose. */
  data object SelectionRejectionShown : MediaSendEvent

  data class NavigateToFiles(val mediaFolder: MediaFolder) : MediaSendEvent
  data object NavigateToEdit : MediaSendEvent
  data object NavigateToCamera : MediaSendEvent
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

  /** Show the app's sticker picker. The pick is handed back via [MediaSendFlowViewModel.onStickerSelected]. */
  data object SelectSticker : HudCommand

  /**
   * Show the app's date and time picker for a scheduled send. The pick is handed back via
   * [MediaSendFlowViewModel.onScheduledSendTimeSelected].
   */
  data object PickScheduledSendTime : HudCommand

  /**
   * Clear whatever the app requires before a send can be scheduled for [scheduledTime], such as the scheduled messages
   * intro sheet or the exact alarm permission. Once cleared, the app calls
   * [MediaSendFlowViewModel.onScheduledSendConfirmed] to let the send proceed.
   */
  data class ConfirmScheduledSend(val scheduledTime: Long) : HudCommand

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
