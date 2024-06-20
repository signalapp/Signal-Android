/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.apkupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.apkupdate.ApkUpdateNotifications.FailureReason
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * This is the receiver that is triggered by the [PackageInstaller] to notify of various events. Package installation is initiated
 * in [ApkUpdateInstaller].
 */
class ApkUpdatePackageInstallerReceiver : BroadcastReceiver() {

  companion object {
    private val TAG = Log.tag(ApkUpdatePackageInstallerReceiver::class.java)

    const val EXTRA_USER_INITIATED = "signal.user_initiated"
    const val EXTRA_DOWNLOAD_ID = "signal.download_id"
  }

  override fun onReceive(context: Context, intent: Intent?) {
    val statusCode: Int = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) ?: -1
    val statusMessage: String? = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
    val userInitiated = intent?.getBooleanExtra(EXTRA_USER_INITIATED, false) ?: false

    Log.w(TAG, "[onReceive] Status: $statusCode, Message: $statusMessage")

    when (statusCode) {
      PackageInstaller.STATUS_SUCCESS -> {
        if (SignalStore.apkUpdate.lastApkUploadTime != SignalStore.apkUpdate.pendingApkUploadTime) {
          Log.i(TAG, "Update installed successfully! Updating our lastApkUploadTime to ${SignalStore.apkUpdate.pendingApkUploadTime}")
          SignalStore.apkUpdate.lastApkUploadTime = SignalStore.apkUpdate.pendingApkUploadTime
          ApkUpdateNotifications.showAutoUpdateSuccess(context)
        } else {
          Log.i(TAG, "Spurious 'success' notification?")
        }
      }
      PackageInstaller.STATUS_PENDING_USER_ACTION -> handlePendingUserAction(context, userInitiated, intent!!)
      PackageInstaller.STATUS_FAILURE_ABORTED -> ApkUpdateNotifications.showInstallFailed(context, FailureReason.ABORTED)
      PackageInstaller.STATUS_FAILURE_BLOCKED -> ApkUpdateNotifications.showInstallFailed(context, FailureReason.BLOCKED)
      PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> ApkUpdateNotifications.showInstallFailed(context, FailureReason.INCOMPATIBLE)
      PackageInstaller.STATUS_FAILURE_INVALID -> ApkUpdateNotifications.showInstallFailed(context, FailureReason.INVALID)
      PackageInstaller.STATUS_FAILURE_CONFLICT -> ApkUpdateNotifications.showInstallFailed(context, FailureReason.CONFLICT)
      PackageInstaller.STATUS_FAILURE_STORAGE -> ApkUpdateNotifications.showInstallFailed(context, FailureReason.STORAGE)
      PackageInstaller.STATUS_FAILURE_TIMEOUT -> ApkUpdateNotifications.showInstallFailed(context, FailureReason.TIMEOUT)
      PackageInstaller.STATUS_FAILURE -> ApkUpdateNotifications.showInstallFailed(context, FailureReason.UNKNOWN)
      else -> Log.w(TAG, "Unknown status! $statusCode")
    }
  }

  private fun handlePendingUserAction(context: Context, userInitiated: Boolean, intent: Intent) {
    val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -2)

    if (!userInitiated) {
      Log.w(TAG, "Not user-initiated, but needs user action! Showing prompt notification.")
      ApkUpdateNotifications.showInstallPrompt(context, downloadId)
      return
    }

    val promptIntent: Intent? = intent.getParcelableExtraCompat(Intent.EXTRA_INTENT, Intent::class.java)
    if (promptIntent == null) {
      Log.w(TAG, "Missing prompt intent! Showing prompt notification instead.")
      ApkUpdateNotifications.showInstallPrompt(context, downloadId)
      return
    }

    promptIntent.apply {
      putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
      putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(promptIntent)
  }
}
