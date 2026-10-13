/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class Base32Test {

  /** The RFC 4648 section 10 vectors, minus the padding we don't emit. */
  @Test
  fun `encode - matches the RFC 4648 test vectors`() {
    assertThat(Base32.encode("".toByteArray())).isEqualTo("")
    assertThat(Base32.encode("f".toByteArray())).isEqualTo("MY")
    assertThat(Base32.encode("fo".toByteArray())).isEqualTo("MZXQ")
    assertThat(Base32.encode("foo".toByteArray())).isEqualTo("MZXW6")
    assertThat(Base32.encode("foob".toByteArray())).isEqualTo("MZXW6YQ")
    assertThat(Base32.encode("fooba".toByteArray())).isEqualTo("MZXW6YTB")
    assertThat(Base32.encode("foobar".toByteArray())).isEqualTo("MZXW6YTBOI")
  }

  @Test
  fun `decodeOrNull - matches the RFC 4648 test vectors`() {
    assertThat(Base32.decodeOrNull("")?.decodeToString()).isEqualTo("")
    assertThat(Base32.decodeOrNull("MY")?.decodeToString()).isEqualTo("f")
    assertThat(Base32.decodeOrNull("MZXQ")?.decodeToString()).isEqualTo("fo")
    assertThat(Base32.decodeOrNull("MZXW6")?.decodeToString()).isEqualTo("foo")
    assertThat(Base32.decodeOrNull("MZXW6YQ")?.decodeToString()).isEqualTo("foob")
    assertThat(Base32.decodeOrNull("MZXW6YTB")?.decodeToString()).isEqualTo("fooba")
    assertThat(Base32.decodeOrNull("MZXW6YTBOI")?.decodeToString()).isEqualTo("foobar")
  }

  @Test
  fun `decodeOrNull - tolerates the padding we don't emit`() {
    assertThat(Base32.decodeOrNull("MZXW6YTBOI======")?.decodeToString()).isEqualTo("foobar")
  }

  @Test
  fun `decodeOrNull - tolerates the spaces and lowercase a pasted key arrives with`() {
    assertThat(Base32.decodeOrNull("mzxw 6ytb oi")?.decodeToString()).isEqualTo("foobar")
  }

  @Test
  fun `decodeOrNull - rejects characters outside the alphabet`() {
    assertThat(Base32.decodeOrNull("MZXW6YTB1")).isNull()
    assertThat(Base32.decodeOrNull("MZXW6YTB0")).isNull()
    assertThat(Base32.decodeOrNull("MZXW6YTB!")).isNull()
    assertThat(Base32.decodeOrNull("MZXW6YTBé")).isNull()
  }

  @Test
  fun `round trip - survives every byte value`() {
    val data = ByteArray(256) { it.toByte() }

    assertThat(Base32.decodeOrNull(Base32.encode(data))?.toList()).isEqualTo(data.toList())
  }

  @Test
  fun `encode - produces a 52 character key for the 32 byte keys the service generates`() {
    assertThat(Base32.encode(ByteArray(32)).length).isEqualTo(52)
  }
}
