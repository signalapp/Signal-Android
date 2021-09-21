package org.thoughtcrime.securesms.webrtc

import org.thoughtcrime.securesms.components.webrtc.CallParticipantsState

data class CallParticipantsViewState(
  val callParticipantsState: CallParticipantsState,
  val isPortrait: Boolean,
  val isLandscapeEnabled: Boolean
)
