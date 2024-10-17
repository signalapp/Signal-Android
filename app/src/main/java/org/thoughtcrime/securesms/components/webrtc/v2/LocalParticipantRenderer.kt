/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable
import org.thoughtcrime.securesms.components.webrtc.TextureViewRenderer
import org.thoughtcrime.securesms.components.webrtc.WebRtcLocalRenderState
import org.thoughtcrime.securesms.compose.GlideImage
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.webrtc.RendererCommon

/**
 * Displays video for the local participant or an appropriate avatar.
 */
@Composable
fun LocalParticipantRenderer(
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  modifier: Modifier = Modifier
) {
  BoxWithConstraints(
    modifier = modifier
  ) {
    val maxWidth = constraints.maxWidth
    val maxHeight = constraints.maxHeight

    val density = LocalDensity.current
    val size = with(density) {
      DpSize(
        width = maxWidth.toDp(),
        height = maxHeight.toDp()
      )
    }

    val localRecipient = if (LocalInspectionMode.current) {
      Recipient()
    } else {
      localParticipant.recipient
    }

    val model = remember {
      ProfileContactPhoto(localRecipient)
    }

    val context = LocalContext.current
    val fallback = remember {
      FallbackAvatarDrawable(context, localRecipient.getFallbackAvatar())
    }

    GlideImage(
      model = model,
      imageSize = size,
      fallback = fallback,
      modifier = Modifier.fillMaxSize()
    )

    if (localParticipant.isVideoEnabled) {
      AndroidView(
        factory = ::TextureViewRenderer,
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.release() }
      ) { renderer ->
        renderer.setMirror(localParticipant.cameraDirection == CameraState.Direction.FRONT)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

        localParticipant.videoSink.lockableEglBase.performWithValidEglBase {
          renderer.init(it)
        }

        renderer.attachBroadcastVideoSink(localParticipant.videoSink)
      }
    }
  }
}
