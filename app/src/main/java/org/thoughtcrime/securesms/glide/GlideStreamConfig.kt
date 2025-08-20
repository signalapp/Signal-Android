/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide

import android.app.ActivityManager
import android.content.Context
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.core.util.gibiBytes
import org.signal.core.util.mebiBytes
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.glide.GlideStreamConfig.MAX_MARK_LIMIT
import org.thoughtcrime.securesms.glide.GlideStreamConfig.MIN_MARK_LIMIT

object GlideStreamConfig {
  private val MIN_MARK_LIMIT: ByteSize = 5.mebiBytes // Glide default
  private val MAX_MARK_LIMIT: ByteSize = 8.mebiBytes

  private val LOW_MEMORY_THRESHOLD: ByteSize = 4.gibiBytes
  private val HIGH_MEMORY_THRESHOLD: ByteSize = 12.gibiBytes

  @JvmStatic
  val markReadLimitBytes: Int by lazy { calculateScaledMarkLimit(context = AppDependencies.application).inWholeBytes.toInt() }

  /**
   * Calculates buffer size, scaling proportionally from [MIN_MARK_LIMIT] to [MAX_MARK_LIMIT] based on how much memory the device has.
   */
  private fun calculateScaledMarkLimit(context: Context): ByteSize {
    val deviceMemory = getAvailableDeviceMemory(context)
    return when {
      deviceMemory <= LOW_MEMORY_THRESHOLD -> MIN_MARK_LIMIT
      deviceMemory >= HIGH_MEMORY_THRESHOLD -> MAX_MARK_LIMIT
      else -> calculateScaledSize(deviceMemory)
    }
  }

  private fun getAvailableDeviceMemory(context: Context): ByteSize {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo().apply {
      activityManager.getMemoryInfo(this)
    }
    return memoryInfo.totalMem.bytes
  }

  private fun calculateScaledSize(deviceMemory: ByteSize): ByteSize {
    val ratio: Float = (deviceMemory - LOW_MEMORY_THRESHOLD).percentageOf(HIGH_MEMORY_THRESHOLD - LOW_MEMORY_THRESHOLD)
    val offsetBytes = (ratio * (MAX_MARK_LIMIT.inWholeBytes - MIN_MARK_LIMIT.inWholeBytes)).toLong()
    return MIN_MARK_LIMIT + ByteSize(offsetBytes)
  }
}
