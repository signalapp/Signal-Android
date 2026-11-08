/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

/**
 * An activity that hosts main navigation content and can answer its events.
 *
 * The main window hands them to [MainNavigationViewModel]. The standalone conversation activity answers
 * the few that still mean something outside the main window with its own fragment transactions. Screens
 * that either one can host send their events here, and check for it to tell the two hosts apart.
 */
fun interface MainNavigationEventSink {
  fun onEvent(event: MainNavigationEvents)
}
