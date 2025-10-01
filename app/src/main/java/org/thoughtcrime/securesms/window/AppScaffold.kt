/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.window.core.ExperimentalWindowCoreApi
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.main.MainFloatingActionButtonsCallback
import org.thoughtcrime.securesms.main.MainNavigationBar
import org.thoughtcrime.securesms.main.MainNavigationRail
import org.thoughtcrime.securesms.main.MainNavigationState
import org.thoughtcrime.securesms.util.RemoteConfig
import java.lang.Integer.max

enum class Navigation {
  RAIL,
  BAR;

  companion object {
    @Composable
    fun rememberNavigation(): Navigation {
      val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()

      return remember(windowSizeClass) { windowSizeClass.navigation }
    }
  }
}

/**
 * Describes the size of screen we are displaying, and what components should be displayed.
 *
 * Screens should utilize this class by convention instead of calling [currentWindowAdaptiveInfo]
 * themselves, as this class includes checks with [RemoteConfig] to ensure we're allowed to display
 * content in different screen sizes.
 *
 * https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
 */
enum class WindowSizeClass(
  val navigation: Navigation
) {
  COMPACT_PORTRAIT(Navigation.BAR),
  COMPACT_LANDSCAPE(Navigation.BAR),
  MEDIUM_PORTRAIT(Navigation.RAIL),
  MEDIUM_LANDSCAPE(Navigation.RAIL),
  EXTENDED_PORTRAIT(Navigation.RAIL),
  EXTENDED_LANDSCAPE(Navigation.RAIL);

  fun isCompact(): Boolean = this == COMPACT_PORTRAIT || this == COMPACT_LANDSCAPE
  fun isMedium(): Boolean = this == MEDIUM_PORTRAIT || this == MEDIUM_LANDSCAPE
  fun isExtended(): Boolean = this == EXTENDED_PORTRAIT || this == EXTENDED_LANDSCAPE

  fun isLandscape(): Boolean = this == COMPACT_LANDSCAPE || this == MEDIUM_LANDSCAPE || this == EXTENDED_LANDSCAPE
  fun isPortrait(): Boolean = !isLandscape()

  fun isSplitPane(): Boolean {
    return if (isLargeScreenSupportEnabled() && SignalStore.internal.forceSplitPaneOnCompactLandscape) {
      this != COMPACT_PORTRAIT
    } else {
      this.navigation != Navigation.BAR
    }
  }

  companion object {
    @OptIn(ExperimentalWindowCoreApi::class)
    fun Resources.getWindowSizeClass(): WindowSizeClass {
      val orientation = configuration.orientation

      if (isForcedCompact()) {
        return getCompactSizeClassForOrientation(orientation)
      }

      val windowSizeClass = androidx.window.core.layout.WindowSizeClass.compute(
        displayMetrics.widthPixels,
        displayMetrics.heightPixels,
        displayMetrics.density
      )

      return getSizeClassForOrientationAndSystemSizeClass(orientation, windowSizeClass)
    }

    fun isLargeScreenSupportEnabled(): Boolean {
      return RemoteConfig.largeScreenUi && SignalStore.internal.largeScreenUi
    }

    fun isForcedCompact(): Boolean {
      return !isLargeScreenSupportEnabled()
    }

    @Composable
    fun checkForcedCompact(): Boolean {
      return !LocalInspectionMode.current && isForcedCompact()
    }

    @Composable
    fun rememberWindowSizeClass(forceCompact: Boolean = checkForcedCompact()): WindowSizeClass {
      val orientation = LocalConfiguration.current.orientation

      if (forceCompact) {
        return remember(orientation) {
          getCompactSizeClassForOrientation(orientation)
        }
      }

      val wsc = currentWindowAdaptiveInfo().windowSizeClass

      return remember(orientation, wsc) {
        getSizeClassForOrientationAndSystemSizeClass(orientation, wsc)
      }
    }

    private fun getCompactSizeClassForOrientation(orientation: Int): WindowSizeClass {
      return when (orientation) {
        Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_UNDEFINED, Configuration.ORIENTATION_SQUARE -> {
          COMPACT_PORTRAIT
        }

        Configuration.ORIENTATION_LANDSCAPE -> COMPACT_LANDSCAPE
        else -> error("Unexpected orientation: $orientation")
      }
    }

    private fun getSizeClassForOrientationAndSystemSizeClass(orientation: Int, windowSizeClass: androidx.window.core.layout.WindowSizeClass): WindowSizeClass {
      return when (orientation) {
        Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_UNDEFINED, Configuration.ORIENTATION_SQUARE -> {
          when (windowSizeClass.windowWidthSizeClass) {
            WindowWidthSizeClass.COMPACT -> COMPACT_PORTRAIT
            WindowWidthSizeClass.MEDIUM -> MEDIUM_PORTRAIT
            WindowWidthSizeClass.EXPANDED -> EXTENDED_PORTRAIT
            else -> error("Unsupported.")
          }
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
          when (windowSizeClass.windowWidthSizeClass) {
            WindowWidthSizeClass.COMPACT -> COMPACT_LANDSCAPE
            WindowWidthSizeClass.MEDIUM -> {
              if (windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT) {
                COMPACT_LANDSCAPE
              } else {
                MEDIUM_LANDSCAPE
              }
            }

            WindowWidthSizeClass.EXPANDED -> EXTENDED_LANDSCAPE
            else -> error("Unsupported.")
          }
        }

        else -> error("Unexpected orientation: $orientation")
      }
    }
  }
}

/**
 * Composable who's precise layout will depend on the window size class of the device it is being utilized on.
 * This is built to be generic so that we can use it throughout the application to support different device classes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppScaffold(
  navigator: ThreePaneScaffoldNavigator<Any> = rememberListDetailPaneScaffoldNavigator<Any>(),
  detailContent: @Composable () -> Unit = {},
  navRailContent: @Composable () -> Unit = {},
  bottomNavContent: @Composable () -> Unit = {},
  paneExpansionState: PaneExpansionState = rememberPaneExpansionState(),
  paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? = null,
  listContent: @Composable () -> Unit
) {
  val isForcedCompact = WindowSizeClass.checkForcedCompact()
  val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()

  if (isForcedCompact) {
    ListAndNavigation(
      listContent = listContent,
      navRailContent = navRailContent,
      bottomNavContent = bottomNavContent,
      windowSizeClass = windowSizeClass
    )

    return
  }

  val minPaneWidth = navigator.scaffoldDirective.defaultPanePreferredWidth

  NavigableListDetailPaneScaffold(
    navigator = navigator,
    listPane = {
      val offset by animateDp(
        targetWhenHiding = {
          (-48).dp
        },
        targetWhenShowing = {
          0.dp
        }
      )

      val alpha by animateFloat {
        1f
      }

      AnimatedPane(
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        modifier = Modifier.zIndex(0f)
      ) {
        Box(
          modifier = Modifier
            .alpha(alpha)
            .offset(x = offset)
            .clipToBounds()
            .layout { measurable, constraints ->
              val width = max(minPaneWidth.roundToPx(), constraints.maxWidth)
              val placeable = measurable.measure(
                constraints.copy(
                  minWidth = minPaneWidth.roundToPx(),
                  maxWidth = width
                )
              )
              layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(
                  x = 0,
                  y = 0
                )
              }
            }
        ) {
          ListAndNavigation(
            listContent = listContent,
            navRailContent = navRailContent,
            bottomNavContent = bottomNavContent,
            windowSizeClass = windowSizeClass
          )
        }
      }
    },
    detailPane = {
      val offset by animateDp(
        targetWhenHiding = {
          48.dp
        },
        targetWhenShowing = {
          0.dp
        }
      )

      val alpha by animateFloat {
        1f
      }

      AnimatedPane(
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        modifier = Modifier.zIndex(1f)
      ) {
        Box(
          modifier = Modifier
            .alpha(alpha)
            .offset(x = offset)
            .clipToBounds()
            .layout { measurable, constraints ->
              val width = max(minPaneWidth.roundToPx(), constraints.maxWidth)
              val placeable = measurable.measure(
                constraints.copy(
                  minWidth = minPaneWidth.roundToPx(),
                  maxWidth = width
                )
              )
              layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(
                  x = constraints.maxWidth -
                    max(constraints.maxWidth, placeable.width),
                  y = 0
                )
              }
            }
        ) {
          detailContent()
        }
      }
    },
    paneExpansionDragHandle = paneExpansionDragHandle,
    paneExpansionState = paneExpansionState
  )
}

@Composable
private fun ListAndNavigation(
  listContent: @Composable () -> Unit,
  navRailContent: @Composable () -> Unit,
  bottomNavContent: @Composable () -> Unit,
  windowSizeClass: WindowSizeClass
) {
  Row(
    modifier = if (windowSizeClass.isLandscape()) {
      Modifier.displayCutoutPadding()
    } else Modifier
  ) {
    if (windowSizeClass.navigation == Navigation.RAIL) {
      navRailContent()
    }

    Column {
      Box(modifier = Modifier.weight(1f)) {
        listContent()
      }

      if (windowSizeClass.navigation == Navigation.BAR) {
        bottomNavContent()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@AllDevicePreviews
@Composable
private fun AppScaffoldPreview() {
  Previews.Preview {
    val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()

    AppScaffold(
      navigator = rememberAppScaffoldNavigator(
        isSplitPane = windowSizeClass.navigation != Navigation.BAR,
        defaultPanePreferredWidth = 416.dp,
        horizontalPartitionSpacerSize = 16.dp
      ),
      listContent = {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red)
        ) {
          Text(
            text = "ListContent\n$windowSizeClass",
            textAlign = TextAlign.Center
          )
        }
      },
      detailContent = {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue)
        ) {
          Text(
            text = "DetailContent",
            textAlign = TextAlign.Center
          )
        }
      },
      navRailContent = {
        MainNavigationRail(
          state = MainNavigationState(),
          mainFloatingActionButtonsCallback = MainFloatingActionButtonsCallback.Empty,
          onDestinationSelected = {}
        )
      },
      bottomNavContent = {
        MainNavigationBar(
          state = MainNavigationState(),
          onDestinationSelected = {}
        )
      },
      paneExpansionState = rememberPaneExpansionState(),
      paneExpansionDragHandle = {
        AppPaneDragHandle(
          paneExpansionState = it,
          mutableInteractionSource = remember { MutableInteractionSource() }
        )
      }
    )
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldScope.AppPaneDragHandle(
  paneExpansionState: PaneExpansionState,
  mutableInteractionSource: MutableInteractionSource
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .paneExpansionDraggable(
        state = paneExpansionState,
        minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
        interactionSource = mutableInteractionSource,
        semanticsProperties = paneExpansionState.defaultDragHandleSemantics()
      )
  ) {
    Box(
      modifier = Modifier
        .size(4.dp, 48.dp)
        .background(color = Color(0xFF605F5D), RoundedCornerShape(percent = 50))
    )
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberAppScaffoldNavigator(
  isSplitPane: Boolean,
  horizontalPartitionSpacerSize: Dp,
  defaultPanePreferredWidth: Dp
): ThreePaneScaffoldNavigator<Any> {
  return rememberListDetailPaneScaffoldNavigator<Any>(
    scaffoldDirective = calculatePaneScaffoldDirective(
      currentWindowAdaptiveInfo()
    ).copy(
      maxHorizontalPartitions = if (isSplitPane) 2 else 1,
      horizontalPartitionSpacerSize = horizontalPartitionSpacerSize,
      defaultPanePreferredWidth = defaultPanePreferredWidth
    )
  )
}
