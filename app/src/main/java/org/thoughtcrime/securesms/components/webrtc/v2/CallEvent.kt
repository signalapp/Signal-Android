/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import org.thoughtcrime.securesms.database.model.IdentityRecord

/**
 * Replacement sealed class for WebRtcCallViewModel.Event
 */
sealed interface CallEvent {
  data object ShowVideoTooltip : CallEvent
  data object DismissVideoTooltip : CallEvent
  data object ShowWifiToCellularPopup : CallEvent
  data object ShowSwitchCameraTooltip : CallEvent
  data object DismissSwitchCameraTooltip : CallEvent
  data class StartCall(val isVideoCall: Boolean) : CallEvent
  data class ShowGroupCallSafetyNumberChange(val identityRecords: List<IdentityRecord>) : CallEvent
  data object SwitchToSpeaker : CallEvent
  data object ShowSwipeToSpeakerHint : CallEvent
}
