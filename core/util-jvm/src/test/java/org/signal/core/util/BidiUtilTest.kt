/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiUtilTest {

  companion object {
    // Isolate initiators
    private const val LRI = "⁦"
    private const val RLI = "⁧"
    private const val FSI = "⁨"

    // Closes any isolate initiator
    private const val PDI = "⁩"

    // Override initiators
    private const val LRE = "‪"
    private const val RLE = "‫"
    private const val LRO = "‭"
    private const val RLO = "‮"

    // Closes any override initiator
    private const val PDF = "‬"

    // A supplementary code point (grinning face emoji), encoded as a surrogate pair.
    private const val EMOJI = "😀"
  }

  private val replacement = "�"

  @Test
  fun replaceBidiCharacters_nullInput_returnsNull() {
    assertNull(BidiUtil.replaceBidiCharacters(null))
  }

  @Test
  fun replaceBidiCharacters_plainText_isUnchanged() {
    assertEquals("document.txt", BidiUtil.replaceBidiCharacters("document.txt"))
  }

  @Test
  fun replaceBidiCharacters_overrides_areReplaced() {
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters(LRE)) // LRE
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters(RLE)) // RLE
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters(PDF)) // PDF
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters(LRO)) // LRO
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters(RLO)) // RLO
  }

  @Test
  fun replaceBidiCharacters_isolates_areReplaced() {
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters(LRI)) // LRI
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters(RLI)) // RLI
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters(FSI)) // FSI
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters(PDI)) // PDI
  }

  @Test
  fun replaceBidiCharacters_directionalIndicator_isReplaced() {
    assertEquals("$replacement", BidiUtil.replaceBidiCharacters("‏")) // RLM
  }

  @Test
  fun replaceBidiCharacters_spoofedExtension_isNeutralized() {
    // "document<RLO>txt.exe<PDI>" would render as "documentexe.txt" without sanitization.
    val malicious = "document" + RLO + "txt.exe" + PDI
    val cleaned = BidiUtil.replaceBidiCharacters(malicious)

    assertEquals("document${replacement}txt.exe$replacement", cleaned)
    assertEquals(0, cleaned!!.count { it == RLO[0] || it == PDI[0] })
  }

  @Test
  fun replaceBidiCharacters_multipleControls_allReplaced() {
    val input = "a" + RLE + "b" + LRI + "c" + RLO + "d"
    assertEquals("a${replacement}b${replacement}c${replacement}d", BidiUtil.replaceBidiCharacters(input))
  }

  @Test
  fun isolateBidi_returnsEmptyForNull() {
    assertEquals("", BidiUtil.isolateBidi(null))
  }

  @Test
  fun isolateBidi_returnsEmptyForEmpty() {
    assertEquals("", BidiUtil.isolateBidi(""))
  }

  @Test
  fun isolateBidi_passesThroughPlainAscii() {
    assertEquals("Hello, World!", BidiUtil.isolateBidi("Hello, World!"))
  }

  @Test
  fun isolateBidi_wrapsNonAsciiInIsolate() {
    // No bidi controls, just non-ASCII text. It should simply be wrapped in FSI...PDI.
    assertEquals(FSI + "café" + PDI, BidiUtil.isolateBidi("café"))
  }

  @Test
  fun isolateBidi_balancedIsolateIsLeftAlone() {
    val input = RLI + "x" + PDI
    assertEquals(FSI + input + PDI, BidiUtil.isolateBidi(input))
  }

  @Test
  fun isolateBidi_unmatchedIsolateInitiatorIsClosedWithPdi() {
    // The core of the report: an unmatched isolate initiator must be terminated with PDI,
    // NOT with another isolate initiator (FSI), which would open yet another scope.
    val result = BidiUtil.isolateBidi(RLI + "evil")
    assertEquals(FSI + RLI + "evil" + PDI + PDI, result)
    assertBalanced(result)
  }

  @Test
  fun isolateBidi_multipleUnmatchedIsolateInitiatorsEachGetPdi() {
    val result = BidiUtil.isolateBidi(LRI + RLI + "evil")
    assertEquals(FSI + LRI + RLI + "evil" + PDI + PDI + PDI, result)
    assertBalanced(result)
  }

  @Test
  fun isolateBidi_unmatchedOverrideIsClosedWithPdf() {
    val result = BidiUtil.isolateBidi(RLO + "evil")
    assertEquals(FSI + RLO + "evil" + PDF + PDI, result)
    assertBalanced(result)
  }

  @Test
  fun isolateBidi_mixedUnmatchedOverridesAndIsolates() {
    val result = BidiUtil.isolateBidi(LRE + RLI + "evil")
    // Overrides are closed first, then isolates.
    assertEquals(FSI + LRE + RLI + "evil" + PDF + PDI + PDI, result)
    assertBalanced(result)
  }

  @Test
  fun isolateBidi_extraClosersAreNotOverBalanced() {
    // Already-closed (or over-closed) controls should not produce extra suffix characters.
    val input = "x" + PDI + PDF
    assertEquals(FSI + input + PDI, BidiUtil.isolateBidi(input))
  }

  @Test
  fun isolateBidi_countsControlAfterSupplementaryCodePoint() {
    // Regression test for the code-point iteration bug: a surrogate pair before a bidi
    // control used to cause the loop to stop short and miss the trailing initiator,
    // leaving it unterminated.
    val result = BidiUtil.isolateBidi(EMOJI + RLI + "evil")
    assertEquals(FSI + EMOJI + RLI + "evil" + PDI + PDI, result)
    assertBalanced(result)
  }

  @Test
  fun isolateBidi_handlesSupplementaryCodePointBetweenControls() {
    val result = BidiUtil.isolateBidi(RLI + EMOJI + RLO + "evil")
    assertEquals(FSI + RLI + EMOJI + RLO + "evil" + PDF + PDI + PDI, result)
    assertBalanced(result)
  }

  /**
   * Asserts that the isolate and override scopes in [text] are fully balanced: depth never
   * goes negative and ends at exactly zero for both isolates and overrides. This is the
   * security-relevant property — no attacker-controlled scope may leak past the isolated segment.
   */
  private fun assertBalanced(text: String) {
    var isolateDepth = 0
    var overrideDepth = 0

    var i = 0
    while (i < text.length) {
      when (text.codePointAt(i)) {
        LRI.codePointAt(0), RLI.codePointAt(0), FSI.codePointAt(0) -> isolateDepth++
        PDI.codePointAt(0) -> isolateDepth--
        LRE.codePointAt(0), RLE.codePointAt(0), LRO.codePointAt(0), RLO.codePointAt(0) -> overrideDepth++
        PDF.codePointAt(0) -> overrideDepth--
      }
      assertTrue("Isolate depth went negative in: $text", isolateDepth >= 0)
      assertTrue("Override depth went negative in: $text", overrideDepth >= 0)
      i += Character.charCount(text.codePointAt(i))
    }

    assertEquals("Unterminated isolate(s) in: $text", 0, isolateDepth)
    assertEquals("Unterminated override(s) in: $text", 0, overrideDepth)
  }
}
