/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Gives content anywhere beneath it a way to draw over the whole window with [WindowOverlay]. Unlike a dialog, an
 * overlay stays in this composition, so shared elements can move between it and the screen that opened it through
 * [LocalSharedTransitionScope], which this provides.
 *
 * Place it once at the root of an activity's content.
 */
@Composable
fun WindowOverlayHost(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val host = remember { WindowOverlayHostState() }

  SharedTransitionLayout(modifier = modifier) {
    CompositionLocalProvider(
      LocalWindowOverlayHost provides host,
      LocalSharedTransitionScope provides this
    ) {
      content()

      host.overlays.forEach { overlay ->
        key(overlay) {
          CompositionLocalProvider(overlay.locals) {
            overlay.content()
          }
        }
      }
    }
  }
}

/**
 * Draws [content] over the whole window, above everything inside the nearest [WindowOverlayHost], for as long as
 * this is in composition. [content] sees the composition locals of where this is called rather than of the host.
 */
@Composable
fun WindowOverlay(content: @Composable () -> Unit) {
  val host = checkNotNull(LocalWindowOverlayHost.current) { "WindowOverlay needs a WindowOverlayHost above it." }
  val locals = currentCompositionLocalContext
  val overlay = remember { WindowOverlayEntry(locals, content) }

  overlay.locals = locals
  overlay.content = content

  DisposableEffect(host, overlay) {
    host.overlays += overlay
    onDispose { host.overlays -= overlay }
  }
}

private val LocalWindowOverlayHost = staticCompositionLocalOf<WindowOverlayHostState?> { null }

private class WindowOverlayHostState {
  val overlays = mutableStateListOf<WindowOverlayEntry>()
}

private class WindowOverlayEntry(locals: CompositionLocalContext, content: @Composable () -> Unit) {
  var locals by mutableStateOf(locals)
  var content by mutableStateOf(content)
}
