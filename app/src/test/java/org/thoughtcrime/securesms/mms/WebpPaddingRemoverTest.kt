/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mms

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.thoughtcrime.securesms.glide.cache.WebpSanitizerCheck
import java.io.ByteArrayInputStream

class WebpPaddingRemoverTest {

  companion object {
    private val VALID_WEBP = byteArrayOf(
      82, 73, 70, 70, 28, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56, 76, 15, 0,
      0, 0, 47, 7, -64, 1, 0, 7, 16, -3, -113, -2, 7, 34, -94, -1, 1, 0
    )
  }

  private fun trim(bytes: ByteArray): ByteArray = WebpPaddingRemover.trim(ByteArrayInputStream(bytes)).readBytes()

  @Test
  fun `padded webp fails the sanitizer`() {
    val padded = VALID_WEBP + ByteArray(42)

    assertThat(WebpSanitizerCheck.isSanitized(ByteArrayInputStream(padded))).isFalse()
  }

  @Test
  fun `zero padded webp is trimmed to its riff size and passes the sanitizer`() {
    val trimmed = trim(VALID_WEBP + ByteArray(42))

    assertThat(trimmed.toList()).isEqualTo(VALID_WEBP.toList())
    assertThat(WebpSanitizerCheck.isSanitized(ByteArrayInputStream(trimmed))).isTrue()
  }

  @Test
  fun `non-zero trailing bytes are dropped`() {
    val trimmed = trim(VALID_WEBP + ByteArray(16) { 0x7F })

    assertThat(trimmed.toList()).isEqualTo(VALID_WEBP.toList())
  }

  @Test
  fun `unpadded webp is unchanged`() {
    assertThat(trim(VALID_WEBP).toList()).isEqualTo(VALID_WEBP.toList())
  }

  @Test
  fun `non-webp data is unchanged`() {
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(64) { it.toByte() }

    assertThat(trim(jpeg).toList()).isEqualTo(jpeg.toList())
  }

  @Test
  fun `data shorter than the header is unchanged`() {
    val short = byteArrayOf(0x52, 0x49, 0x46, 0x46)

    assertThat(trim(short).toList()).isEqualTo(short.toList())
  }

  @Test
  fun `webp truncated below its riff size is returned as is and fails the sanitizer`() {
    val truncated = VALID_WEBP.copyOfRange(0, VALID_WEBP.size - 4)
    val trimmed = trim(truncated)

    assertThat(trimmed.toList()).isEqualTo(truncated.toList())
    assertThat(WebpSanitizerCheck.isSanitized(ByteArrayInputStream(trimmed))).isFalse()
  }
}
