/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard

/**
 * @param offeredTabs Everything the repository has to offer, before any restriction.
 * @param restrictedTo Narrows the keyboard on the host's behalf, or null for no restriction.
 * @param preferredTab The tab the user last picked, returned to whenever it is reachable again.
 *   Seeded by the host from what it recorded of [MediaKeyboardAction.TabSelected], since the
 *   keyboard has no memory of its own across the times it is shown.
 * @param searchActive Whether the search surface is up. Owned by the scaffold rather than tracked
 *   here, and not the events' to set; see
 *   [org.signal.core.ui.compose.keyboard.KeyboardSheetController.isEnteringText].
 * @param searchQuery What the user has typed, which is this keyboard's own to keep. Only meaningful
 *   while [searchActive]; left behind rather than cleared when search closes.
 */
data class MediaKeyboardState(
  val offeredTabs: List<MediaKeyboardTab> = emptyList(),
  val restrictedTo: Set<MediaKeyboardTab>? = null,
  val preferredTab: MediaKeyboardTab = MediaKeyboardTab.EMOJI,
  val searchActive: Boolean = false,
  val searchQuery: String = "",
  val initialized: Boolean = false
) {
  /** What the user can actually reach. A single tab hides the tab row altogether. */
  val availableTabs: List<MediaKeyboardTab> = offeredTabs.filter { restrictedTo == null || it in restrictedTo }

  val selectedTab: MediaKeyboardTab = availableTabs.firstOrNull { it == preferredTab }
    ?: availableTabs.firstOrNull()
    ?: MediaKeyboardTab.EMOJI
}
