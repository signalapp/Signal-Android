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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
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

  val listPaneDefaultPreferredWidth: Dp
    get() = if (isExtended()) 416.dp else 316.dp

  val detailPaneMaxContentWidth: Dp = 624.dp
  val horizontalPartitionDefaultSpacerSize: Dp = 12.dp

  fun isCompact(): Boolean = this == COMPACT_PORTRAIT || this == COMPACT_LANDSCAPE
  fun isMedium(): Boolean = this == MEDIUM_PORTRAIT || this == MEDIUM_LANDSCAPE
  fun isExtended(): Boolean = this == EXTENDED_PORTRAIT || this == EXTENDED_LANDSCAPE

  fun isLandscape(): Boolean = this == COMPACT_LANDSCAPE || this == MEDIUM_LANDSCAPE || this == EXTENDED_LANDSCAPE
  fun isPortrait(): Boolean = !isLandscape()

  @JvmOverloads
  fun isSplitPane(
    forceSplitPaneOnCompactLandscape: Boolean = SignalStore.internal.forceSplitPaneOnCompactLandscape
  ): Boolean {
    return if (isLargeScreenSupportEnabled() && forceSplitPaneOnCompactLandscape) {
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
 * A top-level scaffold that automatically adapts its layout based on the device's window size class. It is a generic container designed to handle the
 * arrangement of navigation rails, top/bottom bars, and list-detail pane management for both compact and large screens.
 *
 * @param topBarContent An optional top bar that spans across all panes.
 *
 * @param primaryContent The main content, which is typically the detail view in a split-pane layout.
 * @param secondaryContent The secondary content, which is typically the list view in a split-pane layout.
 *
 * @param navRailContent The side navigation rail, shown on medium and larger screen sizes.
 * @param bottomNavContent The bottom navigation bar, shown on compact screen sizes.
 *
 * @param paneExpansionState Manages the position and expansion of the panes in a list-detail layout.
 * @param paneExpansionDragHandle An optional drag handle used to resize panes in the list-detail layout.
 *
 * @param animatorFactory Provides animations to control how panes enter and exit the screen during navigation.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppScaffold(
  navigator: AppScaffoldNavigator<Any>,
  topBarContent: @Composable () -> Unit = {},
  primaryContent: @Composable () -> Unit = {},
  secondaryContent: @Composable () -> Unit,
  navRailContent: @Composable () -> Unit = {},
  bottomNavContent: @Composable () -> Unit = {},
  paneExpansionState: PaneExpansionState = rememberPaneExpansionState(),
  paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? = null,
  snackbarHost: @Composable () -> Unit = {},
  contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
  animatorFactory: AppScaffoldAnimationStateFactory = AppScaffoldAnimationStateFactory.Default
) {
  val isForcedCompact = WindowSizeClass.checkForcedCompact()
  val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()

  if (isForcedCompact) {
    ListAndNavigation(
      topBarContent = topBarContent,
      listContent = secondaryContent,
      navRailContent = navRailContent,
      bottomNavContent = bottomNavContent,
      windowSizeClass = windowSizeClass,
      contentWindowInsets = contentWindowInsets
    )

    return
  }

  val minPaneWidth = navigator.scaffoldDirective.defaultPanePreferredWidth
  val navigationState = navigator.state

  Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = contentWindowInsets,
    topBar = topBarContent,
    snackbarHost = snackbarHost
  ) { paddingValues ->
    NavigableListDetailPaneScaffold(
      navigator = navigator,
      listPane = {
        val animationState = with(animatorFactory) {
          this@NavigableListDetailPaneScaffold.getListAnimationState(navigationState)
        }

        AnimatedPane(
          enterTransition = EnterTransition.None,
          exitTransition = ExitTransition.None,
          modifier = Modifier
            .zIndex(0f)
            .then(animationState.parentModifier)
        ) {
          Box(
            modifier = Modifier
              .then(animationState.toModifier())
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
              topBarContent = { },
              listContent = secondaryContent,
              navRailContent = navRailContent,
              bottomNavContent = bottomNavContent,
              windowSizeClass = windowSizeClass,
              contentWindowInsets = contentWindowInsets
            )
          }
        }
      },
      detailPane = {
        val animationState = with(animatorFactory) {
          this@NavigableListDetailPaneScaffold.getDetailAnimationState(navigationState)
        }

        AnimatedPane(
          enterTransition = EnterTransition.None,
          exitTransition = ExitTransition.None,
          modifier = Modifier
            .zIndex(1f)
            .then(animationState.parentModifier)
        ) {
          Box(
            modifier = Modifier
              .then(animationState.toModifier())
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
            primaryContent()
          }
        }
      },
      paneExpansionDragHandle = paneExpansionDragHandle,
      paneExpansionState = paneExpansionState,
      modifier = Modifier.padding(paddingValues)
    )
  }
}

@Composable
private fun ListAndNavigation(
  topBarContent: @Composable () -> Unit,
  listContent: @Composable () -> Unit,
  navRailContent: @Composable () -> Unit,
  bottomNavContent: @Composable () -> Unit,
  snackbarHost: @Composable () -> Unit = {},
  windowSizeClass: WindowSizeClass,
  contentWindowInsets: WindowInsets
) {
  Scaffold(
    containerColor = Color.Transparent,
    topBar = topBarContent,
    contentWindowInsets = contentWindowInsets,
    snackbarHost = snackbarHost
  ) { paddingValues ->
    Row(
      modifier = Modifier
        .padding(paddingValues)
        .then(if (windowSizeClass.isLandscape()) Modifier.displayCutoutPadding() else Modifier)
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
      secondaryContent = {
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
      primaryContent = {
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
