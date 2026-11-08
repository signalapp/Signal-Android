/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.archive.stream.PaddedGzipOutputStream
import org.signal.libsignal.messagebackup.MessageBackupSizing
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.GZIPInputStream

class PaddedGzipOutputStreamTest {

  companion object {
    /** Step 7's rule, derived independently: a block ends at the first point where its own length has reached the interval measured at that point. */
    private fun specLargestBlock(regionBytes: Long): Long {
      var position = 0L
      var largest = 0L

      while (position < regionBytes) {
        var length = MessageBackupSizing.flushInterval(position)
        while (true) {
          val refreshed = MessageBackupSizing.flushInterval(position + length)
          if (refreshed <= length) {
            break
          }
          length = refreshed
        }

        val produced = minOf(length, regionBytes - position)
        largest = maxOf(largest, produced)
        position += produced
      }

      return largest
    }

    private fun randomBytes(seed: Long, size: Int): ByteArray {
      return ByteArray(size).also { Random(seed).nextBytes(it) }
    }

    private fun gunzip(data: ByteArray): ByteArray {
      return GZIPInputStream(data.inputStream()).use { it.readBytes() }
    }
  }

  /** A sync flush would keep the dictionary, making the second copy nearly free. Only a full flush makes it cost again. */
  @Test
  fun `ending a block clears the compression dictionary`() {
    val block = randomBytes(seed = 0, size = 16 * 1024)
    val output = ByteArrayOutputStream()

    PaddedGzipOutputStream(output).use { stream ->
      val headerBytes = stream.compressedBytes

      stream.write(block)
      stream.endBlock()
      val firstBlockBytes = stream.compressedBytes - headerBytes

      stream.write(block)
      stream.endBlock()
      val secondBlockBytes = stream.compressedBytes - headerBytes - firstBlockBytes

      assertTrue(
        "second copy of the same block compressed from $firstBlockBytes to $secondBlockBytes bytes, so the dictionary survived the flush",
        secondBlockBytes * 100 >= firstBlockBytes * 95
      )
    }
  }

  @Test
  fun `a record larger than the block limit is split at the boundary`() {
    val record = randomBytes(seed = 1, size = 1024 * 1024)
    val output = ByteArrayOutputStream()

    PaddedGzipOutputStream(output).use { stream ->
      stream.beginChatItemRegion()
      stream.write(record)

      val limit = MessageBackupSizing.flushInterval(record.size.toLong())
      assertTrue(
        "a block reached ${stream.largestBlockBytes} bytes against a limit of $limit",
        stream.largestBlockBytes <= limit
      )
      assertTrue("the record was never split", stream.largestBlockBytes < record.size)
    }
  }

  @Test
  fun `blocks end exactly where the flush interval says they should`() {
    val regionBytes = 4 * 1024 * 1024
    val output = ByteArrayOutputStream()

    PaddedGzipOutputStream(output).use { stream ->
      stream.beginChatItemRegion()
      stream.write(ByteArray(regionBytes) { (it % 251).toByte() })

      assertEquals(specLargestBlock(regionBytes.toLong()), stream.largestBlockBytes)
    }
  }

  @Test
  fun `an estimate holds one interval instead of ramping up to it`() {
    val regionBytes = 4 * 1024 * 1024
    val content = ByteArray(regionBytes) { (it % 251).toByte() }

    PaddedGzipOutputStream(ByteArrayOutputStream(), regionBytes.toLong()).use { withEstimate ->
      PaddedGzipOutputStream(ByteArrayOutputStream()).use { withoutEstimate ->
        withEstimate.beginChatItemRegion()
        withEstimate.write(content)

        withoutEstimate.beginChatItemRegion()
        withoutEstimate.write(content)

        assertEquals(MessageBackupSizing.flushInterval(0, regionBytes.toLong()), withEstimate.largestBlockBytes)
        assertTrue(
          "estimate gave ${withEstimate.largestBlockBytes} but no estimate gave ${withoutEstimate.largestBlockBytes}",
          withEstimate.largestBlockBytes < withoutEstimate.largestBlockBytes
        )
      }
    }
  }

  @Test
  fun `a repeated beginChatItemRegion call does not rewind the schedule`() {
    val half = ByteArray(1024 * 1024) { (it % 251).toByte() }

    val single = PaddedGzipOutputStream(ByteArrayOutputStream()).use { stream ->
      stream.beginChatItemRegion()
      stream.write(half)
      stream.write(half)
      stream.largestBlockBytes to stream.compressedBytes
    }

    val repeated = PaddedGzipOutputStream(ByteArrayOutputStream()).use { stream ->
      stream.beginChatItemRegion()
      stream.write(half)
      stream.beginChatItemRegion()
      stream.write(half)
      stream.largestBlockBytes to stream.compressedBytes
    }

    assertEquals("the repeated call restarted the flush schedule", single, repeated)
  }

  @Test
  fun `uncompressedBytes counts the whole stream, not just the chat item region`() {
    val output = ByteArrayOutputStream()

    val stream = PaddedGzipOutputStream(output)
    stream.write(ByteArray(1000))
    stream.endBlock()
    stream.beginChatItemRegion()
    stream.write(ByteArray(2000))
    stream.close()

    assertEquals(3000L, stream.uncompressedBytes)
  }

  @Test
  fun `content survives the flushing and the padding`() {
    val expected = ByteArrayOutputStream()
    val output = ByteArrayOutputStream()
    val random = Random(2)

    PaddedGzipOutputStream(output).use { stream ->
      repeat(4) {
        val record = randomBytes(seed = random.nextLong(), size = 4 * 1024)
        stream.write(record)
        stream.endBlock()
        expected.write(record)
      }

      stream.beginChatItemRegion()

      repeat(2000) {
        val record = "message body number $it, on my way".toByteArray()
        stream.write(record.size)
        stream.write(record)
        expected.write(record.size)
        expected.write(record)
      }
    }

    assertArrayEquals(expected.toByteArray(), gunzip(output.toByteArray()))
  }

  @Test
  fun `padding is appended past the end of the gzip stream`() {
    val output = ByteArrayOutputStream()

    val stream = PaddedGzipOutputStream(output)
    stream.beginChatItemRegion()
    repeat(200) { i ->
      stream.write(randomBytes(seed = i.toLong(), size = 4 * 1024))
    }
    stream.close()

    assertTrue("no padding was appended", stream.paddingBytes > 0)
    assertEquals(stream.compressedBytes + stream.paddingBytes, output.size().toLong())

    val trailer = output.toByteArray().copyOfRange(stream.compressedBytes.toInt(), output.size())
    assertArrayEquals(ByteArray(stream.paddingBytes.toInt()), trailer)
  }

  @Test
  fun `a stream with no chat item region is still padded and still readable`() {
    val record = randomBytes(seed = 3, size = 8 * 1024)
    val output = ByteArrayOutputStream()

    val stream = PaddedGzipOutputStream(output)
    stream.write(record)
    stream.endBlock()
    stream.close()

    assertEquals(0L, stream.largestBlockBytes)
    assertTrue("no padding was appended", stream.paddingBytes > 0)
    assertArrayEquals(record, gunzip(output.toByteArray()))
  }

  @Test
  fun `padding is only drawn once when finish is called before close`() {
    val output = ByteArrayOutputStream()

    val stream = PaddedGzipOutputStream(output)
    stream.write(randomBytes(seed = 4, size = 8 * 1024))
    stream.finish()
    val sizeAfterFinish = output.size()
    stream.close()

    assertEquals(sizeAfterFinish, output.size())
  }
}
