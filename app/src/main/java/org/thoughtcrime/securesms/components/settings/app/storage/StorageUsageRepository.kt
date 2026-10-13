/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.signal.core.util.Stopwatch
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTables
import org.thoughtcrime.securesms.dependencies.AppDependencies
import java.io.File

/**
 * Computes how much on-device storage the app is using, bucketed into the categories of [StorageUsage].
 *
 * Media sizes come from the attachment table. Messages is the main database (including its WAL and journal files)
 * and stickers is the sticker directory. Everything else that lives in the app's data directories (other databases,
 * thumbnails, caches, logs, transient files) lands in other.
 */
class StorageUsageRepository(
  private val context: Context = AppDependencies.application,
  private val scope: CoroutineScope = CoroutineScope(SignalDispatchers.IO + SupervisorJob())
) {

  companion object {
    private val TAG = Log.tag(StorageUsageRepository::class)

    val instance: StorageUsageRepository by lazy { StorageUsageRepository() }
  }

  private val store = MutableStateFlow<StorageUsage?>(null)
  private var inFlight: Job? = null

  /** The most recently computed usage, or null if nothing has been computed yet. */
  val usage: StateFlow<StorageUsage?> = store.asStateFlow()

  /** Computes fresh usage in the background and publishes it to [usage]. */
  fun refresh() {
    synchronized(this) {
      if (inFlight?.isActive == true) {
        return
      }

      inFlight = scope.launch {
        store.value = computeStorageUsage()
      }
    }
  }

  @WorkerThread
  private fun computeStorageUsage(): StorageUsage {
    val stopwatch = Stopwatch("storage-usage")

    val media = SignalDatabase.media.getStorageBreakdown()
    stopwatch.split("media")

    val messages = context.getDatabasePath(SignalDatabase.DATABASE_NAME).parentFile?.listFiles()
      ?.filter { it.name == SignalDatabase.DATABASE_NAME || it.name.startsWith("${SignalDatabase.DATABASE_NAME}-") }
      ?.sumOf { it.sizeOnDisk() }
      ?: 0
    stopwatch.split("messages")

    val stickers = context.getDir(StickerTables.DIRECTORY, Context.MODE_PRIVATE).sizeOnDisk()
    stopwatch.split("stickers")

    val total = getTotalSizeFromStorageStats() ?: getTotalSizeFromDirectoryWalk()
    stopwatch.split("total")

    val mediaTotal = media.photoSize + media.videoSize + media.audioSize + media.documentSize
    val other = (total - mediaTotal - messages - stickers).coerceAtLeast(0)

    stopwatch.stop(TAG)

    return StorageUsage(
      photos = media.photoSize,
      videos = media.videoSize,
      files = media.documentSize,
      audio = media.audioSize,
      messages = messages,
      stickers = stickers,
      other = other
    )
  }

  /**
   * Retrieves the total disk usage from the system if available, otherwise null.
   */
  private fun getTotalSizeFromStorageStats(): Long? {
    if (Build.VERSION.SDK_INT < 26) {
      return null
    }

    return try {
      val storageStatsManager = ContextCompat.getSystemService(context, StorageStatsManager::class.java) ?: return null
      storageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, context.packageName, Process.myUserHandle()).dataBytes
    } catch (e: Exception) {
      Log.w(TAG, "Unable to query storage stats, falling back to walking the data directory.", e)
      null
    }
  }

  private fun getTotalSizeFromDirectoryWalk(): Long {
    return listOfNotNull(ContextCompat.getDataDir(context), context.externalCacheDir, context.getExternalFilesDir(null))
      .distinct()
      .sumOf { it.sizeOnDisk() }
  }

  private fun File.sizeOnDisk(): Long {
    val stat = try {
      Os.lstat(path)
    } catch (e: ErrnoException) {
      Log.w(TAG, "Unable to stat $name: ${e.message}")
      return 0
    }

    return when {
      OsConstants.S_ISLNK(stat.st_mode) -> 0
      OsConstants.S_ISDIR(stat.st_mode) -> listFiles()?.sumOf { it.sizeOnDisk() } ?: 0
      OsConstants.S_ISREG(stat.st_mode) -> stat.st_size
      else -> 0
    }
  }
}
