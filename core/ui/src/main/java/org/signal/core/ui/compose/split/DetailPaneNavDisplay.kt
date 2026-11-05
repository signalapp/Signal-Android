/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import org.signal.core.ui.navigation.TransitionSpecs

/**
 * Navigates back from the detail pane of a split-pane window, provided by [ListDetailNavDisplay].
 */
internal val LocalDetailPaneBack = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Displays the detail stack of a split-pane window inside its pane, so that moving within the stack animates only the
 * pane while the list beside it stays put. Moving to a different stack swaps the pane without animating, and when the
 * new stack is a deferred-enter entry the old one stays on screen until the new one is ready.
 *
 * [entries] arrive already decorated by the outer display.
 *
 * @param listKey the content key of the list beside the pane. Only a swap made beside the same list is deferred.
 */
@Composable
internal fun DetailPaneNavDisplay(listKey: Any, entries: List<NavEntry<NavKey>>) {
  val onBack = LocalDetailPaneBack.current
  val paneShift = TransitionSpecs.paneShift()
  val paneShiftPop = TransitionSpecs.paneShift(pop = true)
  val swap = rememberDetailStackSwap(listKey, entries)?.takeUnless { it.isEntered }

  CompositionLocalProvider(LocalPendingDeferredEnter provides swap?.pending) {
    NavDisplay(
      entries = if (swap != null) listOf(swap.outgoing) + entries else entries,
      sceneStrategies = listOf(DetailPaneSceneStrategy(swap)),
      transitionSpec = { withinStackOrNone(paneShift) },
      popTransitionSpec = { withinStackOrNone(paneShiftPop) },
      predictivePopTransitionSpec = { withinStackOrNone(paneShiftPop) },
      onBack = onBack,
      modifier = Modifier.fillMaxSize()
    )
  }
}

/**
 * A swap from one detail stack to a deferred-enter entry, which keeps [outgoing] on screen until the entry is ready.
 */
private class DetailStackSwap(val outgoing: NavEntry<NavKey>, val pending: PendingDeferredEnter) {
  var isEntered by mutableStateOf(false)
}

/**
 * Watches [entries] for a swap to a stack holding only a deferred-enter entry. If a swap arrives while another is still
 * waiting, the entry that was on screen for the first one stays on screen for the second.
 */
@Composable
private fun rememberDetailStackSwap(listKey: Any, entries: List<NavEntry<NavKey>>): DetailStackSwap? {
  val history = remember { SwapHistory() }
  val keys = entries.map { it.contentKey }

  val swap = remember(listKey, keys) {
    val previousEntries = history.entries
    val previousSwap = history.swap?.takeUnless { it.isEntered }
    val top = entries.last()
    val outgoing = previousSwap?.outgoing ?: previousEntries?.last()

    val swap = if (
      previousEntries != null &&
      outgoing != null &&
      history.listKey == listKey &&
      entries.size == 1 &&
      top.isDeferredEnter &&
      previousEntries.first().contentKey != top.contentKey &&
      outgoing.contentKey != top.contentKey
    ) {
      DetailStackSwap(outgoing = outgoing, pending = PendingDeferredEnter(top.contentKey))
    } else {
      null
    }

    history.swap = swap
    swap
  }

  SideEffect {
    history.listKey = listKey
    history.entries = entries
  }

  return swap
}

private class SwapHistory {
  var listKey: Any? = null
  var entries: List<NavEntry<NavKey>>? = null
  var swap: DetailStackSwap? = null
}

/**
 * Shows a pending [swap] as a [DeferredEnterScene] over its outgoing entry, leaving everything else to nav3's default.
 */
private class DetailPaneSceneStrategy(private val swap: DetailStackSwap?) : SceneStrategy<NavKey> {
  override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
    if (swap == null || entries.size != 2) {
      return null
    }

    val top = entries.last()

    return DeferredEnterScene(
      key = top.contentKey,
      previousEntries = emptyList(),
      beneathEntry = entries.first(),
      entry = top,
      transform = STACK_SWAP,
      onEntered = { swap.isEntered = true }
    )
  }
}

/** Swaps instantly, while keeping the incoming entry invisible until it enters. */
private val STACK_SWAP: ContentTransform = fadeIn(snap()) togetherWith fadeOut(snap())

private fun AnimatedContentTransitionScope<Scene<NavKey>>.withinStackOrNone(transform: ContentTransform): ContentTransform {
  val isSwap = initialState is DeferredEnterScene || targetState is DeferredEnterScene
  return if (!isSwap && initialState.stackRootKey == targetState.stackRootKey) transform else TransitionSpecs.Transition.NONE
}

/** The content key of the entry at the bottom of the stack this scene is part of. */
private val Scene<NavKey>.stackRootKey: Any?
  get() = (previousEntries.firstOrNull() ?: entries.firstOrNull())?.contentKey
