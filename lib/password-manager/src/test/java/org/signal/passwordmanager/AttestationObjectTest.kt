/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.passwordmanager

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Test

class AttestationObjectTest {

  private val authData = ByteArray(164) { it.toByte() }

  @Test
  fun `packed attestation is read`() {
    val packed = map(
      text("fmt") + text("packed"),
      text("attStmt") + map(
        text("alg") + byteArrayOf(0x26),
        text("sig") + bytes(ByteArray(71) { 1 }),
        text("x5c") + array(bytes(ByteArray(750) { 2 }))
      ),
      text("authData") + bytes(authData)
    )

    val parsed = AttestationObject.parse(packed)

    assertThat(parsed).isNotNull()
    assertThat(parsed!!.format).isEqualTo("packed")
    assertThat(parsed.authData.toList()).isEqualTo(authData.toList())
  }

  @Test
  fun `none attestation is read`() {
    val none = map(
      text("fmt") + text("none"),
      text("attStmt") + map(),
      text("authData") + bytes(authData)
    )

    val parsed = AttestationObject.parse(none)

    assertThat(parsed!!.format).isEqualTo("none")
    assertThat(parsed.authData.toList()).isEqualTo(authData.toList())
  }

  @Test
  fun `keys are found regardless of order`() {
    val reordered = map(
      text("authData") + bytes(authData),
      text("attStmt") + map(),
      text("fmt") + text("none")
    )

    assertThat(AttestationObject.parse(reordered)!!.format).isEqualTo("none")
  }

  @Test
  fun `encoding without attestation matches the canonical none form`() {
    val expected = map(
      text("fmt") + text("none"),
      text("attStmt") + map(),
      text("authData") + bytes(authData)
    )

    assertThat(AttestationObject.encodeWithoutAttestation(authData).toList()).isEqualTo(expected.toList())
  }

  @Test
  fun `encoding without attestation round trips`() {
    val longAuthData = ByteArray(70_000) { (it * 7).toByte() }

    val parsed = AttestationObject.parse(AttestationObject.encodeWithoutAttestation(longAuthData))

    assertThat(parsed!!.format).isEqualTo("none")
    assertThat(parsed.authData.toList()).isEqualTo(longAuthData.toList())
  }

  @Test
  fun `indefinite length items in the attestation statement are skipped`() {
    val indefinite = byteArrayOf(0xBF.toByte()) +
      text("fmt") + text("tpm") +
      text("attStmt") + byteArrayOf(0xBF.toByte()) + text("x5c") + byteArrayOf(0x9F.toByte()) + bytes(ByteArray(4)) + byteArrayOf(0xFF.toByte(), 0xFF.toByte()) +
      text("authData") + bytes(authData) +
      byteArrayOf(0xFF.toByte())

    val parsed = AttestationObject.parse(indefinite)

    assertThat(parsed!!.format).isEqualTo("tpm")
    assertThat(parsed.authData.toList()).isEqualTo(authData.toList())
  }

  @Test
  fun `truncated input is rejected`() {
    val none = AttestationObject.encodeWithoutAttestation(authData)

    assertThat(AttestationObject.parse(none.copyOf(none.size - 1))).isNull()
  }

  @Test
  fun `missing authData is rejected`() {
    assertThat(AttestationObject.parse(map(text("fmt") + text("none"), text("attStmt") + map()))).isNull()
  }

  @Test
  fun `non-map input is rejected`() {
    assertThat(AttestationObject.parse(array(text("fmt")))).isNull()
    assertThat(AttestationObject.parse(ByteArray(0))).isNull()
  }

  private fun head(major: Int, length: Int): ByteArray {
    val type = major shl 5
    return when {
      length < 24 -> {
        byteArrayOf((type or length).toByte())
      }
      length <= 0xFF -> {
        byteArrayOf((type or 24).toByte(), length.toByte())
      }
      else -> {
        byteArrayOf((type or 25).toByte(), (length shr 8).toByte(), length.toByte())
      }
    }
  }

  private fun text(value: String): ByteArray = value.encodeToByteArray().let { head(3, it.size) + it }

  private fun bytes(value: ByteArray): ByteArray = head(2, value.size) + value

  private fun array(vararg items: ByteArray): ByteArray = items.fold(head(4, items.size)) { acc, item -> acc + item }

  private fun map(vararg entries: ByteArray): ByteArray = entries.fold(head(5, entries.size)) { acc, entry -> acc + entry }
}
