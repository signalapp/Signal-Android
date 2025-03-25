/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.content.Intent

/**
 * Describes which content to display in the detail view.
 */
sealed interface MainNavigationDetailLocation {
  data object Empty : MainNavigationDetailLocation
  data class Conversation(val intent: Intent) : MainNavigationDetailLocation
}
