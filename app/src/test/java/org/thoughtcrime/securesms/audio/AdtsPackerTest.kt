/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.audio

import android.app.Application
import android.media.MediaFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AdtsPackerTest {

  @Test
  fun `writes a header for 44100 Hz mono AAC-LC`() {
    val packer = AdtsPacker.fromFormat(aacFormat(0x12, 0x08))!!
    val payload = ByteBuffer.wrap(ByteArray(100) { it.toByte() })
    val destination = ByteBuffer.allocate(256)

    assertTrue(packer.pack(payload, destination))

    assertEquals(107, destination.position())
    val header = ByteArray(7).also { destination.flip(); destination.get(it) }
    assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x40, 0x0D, 0x7F, 0xFC.toByte()), header)
    val body = ByteArray(100).also { destination.get(it) }
    assertArrayEquals(ByteArray(100) { it.toByte() }, body)
  }

  @Test
  fun `writes a header for 48000 Hz stereo AAC-LC`() {
    val packer = AdtsPacker.fromFormat(aacFormat(0x11, 0x90))!!
    val destination = ByteBuffer.allocate(256)

    assertTrue(packer.pack(ByteBuffer.allocate(10), destination))

    val header = ByteArray(7).also { destination.flip(); destination.get(it) }
    assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x4C, 0x80.toByte(), 0x02, 0x3F, 0xFC.toByte()), header)
  }

  @Test
  fun `leaves destination untouched when the frame does not fit`() {
    val packer = AdtsPacker.fromFormat(aacFormat(0x12, 0x08))!!
    val destination = ByteBuffer.allocate(50)

    assertFalse(packer.pack(ByteBuffer.allocate(100), destination))
    assertEquals(0, destination.position())
  }

  @Test
  fun `rejects HE-AAC`() {
    assertNull(AdtsPacker.fromFormat(aacFormat(0x2B, 0x92)))
  }

  @Test
  fun `rejects tracks without an explicit channel layout`() {
    assertNull(AdtsPacker.fromFormat(aacFormat(0x12, 0x00)))
  }

  @Test
  fun `rejects non-AAC tracks`() {
    assertNull(AdtsPacker.fromFormat(MediaFormat().apply { setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_OPUS) }))
  }

  private fun aacFormat(vararg csd: Int): MediaFormat {
    return MediaFormat().apply {
      setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
      setByteBuffer("csd-0", ByteBuffer.wrap(csd.map { it.toByte() }.toByteArray()))
    }
  }
}
