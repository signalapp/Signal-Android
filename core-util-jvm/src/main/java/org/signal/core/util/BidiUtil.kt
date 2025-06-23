/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.util.regex.Pattern

object BidiUtil {
  private val ALL_ASCII_PATTERN: Pattern = Pattern.compile("^[\\x00-\\x7F]*$")

  private object Bidi {
    /** Override text direction   */
    val OVERRIDES: Set<Int> = SetUtil.newHashSet(
      "\u202a".codePointAt(0), // LRE
      "\u202b".codePointAt(0), // RLE
      "\u202d".codePointAt(0), // LRO
      "\u202e".codePointAt(0) // RLO
    )

    /** Set direction and isolate surrounding text  */
    val ISOLATES: Set<Int> = SetUtil.newHashSet(
      "\u2066".codePointAt(0), // LRI
      "\u2067".codePointAt(0), // RLI
      "\u2068".codePointAt(0) // FSI
    )

    /** Closes things in [.OVERRIDES]  */
    val PDF: Int = "\u202c".codePointAt(0)

    /** Closes things in [.ISOLATES]  */
    val PDI: Int = "\u2069".codePointAt(0)

    /** Auto-detecting isolate  */
    val FSI: Int = "\u2068".codePointAt(0)
  }

  /**
   * @return True if the provided text contains a mix of LTR and RTL characters, otherwise false.
   */
  @JvmStatic
  fun hasMixedTextDirection(text: CharSequence?): Boolean {
    if (text == null) {
      return false
    }

    var isLtr: Boolean? = null

    var i = 0
    val len = Character.codePointCount(text, 0, text.length)
    while (i < len) {
      val codePoint = Character.codePointAt(text, i)
      val direction = Character.getDirectionality(codePoint)
      val isLetter = Character.isLetter(codePoint)

      if (isLtr != null && isLtr && direction != Character.DIRECTIONALITY_LEFT_TO_RIGHT && isLetter) {
        return true
      } else if (isLtr != null && !isLtr && direction != Character.DIRECTIONALITY_RIGHT_TO_LEFT && isLetter) {
        return true
      } else if (isLetter) {
        isLtr = direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT
      }
      i++
    }

    return false
  }

  /**
   * Isolates bi-directional text from influencing surrounding text. You should use this whenever
   * you're injecting user-generated text into a larger string.
   *
   * You'd think we'd be able to trust BidiFormatter, but unfortunately it just misses some
   * corner cases, so here we are.
   *
   * The general idea is just to balance out the opening and closing codepoints, and then wrap the
   * whole thing in FSI/PDI to isolate it.
   *
   * For more details, see:
   * https://www.w3.org/International/questions/qa-bidi-unicode-controls
   */
  @JvmStatic
  fun isolateBidi(text: String?): String {
    if (text == null) {
      return ""
    }

    if (text.isEmpty()) {
      return text
    }

    if (ALL_ASCII_PATTERN.matcher(text).matches()) {
      return text
    }

    var overrideCount = 0
    var overrideCloseCount = 0
    var isolateCount = 0
    var isolateCloseCount = 0

    var i = 0
    val len = text.codePointCount(0, text.length)
    while (i < len) {
      val codePoint = text.codePointAt(i)

      if (Bidi.OVERRIDES.contains(codePoint)) {
        overrideCount++
      } else if (codePoint == Bidi.PDF) {
        overrideCloseCount++
      } else if (Bidi.ISOLATES.contains(codePoint)) {
        isolateCount++
      } else if (codePoint == Bidi.PDI) {
        isolateCloseCount++
      }
      i++
    }

    val suffix = StringBuilder()

    while (overrideCount > overrideCloseCount) {
      suffix.appendCodePoint(Bidi.PDF)
      overrideCloseCount++
    }

    while (isolateCount > isolateCloseCount) {
      suffix.appendCodePoint(Bidi.FSI)
      isolateCloseCount++
    }

    val out = StringBuilder()

    return out.appendCodePoint(Bidi.FSI)
      .append(text)
      .append(suffix)
      .appendCodePoint(Bidi.PDI)
      .toString()
  }

  @JvmStatic
  fun stripBidiProtection(text: String?): String? {
    if (text == null) return null

    return text.replace("[\\u2068\\u2069\\u202c]".toRegex(), "")
  }

  fun stripBidiIndicator(text: String): String {
    return text.replace("\u200F", "")
  }

  @JvmStatic
  fun forceLtr(text: CharSequence): String {
    return "\u202a" + text + "\u202c"
  }
}
