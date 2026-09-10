/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.net.Uri

/**
 * A request from [CustomNotificationsSettingsViewModel] to open the system ringtone picker.
 *
 * @param target Which sound the user is choosing, and therefore which kind of picker to open.
 * @param existing The sound the picker should open on, or null for silence.
 */
data class RingtonePickerRequest(
  val target: Target,
  val existing: Uri?
) {
  enum class Target {
    MESSAGE,
    CALL
  }
}
