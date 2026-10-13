/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

/**
 * State of the main navigation bar or rail.
 *
 * @param destinations the tabs to display, in order. Whoever builds this state decides which ones the user
 *   gets; the bar and the rail display exactly what they are handed.
 */
data class MainNavigationBarState(
  val chatsCount: Int = 0,
  val callsCount: Int = 0,
  val storiesCount: Int = 0,
  val storyFailure: Boolean = false,
  val destinations: List<MainListRoute> = ALL_DESTINATIONS,
  val currentListLocation: MainListRoute = MainListRoute.Chats,
  val compact: Boolean = false
) {
  companion object {
    /**
     * Every tab there is, in display order. The archive is not among them: it is a list within chats
     * rather than a tab of its own.
     */
    val ALL_DESTINATIONS = listOf(MainListRoute.Chats, MainListRoute.Calls, MainListRoute.Stories)
  }
}
