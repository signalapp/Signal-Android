/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * This contains higher level information that would have traditionally been directly
 * set on views. (Statuses, popups, etc.), allowing us to manage this from CallViewModel
 *
 * @param callRecipientId The recipient ID of the call target (1:1 recipient, call link, or group)
 * @param callControlsChange Update to display in a CallStateUpdate component.
 * @param callStatus Status text resource to display as call status.
 * @param isDisplayingAudioToggleSheet Whether the audio toggle sheet is currently displayed. Displaying this sheet should suppress hiding the controls.
 */
data class CallScreenState(
  val callRecipientId: RecipientId = RecipientId.UNKNOWN,
  val callControlsChange: CallControlsChange? = null,
  val callStatus: String? = null,
  val isDisplayingAudioToggleSheet: Boolean = false,
  val displaySwitchCameraTooltip: Boolean = false,
  val displayVideoTooltip: Boolean = false,
  val displaySwipeToSpeakerHint: Boolean = false,
  val displayWifiToCellularPopup: Boolean = false,
  val displayAdditionalActionsPopup: Boolean = false,
  val pendingParticipantsState: PendingParticipantsState? = null,
  val isParticipantUpdatePopupEnabled: Boolean = false,
  val isCallStateUpdatePopupEnabled: Boolean = false
)
