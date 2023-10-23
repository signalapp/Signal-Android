/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.apkupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.signal.core.util.logging.Log

/**
 * Receiver that is triggered based on various notification actions that can be taken on update-related notifications.
 */
class ApkUpdateNotificationReceiver : BroadcastReceiver() {

  companion object {
    private val TAG = Log.tag(ApkUpdateNotificationReceiver::class.java)

    const val ACTION_INITIATE_INSTALL = "signal.apk_update_notification.initiate_install"
    const val EXTRA_DOWNLOAD_ID = "signal.download_id"
  }

  override fun onReceive(context: Context, intent: Intent?) {
    if (intent == null) {
      Log.w(TAG, "Null intent")
      return
    }

    val downloadId: Long = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -2)

    when (val action: String? = intent.action) {
      ACTION_INITIATE_INSTALL -> handleInstall(context, downloadId)
      else -> Log.w(TAG, "Unrecognized notification action: $action")
    }
  }

  private fun handleInstall(context: Context, downloadId: Long) {
    Log.i(TAG, "Got action to install.")
    ApkUpdateInstaller.installOrPromptForInstall(context, downloadId, userInitiated = true)
  }
}
