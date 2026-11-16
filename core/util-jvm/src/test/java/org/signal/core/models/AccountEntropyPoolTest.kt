/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class AccountEntropyPoolTest {

  @Test
  fun `formatForDisplay - swaps storage characters for their display equivalents`() {
    assertThat(AccountEntropyPool.formatForDisplay("O")).isEqualTo("#")
    assertThat(AccountEntropyPool.formatForDisplay("0")).isEqualTo("=")
  }

  @Test
  fun `formatForDisplay - is case-insensitive on lookup`() {
    assertThat(AccountEntropyPool.formatForDisplay("o")).isEqualTo("#")
    assertThat(AccountEntropyPool.formatForDisplay("O")).isEqualTo("#")
  }

  @Test
  fun `formatForDisplay - leaves untouched characters alone`() {
    assertThat(AccountEntropyPool.formatForDisplay("ABCxyz123")).isEqualTo("ABCxyz123")
  }

  @Test
  fun `formatForDisplay - mixed input swaps only mapped characters`() {
    assertThat(AccountEntropyPool.formatForDisplay("A0Ob1")).isEqualTo("A=#b1")
  }

  @Test
  fun `formatForDisplay - empty input returns empty`() {
    assertThat(AccountEntropyPool.formatForDisplay("")).isEqualTo("")
  }

  @Test
  fun `formatForStorage - swaps display characters back to storage equivalents`() {
    assertThat(AccountEntropyPool.formatForStorage("#")).isEqualTo("O")
    assertThat(AccountEntropyPool.formatForStorage("=")).isEqualTo("0")
  }

  @Test
  fun `formatForStorage - leaves untouched characters alone`() {
    assertThat(AccountEntropyPool.formatForStorage("ABCxyz123")).isEqualTo("ABCxyz123")
    assertThat(AccountEntropyPool.formatForStorage("O")).isEqualTo("O")
    assertThat(AccountEntropyPool.formatForStorage("0")).isEqualTo("0")
  }

  @Test
  fun `formatForStorage - mixed input swaps only mapped characters`() {
    assertThat(AccountEntropyPool.formatForStorage("A=#b1")).isEqualTo("A0Ob1")
  }

  @Test
  fun `formatForStorage - empty input returns empty`() {
    assertThat(AccountEntropyPool.formatForStorage("")).isEqualTo("")
  }

  @Test
  fun `format round trip - display then storage is identity for storage characters`() {
    val storage = "ABCD0123Oabcdef"
    assertThat(AccountEntropyPool.formatForStorage(AccountEntropyPool.formatForDisplay(storage))).isEqualTo(storage)
  }

  @Test
  fun `format round trip - storage then display is identity for display characters`() {
    val display = "ABCD=123#abcdef"
    assertThat(AccountEntropyPool.formatForDisplay(AccountEntropyPool.formatForStorage(display))).isEqualTo(display)
  }

  @Test
  fun `displayValue - applies formatting on top of uppercase`() {
    val aep = AccountEntropyPool("abcdef0123456789".repeat(4))

    assertThat(aep.value).isEqualTo("abcdef0123456789".repeat(4))
    assertThat(aep.displayValue).isEqualTo("ABCDEF=123456789".repeat(4))
  }

  @Test
  fun `displayValue - formats uppercase O introduced by uppercasing storage o`() {
    val aep = AccountEntropyPool("oooo0000".repeat(8))

    assertThat(aep.value).isEqualTo("oooo0000".repeat(8))
    assertThat(aep.displayValue).isEqualTo("####====".repeat(8))
  }

  @Test
  fun `parseOrNull - accepts display-form input by formatting back to storage`() {
    val displayForm = "ABCDEF=123456789".repeat(4)

    val parsed = AccountEntropyPool.parseOrNull(displayForm)

    assertThat(parsed?.value).isEqualTo("abcdef0123456789".repeat(4))
  }

  @Test
  fun `parseOrNull - still accepts storage-form input unchanged`() {
    val storageForm = "abcdef0123456789".repeat(4)

    val parsed = AccountEntropyPool.parseOrNull(storageForm)

    assertThat(parsed?.value).isEqualTo(storageForm)
  }

  @Test
  fun `parseOrNull - tolerates whitespace and chunking in pasted display form`() {
    val chunked = "ABCDEF=123456789 ".repeat(4).trim()

    val parsed = AccountEntropyPool.parseOrNull(chunked)

    assertThat(parsed?.value).isEqualTo("abcdef0123456789".repeat(4))
  }

  @Test
  fun `removeIllegalCharacters - preserves alphanumerics and display-map characters`() {
    assertThat(AccountEntropyPool.removeIllegalCharacters("aZ0 9!#=?")).isEqualTo("aZ09#=")
  }
}
