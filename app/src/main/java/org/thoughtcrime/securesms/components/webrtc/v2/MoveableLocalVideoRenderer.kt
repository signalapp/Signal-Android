/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.CallParticipantView
import org.thoughtcrime.securesms.components.webrtc.WebRtcLocalRenderState
import org.thoughtcrime.securesms.events.CallParticipant

/**
 * Small moveable local video renderer that displays the user's video in a draggable and expandable view.
 */
@Composable
fun MoveableLocalVideoRenderer(
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  onClick: () -> Unit,
  onToggleCameraDirectionClick: () -> Unit,
  onFocusLocalParticipantClick: () -> Unit,
  modifier: Modifier = Modifier.Companion
) {
  // 1. We need to remember our small and expanded sizes based off of the call size.
  val size = remember(localRenderState) {
    when (localRenderState) {
      WebRtcLocalRenderState.GONE -> DpSize.Zero
      WebRtcLocalRenderState.SMALL_RECTANGLE -> DpSize(90.dp, 160.dp)
      WebRtcLocalRenderState.SMALLER_RECTANGLE -> DpSize(90.dp, 90.dp)
      WebRtcLocalRenderState.LARGE -> DpSize.Zero
      WebRtcLocalRenderState.LARGE_NO_VIDEO -> DpSize.Zero
      WebRtcLocalRenderState.EXPANDED -> DpSize(170.dp, 300.dp)
      WebRtcLocalRenderState.FOCUSED -> DpSize.Unspecified
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .then(modifier)
      .padding(16.dp)
      .statusBarsPadding()
  ) {
    val targetSize = size.let {
      if (it == DpSize.Unspecified) {
        DpSize(maxWidth, maxHeight)
      } else {
        it
      }
    }

    val state = remember { PictureInPictureState(initialContentSize = targetSize) }
    state.animateTo(targetSize)

    PictureInPicture(
      state = state,
      modifier = Modifier
        .fillMaxSize()
    ) {
      CallParticipantRenderer(
        callParticipant = localParticipant,
        isLocalParticipant = true,
        renderInPip = true,
        selfPipMode = if (localRenderState == WebRtcLocalRenderState.EXPANDED || localRenderState == WebRtcLocalRenderState.FOCUSED) {
          CallParticipantView.SelfPipMode.EXPANDED_SELF_PIP
        } else {
          CallParticipantView.SelfPipMode.NORMAL_SELF_PIP
        },
        onToggleCameraDirection = onToggleCameraDirectionClick,
        modifier = Modifier
          .fillMaxSize()
          .clip(MaterialTheme.shapes.medium)
          .clickable(onClick = {
            onClick()
          })
      )

      AnimatedVisibility(
        visible = localRenderState == WebRtcLocalRenderState.EXPANDED || localRenderState == WebRtcLocalRenderState.FOCUSED,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp)
          .size(48.dp)
      ) {
        IconButton(
          onClick = onFocusLocalParticipantClick,
          modifier = Modifier
            .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
        ) {
          Icon(
            imageVector = ImageVector.vectorResource(
              when (localRenderState) {
                WebRtcLocalRenderState.FOCUSED -> R.drawable.symbol_minimize_24
                else -> R.drawable.symbol_maximize_24
              }
            ),
            contentDescription = stringResource(
              when (localRenderState) {
                WebRtcLocalRenderState.FOCUSED -> R.string.MoveableLocalVideoRenderer__shrink_local_video
                else -> R.string.MoveableLocalVideoRenderer__expand_local_video
              }
            )
          )
        }
      }
    }
  }
}

@NightPreview
@Composable
fun MoveableLocalVideoRendererPreview() {
  var localRenderState by remember { mutableStateOf(WebRtcLocalRenderState.SMALL_RECTANGLE) }

  Previews.Preview {
    MoveableLocalVideoRenderer(
      localParticipant = remember {
        CallParticipant()
      },
      localRenderState = localRenderState,
      onClick = {
        localRenderState = when (localRenderState) {
          WebRtcLocalRenderState.SMALL_RECTANGLE -> {
            WebRtcLocalRenderState.EXPANDED
          }

          WebRtcLocalRenderState.EXPANDED -> {
            WebRtcLocalRenderState.SMALL_RECTANGLE
          }

          else -> localRenderState
        }
      },
      onToggleCameraDirectionClick = {},
      onFocusLocalParticipantClick = {
        localRenderState = if (localRenderState == WebRtcLocalRenderState.FOCUSED) {
          WebRtcLocalRenderState.EXPANDED
        } else {
          WebRtcLocalRenderState.FOCUSED
        }
      }
    )
  }
}
