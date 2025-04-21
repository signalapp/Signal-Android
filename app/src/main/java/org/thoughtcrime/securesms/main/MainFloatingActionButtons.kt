/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.window.Navigation
import org.thoughtcrime.securesms.window.WindowSizeClass
import kotlin.math.roundToInt

private val ACTION_BUTTON_SIZE = 56.dp
private val ACTION_BUTTON_SPACING = 16.dp

@Composable
fun MainFloatingActionButtons(
  destination: MainNavigationDestination,
  onNewChatClick: () -> Unit = {},
  onCameraClick: (MainNavigationDestination) -> Unit = {},
  onNewCallClick: () -> Unit = {}
) {
  val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()
  if (windowSizeClass.navigation == Navigation.RAIL) {
    return
  }

  val boxHeightDp = (ACTION_BUTTON_SIZE * 2 + ACTION_BUTTON_SPACING)
  val boxHeightPx = with(LocalDensity.current) {
    boxHeightDp.toPx().roundToInt()
  }

  Box(
    modifier = Modifier
      .padding(ACTION_BUTTON_SPACING)
      .height(boxHeightDp)
  ) {
    AnimatedVisibility(
      visible = destination == MainNavigationDestination.CHATS,
      modifier = Modifier.align(Alignment.TopCenter),
      enter = slideInVertically(initialOffsetY = { boxHeightPx - it }),
      exit = slideOutVertically(targetOffsetY = { boxHeightPx - it })
    ) {
      val elevation by transition.animateDp(targetValueByState = { if (it == EnterExitState.Visible) 4.dp else 0.dp })

      CameraButton(
        colors = IconButtonDefaults.filledTonalIconButtonColors().copy(
          containerColor = SignalTheme.colors.colorSurface1
        ),
        onClick = {
          onCameraClick(MainNavigationDestination.CHATS)
        },
        shadowElevation = elevation
      )
    }

    Box(
      modifier = Modifier.align(Alignment.BottomCenter)
    ) {
      PrimaryActionButton(
        destination = destination,
        onNewChatClick = onNewChatClick,
        onCameraClick = onCameraClick,
        onNewCallClick = onNewCallClick
      )
    }
  }
}

@Composable
private fun PrimaryActionButton(
  destination: MainNavigationDestination,
  onNewChatClick: () -> Unit = {},
  onCameraClick: (MainNavigationDestination) -> Unit = {},
  onNewCallClick: () -> Unit = {}
) {
  val onClick = remember(destination) {
    when (destination) {
      MainNavigationDestination.CHATS -> onNewChatClick
      MainNavigationDestination.CALLS -> onNewCallClick
      MainNavigationDestination.STORIES -> {
        { onCameraClick(destination) }
      }
    }
  }

  MainFloatingActionButton(
    onClick = onClick,
    icon = {
      AnimatedContent(destination) { targetState ->
        val icon = when (targetState) {
          MainNavigationDestination.CHATS -> R.drawable.symbol_edit_24
          MainNavigationDestination.CALLS -> R.drawable.symbol_phone_plus_24
          MainNavigationDestination.STORIES -> R.drawable.symbol_camera_24
        }

        Icon(
          imageVector = ImageVector.vectorResource(icon),
          contentDescription = ""
        )
      }
    }
  )
}

@Composable
private fun CameraButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shadowElevation: Dp = 4.dp,
  colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors()
) {
  MainFloatingActionButton(
    onClick = onClick,
    icon = {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_camera_24),
        contentDescription = ""
      )
    },
    colors = colors,
    modifier = modifier,
    shadowElevation = shadowElevation
  )
}

@Composable
private fun MainFloatingActionButton(
  onClick: () -> Unit,
  icon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  shadowElevation: Dp = 4.dp,
  colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors()
) {
  FilledTonalIconButton(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    modifier = modifier
      .size(ACTION_BUTTON_SIZE)
      .shadow(shadowElevation, RoundedCornerShape(18.dp)),
    enabled = true,
    colors = colors
  ) {
    icon()
  }
}

@SignalPreview
@Composable
private fun MainFloatingActionButtonsPreview() {
  var destination by remember { mutableStateOf(MainNavigationDestination.CHATS) }

  Previews.Preview {
    MainFloatingActionButtons(
      destination = destination,
      onCameraClick = { destination = MainNavigationDestination.CALLS },
      onNewChatClick = { destination = MainNavigationDestination.STORIES },
      onNewCallClick = { destination = MainNavigationDestination.CHATS }
    )
  }
}
