/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * AppScaffoldNavigator wraps a delegate navigator (such as the value returned by [rememberThreePaneScaffoldNavigatorDelegate]
 * and implements a state machine that will produce [NavigationState] to allow proper animation coordination.
 */
@Stable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
open class AppScaffoldNavigator<T> @RememberInComposition constructor(private val delegate: ThreePaneScaffoldNavigator<T>) : ThreePaneScaffoldNavigator<T> by delegate {

  var state: NavigationState by mutableStateOf(NavigationState.ENTER)
    private set

  override suspend fun navigateTo(pane: ThreePaneScaffoldRole, contentKey: T?) {
    state = NavigationState.ENTER
    return delegate.navigateTo(pane, contentKey)
  }

  override suspend fun navigateBack(backNavigationBehavior: BackNavigationBehavior): Boolean {
    if (state == NavigationState.SEEK) {
      state = NavigationState.RELEASE
    }

    if (state == NavigationState.ENTER) {
      state = NavigationState.EXIT
    }

    return delegate.navigateBack(backNavigationBehavior)
  }

  override suspend fun seekBack(backNavigationBehavior: BackNavigationBehavior, fraction: Float) {
    if (fraction > 0f && state != NavigationState.SEEK) {
      state = NavigationState.SEEK
    }

    return delegate.seekBack(backNavigationBehavior, fraction)
  }

  /**
   * State machine which describes the current navigation state to help with animation coordination.
   */
  enum class NavigationState {
    /**
     * We've navigated to a new pane.
     */
    ENTER,

    /**
     * We've navigated back from a pane without using seek.
     */
    EXIT,

    /**
     * The user is performing a back gesture seek action.
     */
    SEEK,

    /**
     * The user has let go of a seek and will go back.
     */
    RELEASE
  }
}

/**
 * Sane default navigator. If you want to provide your own implementation
 * of AppScaffoldNavigator, utilize the remember pattern here but use
 * [rememberThreePaneScaffoldNavigatorDelegate] to get a delegate and hand off
 * to your own subclass of [AppScaffoldNavigator]
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberAppScaffoldNavigator(
  windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
  isSplitPane: Boolean = windowSizeClass.isSplitPane(
    forceSplitPane = if (LocalInspectionMode.current) false else SignalStore.internal.forceSplitPane
  ),
  horizontalPartitionSpacerSize: Dp = windowSizeClass.horizontalPartitionDefaultSpacerSize,
  defaultPanePreferredWidth: Dp = windowSizeClass.listPaneDefaultPreferredWidth
): AppScaffoldNavigator<Any> {
  val delegate = rememberThreePaneScaffoldNavigatorDelegate(
    isSplitPane,
    horizontalPartitionSpacerSize,
    defaultPanePreferredWidth
  )

  return remember(delegate) { AppScaffoldNavigator(delegate) }
}

/**
 * Produces a ThreePaneScaffoldNavigatorDelegate. Since the developer can
 * further modify navigator behavior, this is best done using the delegate pattern.
 * Use this to grab the initial delegate, and then either subclass or create an
 * instance of [AppScaffoldNavigator] keyed to this delegate.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberThreePaneScaffoldNavigatorDelegate(
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
