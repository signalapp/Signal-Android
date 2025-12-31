/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.text.InputFilter
import android.text.Spanned

/**
 * An [InputFilter] that prevents the target text from growing beyond [byteLimit] bytes when using UTF-8 encoding.
 */
class ByteLimitInputFilter(private val byteLimit: Int) : InputFilter {

  override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int): CharSequence? {
    if (source == null || dest == null) {
      return null
    }

    val insertText = source.subSequence(start, end)
    val beforeText = dest.subSequence(0, dstart)
    val afterText = dest.subSequence(dend, dest.length)

    val insertByteLength = insertText.utf8Size()
    val beforeByteLength = beforeText.utf8Size()
    val afterByteLength = afterText.utf8Size()

    val resultByteSize = beforeByteLength + insertByteLength + afterByteLength
    if (resultByteSize <= byteLimit) {
      return null
    }

    val availableBytes = byteLimit - beforeByteLength - afterByteLength
    if (availableBytes <= 0) {
      return ""
    }

    return truncateToByteLimit(insertText, availableBytes)
  }

  private fun truncateToByteLimit(text: CharSequence, maxBytes: Int): CharSequence {
    var byteCount = 0
    var charIndex = 0

    while (charIndex < text.length) {
      val char = text[charIndex]
      val charBytes = when {
        char.code < 0x80 -> 1
        char.code < 0x800 -> 2
        char.isHighSurrogate() -> {
          if (charIndex + 1 < text.length && text[charIndex + 1].isLowSurrogate()) {
            4
          } else {
            3
          }
        }
        char.isLowSurrogate() -> 3 // Treat orphaned low surrogate as 3 bytes
        else -> 3
      }

      if (byteCount + charBytes > maxBytes) {
        break
      }

      byteCount += charBytes
      charIndex++

      if (char.isHighSurrogate() && charIndex < text.length && text[charIndex].isLowSurrogate()) {
        charIndex++
      }
    }

    return text.subSequence(0, charIndex)
  }
}
