/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.Application
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class EncryptedProxyFileDescriptorCallbackTest {

  private val random = Random(5678)
  private val key = ByteArray(32).also { random.nextBytes(it) }

  private lateinit var backingFile: File
  private lateinit var channel: FileChannel
  private lateinit var callback: EncryptedProxyFileDescriptor.Callback

  private var released = false

  @Before
  fun setUp() {
    backingFile = File.createTempFile("callback-test", ".enc")
    channel = RandomAccessFile(backingFile, "rw").channel
    callback = EncryptedProxyFileDescriptor.Callback(channel, key) { released = true }
  }

  @After
  fun tearDown() {
    backingFile.delete()
  }

  @Test
  fun `sequential write then read roundtrips`() {
    val data = ByteArray(100_000).also { random.nextBytes(it) }

    var written = 0
    while (written < data.size) {
      val chunk = minOf(4096, data.size - written)
      assertEquals(chunk, callback.onWrite(written.toLong(), chunk, data.copyOfRange(written, written + chunk)))
      written += chunk
    }

    assertEquals(data.size.toLong(), callback.onGetSize())

    val readBack = ByteArray(data.size)
    var read = 0
    while (read < data.size) {
      val buffer = ByteArray(8192)
      val result = callback.onRead(read.toLong(), buffer.size, buffer)
      assertTrue(result > 0)
      System.arraycopy(buffer, 0, readBack, read, result)
      read += result
    }

    assertArrayEquals(data, readBack)
  }

  @Test
  fun `seek back and overwrite like an mp4 muxer`() {
    val body = ByteArray(50_000).also { random.nextBytes(it) }
    callback.onWrite(0, body.size, body)

    val patchedHeader = ByteArray(16).also { random.nextBytes(it) }
    callback.onWrite(8, patchedHeader.size, patchedHeader)
    System.arraycopy(patchedHeader, 0, body, 8, patchedHeader.size)

    val readBack = ByteArray(body.size)
    assertEquals(body.size, callback.onRead(0, body.size, readBack))
    assertArrayEquals(body, readBack)
  }

  @Test
  fun `read at end of file returns zero`() {
    val data = ByteArray(1000).also { random.nextBytes(it) }
    callback.onWrite(0, data.size, data)

    val buffer = ByteArray(100)
    assertEquals(0, callback.onRead(1000, buffer.size, buffer))
  }

  @Test
  fun `read straddling end of file returns partial data`() {
    val data = ByteArray(1000).also { random.nextBytes(it) }
    callback.onWrite(0, data.size, data)

    val buffer = ByteArray(100)
    assertEquals(50, callback.onRead(950, buffer.size, buffer))
    assertArrayEquals(data.copyOfRange(950, 1000), buffer.copyOfRange(0, 50))
  }

  @Test
  fun `backing file contains only ciphertext`() {
    val data = ByteArray(10_000).also { random.nextBytes(it) }
    callback.onWrite(0, data.size, data)

    val onDisk = backingFile.readBytes()
    assertEquals(data.size, onDisk.size)
    assertFalse(onDisk.contentEquals(data))

    val window = data.copyOfRange(0, 64)
    for (i in 0..onDisk.size - window.size) {
      if (onDisk.copyOfRange(i, i + window.size).contentEquals(window)) {
        throw AssertionError("Found plaintext window in backing file at offset $i")
      }
    }
  }

  @Test
  fun `release zeroes key closes channel and notifies`() {
    val data = ByteArray(100).also { random.nextBytes(it) }
    callback.onWrite(0, data.size, data)

    callback.onRelease()

    assertTrue(released)
    assertFalse(channel.isOpen)
    assertTrue(key.all { it == 0.toByte() })
  }
}
