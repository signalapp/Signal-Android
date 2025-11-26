/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import org.signal.core.util.logging.Log

object DiskUtil {

  private val TAG = Log.tag(DiskUtil::class)

  /**
   * Gets the remaining storage usable by the application.
   *
   * @param context The application context
   */
  @JvmStatic
  fun getAvailableSpace(context: Context): ByteSize {
    return if (Build.VERSION.SDK_INT >= 26) {
      getAvailableStorageBytesApi26(context).bytes
    } else {
      return getAvailableStorageBytesLegacy(context).bytes
    }
  }

  /**
   * Gets the total disk size of the volume used by the application.
   *
   * @param context The application context
   */
  @JvmStatic
  fun getTotalDiskSize(context: Context): ByteSize {
    return if (Build.VERSION.SDK_INT >= 26) {
      getTotalDiskSizeApi26(context).bytes
    } else {
      return getTotalDiskSizeLegacy(context).bytes
    }
  }

  @RequiresApi(26)
  private fun getAvailableStorageBytesApi26(context: Context): Long {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
    val appStorageUuid = storageManager.getUuidForPath(context.filesDir)

    return try {
      storageStatsManager.getFreeBytes(appStorageUuid)
    } catch (e: Throwable) {
      Log.w(TAG, "Hit a weird platform bug! Falling back to legacy.", e)
      getAvailableStorageBytesLegacy(context)
    }
  }

  private fun getAvailableStorageBytesLegacy(context: Context): Long {
    val stat = StatFs(context.filesDir.absolutePath)
    return stat.availableBytes
  }

  @RequiresApi(26)
  private fun getTotalDiskSizeApi26(context: Context): Long {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
    val appStorageUuid = storageManager.getUuidForPath(context.filesDir)

    return try {
      storageStatsManager.getTotalBytes(appStorageUuid)
    } catch (e: Throwable) {
      Log.w(TAG, "Hit a weird platform bug! Falling back to legacy.", e)
      getTotalDiskSizeLegacy(context)
    }
  }

  private fun getTotalDiskSizeLegacy(context: Context): Long {
    val stat = StatFs(context.filesDir.absolutePath)
    return stat.totalBytes
  }
}
