/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer.app

import android.content.Context
import org.signal.core.util.eventbus.EventBus
import org.signal.core.util.logging.Log
import org.signal.devicetransfer.ClientTask
import org.signal.devicetransfer.ServerTask
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Random
import java.util.zip.CRC32
import kotlin.math.min

private const val CHUNK_SIZE = 8192
private const val PROGRESS_INTERVAL_MS = 250L

sealed interface TestTaskUpdate {
  data class Progress(val bytes: Long, val totalBytes: Long, val elapsedMillis: Long) : TestTaskUpdate
  data class Sent(val bytes: Long, val durationMillis: Long) : TestTaskUpdate
  data class Received(val bytes: Long, val durationMillis: Long, val checksumMatches: Boolean) : TestTaskUpdate
  data class Failed(val message: String) : TestTaskUpdate
}

/**
 * Sends [totalBytes] of random data framed as: payload length, payload, CRC32 of the payload.
 */
class TestClientTask(private val totalBytes: Long) : ClientTask {

  companion object {
    private val TAG = Log.tag(TestClientTask::class)
  }

  @Transient
  private var startMillis: Long = 0

  override fun run(context: Context, outputStream: OutputStream) {
    val output = DataOutputStream(outputStream)
    val random = Random()
    val chunk = ByteArray(CHUNK_SIZE)
    val crc = CRC32()
    val reporter = ProgressReporter(totalBytes)
    startMillis = System.currentTimeMillis()

    Log.i(TAG, "Sending $totalBytes bytes")
    try {
      output.writeLong(totalBytes)

      var sent = 0L
      while (sent < totalBytes) {
        val length = min(CHUNK_SIZE.toLong(), totalBytes - sent).toInt()
        random.nextBytes(chunk)
        crc.update(chunk, 0, length)
        output.write(chunk, 0, length)
        sent += length
        reporter.onBytes(sent)
      }

      output.writeLong(crc.value)
      output.flush()
      reporter.finish(sent)
    } catch (e: IOException) {
      Log.w(TAG, "Send failed", e)
      EventBus.default.post(TestTaskUpdate.Failed(e.toString()))
      throw e
    }
  }

  override fun success() {
    val duration = System.currentTimeMillis() - startMillis
    Log.i(TAG, "Send complete in $duration ms")
    EventBus.default.post(TestTaskUpdate.Sent(totalBytes, duration))
  }
}

class TestServerTask : ServerTask {

  companion object {
    private val TAG = Log.tag(TestServerTask::class)
  }

  override fun run(context: Context, inputStream: InputStream) {
    val input = DataInputStream(inputStream)
    val chunk = ByteArray(CHUNK_SIZE)
    val crc = CRC32()
    val startMillis = System.currentTimeMillis()

    try {
      val totalBytes = input.readLong()
      val reporter = ProgressReporter(totalBytes)
      Log.i(TAG, "Receiving $totalBytes bytes")

      var received = 0L
      while (received < totalBytes) {
        val length = min(CHUNK_SIZE.toLong(), totalBytes - received).toInt()
        input.readFully(chunk, 0, length)
        crc.update(chunk, 0, length)
        received += length
        reporter.onBytes(received)
      }

      val expectedCrc = input.readLong()
      val matches = expectedCrc == crc.value
      val duration = System.currentTimeMillis() - startMillis
      reporter.finish(received)

      Log.i(TAG, "Receive complete in $duration ms, checksum matches: $matches")
      EventBus.default.post(TestTaskUpdate.Received(received, duration, matches))
    } catch (e: IOException) {
      Log.w(TAG, "Receive failed", e)
      EventBus.default.post(TestTaskUpdate.Failed(e.toString()))
      throw e
    }
  }
}

private class ProgressReporter(private val totalBytes: Long) {
  private val startMillis = System.currentTimeMillis()
  private var lastReportMillis = 0L

  fun onBytes(bytes: Long) {
    val now = System.currentTimeMillis()
    if (now - lastReportMillis >= PROGRESS_INTERVAL_MS) {
      lastReportMillis = now
      post(bytes, now)
    }
  }

  fun finish(bytes: Long) {
    post(bytes, System.currentTimeMillis())
  }

  private fun post(bytes: Long, now: Long) {
    EventBus.default.post(TestTaskUpdate.Progress(bytes, totalBytes, now - startMillis))
  }
}
