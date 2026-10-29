/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.app.ApplicationExitInfo
import android.content.Context
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.signal.core.util.ServiceUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads the system's record of why our main process exited. A force stop matters most for notification reliability:
 * a force-stopped app receives no FCM pushes until the user relaunches it, and OEM battery managers do exactly that.
 */
@RequiresApi(30)
object ProcessExitReasonTracker {

  private val TAG = Log.tag(ProcessExitReasonTracker::class.java)

  private const val MAX_EXITS = 16

  @WorkerThread
  @JvmStatic
  fun recordNewExits(context: Context) {
    val newExits = newMainProcessExits(getExits(context), context.packageName, SignalStore.misc.lastProcessExitTimestampSeen)

    if (newExits.isEmpty()) {
      Log.d(TAG, "No new process exits.")
      return
    }

    for (exit in newExits) {
      Log.i(TAG, "Process exit: ${describe(exit)}")

      if (exit.reason == ApplicationExitInfo.REASON_USER_REQUESTED) {
        SignalLocalMetrics.ProcessExit.onForceStop()
      }
    }

    SignalStore.misc.lastProcessExitTimestampSeen = newExits.maxOf { it.timestamp }
  }

  @WorkerThread
  fun getExits(context: Context): List<ApplicationExitInfo> {
    return try {
      ServiceUtil.getActivityManager(context).getHistoricalProcessExitReasons(null, 0, MAX_EXITS)
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unable to read process exit reasons.", e)
      emptyList()
    }
  }

  @VisibleForTesting
  fun newMainProcessExits(exits: List<ApplicationExitInfo>, packageName: String, lastSeenTimestamp: Long): List<ApplicationExitInfo> {
    return exits
      .filter { it.processName == packageName && it.timestamp > lastSeenTimestamp }
      .sortedBy { it.timestamp }
  }

  fun describe(exit: ApplicationExitInfo): String {
    return "${reasonToString(exit.reason)} at ${formatTimestamp(exit.timestamp)} (process: ${exit.processName}, status: ${exit.status}, importance: ${exit.importance}, description: ${exit.description})"
  }

  fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.US).format(Date(timestamp))

  fun reasonToString(reason: Int): String {
    return when (reason) {
      ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
      ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
      ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
      ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
      ApplicationExitInfo.REASON_CRASH -> "CRASH"
      ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
      ApplicationExitInfo.REASON_ANR -> "ANR"
      ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
      ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
      ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
      ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED (Force Stop)"
      ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
      ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
      ApplicationExitInfo.REASON_OTHER -> "OTHER"
      ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
      ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
      ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
      else -> "UNRECOGNIZED ($reason)"
    }
  }
}
