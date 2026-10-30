/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import org.signal.core.ui.navigation.TransitionSpecs

/**
 * Navigates back from the detail pane of a split-pane window, provided by [ListDetailNavDisplay].
 */
internal val LocalDetailPaneBack = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Displays the detail stack of a split-pane window inside its pane, so that moving within the stack animates only the
 * pane while the list beside it stays put. Moving to a different stack swaps the pane without animating.
 *
 * [entries] arrive already decorated by the outer display.
 */
@Composable
internal fun DetailPaneNavDisplay(entries: List<NavEntry<NavKey>>) {
  val onBack = LocalDetailPaneBack.current
  val paneShift = TransitionSpecs.paneShift()
  val paneShiftPop = TransitionSpecs.paneShift(pop = true)

  NavDisplay(
    entries = entries,
    transitionSpec = { withinStackOrNone(paneShift) },
    popTransitionSpec = { withinStackOrNone(paneShiftPop) },
    predictivePopTransitionSpec = { withinStackOrNone(paneShiftPop) },
    onBack = onBack,
    modifier = Modifier.fillMaxSize()
  )
}

private fun AnimatedContentTransitionScope<Scene<NavKey>>.withinStackOrNone(transform: ContentTransform): ContentTransform {
  return if (initialState.stackRootKey == targetState.stackRootKey) transform else TransitionSpecs.Transition.NONE
}

/** The content key of the entry at the bottom of the stack this scene is part of. */
private val Scene<NavKey>.stackRootKey: Any?
  get() = (previousEntries.firstOrNull() ?: entries.firstOrNull())?.contentKey
