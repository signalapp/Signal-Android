/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Which conversation settings screen [ConversationSettingsFragment] should show.
 *
 * This is a fragment argument so the fragment can pick a view model without a database round-trip first.
 */
enum class ConversationSettingsKind {
  INDIVIDUAL,
  NOTE_TO_SELF,
  RELEASE_NOTES,
  GROUP;

  companion object {
    @JvmStatic
    fun from(recipient: Recipient): ConversationSettingsKind {
      return when {
        recipient.isSelf -> NOTE_TO_SELF
        recipient.isReleaseNotes -> RELEASE_NOTES
        recipient.isGroup -> GROUP
        else -> INDIVIDUAL
      }
    }
  }
}
