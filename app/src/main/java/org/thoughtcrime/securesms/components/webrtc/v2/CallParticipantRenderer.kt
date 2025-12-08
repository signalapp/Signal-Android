/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.thoughtcrime.securesms.events.CallParticipant

/**
 * Displays video for the local participant or an appropriate avatar.
 */
@Composable
fun CallParticipantRenderer(
  callParticipant: CallParticipant,
  renderInPip: Boolean,
  modifier: Modifier = Modifier,
  isRaiseHandAllowed: Boolean = false,
  selfPipMode: SelfPipMode = SelfPipMode.NOT_SELF_PIP,
  onToggleCameraDirection: () -> Unit = {}
) {
  CallParticipantViewer(
    participant = callParticipant,
    renderInPip = renderInPip,
    raiseHandAllowed = isRaiseHandAllowed,
    selfPipMode = selfPipMode,
    isMoreThanOneCameraAvailable = callParticipant.cameraState.cameraCount > 1,
    onSwitchCameraClick = if (selfPipMode != SelfPipMode.NOT_SELF_PIP) {
      { onToggleCameraDirection() }
    } else {
      null
    },
    modifier = modifier
  )
}
