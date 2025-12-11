/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
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
  modifier: Modifier = Modifier
) {
  val size = rememberSelfPipSize(localRenderState)
  val isFocused = localRenderState == WebRtcLocalRenderState.FOCUSED

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .then(modifier)
      .statusBarsPadding()
      .displayCutoutPadding()
  ) {
    val orientation = LocalConfiguration.current.orientation
    val focusedSize = remember(maxWidth, maxHeight, orientation) {
      val desiredWidth = maxWidth - 32.dp
      val desiredHeight = maxHeight - 32.dp

      val aspectRatio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        16f / 9f
      } else {
        9f / 16f
      }

      val widthFromHeight = desiredHeight * aspectRatio
      val heightFromWidth = desiredWidth / aspectRatio

      if (widthFromHeight <= desiredWidth) {
        DpSize(widthFromHeight, desiredHeight)
      } else {
        DpSize(desiredWidth, heightFromWidth)
      }
    }

    val targetSize = if (isFocused) focusedSize else size.rotateForConfiguration()

    val state = remember { PictureInPictureState(initialContentSize = targetSize) }
    state.animateTo(targetSize)

    val selfPipMode = when (localRenderState) {
      WebRtcLocalRenderState.EXPANDED -> SelfPipMode.EXPANDED_SELF_PIP
      WebRtcLocalRenderState.FOCUSED -> SelfPipMode.FOCUSED_SELF_PIP
      WebRtcLocalRenderState.SMALLER_RECTANGLE -> SelfPipMode.MINI_SELF_PIP
      else -> SelfPipMode.NORMAL_SELF_PIP
    }

    val clip by animateClip(localRenderState)
    val shadow by animateShadow(localRenderState)

    val showFocusButton = localRenderState == WebRtcLocalRenderState.EXPANDED || isFocused

    PictureInPicture(
      state = state,
      isFocused = isFocused,
      modifier = Modifier
        .padding(16.dp)
        .fillMaxSize()
    ) {
      SelfPipContent(
        participant = localParticipant,
        selfPipMode = selfPipMode,
        isMoreThanOneCameraAvailable = localParticipant.cameraState.cameraCount > 1,
        onSwitchCameraClick = onToggleCameraDirectionClick,
        modifier = Modifier
          .fillMaxSize()
          .dropShadow(
            shape = RoundedCornerShape(clip),
            shadow = shadow
          )
          .clip(RoundedCornerShape(clip))
          .clickable(onClick = onClick)
      )

      AnimatedVisibility(
        visible = showFocusButton,
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
              if (isFocused) R.drawable.symbol_minimize_24 else R.drawable.symbol_maximize_24
            ),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            contentDescription = stringResource(
              if (isFocused) {
                R.string.MoveableLocalVideoRenderer__shrink_local_video
              } else {
                R.string.MoveableLocalVideoRenderer__expand_local_video
              }
            )
          )
        }
      }
    }
  }
}

@Composable
private fun animateClip(localRenderState: WebRtcLocalRenderState): State<Dp> {
  val targetDp = when (localRenderState) {
    WebRtcLocalRenderState.FOCUSED -> CallScreenMetrics.FocusedRendererCornerSize
    WebRtcLocalRenderState.EXPANDED -> CallScreenMetrics.ExpandedRendererCornerSize
    else -> CallScreenMetrics.OverflowParticipantRendererCornerSize
  }

  return animateDpAsState(targetValue = targetDp)
}

@Composable
private fun animateShadow(localRenderState: WebRtcLocalRenderState): State<Shadow> {
  val targetShadowRadius = when (localRenderState) {
    WebRtcLocalRenderState.EXPANDED, WebRtcLocalRenderState.FOCUSED, WebRtcLocalRenderState.SMALLER_RECTANGLE -> {
      14.dp
    }

    else -> {
      0.dp
    }
  }

  val targetShadowOffset = when (localRenderState) {
    WebRtcLocalRenderState.EXPANDED, WebRtcLocalRenderState.FOCUSED, WebRtcLocalRenderState.SMALLER_RECTANGLE -> {
      4.dp
    }

    else -> {
      0.dp
    }
  }

  val shadowRadius by animateDpAsState(targetShadowRadius)
  val shadowOffset by animateDpAsState(targetShadowOffset)
  return remember {
    derivedStateOf { Shadow(radius = shadowRadius, offset = DpOffset(0.dp, shadowOffset)) }
  }
}

@AllNightPreviews
@Composable
private fun MoveableLocalVideoRendererPreview() {
  var localRenderState by remember { mutableStateOf(WebRtcLocalRenderState.SMALL_RECTANGLE) }

  Previews.Preview {
    val blur by animateDpAsState(
      if (localRenderState == WebRtcLocalRenderState.FOCUSED) {
        20.dp
      } else {
        0.dp
      }
    )

    Box(modifier = Modifier.background(color = Color.Green)) {
      BlurContainer(
        isBlurred = localRenderState == WebRtcLocalRenderState.FOCUSED
      ) {
        Image(
          painter = painterResource(R.drawable.ic_add_a_profile_megaphone_image),
          contentDescription = null,
          modifier = Modifier
            .fillMaxSize()
            .blur(blur)
        )
      }

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
}

@Composable
fun rememberSelfPipSize(
  localRenderState: WebRtcLocalRenderState
): DpSize {
  val callScreenMetrics = rememberCallScreenMetrics()
  return remember(localRenderState, callScreenMetrics) {
    when (localRenderState) {
      WebRtcLocalRenderState.GONE -> DpSize.Zero
      WebRtcLocalRenderState.SMALL_RECTANGLE -> callScreenMetrics.normalRendererDpSize
      WebRtcLocalRenderState.SMALLER_RECTANGLE -> callScreenMetrics.overflowParticipantRendererDpSize
      WebRtcLocalRenderState.LARGE -> DpSize.Zero
      WebRtcLocalRenderState.LARGE_NO_VIDEO -> DpSize.Zero
      WebRtcLocalRenderState.EXPANDED -> callScreenMetrics.expandedRendererDpSize
      WebRtcLocalRenderState.FOCUSED -> DpSize.Unspecified
    }
  }
}

/**
 * Sets the proper DpSize rotation based off the window configuration.
 *
 * Call-Screen DpSizes for the movable pip are expected to be in portrait by default.
 */
@Composable
private fun DpSize.rotateForConfiguration(): DpSize {
  val orientation = LocalConfiguration.current.orientation

  return when (orientation) {
    Configuration.ORIENTATION_LANDSCAPE -> DpSize(this.height, this.width)
    else -> this
  }
}
