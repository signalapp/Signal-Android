/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.signal.core.util.PendingIntentFlags
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.IssueEntry
import org.thoughtcrime.securesms.database.model.IssuePriority
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Records noteworthy runtime issues to the [LogDatabase] issue table on a low-priority background thread.
 *
 * Issues are assigned an [IssuePriority]. Issues whose priority is at or above the user's configured notification
 * threshold ([SignalStore.internal] `issueNotificationPriority`) additionally raise a user notification.
 * Lower-priority issues simply sit in the table to be reviewed later via the internal issues screen or a submitted debug log.
 *
 * To limit any potential perf overhead for external users, issues are gated to be saved at most once ever [NON_INTERNAL_DEBOUNCE_MS].
 */
object IssueReporter {

  const val ISSUE_SLOW_DATABASE_WRITE = "Slow Database Write"
  const val ISSUE_SLOW_DATABASE_READ = "Slow Database Read"
  const val ISSUE_SLOW_DATABASE_LOCK = "Slow Database Lock"

  const val SLOW_WRITE_LOW_PRIORITY_MS = 1_000L
  const val SLOW_WRITE_MEDIUM_PRIORITY_MS = 5_000L
  const val SLOW_READ_LOW_PRIORITY_MS = 3_000L
  const val SLOW_READ_MEDIUM_PRIORITY_MS = 10_000L
  const val SLOW_LOCK_LOW_PRIORITY_MS = 1_000L
  const val SLOW_LOCK_MEDIUM_PRIORITY_MS = 5_000L

  private const val NON_INTERNAL_DEBOUNCE_MS = 5_000L

  private val IGNORED_DB_STACK_TRACE_CLASSES = listOf(
    "BackupRepository",
    "BackupMessagesJob",
    "ArchiveAttachmentReconciliationJob",
    "SubmitDebugLogRepository"
  )

  private val requests = IssueRequests()

  @Volatile
  private var lastInsertTime = 0L

  init {
    WriteThread(requests).apply {
      priority = Thread.MIN_PRIORITY
    }.start()
  }

  /**
   * Records a generic issue. Safe to call from any thread.
   */
  @JvmStatic
  @JvmOverloads
  fun report(name: String, description: String, throwable: Throwable? = null, priority: IssuePriority = IssuePriority.LOW, duration: Long? = null) {
    val now = System.currentTimeMillis()

    if (!RemoteConfig.internalUser) {
      if (now - lastInsertTime < NON_INTERNAL_DEBOUNCE_MS) {
        return
      }
      lastInsertTime = now
    }

    requests.add(IssueRequest(now, BuildConfig.VERSION_NAME, name, description, throwable, priority, duration))

    maybeNotify(name, priority)
  }

  @JvmStatic
  fun noteSlowDatabaseWrite(query: String?, durationMs: Long, throwable: Throwable) {
    if (isExpectedSlowDatabaseOperation()) {
      return
    }

    val priority = when {
      durationMs >= SLOW_WRITE_MEDIUM_PRIORITY_MS -> IssuePriority.MEDIUM
      durationMs >= SLOW_WRITE_LOW_PRIORITY_MS -> IssuePriority.LOW
      else -> return
    }

    report(ISSUE_SLOW_DATABASE_WRITE, query?.trim() ?: "", throwable, priority = priority, duration = durationMs)
  }

  /**
   * Notes time spent waiting to acquire the write lock to begin a transaction. This is distinct from a slow write: the
   * write itself may be fast, but it was blocked waiting on another holder of the lock (e.g. a long-running transaction).
   */
  @JvmStatic
  fun noteSlowDatabaseLockAcquire(durationMs: Long, throwable: Throwable) {
    if (isExpectedSlowDatabaseOperation()) {
      return
    }

    val priority = when {
      durationMs >= SLOW_LOCK_MEDIUM_PRIORITY_MS -> IssuePriority.MEDIUM
      durationMs >= SLOW_LOCK_LOW_PRIORITY_MS -> IssuePriority.LOW
      else -> return
    }

    report(ISSUE_SLOW_DATABASE_LOCK, "Long wait to acquire the write lock to BEGIN a transaction.", throwable, priority = priority, duration = durationMs)
  }

  @JvmStatic
  fun noteSlowDatabaseRead(query: String?, durationMs: Long, throwable: Throwable) {
    if (isExpectedSlowDatabaseOperation()) {
      return
    }

    val priority = when {
      durationMs >= SLOW_READ_MEDIUM_PRIORITY_MS -> IssuePriority.MEDIUM
      durationMs >= SLOW_READ_LOW_PRIORITY_MS -> IssuePriority.LOW
      else -> return
    }

    report(ISSUE_SLOW_DATABASE_READ, query?.trim() ?: "", throwable, priority = priority, duration = durationMs)
  }

  private fun maybeNotify(name: String, priority: IssuePriority) {
    if (!RemoteConfig.internalUser) {
      return
    }

    if (priority.value < SignalStore.internal.issueNotificationPriority.value) {
      return
    }

    val context = AppDependencies.application
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().INTERNAL_ISSUES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Issue detected")
      .setContentText("$name (${priority.label}). Please tap to get a debug log.")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)
  }

  private fun isExpectedSlowDatabaseOperation(): Boolean {
    return Thread
      .currentThread()
      .stackTrace
      .any { element ->
        IGNORED_DB_STACK_TRACE_CLASSES.any {
          element.className.contains(it)
        }
      }
  }

  private data class IssueRequest(
    val createdAt: Long,
    val version: String,
    val name: String,
    val description: String,
    val throwable: Throwable?,
    val priority: IssuePriority,
    val duration: Long?
  )

  private class WriteThread(
    private val requests: IssueRequests
  ) : Thread("signal-issue-reporter") {

    private val db: LogDatabase by lazy { LogDatabase.getInstance(AppDependencies.application) }

    override fun run() {
      var buffer = mutableListOf<IssueRequest>()
      while (true) {
        buffer = requests.blockForRequests(buffer)
        db.issues.insert(buffer.asSequence().map { requestToEntry(it) }, System.currentTimeMillis())
        buffer.clear()
      }
    }

    private fun requestToEntry(request: IssueRequest): IssueEntry {
      return IssueEntry(
        createdAt = request.createdAt,
        version = request.version,
        name = request.name,
        description = request.description,
        stackTrace = request.throwable?.let { stackTraceToString(it) },
        priority = request.priority,
        duration = request.duration
      )
    }

    private fun stackTraceToString(throwable: Throwable): String {
      val outputStream = ByteArrayOutputStream()
      throwable.printStackTrace(PrintStream(outputStream))
      return String(outputStream.toByteArray())
    }
  }

  private class IssueRequests {
    // Mutable because it gets replaced in blockForRequests, to save a copy operation.
    var requests = mutableListOf<IssueRequest>()
    val lock = Object()

    fun add(request: IssueRequest) {
      synchronized(lock) {
        requests.add(request)
        lock.notify()
      }
    }

    /**
     * Blocks until requests are available. When they are, returns all pending requests and swaps `swapBuffer` with the
     * internal storage for future requests. `swapBuffer` should already be empty upon entry to this method.
     */
    fun blockForRequests(swapBuffer: MutableList<IssueRequest>): MutableList<IssueRequest> {
      synchronized(lock) {
        while (requests.isEmpty()) {
          lock.wait()
        }

        val result = requests
        requests = swapBuffer
        return result
      }
    }
  }
}
