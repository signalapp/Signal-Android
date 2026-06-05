/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

/**
 * Handles navigation for sub-screens within the chats detail pane.
 */
interface MainNavigationChatDetailRouter {
  fun exitDetailLocation()
  fun goToChatDetail(location: MainNavigationDetailLocation.Chats)
}

/**
 * Handles navigation for sub-screens within the calls detail pane.
 */
interface MainNavigationCallDetailRouter {
  fun exitDetailLocation()
  fun goToCallDetail(location: MainNavigationDetailLocation.Calls)
}

/**
 * Handles navigation to all [MainNavigationListLocation]s and [MainNavigationDetailLocation]s, including the top-level roots.
 */
interface MainNavigationRouter : MainNavigationChatDetailRouter, MainNavigationCallDetailRouter {
  fun goTo(location: MainNavigationListLocation)
  fun goTo(location: MainNavigationDetailLocation)

  override fun goToChatDetail(location: MainNavigationDetailLocation.Chats) = goTo(location)
  override fun goToCallDetail(location: MainNavigationDetailLocation.Calls) = goTo(location)
  override fun exitDetailLocation() = goTo(MainNavigationDetailLocation.Empty)
}
