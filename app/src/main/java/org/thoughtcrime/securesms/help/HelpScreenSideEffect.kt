/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

sealed interface HelpScreenSideEffect {
  data class OpenEmail(val subject: String, val body: String) : HelpScreenSideEffect
  data class ShowSnackbar(val messageRes: Int) : HelpScreenSideEffect
}
