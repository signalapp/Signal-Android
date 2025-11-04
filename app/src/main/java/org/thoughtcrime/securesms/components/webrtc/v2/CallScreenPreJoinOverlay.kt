/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.Recipient

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
  isMoreThanOneCameraAvailable: Boolean,
  isLocalVideoEnabled: Boolean,
  modifier: Modifier = Modifier,
  onNavigationClick: () -> Unit = {},
  onCallInfoClick: () -> Unit = {},
  onCameraToggleClick: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxSize()
      .background(color = Color(0f, 0f, 0f, 0.4f))
      .then(modifier)
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

    if (!isLocalVideoEnabled) {
      Spacer(modifier = Modifier.weight(1f))

      Icon(
        painter = painterResource(
          id = R.drawable.symbol_video_slash_24
        ),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.padding(bottom = 8.dp)
      )

      Text(
        text = stringResource(id = R.string.CallScreenPreJoinOverlay__your_camera_is_off),
        color = Color.White
      )

      Spacer(modifier = Modifier.weight(1f))
    }

    if (isLocalVideoEnabled && isMoreThanOneCameraAvailable) {
      Spacer(modifier = Modifier.weight(1f))

      Box(modifier = Modifier.fillMaxWidth()) {
        CallCameraDirectionToggle(
          onClick = onCameraToggleClick,
          modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
      }
    }
  }
}

@Composable
private fun CallCameraDirectionToggle(
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

@NightPreview
@Composable
fun CallScreenPreJoinOverlayPreview() {
  Previews.Preview {
    CallScreenPreJoinOverlay(
      callRecipient = Recipient(systemContactName = "Test User"),
      callStatus = stringResource(R.string.Recipient_unknown),
      isLocalVideoEnabled = false,
      isMoreThanOneCameraAvailable = false
    )
  }
}

@NightPreview
@Composable
fun CallScreenPreJoinOverlayWithTogglePreview() {
  Previews.Preview {
    CallScreenPreJoinOverlay(
      callRecipient = Recipient(systemContactName = "Test User"),
      callStatus = stringResource(R.string.Recipient_unknown),
      isLocalVideoEnabled = true,
      isMoreThanOneCameraAvailable = true
    )
  }
}
