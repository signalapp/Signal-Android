/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import org.signal.core.models.storage.KeepMessagesDuration

/**
 * Everything [KeepMessagesSettingsScreen] needs to render.
 */
data class KeepMessagesSettingsState(
  val selection: KeepMessagesDuration = KeepMessagesDuration.FOREVER,
  val dialog: Dialog = Dialog.None
) {
  sealed interface Dialog {
    data object None : Dialog
    data class ConfirmDuration(val duration: KeepMessagesDuration) : Dialog
  }
}
