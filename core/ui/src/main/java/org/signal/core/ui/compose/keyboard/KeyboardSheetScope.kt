/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Declares which keyboards a [KeyboardSheetScaffold] offers. */
interface KeyboardSheetScope {
  /**
   * Offers the keyboard identified by [key].
   *
   * @param enabled False to keep the key known but refuse requests for it.
   * @param containerColor Fills the sheet, navigation bar included, so it should match whatever
   *   [content] paints its edges with. Unspecified falls back to the scaffold's surface.
   * @param expandable True to let this keyboard grow past keyboard height to fill the window. Gives
   *   the sheet a drag handle, lets it be swiped, and dims what is behind it as it grows.
   * @param overlay Drawn over the whole sheet, drag handle and navigation bar included. It fills
   *   the sheet whether or not it draws anything, so it is left to the overlay to take touches away
   *   from the keyboard.
   * @param blurRadius How much to blur the sheet beneath [overlay]. Read as the sheet draws, so it
   *   can animate without recomposing anything. Below API 31 the sheet is never blurred, so
   *   [overlay] needs a background opaque enough to stand on its own there.
   * @param content The keyboard itself.
   */
  fun keyboard(
    key: KeyboardSheetKey,
    enabled: Boolean = true,
    containerColor: Color = Color.Unspecified,
    expandable: Boolean = false,
    overlay: (@Composable () -> Unit)? = null,
    blurRadius: () -> Dp = { 0.dp },
    content: @Composable () -> Unit
  )
}

internal class KeyboardSheetRegistry : KeyboardSheetScope {
  private val entries = LinkedHashMap<KeyboardSheetKey, Entry>()

  override fun keyboard(
    key: KeyboardSheetKey,
    enabled: Boolean,
    containerColor: Color,
    expandable: Boolean,
    overlay: (@Composable () -> Unit)?,
    blurRadius: () -> Dp,
    content: @Composable () -> Unit
  ) {
    entries[key] = Entry(enabled, containerColor, expandable, overlay, blurRadius, content)
  }

  fun isEnabled(key: KeyboardSheetKey?): Boolean = key != null && entries[key]?.enabled == true

  fun contentFor(key: KeyboardSheetKey?): (@Composable () -> Unit)? {
    return enabledEntry(key)?.content
  }

  fun overlayFor(key: KeyboardSheetKey?): (@Composable () -> Unit)? {
    return enabledEntry(key)?.overlay
  }

  fun blurRadiusFor(key: KeyboardSheetKey?): () -> Dp {
    return enabledEntry(key)?.blurRadius ?: { 0.dp }
  }

  fun containerColorFor(key: KeyboardSheetKey?): Color {
    return enabledEntry(key)?.containerColor ?: Color.Unspecified
  }

  fun isExpandable(key: KeyboardSheetKey?): Boolean = enabledEntry(key)?.expandable == true

  private fun enabledEntry(key: KeyboardSheetKey?): Entry? {
    return key?.let { entries[it] }?.takeIf { it.enabled }
  }

  private class Entry(
    val enabled: Boolean,
    val containerColor: Color,
    val expandable: Boolean,
    val overlay: (@Composable () -> Unit)?,
    val blurRadius: () -> Dp,
    val content: @Composable () -> Unit
  )
}
