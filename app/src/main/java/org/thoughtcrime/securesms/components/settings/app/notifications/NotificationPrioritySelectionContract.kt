/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import org.thoughtcrime.securesms.notifications.NotificationChannels

/**
 * Activity result contract for launching the system notification channel settings screen
 * for the messages notification channel.
 *
 * This contract allows users to configure notification priority, sound, vibration, and other
 * channel-specific settings through the system's native notification settings UI.
 */
class NotificationPrioritySelectionContract : ActivityResultContract<Unit, Unit>() {
  override fun createIntent(context: Context, input: Unit): Intent {
    return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
      .putExtra(
        Settings.EXTRA_CHANNEL_ID,
        NotificationChannels.getInstance().messagesChannel
      )
      .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
  }

  override fun parseResult(resultCode: Int, intent: Intent?) = Unit
}
