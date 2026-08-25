/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorcodeentry

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AuthenticatorCodeEntryEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : AuthenticatorCodeEntryEvent

  /** The user typed in the code field. */
  data class CodeChanged(val code: String) : AuthenticatorCodeEntryEvent {
    override fun toString(): String = "CodeChanged(length=${code.length})"
  }

  /** The user submitted the code they entered. */
  data object DoneClicked : AuthenticatorCodeEntryEvent
}
