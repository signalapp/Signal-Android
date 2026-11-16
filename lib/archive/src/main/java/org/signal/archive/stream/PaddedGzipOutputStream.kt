/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.archive.stream

import androidx.annotation.VisibleForTesting
import org.signal.core.util.writeZeros
import org.signal.libsignal.messagebackup.MessageBackupSizing
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/**
 * GZIPs the content written to it, ends DEFLATE blocks at chosen points, and appends padding once the stream is
 * finished. Ending a block means a full flush, which clears the compression dictionary, so content on either side of
 * one compresses independently and neither side's size tells you anything about the other.
 *
 * The stream runs in two phases, and they decide where blocks end differently:
 *
 * 1. Before the chat items, the caller picks the boundaries by calling [endBlock]. [EncryptedBackupWriter] does so
 *    after the header, after the account data, and after every recipient, so each of those compresses on its own.
 * 2. From [beginChatItemRegion] onward this class picks them itself. [MessageBackupSizing.flushInterval] says how many
 *    uncompressed bytes a block may hold, measured from the start of the region, and a write that would cross that
 *    limit is split at the boundary instead of being allowed to finish first.
 *
 * That limit grows as the region grows, so it is re-read as a block fills rather than fixed when the block opens.
 * [blockLimitBytes] is therefore only ever a lower bound on the real limit.
 *
 * The padding [finish] appends is scaled to [largestBlockBytes], the largest block this stream actually produced in
 * the chat item region. Blocks closed before the region opened are excluded, which falls out of [beginChatItemRegion]
 * flushing before it records the region start.
 *
 * Bolting zeros onto the end of a GZIP stream is fine, because GZIP is smart enough to ignore them. This means readers
 * of this data don't have to do anything special.
 */
class PaddedGzipOutputStream private constructor(
  private val outputStream: SizeObservingOutputStream,
  private val estimatedTotalUncompressedSize: Long?
) : GZIPOutputStream(outputStream) {

  companion object {
    private const val NO_CHAT_ITEM_REGION = -1L
  }

  constructor(outputStream: OutputStream, estimatedTotalUncompressedSize: Long? = null) : this(SizeObservingOutputStream(outputStream), estimatedTotalUncompressedSize)

  private val singleByte = ByteArray(1)

  private var uncompressedBytesAtBlockStart: Long = 0

  /** [NO_CHAT_ITEM_REGION] until [beginChatItemRegion], which is what switches this stream from caller driven to scheduled flushing. */
  private var chatItemRegionStart: Long = NO_CHAT_ITEM_REGION

  private var blockLimitBytes: Long = 0
  private var largestClosedBlockBytes: Long = 0
  private var padded = false
  private var compressedBytesBeforePadding: Long = 0

  var uncompressedBytes: Long = 0
    private set

  @VisibleForTesting
  val compressedBytes: Long
    get() = if (padded) compressedBytesBeforePadding else outputStream.size

  @VisibleForTesting
  var paddingBytes: Long = 0
    private set

  @VisibleForTesting
  val largestBlockBytes: Long
    get() = if (chatItemRegionStart == NO_CHAT_ITEM_REGION) largestClosedBlockBytes else maxOf(largestClosedBlockBytes, uncompressedBytes - uncompressedBytesAtBlockStart)

  fun endBlock() {
    fullFlush()
  }

  fun beginChatItemRegion() {
    if (chatItemRegionStart != NO_CHAT_ITEM_REGION) {
      return
    }

    fullFlush()
    chatItemRegionStart = uncompressedBytes
    blockLimitBytes = flushIntervalAt(0)
  }

  @Synchronized
  override fun write(b: Int) {
    singleByte[0] = b.toByte()
    write(singleByte, 0, 1)
  }

  @Synchronized
  override fun write(b: ByteArray, off: Int, len: Int) {
    if (chatItemRegionStart == NO_CHAT_ITEM_REGION) {
      super.write(b, off, len)
      uncompressedBytes += len
      return
    }

    var written = 0
    while (written < len) {
      val roomInBlock = (blockLimitBytes - (uncompressedBytes - uncompressedBytesAtBlockStart)).coerceAtLeast(1L)
      val chunk = minOf((len - written).toLong(), roomInBlock).toInt()

      super.write(b, off + written, chunk)
      uncompressedBytes += chunk
      written += chunk

      if (uncompressedBytes - uncompressedBytesAtBlockStart >= blockLimitBytes) {
        blockLimitBytes = flushIntervalAt(uncompressedBytes - chatItemRegionStart)

        if (uncompressedBytes - uncompressedBytesAtBlockStart >= blockLimitBytes) {
          fullFlush()
        }
      }
    }
  }

  override fun finish() {
    super.finish()

    if (padded) {
      return
    }
    padded = true
    compressedBytesBeforePadding = outputStream.size

    paddingBytes = MessageBackupSizing.paddingSize(largestBlockBytes, compressedBytesBeforePadding)
    outputStream.writeZeros(paddingBytes)
  }

  /** zlib can consume all of its input while still holding flush output, so only a `deflate` call that underfills [buf] proves the flush finished. */
  private fun fullFlush() {
    val blockBytes = uncompressedBytes - uncompressedBytesAtBlockStart

    if (blockBytes > 0) {
      var produced: Int
      do {
        produced = def.deflate(buf, 0, buf.size, Deflater.FULL_FLUSH)
        if (produced > 0) {
          outputStream.write(buf, 0, produced)
        }
      } while (produced == buf.size)
    }

    if (chatItemRegionStart != NO_CHAT_ITEM_REGION) {
      largestClosedBlockBytes = maxOf(largestClosedBlockBytes, blockBytes)
      blockLimitBytes = flushIntervalAt(uncompressedBytes - chatItemRegionStart)
    }

    uncompressedBytesAtBlockStart = uncompressedBytes
  }

  private fun flushIntervalAt(regionPosition: Long): Long {
    return if (estimatedTotalUncompressedSize != null) {
      MessageBackupSizing.flushInterval(regionPosition, estimatedTotalUncompressedSize)
    } else {
      MessageBackupSizing.flushInterval(regionPosition)
    }
  }

  /**
   * Tracks the compressed length, which [MessageBackupSizing.paddingSize] uses only to enforce the minimum backup
   * size. How much padding is drawn otherwise comes from [largestBlockBytes], not from this.
   */
  private class SizeObservingOutputStream(val wrapped: OutputStream) : FilterOutputStream(wrapped) {

    var size: Long = 0L
      private set

    override fun write(b: Int) {
      wrapped.write(b)
      size++
    }

    override fun write(b: ByteArray) {
      wrapped.write(b)
      size += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      wrapped.write(b, off, len)
      size += len
    }
  }
}
