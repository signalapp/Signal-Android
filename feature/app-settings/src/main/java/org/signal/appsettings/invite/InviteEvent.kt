/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface InviteEvent {

  /** The user changed the invite message. */
  data class InviteTextChanged(val updatedText: String) : InviteEvent {
    override fun toString(): String = "InviteTextChanged"
  }

  /** The user tapped the share icon. */
  data object ShareClicked : InviteEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : InviteEvent
}
