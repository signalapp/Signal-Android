/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata

/**
 * Marks an entry as list-pane content, so that a scene can tell which of the entries it has been handed
 * belongs in the list pane, since it can't gather that information for itself via a key which is private.
 */
internal object ListPane : NavMetadataKey<Boolean>

/**
 * Marks an entry as detail-pane content, the counterpart to [ListPane].
 */
internal object DetailPane : NavMetadataKey<Boolean>

/**
 * Registers [content] as the list pane for [K]. The counterpart to `entry` for a list location, and the
 * only way to mark one.
 *
 * @param metadata additional metadata for the display, such as a transition spec.
 */
inline fun <reified K : ListNavKey> EntryProviderScope<NavKey>.listEntry(
  metadata: Map<String, Any> = emptyMap(),
  noinline content: @Composable (K) -> Unit
) {
  entry<K>(metadata = metadata + listPaneMetadata(), content = content)
}

/**
 * Registers [content] as the detail pane for [K], displayed beside the list in a split-pane window and
 * over it in a single-pane one.
 *
 * @param metadata additional metadata for the display, such as a transition spec.
 */
inline fun <reified K : DetailNavKey> EntryProviderScope<NavKey>.detailEntry(
  metadata: Map<String, Any> = emptyMap(),
  noinline content: @Composable (K) -> Unit
) {
  entry<K>(metadata = metadata + detailPaneMetadata(), content = content)
}

/**
 * Metadata marking an entry as list-pane content, applied by [listEntry].
 */
@PublishedApi
internal fun listPaneMetadata(): Map<String, Any> = metadata { put(ListPane, true) }

/**
 * Metadata marking an entry as detail-pane content, applied by [detailEntry].
 */
@PublishedApi
internal fun detailPaneMetadata(): Map<String, Any> = metadata { put(DetailPane, true) }

/**
 * Whether this entry belongs in the list pane.
 */
internal val NavEntry<*>.isListPane: Boolean
  get() = ListPane in metadata

/**
 * Whether this entry belongs in the detail pane.
 */
internal val NavEntry<*>.isDetailPane: Boolean
  get() = DetailPane in metadata

/**
 * Whether this entry takes the whole window. An entry that claims neither pane gets both, which is what a
 * plain `entry` registration means.
 */
internal val NavEntry<*>.isFullScreen: Boolean
  get() = !isListPane && !isDetailPane
