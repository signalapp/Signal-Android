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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.NavigationType
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import kotlin.math.roundToInt
import org.signal.core.ui.R as CoreUiR

private val ACTION_BUTTON_SIZE = 56.dp
private val ACTION_BUTTON_SPACING = 16.dp

interface MainFloatingActionButtonsCallback {
  fun onNewChatClick()
  fun onNewCallClick()
  fun onCameraClick(destination: MainListRoute)

  object Empty : MainFloatingActionButtonsCallback {
    override fun onNewChatClick() = Unit
    override fun onNewCallClick() = Unit
    override fun onCameraClick(destination: MainListRoute) = Unit
  }
}

@Composable
fun MainFloatingActionButtons(
  destination: MainListRoute,
  callback: MainFloatingActionButtonsCallback,
  modifier: Modifier = Modifier,
  navigationType: NavigationType = NavigationType.rememberNavigationType()
) {
  val boxHeightDp = (ACTION_BUTTON_SIZE * 2 + ACTION_BUTTON_SPACING)
  val boxHeightPx = with(LocalDensity.current) {
    boxHeightDp.toPx().roundToInt()
  }

  val primaryButtonAlignment = remember(navigationType) {
    when (navigationType) {
      NavigationType.RAIL -> Alignment.TopCenter
      NavigationType.BAR -> Alignment.BottomCenter
    }
  }

  val shadowElevation: Dp = remember(navigationType) {
    when (navigationType) {
      NavigationType.RAIL -> 0.dp
      NavigationType.BAR -> 4.dp
    }
  }

  Box(
    modifier = modifier
      .padding(ACTION_BUTTON_SPACING)
      .height(boxHeightDp)
  ) {
    SecondaryActionButton(
      destination = destination,
      boxHeightPx = boxHeightPx,
      onCameraClick = callback::onCameraClick,
      elevation = shadowElevation
    )

    Box(
      modifier = Modifier.align(primaryButtonAlignment)
    ) {
      PrimaryActionButton(
        destination = destination,
        onNewChatClick = callback::onNewChatClick,
        onCameraClick = callback::onCameraClick,
        onNewCallClick = callback::onNewCallClick,
        elevation = shadowElevation
      )
    }
  }
}

@Composable
private fun BoxScope.SecondaryActionButton(
  destination: MainListRoute,
  boxHeightPx: Int,
  elevation: Dp,
  onCameraClick: (MainListRoute) -> Unit
) {
  val navigationType = NavigationType.rememberNavigationType()
  val secondaryButtonAlignment = remember(navigationType) {
    when (navigationType) {
      NavigationType.RAIL -> Alignment.BottomCenter
      NavigationType.BAR -> Alignment.TopCenter
    }
  }

  val offsetYProvider: (Int) -> Int = remember(navigationType) {
    when (navigationType) {
      NavigationType.RAIL -> {
        { it - boxHeightPx }
      }
      NavigationType.BAR -> {
        { boxHeightPx - it }
      }
    }
  }

  AnimatedVisibility(
    visible = destination == MainListRoute.Chats || destination == MainListRoute.Archive,
    modifier = Modifier.align(secondaryButtonAlignment),
    enter = slideInVertically(initialOffsetY = offsetYProvider),
    exit = slideOutVertically(targetOffsetY = offsetYProvider)
  ) {
    val animatedElevation by transition.animateDp(targetValueByState = { if (it == EnterExitState.Visible) elevation else 0.dp })

    CameraButton(
      colors = IconButtonDefaults.filledTonalIconButtonColors().copy(
        containerColor = when (navigationType) {
          NavigationType.RAIL -> MaterialTheme.colorScheme.surface
          NavigationType.BAR -> SignalTheme.colors.colorSurface2
        },
        contentColor = MaterialTheme.colorScheme.onSurface
      ),
      onClick = {
        onCameraClick(MainListRoute.Chats)
      },
      shadowElevation = animatedElevation
    )
  }
}

@Composable
private fun PrimaryActionButton(
  destination: MainListRoute,
  elevation: Dp,
  onNewChatClick: () -> Unit = {},
  onCameraClick: (MainListRoute) -> Unit = {},
  onNewCallClick: () -> Unit = {}
) {
  val onClick = remember(destination) {
    when (destination) {
      MainListRoute.Archive -> onNewChatClick
      MainListRoute.Chats -> onNewChatClick
      MainListRoute.Calls -> onNewCallClick
      MainListRoute.Stories -> {
        { onCameraClick(destination) }
      }
    }
  }

  MainFloatingActionButton(
    onClick = onClick,
    shadowElevation = elevation,
    icon = {
      AnimatedContent(destination) { targetState ->
        val (icon, contentDescriptionId) = when (targetState) {
          MainListRoute.Archive -> CoreUiR.drawable.symbol_edit_24 to R.string.conversation_list_fragment__fab_content_description
          MainListRoute.Chats -> CoreUiR.drawable.symbol_edit_24 to R.string.conversation_list_fragment__fab_content_description
          MainListRoute.Calls -> R.drawable.symbol_phone_plus_24 to R.string.CallLogFragment__start_a_new_call
          MainListRoute.Stories -> CoreUiR.drawable.symbol_camera_24 to R.string.conversation_list_fragment__open_camera_description
        }

        Icon(
          imageVector = ImageVector.vectorResource(icon),
          contentDescription = stringResource(contentDescriptionId)
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
        imageVector = SignalIcons.Camera.imageVector,
        contentDescription = stringResource(R.string.conversation_list_fragment__open_camera_description)
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

@DayNightPreviews
@Composable
private fun MainFloatingActionButtonsNavigationRailPreview() {
  var currentDestination by remember { mutableStateOf(MainListRoute.Chats) }
  val callback = remember {
    object : MainFloatingActionButtonsCallback {
      override fun onCameraClick(destination: MainListRoute) {
        currentDestination = MainListRoute.Calls
      }

      override fun onNewChatClick() {
        currentDestination = MainListRoute.Stories
      }

      override fun onNewCallClick() {
        currentDestination = MainListRoute.Chats
      }
    }
  }

  Previews.Preview {
    MainFloatingActionButtons(
      destination = currentDestination,
      callback = callback,
      navigationType = NavigationType.RAIL
    )
  }
}

@DayNightPreviews
@Composable
private fun MainFloatingActionButtonsNavigationBarPreview() {
  var currentDestination by remember { mutableStateOf(MainListRoute.Chats) }
  val callback = remember {
    object : MainFloatingActionButtonsCallback {
      override fun onCameraClick(destination: MainListRoute) {
        currentDestination = MainListRoute.Calls
      }

      override fun onNewChatClick() {
        currentDestination = MainListRoute.Stories
      }

      override fun onNewCallClick() {
        currentDestination = MainListRoute.Chats
      }
    }
  }

  Previews.Preview {
    MainFloatingActionButtons(
      destination = currentDestination,
      callback = callback,
      navigationType = NavigationType.BAR
    )
  }
}
