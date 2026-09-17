/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
   * @param content The keyboard itself.
   */
  fun keyboard(
    key: KeyboardSheetKey,
    enabled: Boolean = true,
    containerColor: Color = Color.Unspecified,
    expandable: Boolean = false,
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
    content: @Composable () -> Unit
  ) {
    entries[key] = Entry(enabled, containerColor, expandable, content)
  }

  fun isEnabled(key: KeyboardSheetKey?): Boolean = key != null && entries[key]?.enabled == true

  fun contentFor(key: KeyboardSheetKey?): (@Composable () -> Unit)? {
    return enabledEntry(key)?.content
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
    val content: @Composable () -> Unit
  )
}
