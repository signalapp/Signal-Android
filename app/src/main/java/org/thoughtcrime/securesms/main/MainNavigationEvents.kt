/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import org.signal.core.ui.compose.split.ListDetailEvents
import org.thoughtcrime.securesms.megaphone.Megaphone
import org.thoughtcrime.securesms.megaphone.Megaphones

/**
 * UI Events to drive the main navigation view model.
 */
sealed interface MainNavigationEvents {

  /**
   * The user clicked [tab] in the navigation bar or rail. Clicking the tab already displayed reveals its
   * list and tells that tab's screen about the click, rather than navigating anywhere.
   */
  data class GoToTab(val tab: MainListRoute) : MainNavigationEvents

  /** Display [route], which comes back to whatever detail content its tab had open. */
  data class GoToList(val route: MainListRoute) : MainNavigationEvents

  /** Open [route] above the list of whichever tab owns it. */
  data class GoToDetail(val route: MainDetailRoute) : MainNavigationEvents

  /** Drop the detail above the displayed list, leaving that list on its own. */
  data object ExitDetail : MainNavigationEvents

  /** Open the camera straight into story capture. */
  data object GoToCameraFirstStoryCapture : MainNavigationEvents

  /** Re-read the settings that shape the navigation bar. */
  data object RefreshNavigationBar : MainNavigationEvents

  /** Ask for the next megaphone to display, if there is one. */
  data object RequestNextMegaphone : MainNavigationEvents

  /** [megaphone] was displayed to the user. */
  data class MegaphoneVisible(val megaphone: Megaphone) : MainNavigationEvents

  /** The user put [event]'s megaphone off until later. */
  data class MegaphoneSnoozed(val event: Megaphones.Event) : MainNavigationEvents

  /** [event]'s megaphone is done with, and should not come back. */
  data class MegaphoneCompleted(val event: Megaphones.Event) : MainNavigationEvents

  /** Something that is navigation and nothing else, so the navigator can answer it unaided. */
  data class ListDetailEvent(val event: ListDetailEvents) : MainNavigationEvents
}
