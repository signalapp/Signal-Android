/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.thoughtcrime.securesms.components.webrtc.TextureViewRenderer
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.webrtc.RendererCommon

/**
 * Displays video for the given participant if attachVideoSink is true.
 */
@Composable
fun CallParticipantVideoRenderer(
  callParticipant: CallParticipant,
  attachVideoSink: Boolean,
  modifier: Modifier = Modifier
) {
  AndroidView(
    factory = ::TextureViewRenderer,
    modifier = modifier,
    onRelease = { it.release() }
  ) { renderer ->
    renderer.setMirror(callParticipant.cameraDirection == CameraState.Direction.FRONT)
    renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

    callParticipant.videoSink.lockableEglBase.performWithValidEglBase {
      renderer.init(it)
    }

    if (attachVideoSink) {
      renderer.attachBroadcastVideoSink(callParticipant.videoSink)
    } else {
      renderer.attachBroadcastVideoSink(null)
    }
  }
}
