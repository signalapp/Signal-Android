/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernameedit

/**
 * One-shot side effects the host carries out for [UsernameEditScreen].
 */
sealed interface UsernameEditAction {

  /** Open [url] in the browser. */
  data class OpenBrowserLink(val url: String) : UsernameEditAction

  /** A username was saved. Whatever launched the screen may want to know. */
  data object UsernameSaved : UsernameEditAction

  /** The username was deleted. Tells the user, since the screen leaves straight away. */
  data object UsernameDeleted : UsernameEditAction

  /** Leave by closing the whole activity, as recovery does, rather than going back a screen. */
  data object Finish : UsernameEditAction
}
