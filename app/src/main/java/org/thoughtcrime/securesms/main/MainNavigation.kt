/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R

private val LOTTIE_SIZE = 28.dp

enum class MainNavigationDestination(
  @StringRes val label: Int,
  @RawRes val icon: Int,
  @StringRes val contentDescription: Int = label
) {
  CHATS(
    label = R.string.ConversationListTabs__chats,
    icon = R.raw.chats_28
  ),
  CALLS(
    label = R.string.ConversationListTabs__calls,
    icon = R.raw.calls_28
  ),
  STORIES(
    label = R.string.ConversationListTabs__stories,
    icon = R.raw.stories_28
  )
}

data class MainNavigationState(
  val chatsCount: Int = 0,
  val callsCount: Int = 0,
  val storiesCount: Int = 0,
  val storyFailure: Boolean = false,
  val isStoriesFeatureEnabled: Boolean = true,
  val selectedDestination: MainNavigationDestination = MainNavigationDestination.CHATS,
  val compact: Boolean = false
)

/**
 * Chats list bottom navigation bar.
 */
@Composable
fun MainNavigationBar(
  state: MainNavigationState,
  onDestinationSelected: (MainNavigationDestination) -> Unit
) {
  NavigationBar(
    containerColor = SignalTheme.colors.colorSurface2,
    contentColor = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.height(if (state.compact) 48.dp else 80.dp)
  ) {
    val entries = remember(state.isStoriesFeatureEnabled) {
      if (state.isStoriesFeatureEnabled) {
        MainNavigationDestination.entries
      } else {
        MainNavigationDestination.entries.filterNot { it == MainNavigationDestination.STORIES }
      }
    }

    entries.forEach { destination ->

      val badgeCount = when (destination) {
        MainNavigationDestination.CHATS -> state.chatsCount
        MainNavigationDestination.CALLS -> state.callsCount
        MainNavigationDestination.STORIES -> state.storiesCount
      }

      val selected = state.selectedDestination == destination
      NavigationBarItem(
        selected = selected,
        icon = {
          NavigationDestinationIcon(
            destination = destination,
            selected = selected
          )
        },
        label = if (state.compact) null else {
          { NavigationDestinationLabel(destination) }
        },
        onClick = {
          onDestinationSelected(destination)
        },
        modifier = Modifier.drawNavigationBarBadge(count = badgeCount, compact = state.compact)
      )
    }
  }
}

/**
 * Draws badge over navigation bar item. We do this since they're required to be inside a row,
 * and things get really funky or clip weird if we try to use a normal composable.
 */
@Composable
private fun Modifier.drawNavigationBarBadge(count: Int, compact: Boolean): Modifier {
  return if (count <= 0) {
    this
  } else {
    val formatted = formatCount(count)
    val textMeasurer = rememberTextMeasurer()
    val color = colorResource(R.color.ConversationListTabs__unread)
    val textStyle = MaterialTheme.typography.labelMedium
    val textLayoutResult = remember(formatted) {
      textMeasurer.measure(formatted, textStyle)
    }

    var size by remember { mutableStateOf(IntSize.Zero) }

    val padding = with(LocalDensity.current) {
      4.dp.toPx()
    }

    val xOffsetExtra = with(LocalDensity.current) {
      4.dp.toPx()
    }

    val yOffset = with(LocalDensity.current) {
      if (compact) 6.dp.toPx() else 10.dp.toPx()
    }

    this
      .onSizeChanged {
        size = it
      }
      .drawWithContent {
        drawContent()

        val xOffset = size.width.toFloat() / 2f + xOffsetExtra

        if (size != IntSize.Zero) {
          drawRoundRect(
            color = color,
            topLeft = Offset(xOffset, yOffset),
            size = Size(textLayoutResult.size.width.toFloat() + padding * 2, textLayoutResult.size.height.toFloat()),
            cornerRadius = CornerRadius(20f, 20f)
          )

          drawText(
            textLayoutResult = textLayoutResult,
            color = Color.White,
            topLeft = Offset(xOffset + padding, yOffset)
          )
        }
      }
  }
}

/**
 * Navigation Rail for medium and large form factor devices.
 */
@Composable
fun MainNavigationRail(
  state: MainNavigationState,
  onDestinationSelected: (MainNavigationDestination) -> Unit
) {
  NavigationRail(
    containerColor = SignalTheme.colors.colorSurface1,
    header = {
      FilledTonalIconButton(
        onClick = { },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .padding(top = 56.dp, bottom = 16.dp)
          .size(56.dp),
        enabled = true,
        colors = IconButtonDefaults.filledTonalIconButtonColors()
          .copy(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onBackground
          )
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_edit_24),
          contentDescription = null
        )
      }

      FilledTonalIconButton(
        onClick = { },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .padding(bottom = 80.dp)
          .size(56.dp),
        enabled = true,
        colors = IconButtonDefaults.filledTonalIconButtonColors()
          .copy(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
          )
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_camera_24),
          contentDescription = null
        )
      }
    }
  ) {
    val entries = remember(state.isStoriesFeatureEnabled) {
      if (state.isStoriesFeatureEnabled) {
        MainNavigationDestination.entries
      } else {
        MainNavigationDestination.entries.filterNot { it == MainNavigationDestination.STORIES }
      }
    }

    entries.forEachIndexed { idx, destination ->
      val selected = state.selectedDestination == destination

      Box {
        NavigationRailItem(
          modifier = Modifier.padding(bottom = if (MainNavigationDestination.entries.lastIndex == idx) 0.dp else 16.dp),
          icon = {
            NavigationDestinationIcon(
              destination = destination,
              selected = selected
            )
          },
          label = {
            NavigationDestinationLabel(destination)
          },
          selected = selected,
          onClick = {
            onDestinationSelected(destination)
          }
        )

        NavigationRailCountIndicator(
          state = state,
          destination = destination
        )
      }
    }
  }
}

@Composable
private fun BoxScope.NavigationRailCountIndicator(
  state: MainNavigationState,
  destination: MainNavigationDestination
) {
  val count = remember(state, destination) {
    when (destination) {
      MainNavigationDestination.CHATS -> state.chatsCount
      MainNavigationDestination.CALLS -> state.callsCount
      MainNavigationDestination.STORIES -> state.storiesCount
    }
  }

  if (count > 0) {
    Box(
      modifier = Modifier
        .padding(start = 42.dp)
        .height(16.dp)
        .defaultMinSize(minWidth = 16.dp)
        .background(color = colorResource(R.color.ConversationListTabs__unread), shape = RoundedCornerShape(percent = 50))
        .align(Alignment.TopStart)
    ) {
      Text(
        text = formatCount(count),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
          .align(Alignment.Center)
          .padding(horizontal = 4.dp)
      )
    }
  }
}

@Composable
private fun NavigationDestinationIcon(
  destination: MainNavigationDestination,
  selected: Boolean
) {
  val dynamicProperties = rememberLottieDynamicProperties(
    rememberLottieDynamicProperty(
      property = LottieProperty.COLOR_FILTER,
      value = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
        MaterialTheme.colorScheme.onSurface.hashCode(),
        BlendModeCompat.SRC_ATOP
      ),
      keyPath = arrayOf("**")
    )
  )

  val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(destination.icon))
  val progress by animateFloatAsState(targetValue = if (selected) 1f else 0f, animationSpec = tween(durationMillis = composition?.duration?.toInt() ?: 0))

  LottieAnimation(
    composition = composition,
    progress = { progress },
    dynamicProperties = dynamicProperties,
    modifier = Modifier.size(LOTTIE_SIZE)
  )
}

@Composable
private fun NavigationDestinationLabel(destination: MainNavigationDestination) {
  Text(stringResource(destination.label))
}

@Composable
private fun formatCount(count: Int): String {
  if (count > 99) {
    return stringResource(R.string.ConversationListTabs__99p)
  }
  return count.toString()
}

@SignalPreview
@Composable
private fun MainNavigationRailPreview() {
  Previews.Preview {
    var selected by remember { mutableStateOf(MainNavigationDestination.CHATS) }

    MainNavigationRail(
      state = MainNavigationState(
        chatsCount = 500,
        callsCount = 10,
        storiesCount = 5,
        selectedDestination = selected
      ),
      onDestinationSelected = { selected = it }
    )
  }
}

@SignalPreview
@Composable
private fun MainNavigationBarPreview() {
  Previews.Preview {
    var selected by remember { mutableStateOf(MainNavigationDestination.CHATS) }

    MainNavigationBar(
      state = MainNavigationState(
        chatsCount = 500,
        callsCount = 10,
        storiesCount = 5,
        selectedDestination = selected,
        compact = false
      ),
      onDestinationSelected = { selected = it }
    )
  }
}
