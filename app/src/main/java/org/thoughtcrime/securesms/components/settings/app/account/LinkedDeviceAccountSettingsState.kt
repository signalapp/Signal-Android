/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

data class LinkedDeviceAccountSettingsState(
  val showDeleteConfirmationDialog: Boolean = false,
  val deleting: Boolean = false,
  val oneTimeEvent: OneTimeEvent? = null
) {
  /** One-time side effects the fragment must carry out (they need Android/fragment context). Consumed via [LinkedDeviceAccountSettingsEvent.ConsumeOneTimeEvent]. */
  sealed interface OneTimeEvent {
    data object OpenLearnMore : OneTimeEvent
    data object NavigateBack : OneTimeEvent
    data object WipeData : OneTimeEvent
    data object DeleteFailed : OneTimeEvent
  }
}
