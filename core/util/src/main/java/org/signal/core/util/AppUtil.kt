/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.signal.core.util.logging.Log

object AppUtil {

  private val TAG = Log.tag(AppUtil::class)

  private const val RESTART_REQUEST_CODE = 1
  private const val RESTART_DELAY_MS = 250L

  /**
   * Restarts the application. Should generally only be used for internal tools.
   */
  @JvmStatic
  fun restart(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (launchIntent != null) {
      context.startActivity(launchIntent)
    }

    Runtime.getRuntime().exit(0)
  }

  /**
   * DANGER! Wipes ALL local app data (databases, key-value store, shared prefs, files) and attempts to relaunch the app
   * into a fresh state.
   */
  @JvmStatic
  fun clearAllDataAndRestart(context: Context) {
    scheduleRestart(context)

    val activityManager = ContextCompat.getSystemService(context, ActivityManager::class.java)
    if (activityManager == null || !activityManager.clearApplicationUserData()) {
      Log.w(TAG, "Could not wipe app data; falling back to a plain restart.")
      restart(context)
    }
  }

  private fun scheduleRestart(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    if (launchIntent == null) {
      Log.w(TAG, "No launch intent available; cannot schedule a relaunch.")
      return
    }

    val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java)
    if (alarmManager == null) {
      Log.w(TAG, "No AlarmManager available; cannot schedule a relaunch.")
      return
    }

    val pendingIntent = PendingIntent.getActivity(context, RESTART_REQUEST_CODE, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
    alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + RESTART_DELAY_MS, pendingIntent)
  }
}
