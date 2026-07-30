/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptedProxyFileDescriptorCryptoTest {

  private val random = Random(1234)

  private val key = ByteArray(32).also { random.nextBytes(it) }

  private fun referenceEncrypt(plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
    return cipher.doFinal(plaintext)
  }

  @Test
  fun `whole buffer at offset zero matches streaming cipher`() {
    val plaintext = ByteArray(10_000).also { random.nextBytes(it) }
    val output = ByteArray(plaintext.size)

    EncryptedProxyFileDescriptor.encryptOrDecrypt(key, 0, plaintext, plaintext.size, output)

    assertArrayEquals(referenceEncrypt(plaintext), output)
  }

  @Test
  fun `chunks at arbitrary offsets match streaming cipher`() {
    val plaintext = ByteArray(100_000).also { random.nextBytes(it) }
    val expected = referenceEncrypt(plaintext)
    val actual = ByteArray(plaintext.size)

    val offsets = (0 until plaintext.size).shuffled(random).take(50).sorted() + plaintext.size
    var start = 0
    val chunks = offsets.map { end -> (start until end).also { start = end } }.filter { !it.isEmpty() }.shuffled(random)

    for (range in chunks) {
      val length = range.last - range.first + 1
      val chunkOutput = ByteArray(length)
      EncryptedProxyFileDescriptor.encryptOrDecrypt(key, range.first.toLong(), plaintext.copyOfRange(range.first, range.last + 1), length, chunkOutput)
      System.arraycopy(chunkOutput, 0, actual, range.first, length)
    }

    assertArrayEquals(expected, actual)
  }

  @Test
  fun `roundtrip with overwrites decrypts to final plaintext`() {
    val size = 50_000
    val ciphertext = ByteArray(size)
    val plaintext = ByteArray(size)

    EncryptedProxyFileDescriptor.encryptOrDecrypt(key, 0, plaintext, size, ciphertext)

    repeat(200) {
      val offset = random.nextInt(size - 1)
      val length = 1 + random.nextInt(size - offset)
      val data = ByteArray(length).also { random.nextBytes(it) }

      val encrypted = ByteArray(length)
      EncryptedProxyFileDescriptor.encryptOrDecrypt(key, offset.toLong(), data, length, encrypted)

      System.arraycopy(data, 0, plaintext, offset, length)
      System.arraycopy(encrypted, 0, ciphertext, offset, length)
    }

    val decrypted = ByteArray(size)
    EncryptedProxyFileDescriptor.encryptOrDecrypt(key, 0, ciphertext, size, decrypted)

    assertArrayEquals(plaintext, decrypted)
  }

  @Test
  fun `partial length only processes requested bytes`() {
    val input = ByteArray(64).also { random.nextBytes(it) }
    val output = ByteArray(64)

    EncryptedProxyFileDescriptor.encryptOrDecrypt(key, 32, input, 16, output)

    assertArrayEquals(referenceEncrypt(ByteArray(32) + input.copyOfRange(0, 16)).copyOfRange(32, 48), output.copyOfRange(0, 16))
    assertArrayEquals(ByteArray(48), output.copyOfRange(16, 64))
  }

  @Test
  fun `ciphertext does not contain plaintext`() {
    val plaintext = ByteArray(4096)
    val output = ByteArray(plaintext.size)

    EncryptedProxyFileDescriptor.encryptOrDecrypt(key, 0, plaintext, plaintext.size, output)

    assertFalse(output.all { it == 0.toByte() })
    assertEquals(plaintext.size, output.size)
  }
}
