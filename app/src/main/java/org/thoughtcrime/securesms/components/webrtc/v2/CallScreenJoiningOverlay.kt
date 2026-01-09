/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Overlay displayed when the user has initiated joining a call but is waiting to be admitted,
 * such as when waiting to be let into a call link.
 *
 * This overlay is displayed after the pre-join stage but before the user can see other participants.
 */
@Composable
fun CallScreenJoiningOverlay(
  callRecipient: Recipient,
  callStatus: String?,
  localParticipant: CallParticipant,
  isLocalVideoEnabled: Boolean,
  isMoreThanOneCameraAvailable: Boolean,
  isWaitingToBeLetIn: Boolean,
  bottomSheetPadding: Dp = 0.dp,
  modifier: Modifier = Modifier,
  onNavigationClick: () -> Unit = {},
  onCallInfoClick: () -> Unit = {},
  onCameraToggleClick: () -> Unit = {},
  pendingParticipantsSlot: @Composable () -> Unit = {}
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .then(modifier)
  ) {
    CallScreenTopBar(
      callRecipient = callRecipient,
      callStatus = callStatus,
      onNavigationClick = onNavigationClick,
      onCallInfoClick = onCallInfoClick
    )

    if (!isLocalVideoEnabled) {
      val isCompactWidth = !currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

      if (isCompactWidth) {
        YourCameraIsOff(
          spacedBy = 8.dp,
          modifier = Modifier.align(Alignment.Center)
        )
      } else {
        YourCameraIsOffLandscape(
          modifier = Modifier.align(Alignment.Center)
        )
      }
    }

    val showCameraToggle = isLocalVideoEnabled && isMoreThanOneCameraAvailable

    BottomControlsWithOptionalBar(
      bottomSheetPadding = bottomSheetPadding,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      controlsRow = {
        if (showCameraToggle || isLocalVideoEnabled) {
          Row(
            modifier = Modifier
              .layoutId(BottomControlsLayoutId.CONTROLS)
              .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
          ) {
            if (isLocalVideoEnabled) {
              AudioIndicator(
                participant = localParticipant,
                selfPipMode = SelfPipMode.OVERLAY_SELF_PIP
              )
            }

            if (showCameraToggle) {
              CallCameraDirectionToggle(onClick = onCameraToggleClick)
            }
          }
        }
      },
      barSlot = {
        Column(
          modifier = Modifier.layoutId(BottomControlsLayoutId.BAR)
        ) {
          pendingParticipantsSlot()

          if (isWaitingToBeLetIn) {
            WaitingToBeLetInBar()
          }
        }
      }
    )
  }
}

@Composable
private fun WaitingToBeLetInBar(
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .widthIn(max = CallScreenMetrics.SheetMaxWidth)
      .fillMaxWidth()
      .background(
        color = SignalTheme.colors.colorSurface1,
        shape = MaterialTheme.shapes.medium
      )
      .padding(horizontal = 16.dp, vertical = 13.dp)
  ) {
    CircularProgressIndicator(
      color = MaterialTheme.colorScheme.onSurface,
      strokeWidth = 2.dp,
      modifier = Modifier.size(20.dp)
    )

    Text(
      text = stringResource(R.string.WebRtcCallView__waiting_to_be_let_in),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun YourCameraIsOff(
  spacedBy: Dp = 0.dp,
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
  ) {
    Icon(
      painter = painterResource(id = R.drawable.symbol_video_slash_24),
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.padding(bottom = spacedBy)
    )

    Text(
      text = stringResource(id = R.string.CallScreenPreJoinOverlay__your_camera_is_off),
      color = Color.White
    )
  }
}

@Composable
private fun YourCameraIsOffLandscape(
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
  ) {
    Icon(
      painter = painterResource(id = R.drawable.symbol_video_slash_24),
      contentDescription = null,
      tint = Color.White
    )

    Text(
      text = stringResource(id = R.string.CallScreenPreJoinOverlay__your_camera_is_off),
      color = Color.White
    )
  }
}

@AllNightPreviews
@Composable
private fun CallScreenJoiningOverlayPreview() {
  Previews.Preview {
    CallScreenJoiningOverlay(
      callRecipient = Recipient(systemContactName = "Test User"),
      callStatus = "Joining...",
      localParticipant = CallParticipant.EMPTY,
      isLocalVideoEnabled = false,
      isMoreThanOneCameraAvailable = false,
      isWaitingToBeLetIn = false
    )
  }
}

@AllNightPreviews
@Composable
private fun CallScreenJoiningOverlayWithCameraTogglePreview() {
  Previews.Preview {
    CallScreenJoiningOverlay(
      callRecipient = Recipient(systemContactName = "Test User"),
      callStatus = "Joining...",
      localParticipant = CallParticipant.EMPTY.copy(
        isVideoEnabled = true,
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      ),
      isLocalVideoEnabled = true,
      isMoreThanOneCameraAvailable = true,
      isWaitingToBeLetIn = false
    )
  }
}

@AllNightPreviews
@Composable
private fun CallScreenJoiningOverlayWaitingPreview() {
  Previews.Preview {
    CallScreenJoiningOverlay(
      callRecipient = Recipient(systemContactName = "Test User"),
      callStatus = "Waiting to be let in...",
      localParticipant = CallParticipant.EMPTY.copy(
        isVideoEnabled = true,
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      ),
      isLocalVideoEnabled = true,
      isMoreThanOneCameraAvailable = true,
      isWaitingToBeLetIn = true
    )
  }
}

@AllNightPreviews
@Composable
private fun CallScreenJoiningOverlayWaitingCameraOffPreview() {
  Previews.Preview {
    CallScreenJoiningOverlay(
      callRecipient = Recipient(systemContactName = "Test User"),
      callStatus = "Waiting to be let in...",
      localParticipant = CallParticipant.EMPTY,
      isLocalVideoEnabled = false,
      isMoreThanOneCameraAvailable = false,
      isWaitingToBeLetIn = true
    )
  }
}
