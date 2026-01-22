/*
 * Copyright 2024 Signal Messenger, LLC
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.window.isWidthCompact
import kotlin.math.max

/**
 * Pre-join call screen overlay.
 *
 * This overlay is displayed when the user has entered the calling screen but has not yet joined a call, and is
 * positioned above the controls sheet.
 */
@Composable
fun CallScreenPreJoinOverlay(
  callRecipient: Recipient,
  callStatus: String?,
  localParticipant: CallParticipant,
  isMoreThanOneCameraAvailable: Boolean,
  isLocalVideoEnabled: Boolean,
  bottomSheetPadding: Dp = 0.dp,
  modifier: Modifier = Modifier,
  onNavigationClick: () -> Unit = {},
  onCallInfoClick: () -> Unit = {},
  onCameraToggleClick: () -> Unit = {}
) {
  val showCameraToggle = isLocalVideoEnabled && isMoreThanOneCameraAvailable
  val showInfoCard = callRecipient.isCallLink

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(color = Color(0f, 0f, 0f, 0.4f))
      .then(modifier)
  ) {
    val isCompactWidth = currentWindowAdaptiveInfo().windowSizeClass.isWidthCompact

    if (!isLocalVideoEnabled) {
      TopWithCenteredContentLayout(
        topSlot = {
          PreJoinHeader(
            callRecipient = callRecipient,
            callStatus = callStatus,
            onNavigationClick = onNavigationClick,
            onCallInfoClick = onCallInfoClick
          )
        },
        centerSlot = {
          if (isCompactWidth) {
            YourCameraIsOff(spacedBy = 8.dp)
          } else {
            YourCameraIsOffLandscape()
          }
        },
        modifier = Modifier
          .fillMaxSize()
      )
    } else {
      PreJoinHeader(
        callRecipient = callRecipient,
        callStatus = callStatus,
        onNavigationClick = onNavigationClick,
        onCallInfoClick = onCallInfoClick
      )
    }

    // Bottom controls in a separate layer for proper screen-edge positioning
    if (showCameraToggle || showInfoCard || isLocalVideoEnabled) {
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
                ParticipantAudioIndicator(
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
          if (showInfoCard) {
            CallLinkInfoCard(
              modifier = Modifier.layoutId(BottomControlsLayoutId.BAR)
            )
          }
        }
      )
    }
  }
}

@Composable
private fun PreJoinHeader(
  callRecipient: Recipient,
  callStatus: String?,
  onNavigationClick: () -> Unit,
  onCallInfoClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    CallScreenTopAppBar(
      onNavigationClick = onNavigationClick,
      onCallInfoClick = onCallInfoClick
    )

    AvatarImage(
      recipient = callRecipient,
      modifier = Modifier
        .padding(top = 8.dp)
        .size(96.dp)
    )

    Text(
      text = callRecipient.getDisplayName(LocalContext.current),
      style = MaterialTheme.typography.headlineMedium,
      color = Color.White,
      modifier = Modifier.padding(top = 16.dp)
    )

    if (callStatus != null) {
      Text(
        text = callStatus,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        modifier = Modifier.padding(top = 8.dp)
      )
    }
  }
}

private object TopWithCenteredContentLayoutId {
  const val TOP = "top"
  const val CENTER = "center"
}

/**
 * A layout that places content at the top and centers other content in the viewport.
 * If the centered content would overlap with the top content, it is pushed down to stay below.
 */
@Composable
private fun TopWithCenteredContentLayout(
  topSlot: @Composable () -> Unit,
  centerSlot: @Composable () -> Unit,
  modifier: Modifier = Modifier
) {
  Layout(
    content = {
      Box(modifier = Modifier.layoutId(TopWithCenteredContentLayoutId.TOP)) { topSlot() }
      Box(modifier = Modifier.layoutId(TopWithCenteredContentLayoutId.CENTER)) { centerSlot() }
    },
    modifier = modifier
  ) { measurables, constraints ->
    val looseConstraints = constraints.copy(minHeight = 0, minWidth = 0)
    val topPlaceable = measurables.first { it.layoutId == TopWithCenteredContentLayoutId.TOP }.measure(looseConstraints)
    val centerPlaceable = measurables.first { it.layoutId == TopWithCenteredContentLayoutId.CENTER }.measure(looseConstraints)

    val layoutHeight = if (constraints.hasBoundedHeight) {
      constraints.maxHeight
    } else {
      topPlaceable.height + centerPlaceable.height
    }

    layout(constraints.maxWidth, layoutHeight) {
      topPlaceable.placeRelative(
        x = (constraints.maxWidth - topPlaceable.width) / 2,
        y = 0
      )

      val viewportCenterY = (layoutHeight - centerPlaceable.height) / 2
      val minY = topPlaceable.height
      centerPlaceable.placeRelative(
        x = (constraints.maxWidth - centerPlaceable.width) / 2,
        y = max(viewportCenterY, minY)
      )
    }
  }
}

@Composable
private fun CallLinkInfoCard(
  modifier: Modifier = Modifier
) {
  val isPhoneNumberSharingEnabled: Boolean by if (LocalInspectionMode.current) {
    remember { mutableStateOf(false) }
  } else {
    rememberRecipientField(Recipient.self()) { phoneNumberSharing.enabled }
  }

  val text = if (isPhoneNumberSharingEnabled) {
    stringResource(R.string.WebRtcCallView__anyone_who_joins_pnp_disabled)
  } else {
    stringResource(R.string.WebRtcCallView__anyone_who_joins_pnp_enabled)
  }

  Box(
    modifier = modifier
      .background(
        color = SignalTheme.colors.colorSurface1,
        shape = MaterialTheme.shapes.medium
      )
      .padding(horizontal = 16.dp, vertical = 13.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
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
      painter = painterResource(
        id = R.drawable.symbol_video_slash_24
      ),
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
      painter = painterResource(
        id = R.drawable.symbol_video_slash_24
      ),
      contentDescription = null,
      tint = Color.White
    )

    Text(
      text = stringResource(id = R.string.CallScreenPreJoinOverlay__your_camera_is_off),
      color = Color.White
    )
  }
}

@Composable
internal fun CallCameraDirectionToggle(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  IconButton(
    onClick = onClick,
    colors = IconButtonDefaults.filledIconButtonColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = Color.White
    ),
    shape = CircleShape,
    modifier = modifier.size(48.dp)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_switch_24),
      contentDescription = stringResource(R.string.CallScreen__change_camera_direction)
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreenTopAppBar(
  callRecipient: Recipient? = null,
  callStatus: String? = null,
  onNavigationClick: () -> Unit = {},
  onCallInfoClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val textShadow = remember {
    Shadow(
      color = Color(0f, 0f, 0f, 0.25f),
      blurRadius = 4f
    )
  }

  TopAppBar(
    modifier = modifier,
    colors = TopAppBarDefaults.topAppBarColors().copy(
      containerColor = Color.Transparent
    ),
    title = {
      Column {
        if (callRecipient != null) {
          Text(
            text = callRecipient.getDisplayName(LocalContext.current),
            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow)
          )
        }

        if (callStatus != null) {
          Text(
            text = callStatus,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
            modifier = Modifier.padding(top = 2.dp)
          )
        }
      }
    },
    navigationIcon = {
      IconButton(
        onClick = onNavigationClick
      ) {
        Icon(
          painter = painterResource(id = R.drawable.symbol_arrow_start_24),
          contentDescription = stringResource(id = R.string.CallScreenTopBar__go_back),
          tint = Color.White
        )
      }
    },
    actions = {
      IconButton(
        onClick = onCallInfoClick,
        modifier = Modifier.padding(16.dp)
      ) {
        Icon(
          painter = painterResource(id = R.drawable.symbol_info_24),
          contentDescription = stringResource(id = R.string.CallScreenTopBar__call_information),
          tint = Color.White
        )
      }
    }
  )
}

@NightPreview
@Composable
fun CallScreenTopBarPreview() {
  Previews.Preview {
    CallScreenTopBar(
      callRecipient = Recipient(systemContactName = "Test User"),
      callStatus = null
    )
  }
}

@AllNightPreviews
@Composable
fun CallScreenPreJoinOverlayPreview() {
  Previews.Preview {
    CallScreenPreJoinOverlay(
      callRecipient = Recipient(systemContactName = "Test User"),
      callStatus = stringResource(R.string.Recipient_unknown),
      localParticipant = CallParticipant.EMPTY,
      isLocalVideoEnabled = false,
      isMoreThanOneCameraAvailable = false
    )
  }
}

@AllNightPreviews
@Composable
fun CallScreenPreJoinOverlayWithTogglePreview() {
  Previews.Preview {
    CallScreenPreJoinOverlay(
      callRecipient = Recipient(systemContactName = "Test User"),
      callStatus = stringResource(R.string.Recipient_unknown),
      localParticipant = CallParticipant.EMPTY.copy(
        isVideoEnabled = true,
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      ),
      isLocalVideoEnabled = true,
      isMoreThanOneCameraAvailable = true
    )
  }
}

@AllNightPreviews
@Composable
fun CallScreenPreJoinOverlayWithCallLinkPreview() {
  Previews.Preview {
    CallScreenPreJoinOverlay(
      callRecipient = Recipient(systemContactName = "Test User", callLinkRoomId = CallLinkRoomId.fromBytes(byteArrayOf(1, 2, 3))),
      callStatus = stringResource(R.string.Recipient_unknown),
      localParticipant = CallParticipant.EMPTY.copy(
        isVideoEnabled = true,
        isMicrophoneEnabled = true,
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      ),
      isLocalVideoEnabled = true,
      isMoreThanOneCameraAvailable = true
    )
  }
}
