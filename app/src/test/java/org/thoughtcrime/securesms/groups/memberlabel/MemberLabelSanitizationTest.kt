/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.core.util.StringUtil

class MemberLabelSanitizationTest {
  @Test
  fun `sanitizeLabelText trims leading and trailing whitespace`() {
    assertEquals("hello", MemberLabel.sanitizeLabelText("  hello  "))
  }

  @Test
  fun `sanitizeLabelText replaces newline with space`() {
    assertEquals("hello world", MemberLabel.sanitizeLabelText("hello\nworld"))
  }

  @Test
  fun `sanitizeLabelText replaces carriage return with space`() {
    assertEquals("hello world", MemberLabel.sanitizeLabelText("hello\rworld"))
  }

  @Test
  fun `sanitizeLabelText replaces carriage return newline with space`() {
    assertEquals("hello world", MemberLabel.sanitizeLabelText("hello\r\nworld"))
  }

  @Test
  fun `sanitizeLabelText replaces tab with space`() {
    assertEquals("hello world", MemberLabel.sanitizeLabelText("hello\tworld"))
  }

  @Test
  fun `sanitizeLabelText collapses multiple spaces into one`() {
    assertEquals("hello world", MemberLabel.sanitizeLabelText("hello   world"))
  }

  @Test
  fun `sanitizeLabelText collapses mixed whitespace into single space`() {
    assertEquals("hello world", MemberLabel.sanitizeLabelText("hello \n\r\t world"))
  }

  @Test
  fun `sanitizeLabelText collapses all-whitespace string`() {
    assertEquals("", MemberLabel.sanitizeLabelText("  \n\r\t  "))
  }

  @Test
  fun `sanitizeLabelText preserves normal text`() {
    assertEquals("hello world", MemberLabel.sanitizeLabelText("hello world"))
  }

  @Test
  fun `sanitizeLabelText trims leading and trailing unicode whitespace characters`() {
    assertEquals("hello", MemberLabel.sanitizeLabelText("\u200Ehello\u200F"))
  }

  @Test
  fun `sanitizeLabelText does not truncate short text`() {
    assertEquals("hello", MemberLabel.sanitizeLabelText("hello"))
  }

  @Test
  fun `sanitizeLabelText truncates to 24 graphemes`() {
    val input = "A".repeat(30)
    val result = MemberLabel.sanitizeLabelText(input)
    assertEquals("A".repeat(24), result)
  }

  @Test
  fun `sanitizeLabelText counts emoji as single grapheme`() {
    val input = "\uD83C\uDF89".repeat(30) // 🎉
    val result = MemberLabel.sanitizeLabelText(input)
    assertEquals("\uD83C\uDF89".repeat(24), result)
  }

  @Test
  fun `sanitizeLabelText handles mix of ascii and emoji`() {
    val input = "A".repeat(20) + "\uD83C\uDF89".repeat(10)
    val result = MemberLabel.sanitizeLabelText(input)
    assertEquals("A".repeat(20) + "\uD83C\uDF89".repeat(4), result)
  }

  @Test
  fun `sanitizeLabelText enforces byte limit`() {
    val fourByteEmoji = "\uD83C\uDF89" // 🎉 is 4 bytes in UTF-8
    val input = fourByteEmoji.repeat(24)
    val result = MemberLabel.sanitizeLabelText(input)
    assertTrue(result.toByteArray(Charsets.UTF_8).size <= MemberLabel.MAX_LABEL_BYTES)
  }

  @Test
  fun `sanitizeLabelText does not exceed byte limit with large graphemes`() {
    val familyEmoji = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66" // 👨‍👩‍👧‍👦 is 25 bytes in UTF-8
    val input = familyEmoji.repeat(10)
    val result = MemberLabel.sanitizeLabelText(input)
    assertTrue(result.toByteArray(Charsets.UTF_8).size <= MemberLabel.MAX_LABEL_BYTES)
  }

  @Test
  fun `truncateLabelText truncates to 24 graphemes`() {
    val input = "A".repeat(30)
    assertEquals("A".repeat(24), MemberLabel.truncateLabelText(input))
  }

  @Test
  fun `truncateLabelText preserves trailing space`() {
    assertEquals("hello ", MemberLabel.truncateLabelText("hello "))
  }

  @Test
  fun `truncateLabelText preserves leading space`() {
    assertEquals("  hello", MemberLabel.truncateLabelText("  hello"))
  }

  @Test
  fun `truncateLabelText preserves multiple spaces between words`() {
    assertEquals("hello   world", MemberLabel.truncateLabelText("hello   world"))
  }

  @Test
  fun `truncateLabelText enforces byte limit`() {
    val fourByteEmoji = "\uD83C\uDF89" // 🎉 = 4 bytes
    val input = fourByteEmoji.repeat(30)
    val result = MemberLabel.truncateLabelText(input)
    assertTrue(result.toByteArray(Charsets.UTF_8).size <= MemberLabel.MAX_LABEL_BYTES)
    assertEquals(fourByteEmoji.repeat(24), result)
  }

  @Test
  fun `displayText wraps non-ascii label text with BiDi isolation`() {
    val arabicText = "مرحبا بالعالم"
    assertEquals("\u2068$arabicText\u2069", MemberLabel(emoji = null, text = arabicText).displayText)
  }

  @Test
  fun `displayText does not wrap ascii-only label text`() {
    val ascii = "Vet Coordinator"
    assertEquals(ascii, MemberLabel(emoji = null, text = ascii).displayText)
  }

  @Test
  fun `displayText balances unmatched opening BiDi character`() {
    val unbalanced = "hello\u2067world"
    val result = MemberLabel(emoji = null, text = unbalanced).displayText
    assertTrue(result.startsWith("\u2068"))
    assertTrue(result.endsWith("\u2069"))
  }

  @Test
  fun `trimToFit does not truncate when within limit`() {
    assertEquals("hello", StringUtil.trimToFit("hello", 10))
  }

  @Test
  fun `trimToFit truncates ascii to byte limit`() {
    val input = "A".repeat(100)
    val result = StringUtil.trimToFit(input, 48)
    assertEquals(48, result.toByteArray(Charsets.UTF_8).size)
    assertEquals("A".repeat(48), result)
  }

  @Test
  fun `trimToFit does not split multi-byte graphemes`() {
    val emoji = "\uD83C\uDF89" // 🎉 = 4 bytes
    val input = emoji.repeat(15)
    val result = StringUtil.trimToFit(input, MemberLabel.MAX_EMOJI_BYTES)
    assertTrue(result.toByteArray(Charsets.UTF_8).size <= MemberLabel.MAX_EMOJI_BYTES)
    assertEquals(emoji.repeat(12), result)
  }
}
