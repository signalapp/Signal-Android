/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.attachments.AttachmentId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * A class for tracking restore progress, largely just for debugging purposes. It keeps no state on disk, and is therefore only useful for testing.
 */
object ArchiveRestoreProgress {
  private val TAG = Log.tag(ArchiveRestoreProgress::class.java)

  private var debugAttachmentStartTime: Long = 0
  private val debugTotalAttachments: AtomicInteger = AtomicInteger(0)
  private val debugTotalBytes: AtomicLong = AtomicLong(0)

  private val attachmentProgress: MutableMap<AttachmentId, AttachmentProgressDetails> = ConcurrentHashMap()

  fun onProcessStart() {
    debugAttachmentStartTime = System.currentTimeMillis()
  }

  fun onDownloadStart(attachmentId: AttachmentId) {
    attachmentProgress[attachmentId] = AttachmentProgressDetails(startTimeMs = System.currentTimeMillis())
  }

  fun onDownloadEnd(attachmentId: AttachmentId, totalBytes: Long) {
    val details = attachmentProgress[attachmentId] ?: return
    details.networkFinishTime = System.currentTimeMillis()
    details.totalBytes = totalBytes
  }

  fun onWriteToDiskEnd(attachmentId: AttachmentId) {
    val details = attachmentProgress[attachmentId] ?: return
    attachmentProgress.remove(attachmentId)

    debugTotalAttachments.incrementAndGet()
    debugTotalBytes.addAndGet(details.totalBytes)

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "Attachment restored: $details")
    }
  }

  fun onProcessEnd() {
    if (debugAttachmentStartTime <= 0 || debugTotalAttachments.get() <= 0 || debugTotalBytes.get() <= 0) {
      Log.w(TAG, "Insufficient data to print debug stats.")
      return
    }

    val seconds: Double = (System.currentTimeMillis() - debugAttachmentStartTime).milliseconds.toDouble(DurationUnit.SECONDS)
    val bytesPerSecond: Long = (debugTotalBytes.get() / seconds).toLong()

    Log.w(TAG, "Restore Finished! TotalAttachments=$debugTotalAttachments, TotalBytes=$debugTotalBytes (${debugTotalBytes.get().bytes.toUnitString()}), Rate=$bytesPerSecond bytes/sec (${bytesPerSecond.bytes.toUnitString()}/sec)")
  }

  private class AttachmentProgressDetails(
    val startTimeMs: Long = 0,
    var networkFinishTime: Long = 0,
    var totalBytes: Long = 0
  ) {
    override fun toString(): String {
      if (startTimeMs == 0L || totalBytes == 0L) {
        return "N/A"
      }

      val networkSeconds: Double = (networkFinishTime - startTimeMs).milliseconds.toDouble(DurationUnit.SECONDS)
      val networkBytesPerSecond: Long = (totalBytes / networkSeconds).toLong()

      val diskSeconds: Double = (System.currentTimeMillis() - networkFinishTime).milliseconds.toDouble(DurationUnit.SECONDS)
      val diskBytesPerSecond: Long = (totalBytes / diskSeconds).toLong()

      return "Duration=${System.currentTimeMillis() - startTimeMs}ms, TotalBytes=$totalBytes (${totalBytes.bytes.toUnitString()}), NetworkRate=$networkBytesPerSecond bytes/sec (${networkBytesPerSecond.bytes.toUnitString()}/sec), DiskRate=$diskBytesPerSecond bytes/sec (${diskBytesPerSecond.bytes.toUnitString()}/sec)"
    }
  }
}
