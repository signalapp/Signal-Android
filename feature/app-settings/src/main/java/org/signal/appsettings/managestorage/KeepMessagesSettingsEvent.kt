/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import org.signal.core.models.storage.KeepMessagesDuration

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface KeepMessagesSettingsEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : KeepMessagesSettingsEvent

  /** The user picked a duration to keep messages. */
  data class DurationSelected(val duration: KeepMessagesDuration) : KeepMessagesSettingsEvent

  /** The user confirmed the duration in the dialog. */
  data object DurationConfirmed : KeepMessagesSettingsEvent

  /** Dismisses the displayed dialog. */
  data object DialogDismissed : KeepMessagesSettingsEvent
}
