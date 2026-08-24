/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.recentmediarail

import androidx.compose.ui.unit.IntRect

/**
 * Everything a [RecentMediaRailPresenter] can be told, whether it came from the rail itself or from the screen hosting
 * it.
 *
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface RecentMediaRailEvents {

  /** The rail has something to load from. Each distinct [sourceId] kicks off a fresh load. */
  data class SourceChanged(val sourceId: Long) : RecentMediaRailEvents

  /** Reload the current source, e.g. after the user came back from the media viewer. */
  data object RefreshRequested : RecentMediaRailEvents

  /**
   * The user tapped an item.
   *
   * @param index Position in [RecentMediaRailState.media].
   * @param bounds The item's bounds in window coordinates, for hosts that want to animate out of it.
   * @param leftToRight Whether the rail was laid out left to right, which decides which way the media viewer pages.
   */
  data class ItemClicked(val index: Int, val bounds: IntRect, val leftToRight: Boolean) : RecentMediaRailEvents

  /** The user asked to see everything the rail is a preview of. */
  data object SeeAllClicked : RecentMediaRailEvents
}
