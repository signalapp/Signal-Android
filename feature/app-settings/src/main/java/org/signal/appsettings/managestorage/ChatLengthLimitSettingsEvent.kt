/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface ChatLengthLimitSettingsEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : ChatLengthLimitSettingsEvent

  /** The user picked one of the preset limits. */
  data class LimitSelected(val limit: Int) : ChatLengthLimitSettingsEvent

  /** The user tapped the custom option. */
  data object CustomClicked : ChatLengthLimitSettingsEvent

  /** The user entered [limit] in the custom limit dialog. */
  data class CustomLimitEntered(val limit: Int) : ChatLengthLimitSettingsEvent

  /** The user confirmed the limit in [ChatLengthLimitSettingsState.Dialog.ConfirmLimit]. */
  data object LimitConfirmed : ChatLengthLimitSettingsEvent

  /** Dismisses whatever is in [ChatLengthLimitSettingsState.dialog]. */
  data object DialogDismissed : ChatLengthLimitSettingsEvent
}
