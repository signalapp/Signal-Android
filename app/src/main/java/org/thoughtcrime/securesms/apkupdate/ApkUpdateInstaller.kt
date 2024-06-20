/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.apkupdate

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.StreamUtil
import org.signal.core.util.getDownloadManager
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ApkUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.FileUtils
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

object ApkUpdateInstaller {

  private val TAG = Log.tag(ApkUpdateInstaller::class.java)

  /**
   * Installs the downloaded APK silently if possible. If not, prompts the user with a notification to install.
   * May show errors instead under certain conditions.
   *
   * A common pattern you may see is that this is called with [userInitiated] = false (or some other state
   * that prevents us from auto-updating, like the app being in the foreground), causing this function
   * to show an install prompt notification. The user clicks that notification, calling this with
   * [userInitiated] = true, and then everything installs.
   */
  fun installOrPromptForInstall(context: Context, downloadId: Long, userInitiated: Boolean) {
    if (downloadId != SignalStore.apkUpdate.downloadId) {
      Log.w(TAG, "DownloadId doesn't match the one we're waiting for (current: $downloadId, expected: ${SignalStore.apkUpdate.downloadId})! We likely have newer data. Ignoring.")
      ApkUpdateNotifications.dismissInstallPrompt(context)
      AppDependencies.jobManager.add(ApkUpdateJob())
      return
    }

    val digest = SignalStore.apkUpdate.digest
    if (digest == null) {
      Log.w(TAG, "DownloadId matches, but digest is null! Inconsistent state. Failing and clearing state.")
      SignalStore.apkUpdate.clearDownloadAttributes()
      ApkUpdateNotifications.showInstallFailed(context, ApkUpdateNotifications.FailureReason.UNKNOWN)
      return
    }

    if (!isMatchingDigest(context, downloadId, digest)) {
      Log.w(TAG, "DownloadId matches, but digest does not! Bad download or inconsistent state. Failing and clearing state.")
      SignalStore.apkUpdate.clearDownloadAttributes()
      ApkUpdateNotifications.showInstallFailed(context, ApkUpdateNotifications.FailureReason.UNKNOWN)
      return
    }

    if (!userInitiated && !shouldAutoUpdate()) {
      Log.w(TAG, "Not user-initiated and not eligible for auto-update. Prompting. (API=${Build.VERSION.SDK_INT}, Foreground=${AppDependencies.appForegroundObserver.isForegrounded}, AutoUpdate=${SignalStore.apkUpdate.autoUpdate})")
      ApkUpdateNotifications.showInstallPrompt(context, downloadId)
      return
    }

    try {
      installApk(context, downloadId, userInitiated)
    } catch (e: IOException) {
      Log.w(TAG, "Hit IOException when trying to install APK!", e)
      SignalStore.apkUpdate.clearDownloadAttributes()
      ApkUpdateNotifications.showInstallFailed(context, ApkUpdateNotifications.FailureReason.UNKNOWN)
    } catch (e: SecurityException) {
      Log.w(TAG, "Hit SecurityException when trying to install APK!", e)
      SignalStore.apkUpdate.clearDownloadAttributes()
      ApkUpdateNotifications.showInstallFailed(context, ApkUpdateNotifications.FailureReason.UNKNOWN)
    }
  }

  @Throws(IOException::class, SecurityException::class)
  private fun installApk(context: Context, downloadId: Long, userInitiated: Boolean) {
    val apkInputStream: InputStream? = getDownloadedApkInputStream(context, downloadId)
    if (apkInputStream == null) {
      Log.w(TAG, "Could not open download APK input stream!")
      return
    }

    Log.d(TAG, "Beginning APK install...")
    val packageInstaller: PackageInstaller = context.packageManager.packageInstaller

    val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
      // At this point, we always want to set this if possible, since we've already prompted the user with our own notification when necessary.
      // This lets us skip the system-generated notification.
      if (Build.VERSION.SDK_INT >= 31) {
        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
      }
    }

    Log.d(TAG, "Creating install session...")
    val sessionId: Int = packageInstaller.createSession(sessionParams)
    val session: PackageInstaller.Session = packageInstaller.openSession(sessionId)

    Log.d(TAG, "Writing APK data...")
    session.use { activeSession ->
      val sessionOutputStream = activeSession.openWrite(context.packageName, 0, -1)
      StreamUtil.copy(apkInputStream, sessionOutputStream)
    }

    val installerPendingIntent = PendingIntent.getBroadcast(
      context,
      sessionId,
      Intent(context, ApkUpdatePackageInstallerReceiver::class.java).apply {
        putExtra(ApkUpdatePackageInstallerReceiver.EXTRA_USER_INITIATED, userInitiated)
        putExtra(ApkUpdatePackageInstallerReceiver.EXTRA_DOWNLOAD_ID, downloadId)
      },
      PendingIntentFlags.mutable() or PendingIntentFlags.updateCurrent()
    )

    Log.d(TAG, "Committing session...")
    session.commit(installerPendingIntent.intentSender)
  }

  private fun getDownloadedApkInputStream(context: Context, downloadId: Long): InputStream? {
    return try {
      FileInputStream(context.getDownloadManager().openDownloadedFile(downloadId).fileDescriptor)
    } catch (e: IOException) {
      Log.w(TAG, e)
      null
    }
  }

  private fun isMatchingDigest(context: Context, downloadId: Long, expectedDigest: ByteArray): Boolean {
    return try {
      FileInputStream(context.getDownloadManager().openDownloadedFile(downloadId).fileDescriptor).use { stream ->
        val digest = FileUtils.getFileDigest(stream)
        MessageDigest.isEqual(digest, expectedDigest)
      }
    } catch (e: IOException) {
      Log.w(TAG, e)
      false
    }
  }

  private fun shouldAutoUpdate(): Boolean {
    // TODO Auto-updates temporarily restricted to nightlies. Once we have designs for allowing users to opt-out of auto-updates, we can re-enable this
    return Environment.IS_NIGHTLY && Build.VERSION.SDK_INT >= 31 && SignalStore.apkUpdate.autoUpdate && !AppDependencies.appForegroundObserver.isForegrounded
  }
}
