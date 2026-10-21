/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import org.signal.core.models.recipients.RecipientId

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface SelectRecipientsEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : SelectRecipientsEvent

  /** The user picked or unpicked someone, leaving [selection] newly picked. */
  data class SelectionChanged(val selection: Set<RecipientId>) : SelectRecipientsEvent {
    override fun toString(): String = "SelectionChanged(size=${selection.size})"
  }

  /** The user tapped add. */
  data object AddClicked : SelectRecipientsEvent
}
