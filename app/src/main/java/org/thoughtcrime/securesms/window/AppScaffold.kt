/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.getWindowBreakpoint
import org.signal.core.ui.rememberIsSplitPane
import org.thoughtcrime.securesms.keyvalue.SignalStore
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
      val resources = LocalResources.current
      val config = LocalConfiguration.current
      val windowBreakpoint = remember(config) { resources.getWindowBreakpoint() }

      return when (windowBreakpoint) {
        is WindowBreakpoint.Small -> BAR
        is WindowBreakpoint.Medium -> if (windowBreakpoint.isWidthExpanded) RAIL else BAR
        is WindowBreakpoint.Large -> RAIL
      }
    }
  }
}

/**
 * A top-level scaffold that automatically adapts its layout based on the device's window size class. It is a generic container designed to handle the
 * arrangement of navigation rails, top/bottom bars, and list-detail pane management for both compact and large screens.
 *
 * On phone-class layouts (single horizontal partition) running on devices that predate predictive back (API < 33),
 * this dispatches to [SinglePaneAppScaffold], which skips [NavigableListDetailPaneScaffold] / [ThreePaneScaffold] and
 * its lookahead measurement pass. The scaffold's seek-driven predictive back animation never fires on those devices,
 * so we pay no UX cost for the simpler implementation.
 *
 * @param topBarContent An optional top bar that spans across all panes.
 *
 * @param primaryContent The main content, which is typically the detail view in a split-pane layout.
 * @param secondaryContent The secondary content, which is typically the list view in a split-pane layout.
 *
 * @param navRailContent The side navigation rail, shown on medium and larger screen sizes.
 * @param bottomNavContent The bottom navigation bar, shown on compact screen sizes.
 *
 * @param paneExpansionState Manages the position and expansion of the panes in a list-detail layout. Ignored by [SinglePaneAppScaffold].
 * @param paneExpansionDragHandle An optional drag handle used to resize panes in the list-detail layout. Ignored by [SinglePaneAppScaffold].
 *
 * @param animatorFactory Provides animations to control how panes enter and exit the screen during navigation. Ignored by [SinglePaneAppScaffold].
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
  val isForceSinglePane = if (LocalInspectionMode.current) {
    false
  } else {
    SignalStore.internal.forceSinglePane
  }

  val useSimpleScaffold = isForceSinglePane || (navigator.scaffoldDirective.maxHorizontalPartitions == 1 && Build.VERSION.SDK_INT < 33)
  if (useSimpleScaffold && LocalLayoutDirection.current != LayoutDirection.Rtl) {
    SinglePaneAppScaffold(
      navigator = navigator,
      modifier = modifier,
      topBarContent = topBarContent,
      primaryContent = primaryContent,
      secondaryContent = secondaryContent,
      bottomNavContent = bottomNavContent,
      snackbarHost = snackbarHost,
      contentWindowInsets = contentWindowInsets,
      animatorFactory = animatorFactory
    )
  } else {
    AdaptiveAppScaffold(
      navigator = navigator,
      modifier = modifier,
      topBarContent = topBarContent,
      primaryContent = primaryContent,
      secondaryContent = secondaryContent,
      navRailContent = navRailContent,
      bottomNavContent = bottomNavContent,
      paneExpansionState = paneExpansionState,
      paneExpansionDragHandle = paneExpansionDragHandle,
      snackbarHost = snackbarHost,
      contentWindowInsets = contentWindowInsets,
      animatorFactory = animatorFactory
    )
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AdaptiveAppScaffold(
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

/**
 * Phone-only scaffold that swaps content between [secondaryContent] and [primaryContent] without using
 * [NavigableListDetailPaneScaffold]. Avoids the lookahead measurement pass and deep adaptive layout tree
 * that drives ANR on low-end devices.
 *
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun SinglePaneAppScaffold(
  navigator: AppScaffoldNavigator<Any>,
  modifier: Modifier = Modifier,
  topBarContent: @Composable () -> Unit = {},
  primaryContent: @Composable () -> Unit = {},
  secondaryContent: @Composable () -> Unit,
  bottomNavContent: @Composable () -> Unit = {},
  snackbarHost: @Composable () -> Unit = {},
  contentWindowInsets: WindowInsets = WindowInsets.systemBars,
  animatorFactory: AppScaffoldAnimationStateFactory = AppScaffoldAnimationStateFactory.Default
) {
  val showDetail = navigator.scaffoldValue.primary == PaneAdaptedValue.Expanded
  val coroutineScope = rememberCoroutineScope()
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val directionMultiplier = if (isRtl) -1 else 1
  val skipSlide = AppScaffoldNavigator.NavigationState.ENTER !in animatorFactory.enabledStates

  BackHandler(enabled = navigator.canNavigateBack()) {
    coroutineScope.launch { navigator.navigateBack() }
  }

  Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = contentWindowInsets,
    topBar = topBarContent,
    snackbarHost = snackbarHost,
    modifier = modifier
  ) { paddingValues ->
    AnimatedContent(
      targetState = showDetail,
      transitionSpec = {
        val transform = when {
          skipSlide -> EnterTransition.None togetherWith ExitTransition.None
          targetState -> slideInHorizontally(animationSpec = AppScaffoldAnimationDefaults.tween()) { fullWidth -> fullWidth * directionMultiplier } togetherWith
            slideOutHorizontally(animationSpec = AppScaffoldAnimationDefaults.tween()) { fullWidth -> -fullWidth * directionMultiplier }
          else -> slideInHorizontally(animationSpec = AppScaffoldAnimationDefaults.tween()) { fullWidth -> -fullWidth * directionMultiplier } togetherWith
            slideOutHorizontally(animationSpec = AppScaffoldAnimationDefaults.tween()) { fullWidth -> fullWidth * directionMultiplier }
        }
        transform using SizeTransform(clip = false) { _, _ -> snap() }
      },
      modifier = Modifier.padding(paddingValues),
      label = "SimpleAppScaffold"
    ) { isDetail ->
      if (isDetail) {
        primaryContent()
      } else {
        Column(modifier = Modifier.fillMaxSize()) {
          Box(modifier = Modifier.weight(1f)) {
            secondaryContent()
          }
          bottomNavContent()
        }
      }
    }
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
    val isSplitPane = LocalResources.current.rememberIsSplitPane(false)

    AppScaffold(
      navigator = rememberAppScaffoldNavigator(
        isSplitPane = isSplitPane,
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
