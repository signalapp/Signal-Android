/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactornameentry

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface TwoFactorNameEntryEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : TwoFactorNameEntryEvent

  /** The user typed in the name field. */
  data class NameChanged(val name: String) : TwoFactorNameEntryEvent {
    override fun toString(): String = "NameChanged(length=${name.length})"
  }

  /** The user submitted the name they entered. */
  data object NextClicked : TwoFactorNameEntryEvent
}
