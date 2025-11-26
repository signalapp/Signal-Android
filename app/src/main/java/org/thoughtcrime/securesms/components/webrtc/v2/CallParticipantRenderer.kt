/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.CallParticipantView
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.webrtc.RendererCommon

/**
 * Displays video for the local participant or an appropriate avatar.
 */
@Composable
fun CallParticipantRenderer(
  callParticipant: CallParticipant,
  renderInPip: Boolean,
  modifier: Modifier = Modifier,
  isLocalParticipant: Boolean = false,
  isRaiseHandAllowed: Boolean = false,
  selfPipMode: CallParticipantView.SelfPipMode = CallParticipantView.SelfPipMode.NOT_SELF_PIP,
  onToggleCameraDirection: () -> Unit = {}
) {
  if (LocalInspectionMode.current) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = modifier.background(color = MaterialTheme.colorScheme.secondaryContainer)
    ) {
      Text(
        text = "${callParticipant.callParticipantId.recipientId.toLong()}",
        style = MaterialTheme.typography.titleLarge
      )
    }
  } else {
    AndroidView(
      factory = { LayoutInflater.from(it).inflate(R.layout.call_participant_item, FrameLayout(it), false) as CallParticipantView },
      modifier = modifier.fillMaxSize(),
      onRelease = { it.releaseRenderer() },
      onReset = {} // Allows reuse in lazy lists
    ) { view ->
      view.setCallParticipant(callParticipant)
      view.setMirror(isLocalParticipant && callParticipant.cameraState.activeDirection == CameraState.Direction.FRONT)
      view.setScalingType(
        if (callParticipant.isScreenSharing && !isLocalParticipant) {
          RendererCommon.ScalingType.SCALE_ASPECT_FIT
        } else {
          RendererCommon.ScalingType.SCALE_ASPECT_FILL
        }
      )
      view.setRenderInPip(renderInPip)
      view.setRaiseHandAllowed(isRaiseHandAllowed)

      if (selfPipMode != CallParticipantView.SelfPipMode.NOT_SELF_PIP) {
        view.setSelfPipMode(selfPipMode, callParticipant.cameraState.cameraCount > 1)
        view.setCameraToggleOnClickListener { onToggleCameraDirection() }
      } else {
        view.setCameraToggleOnClickListener(null)
      }
    }
  }
}
