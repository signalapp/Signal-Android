/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.messages.calls.HangupMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * This contains higher level information that would have traditionally been directly
 * set on views. (Statuses, popups, etc.), allowing us to manage this from CallViewModel
 *
 * @param callRecipientId The recipient ID of the call target (1:1 recipient, call link, or group)
 * @param hangup Set on call termination.
 * @param callControlsChange Update to display in a CallStateUpdate component.
 * @param callStatus Status text resource to display as call status.
 * @param isDisplayingAudioToggleSheet Whether the audio toggle sheet is currently displayed. Displaying this sheet should suppress hiding the controls.
 */
data class CallScreenState(
  val callRecipientId: RecipientId = RecipientId.UNKNOWN,
  val hangup: Hangup? = null,
  val callControlsChange: CallControlsChange? = null,
  val callStatus: CallString? = null,
  val isDisplayingAudioToggleSheet: Boolean = false,
  val displaySwitchCameraTooltip: Boolean = false,
  val displayVideoTooltip: Boolean = false,
  val displaySwipeToSpeakerHint: Boolean = false,
  val displayWifiToCellularPopup: Boolean = false
) {
  data class Hangup(
    val hangupMessageType: HangupMessage.Type,
    val delay: Duration = 1.seconds
  )
}
