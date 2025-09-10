/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Activity result contract for launching the system ringtone picker to select notification sounds.
 *
 * Supports selecting sounds for both message notifications and call ringtones through the
 * Android system's ringtone picker interface.
 *
 * @param target Specifies whether to configure sounds for messages or calls
 */
class NotificationSoundSelectionContract(
  private val target: Target
) : ActivityResultContract<Unit, Uri?>() {

  /**
   * Defines the type of notification sound to configure.
   */
  enum class Target {
    /** Message notification sounds */
    MESSAGE,

    /** Call ringtones */
    CALL
  }

  override fun createIntent(context: Context, input: Unit): Intent {
    return when (target) {
      Target.MESSAGE -> createIntentForMessageSoundSelection()
      Target.CALL -> createIntentForCallSoundSelection()
    }
  }

  private fun createIntentForMessageSoundSelection(): Intent {
    val current = SignalStore.settings.messageNotificationSound

    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
      .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
      .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
      .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
      .putExtra(
        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
        Settings.System.DEFAULT_NOTIFICATION_URI
      )
      .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
  }

  private fun createIntentForCallSoundSelection(): Intent {
    val current = SignalStore.settings.callRingtone

    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
      .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
      .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
      .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
      .putExtra(
        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
        Settings.System.DEFAULT_RINGTONE_URI
      )
      .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
  }

  override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
    return intent?.getParcelableExtraCompat(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
  }
}
