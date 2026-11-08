/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.styledtext

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class StyledTextIndexTest {

  private val emojis = listOf(2 until 4, 6 until 8)

  @Test
  fun `masking replaces hidden characters but keeps whitespace and length`() {
    val masked = "ab cd\nef".maskSpoilers(listOf(StyleRange(1, 7, StyleRange.Style.SPOILER)))

    assertThat(masked).isEqualTo("a$SPOILER_MASK_CHAR $SPOILER_MASK_CHAR$SPOILER_MASK_CHAR\n${SPOILER_MASK_CHAR}f")
  }

  @Test
  fun `masking clamps a range that runs past the end of the text`() {
    val masked = "abc".maskSpoilers(listOf(StyleRange(2, 10, StyleRange.Style.SPOILER)))

    assertThat(masked).isEqualTo("ab$SPOILER_MASK_CHAR")
  }

  @Test
  fun `raw indices before, after, and between emoji shift by the collapsed characters`() {
    assertThat(rawIndexToEmojified(1, emojis)).isEqualTo(1)
    assertThat(rawIndexToEmojified(4, emojis)).isEqualTo(3)
    assertThat(rawIndexToEmojified(5, emojis)).isEqualTo(4)
    assertThat(rawIndexToEmojified(9, emojis)).isEqualTo(7)
  }

  @Test
  fun `a raw index inside an emoji lands just past its placeholder`() {
    assertThat(rawIndexToEmojified(3, emojis)).isEqualTo(3)
  }

  @Test
  fun `layout offsets map back to raw indices`() {
    assertThat(emojifiedIndexToRaw(1, emojis)).isEqualTo(1)
    assertThat(emojifiedIndexToRaw(3, emojis)).isEqualTo(4)
    assertThat(emojifiedIndexToRaw(4, emojis)).isEqualTo(5)
    assertThat(emojifiedIndexToRaw(7, emojis)).isEqualTo(9)
  }

  @Test
  fun `no emoji means identical index spaces`() {
    assertThat(rawIndexToEmojified(5, emptyList())).isEqualTo(5)
    assertThat(emojifiedIndexToRaw(5, emptyList())).isEqualTo(5)
  }
}
