/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.DeferredAnimatedContent
import androidx.compose.animation.core.DeferredTransitionState
import androidx.compose.animation.core.ExperimentalDeferredTransitionApi
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import org.signal.core.ui.navigation.TransitionSpecs
import kotlin.time.Duration.Companion.seconds

/**
 * Marks an entry as one whose enter transition waits for [DeferredEnter.start].
 */
internal object DeferredEnterKey : NavMetadataKey<Boolean>

/**
 * Metadata for an entry that takes a while to draw its first frame, so it isn't revealed half-built. When pushed in a
 * single pane, the entry beneath stays on screen while this one composes invisibly, and the two only animate once the
 * entry calls [DeferredEnter.start] from [LocalDeferredEnter].
 *
 * Nav3's own enter is suppressed, since the swap to the deferring scene has to be instant for the entry beneath to
 * carry on being shown. Pops are left to the display's defaults.
 */
fun deferredEnterMetadata(): Map<String, Any> = metadata {
  put(DeferredEnterKey, true)
  put(NavDisplay.TransitionKey) {
    TransitionSpecs.Transition.NONE
  }
}

internal val NavEntry<*>.isDeferredEnter: Boolean
  get() = DeferredEnterKey in metadata

/**
 * Lets an entry registered with [deferredEnterMetadata] say it's ready to be shown.
 */
fun interface DeferredEnter {
  fun start()
}

/**
 * Provided to every entry; a no-op unless the entry is being held back by a deferred enter.
 */
val LocalDeferredEnter = staticCompositionLocalOf { DeferredEnter {} }

/**
 * A push of an entry registered with [deferredEnterMetadata], which the first scene to display it gets to claim. Pops,
 * tab switches and restores never produce one, so returning to an entry shows it straight away.
 */
internal class PendingDeferredEnter(private val contentKey: Any) {
  private var claimed = false

  fun claim(key: Any): Boolean {
    if (claimed || key != contentKey) {
      return false
    }

    claimed = true
    return true
  }
}

internal val LocalPendingDeferredEnter = compositionLocalOf<PendingDeferredEnter?> { null }

/**
 * Watches [entries] for a push that lands on a deferred-enter entry.
 */
@Composable
internal fun rememberPendingDeferredEnter(entries: List<NavEntry<NavKey>>): PendingDeferredEnter? {
  val history = remember { KeyHistory() }
  val keys = entries.map { it.contentKey }

  return remember(keys) {
    val previousKeys = history.keys
    history.keys = keys

    val isPush = previousKeys != null && keys.size > previousKeys.size && keys.subList(0, previousKeys.size) == previousKeys
    val top = entries.last()

    if (isPush && top.isDeferredEnter) PendingDeferredEnter(top.contentKey) else null
  }
}

private class KeyHistory {
  var keys: List<Any>? = null
}

/**
 * Single-pane scene for a deferred-enter [entry]. When it has been pushed, [beneathEntry] keeps being rendered here
 * while [entry] composes in its pre-enter state, then the two pane shift once [entry] starts its enter or
 * [DEFERRED_ENTER_TIMEOUT] passes. Otherwise it's just [entry].
 *
 * [beneathEntry] is included in [entries] so nav3 moves it here rather than tearing it down when the push swaps scenes.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
@Immutable
internal class DeferredEnterScene(
  override val key: Any,
  override val previousEntries: List<NavEntry<NavKey>>,
  private val beneathEntry: NavEntry<NavKey>,
  private val entry: NavEntry<NavKey>
) : Scene<NavKey> {

  override val entries: List<NavEntry<NavKey>> = listOf(beneathEntry, entry)

  override val content: @Composable () -> Unit = {
    val pending = LocalPendingDeferredEnter.current
    val transitionState = remember {
      val shouldDefer = pending?.claim(entry.contentKey) == true
      DeferredTransitionState(initialState = !shouldDefer).apply {
        if (shouldDefer) {
          defer(true)
        }
      }
    }

    val transition = rememberTransition(transitionState, label = "DeferredEnterScene")
    val deferredEnter = remember(transitionState) { DeferredEnter { transitionState.animateTo(true) } }
    val isDeferred = transition.pendingTargetState != null

    if (isDeferred) {
      LaunchedEffect(Unit) {
        delay(DEFERRED_ENTER_TIMEOUT)
        deferredEnter.start()
      }
    }

    val paneShift = TransitionSpecs.paneShift()

    Box(modifier = Modifier.fillMaxSize()) {
      transition.DeferredAnimatedContent(
        transitionSpec = { ContentTransform(paneShift.targetContentEnter, paneShift.initialContentExit, sizeTransform = null) },
        modifier = Modifier.fillMaxSize()
      ) { entered ->
        if (entered) {
          CompositionLocalProvider(LocalDeferredEnter provides deferredEnter) {
            entry.Content()
          }
        } else if (beneathEntry.isListPane) {
          ListPaneContent { beneathEntry.Content() }
        } else {
          beneathEntry.Content()
        }
      }

      if (isDeferred) {
        // The entering content is invisible but on top, and would otherwise take touches aimed at what's beneath it.
        Box(
          modifier = Modifier
            .matchParentSize()
            .pointerInput(Unit) {
              awaitPointerEventScope {
                while (true) {
                  awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
              }
            }
        )
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DeferredEnterScene) return false

    return key == other.key &&
      beneathEntry == other.beneathEntry &&
      entry == other.entry &&
      previousEntries == other.previousEntries
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + beneathEntry.hashCode()
    result = 31 * result + entry.hashCode()
    result = 31 * result + previousEntries.hashCode()
    return result
  }
}

private val DEFERRED_ENTER_TIMEOUT = 5.seconds
