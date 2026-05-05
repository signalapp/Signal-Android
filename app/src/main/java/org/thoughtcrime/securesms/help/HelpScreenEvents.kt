/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

sealed interface HelpScreenEvents {
  data class OpenEmail(val subject: String, val body: String) : HelpScreenEvents
  data class ShowSnackbar(val messageRes: Int) : HelpScreenEvents
}
