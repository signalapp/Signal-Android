/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import androidx.annotation.VisibleForTesting
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.ThrottledDebouncer
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

/**
 * During media upload/restore, we tend to hammer the database from a lot of different threads at once. This can block writes for more urgent things, like message
 * sends. To reduce the impact, we put all of our database writes on a single-thread executor.
 */
object ArchiveDatabaseExecutor {

  @VisibleForTesting
  const val THREAD_NAME = "archive-db"

  private val executor = Executors.newSingleThreadExecutor { Thread(it, THREAD_NAME) }

  /**
   * By default, downloading/uploading an attachment wants to notify a bunch of database observation listeners. This slams the observer so hard that other
   * people using it will experience massive delays in notifications. To avoid this, we turn off notifications for downloads, and then use this notifier to
   * push some out every so often.
   */
  val databaseObserverNotifier = ThrottledDebouncer(5.seconds.inWholeMilliseconds)

  val notifyAttachmentObservers = {
    AppDependencies.databaseObserver.notifyConversationListListeners()
    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
  }

  val notifyAttachmentAndChatListObservers = {
    AppDependencies.databaseObserver.notifyConversationListListeners()
    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
  }

  fun <T> runBlocking(block: () -> T): T {
    if (Thread.currentThread().name.equals(THREAD_NAME)) {
      return block()
    }

    return try {
      executor.submit(block).get()
    } catch (e: ExecutionException) {
      throw e.cause ?: e
    }
  }

  fun throttledNotifyAttachmentObservers() {
    databaseObserverNotifier.publish(notifyAttachmentObservers)
  }

  fun throttledNotifyAttachmentAndChatListObservers() {
    databaseObserverNotifier.publish(notifyAttachmentAndChatListObservers)
  }
}
