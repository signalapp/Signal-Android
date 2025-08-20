/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.content.Intent
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Describes which content to display in the detail view.
 */
@Parcelize
sealed interface MainNavigationDetailLocation : Parcelable {
  data object Empty : MainNavigationDetailLocation
  data class Conversation(val intent: Intent) : MainNavigationDetailLocation
}
