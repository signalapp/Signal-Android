/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.apkupdate

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Provided to the DownloadManager as a callback receiver for when it has finished downloading the APK we're trying to install.
 *
 * Registered in the manifest to list to [DownloadManager.ACTION_DOWNLOAD_COMPLETE].
 */
class ApkUpdateDownloadManagerReceiver : BroadcastReceiver() {

  companion object {
    private val TAG = Log.tag(ApkUpdateDownloadManagerReceiver::class.java)
  }

  override fun onReceive(context: Context, intent: Intent) {
    Log.i(TAG, "onReceive()")

    if (DownloadManager.ACTION_DOWNLOAD_COMPLETE != intent.action) {
      Log.i(TAG, "Unexpected action: " + intent.action)
      return
    }

    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2)
    if (downloadId != SignalStore.apkUpdate.downloadId) {
      Log.w(TAG, "downloadId doesn't match the one we're waiting for! Ignoring.")
      return
    }

    ApkUpdateInstaller.installOrPromptForInstall(context, downloadId, userInitiated = false)
  }
}
