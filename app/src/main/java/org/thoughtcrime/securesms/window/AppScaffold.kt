/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.window.core.layout.WindowHeightSizeClass
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.main.MainFloatingActionButtonsCallback
import org.thoughtcrime.securesms.main.MainNavigationBar
import org.thoughtcrime.securesms.main.MainNavigationRail
import org.thoughtcrime.securesms.main.MainNavigationState
import kotlin.math.max

enum class NavigationType {
  RAIL,
  BAR;

  companion object {
    @Composable
    fun rememberNavigationType(): NavigationType {
      val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

      return remember(windowSizeClass) {
        if (windowSizeClass.isSplitPane() && windowSizeClass.windowHeightSizeClass.isAtLeast(WindowHeightSizeClass.MEDIUM)) {
          RAIL
        } else {
          BAR
        }
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
  modifier: Modifier = Modifier,
  topBarContent: @Composable () -> Unit = {},
  primaryContent: @Composable () -> Unit = {},
  secondaryContent: @Composable () -> Unit,
  navRailContent: @Composable () -> Unit = {},
  bottomNavContent: @Composable () -> Unit = {},
  paneExpansionState: PaneExpansionState = rememberPaneExpansionState(),
  paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? = null,
  snackbarHost: @Composable () -> Unit = {},
  contentWindowInsets: WindowInsets = WindowInsets.systemBars,
  animatorFactory: AppScaffoldAnimationStateFactory = AppScaffoldAnimationStateFactory.Default
) {
  val isForcedCompact = !LocalInspectionMode.current && !isLargeScreenSupportEnabled()

  if (isForcedCompact) {
    ListAndNavigation(
      topBarContent = topBarContent,
      listContent = secondaryContent,
      navRailContent = navRailContent,
      bottomNavContent = bottomNavContent,
      contentWindowInsets = contentWindowInsets,
      modifier = modifier
    )

    return
  }

  val minPaneWidth = navigator.scaffoldDirective.defaultPanePreferredWidth
  val navigationState = navigator.state

  Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = contentWindowInsets,
    topBar = topBarContent,
    snackbarHost = snackbarHost,
    modifier = modifier
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
            .drawWithContent {
              with(animationState) {
                applyParentValues()
              }
            }
        ) {
          Box(
            modifier = Modifier
              .graphicsLayer {
                with(animationState) {
                  applyChildValues()
                }
              }
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
              contentWindowInsets = WindowInsets() // parent scaffold already applies the necessary insets
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
            .drawWithContent {
              with(animationState) {
                applyParentValues()
              }
            }
        ) {
          Box(
            modifier = Modifier
              .graphicsLayer {
                with(animationState) {
                  applyChildValues()
                }
              }
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
  contentWindowInsets: WindowInsets,
  modifier: Modifier = Modifier
) {
  val navigationType = NavigationType.rememberNavigationType()

  Scaffold(
    containerColor = Color.Transparent,
    topBar = topBarContent,
    contentWindowInsets = contentWindowInsets,
    snackbarHost = snackbarHost,
    modifier = modifier
  ) { paddingValues ->
    Row(
      modifier = Modifier
        .padding(paddingValues)
    ) {
      if (navigationType == NavigationType.RAIL) {
        navRailContent()
      }

      Column {
        Box(modifier = Modifier.weight(1f)) {
          listContent()
        }

        if (navigationType == NavigationType.BAR) {
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
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    AppScaffold(
      navigator = rememberAppScaffoldNavigator(
        isSplitPane = windowSizeClass.isSplitPane(),
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
