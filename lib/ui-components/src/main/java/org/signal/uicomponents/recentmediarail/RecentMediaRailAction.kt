/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.recentmediarail

import androidx.compose.ui.unit.IntRect

/**
 * Side effects that can be emitted by [RecentMediaRailPresenter] that need to be handled by the user of the component.
 *
 * Every [index] is a position in [RecentMediaRailState.media].
 */
sealed interface RecentMediaRailAction {

  /** Show the media the user tapped, animating out of [bounds] (window coordinates). */
  data class OpenMedia(val index: Int, val bounds: IntRect, val leftToRight: Boolean) : RecentMediaRailAction

  /** Download the media the user tapped, since it isn't on the device yet. */
  data class DownloadMedia(val index: Int) : RecentMediaRailAction

  /** Tell the user the media they tapped isn't available. */
  data object ShowMediaUnavailable : RecentMediaRailAction

  /** Open the full list of media this rail is a preview of. */
  data object OpenAllMedia : RecentMediaRailAction
}
