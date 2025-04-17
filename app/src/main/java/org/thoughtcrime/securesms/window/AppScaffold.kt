/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.ExperimentalWindowCoreApi
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.main.MainFloatingActionButtonsCallback
import org.thoughtcrime.securesms.main.MainNavigationBar
import org.thoughtcrime.securesms.main.MainNavigationRail
import org.thoughtcrime.securesms.main.MainNavigationState
import org.thoughtcrime.securesms.util.RemoteConfig

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

  fun isSplitPane(): Boolean {
    return if (SignalStore.internal.largeScreenUi && SignalStore.internal.forceSplitPaneOnCompactLandscape) {
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

    fun isForcedCompact(): Boolean {
      return !SignalStore.internal.largeScreenUi
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
          when (windowSizeClass.windowHeightSizeClass) {
            WindowHeightSizeClass.COMPACT -> COMPACT_LANDSCAPE
            WindowHeightSizeClass.MEDIUM -> MEDIUM_LANDSCAPE
            WindowHeightSizeClass.EXPANDED -> EXTENDED_LANDSCAPE
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

  NavigableListDetailPaneScaffold(
    navigator = navigator,
    listPane = {
      AnimatedPane {
        ListAndNavigation(
          listContent = listContent,
          navRailContent = navRailContent,
          bottomNavContent = bottomNavContent,
          windowSizeClass = windowSizeClass
        )
      }
    },
    detailPane = {
      AnimatedPane {
        detailContent()
      }
    },
    paneExpansionDragHandle = paneExpansionDragHandle,
    paneExpansionState = rememberPaneExpansionState()
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
@Preview(device = "spec:width=360dp,height=640dp,orientation=portrait")
@Preview(device = "spec:width=640dp,height=360dp,orientation=landscape")
@Preview(device = "spec:width=600dp,height=1024dp,orientation=portrait")
@Preview(device = "spec:width=1024dp,height=600dp,orientation=landscape")
@Preview(device = "spec:width=840dp,height=1280dp,orientation=portrait")
@Preview(device = "spec:width=1280dp,height=840dp,orientation=landscape")
@Composable
private fun AppScaffoldPreview() {
  Previews.Preview {
    AppScaffold(
      navigator = rememberListDetailPaneScaffoldNavigator<Any>(
        scaffoldDirective = calculatePaneScaffoldDirective(
          currentWindowAdaptiveInfo()
        ).copy(
          horizontalPartitionSpacerSize = 10.dp
        )
      ),
      listContent = {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red)
        ) {
          Text(
            text = "ListContent",
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
      }
    )
  }
}
