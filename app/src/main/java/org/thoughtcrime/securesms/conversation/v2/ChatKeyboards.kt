/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import org.signal.core.ui.compose.keyboard.KeyboardSheetKey

/** The keyboards the conversation offers in place of the system keyboard. */
object ChatKeyboards {
  val Media = KeyboardSheetKey("conversation.media")
  val Attachment = KeyboardSheetKey("conversation.attachment")
}
