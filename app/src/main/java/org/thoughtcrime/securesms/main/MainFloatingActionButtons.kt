/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
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
      CameraButton(
        colors = IconButtonDefaults.filledTonalIconButtonColors().copy(
          containerColor = SignalTheme.colors.colorSurface1
        ),
        onClick = {
          onCameraClick(MainNavigationDestination.CHATS)
        }
      )
    }

    AnimatedContent(
      targetState = destination,
      modifier = Modifier.align(Alignment.BottomCenter),
      transitionSpec = { EnterTransition.None togetherWith ExitTransition.None }
    ) { targetState ->
      when (targetState) {
        MainNavigationDestination.CHATS -> NewChatButton(onNewChatClick)
        MainNavigationDestination.CALLS -> NewCallButton(onNewCallClick)
        MainNavigationDestination.STORIES -> CameraButton(onClick = { onCameraClick(MainNavigationDestination.STORIES) })
      }
    }
  }
}

@Composable
private fun NewChatButton(
  onClick: () -> Unit
) {
  MainFloatingActionButton(
    onClick = onClick,
    contentDescription = "",
    icon = ImageVector.vectorResource(R.drawable.symbol_edit_24)
  )
}

@Composable
private fun CameraButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors()
) {
  MainFloatingActionButton(
    onClick = onClick,
    contentDescription = "",
    icon = ImageVector.vectorResource(R.drawable.symbol_camera_24),
    colors = colors,
    modifier = modifier
  )
}

@Composable
private fun NewCallButton(
  onClick: () -> Unit
) {
  MainFloatingActionButton(
    onClick = onClick,
    contentDescription = "",
    icon = ImageVector.vectorResource(R.drawable.symbol_phone_plus_24)
  )
}

@Composable
private fun MainFloatingActionButton(
  onClick: () -> Unit,
  icon: ImageVector,
  contentDescription: String,
  modifier: Modifier = Modifier,
  colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors()
) {
  FilledTonalIconButton(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    modifier = modifier
      .size(ACTION_BUTTON_SIZE)
      .shadow(4.dp, RoundedCornerShape(18.dp)),
    enabled = true,
    colors = colors
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription
    )
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

@SignalPreview
@Composable
private fun NewChatButtonPreview() {
  Previews.Preview {
    NewChatButton(
      onClick = {}
    )
  }
}

@SignalPreview
@Composable
private fun CameraButtonPreview() {
  Previews.Preview {
    CameraButton(
      onClick = {}
    )
  }
}

@SignalPreview
@Composable
private fun NewCallButtonPreview() {
  Previews.Preview {
    NewCallButton(
      onClick = {}
    )
  }
}
