/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.shared

import androidx.compose.ui.text.AnnotatedString
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class AccountIdVisualTransformationTest {

  companion object {
    private const val FULL_KEY = "a6b284822e3283d07f2391360a4c2b91"
    private const val FULL_KEY_FORMATTED = "A6B28482-2E32-83D0-7F23-91360A4C2B91"

    /** Characters whose whole-string uppercase mapping is longer than the character itself. */
    private val LENGTH_CHANGING_CHARACTERS = listOf('ß', 'ŉ', 'ﬀ', 'ﬁ', 'ǰ')
  }

  @Test
  fun `a full key is uppercased and split into UUID groups`() {
    assertThat(transform(FULL_KEY)).isEqualTo(FULL_KEY_FORMATTED)
  }

  @Test
  fun `an empty key transforms to nothing`() {
    assertThat(transform("")).isEqualTo("")
  }

  @Test
  fun `no trailing dash is added to a key that ends on a group boundary`() {
    assertThat(transform("a6b28482")).isEqualTo("A6B28482")
    assertThat(transform("a6b284822e32")).isEqualTo("A6B28482-2E32")
  }

  @Test
  fun `a dash appears as soon as the next group is started`() {
    assertThat(transform("a6b284822")).isEqualTo("A6B28482-2")
  }

  @Test
  fun `every cursor position maps into the transformed text and back`() {
    for (length in 0..FULL_KEY.length) {
      val key = FULL_KEY.take(length)
      val mapping = AccountIdVisualTransformation.filter(AnnotatedString(key)).offsetMapping
      val transformedLength = transform(key).length

      for (offset in 0..length) {
        val transformed = mapping.originalToTransformed(offset)

        assertThat(transformed in 0..transformedLength).isEqualTo(true)
        assertThat(mapping.transformedToOriginal(transformed)).isEqualTo(offset)
      }
    }
  }

  @Test
  fun `characters that uppercase into multiple characters stay one character long`() {
    for (character in LENGTH_CHANGING_CHARACTERS) {
      assertThat(transform(character.toString()).length).isEqualTo(1)
      assertThat(transform("a6b2848$character")).isEqualTo("A6B2848${character.uppercaseChar()}")
    }
  }

  @Test
  fun `every cursor position round trips even when the key holds characters that normally expand`() {
    for (character in LENGTH_CHANGING_CHARACTERS) {
      val key = character + FULL_KEY.drop(1)
      val mapping = AccountIdVisualTransformation.filter(AnnotatedString(key)).offsetMapping
      val transformedLength = transform(key).length

      for (offset in 0..key.length) {
        val transformed = mapping.originalToTransformed(offset)

        assertThat(transformed in 0..transformedLength).isEqualTo(true)
        assertThat(mapping.transformedToOriginal(transformed)).isEqualTo(offset)
      }

      for (offset in 0..transformedLength) {
        assertThat(mapping.transformedToOriginal(offset) in 0..key.length).isEqualTo(true)
      }
    }
  }

  private fun transform(text: String): String = AccountIdVisualTransformation.filter(AnnotatedString(text)).text.text
}
