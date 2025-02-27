package org.signal.core.util

import android.text.SpannableStringBuilder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

object StringUtil {
  private val WHITESPACE: Set<Char> = setOf(
    '\u200E', // left-to-right mark
    '\u200F', // right-to-left mark
    '\u2007', // figure space
    '\u200B', // zero-width space
    '\u2800' // braille blank
  )

  /**
   * Trims a name string to fit into the byte length requirement.
   *
   *
   * This method treats a surrogate pair and a grapheme cluster a single character
   * See examples in tests defined in StringUtilText_trimToFit.
   */
  @JvmStatic
  fun trimToFit(name: String?, maxByteLength: Int): String {
    if (name.isNullOrEmpty()) {
      return ""
    }

    if (name.toByteArray(StandardCharsets.UTF_8).size <= maxByteLength) {
      return name
    }

    try {
      ByteArrayOutputStream().use { stream ->
        for (graphemeCharacter in CharacterIterable(name)) {
          val bytes = graphemeCharacter.toByteArray(StandardCharsets.UTF_8)

          if (stream.size() + bytes.size <= maxByteLength) {
            stream.write(bytes)
          } else {
            break
          }
        }
        return stream.toString()
      }
    } catch (e: IOException) {
      throw AssertionError(e)
    }
  }

  /**
   * @return A charsequence with no leading or trailing whitespace. Only creates a new charsequence
   * if it has to.
   */
  @JvmStatic
  fun trim(charSequence: CharSequence): CharSequence {
    if (charSequence.isEmpty()) {
      return charSequence
    }

    var start = 0
    var end = charSequence.length - 1

    while (start < charSequence.length && Character.isWhitespace(charSequence[start])) {
      start++
    }

    while (end >= 0 && end > start && Character.isWhitespace(charSequence[end])) {
      end--
    }

    return if (start > 0 || end < charSequence.length - 1) {
      charSequence.subSequence(start, end + 1)
    } else {
      charSequence
    }
  }

  /**
   * @return True if the string is empty, or if it contains nothing but whitespace characters.
   * Accounts for various unicode whitespace characters.
   */
  @JvmStatic
  fun isVisuallyEmpty(value: String?): Boolean {
    if (value.isNullOrEmpty()) {
      return true
    }

    return indexOfFirstNonEmptyChar(value) == -1
  }

  /**
   * @return String without any leading or trailing whitespace.
   * Accounts for various unicode whitespace characters.
   */
  @JvmStatic
  fun trimToVisualBounds(value: String): String {
    val start = indexOfFirstNonEmptyChar(value)

    if (start == -1) {
      return ""
    }

    val end = indexOfLastNonEmptyChar(value)

    return value.substring(start, end + 1)
  }

  private fun indexOfFirstNonEmptyChar(value: String): Int {
    val length = value.length

    for (i in 0 until length) {
      if (!isVisuallyEmpty(value[i])) {
        return i
      }
    }

    return -1
  }

  private fun indexOfLastNonEmptyChar(value: String): Int {
    for (i in value.length - 1 downTo 0) {
      if (!isVisuallyEmpty(value[i])) {
        return i
      }
    }
    return -1
  }

  /**
   * @return True if the character is invisible or whitespace. Accounts for various unicode
   * whitespace characters.
   */
  fun isVisuallyEmpty(c: Char): Boolean {
    return Character.isWhitespace(c) || WHITESPACE.contains(c)
  }

  /**
   * @return A string representation of the provided unicode code point.
   */
  fun codePointToString(codePoint: Int): String {
    return String(Character.toChars(codePoint))
  }

  /**
   * @return True if the text is null or has a length of 0, otherwise false.
   */
  @JvmStatic
  fun isEmpty(text: String?): Boolean {
    return text.isNullOrEmpty()
  }

  /**
   * Trims a [CharSequence] of starting and trailing whitespace. Behavior matches
   * [String.trim] to preserve expectations around results.
   */
  @JvmStatic
  fun trimSequence(text: CharSequence): CharSequence {
    var length = text.length
    var startIndex = 0

    while ((startIndex < length) && (text[startIndex] <= ' ')) {
      startIndex++
    }
    while ((startIndex < length) && (text[length - 1] <= ' ')) {
      length--
    }
    return if ((startIndex > 0 || length < text.length)) text.subSequence(startIndex, length) else text
  }

  /**
   * If the {@param text} exceeds the {@param maxChars} it is trimmed in the middle so that the result is exactly {@param maxChars} long including an added
   * ellipsis character.
   *
   *
   * Otherwise the string is returned untouched.
   *
   *
   * When {@param maxChars} is even, one more character is kept from the end of the string than the start.
   */
  @JvmStatic
  fun abbreviateInMiddle(text: CharSequence?, maxChars: Int): CharSequence? {
    if (text == null || text.length <= maxChars) {
      return text
    }

    val start = (maxChars - 1) / 2
    val end = (maxChars - 1) - start
    return text.subSequence(0, start).toString() + "…" + text.subSequence(text.length - end, text.length)
  }

  /**
   * @return The number of graphemes in the provided string.
   */
  @JvmStatic
  fun getGraphemeCount(text: CharSequence): Int {
    val iterator = BreakIteratorCompat.getInstance()
    iterator.setText(text)
    return iterator.countBreaks()
  }

  @JvmStatic
  fun replace(text: CharSequence, toReplace: Char, replacement: String?): CharSequence {
    var updatedText: SpannableStringBuilder? = null

    for (i in text.length - 1 downTo 0) {
      if (text[i] == toReplace) {
        if (updatedText == null) {
          updatedText = SpannableStringBuilder.valueOf(text)
        }
        updatedText!!.replace(i, i + 1, replacement)
      }
    }

    return updatedText ?: text
  }

  @JvmStatic
  fun startsWith(text: CharSequence, substring: CharSequence): Boolean {
    if (substring.length > text.length) {
      return false
    }

    for (i in substring.indices) {
      if (text[i] != substring[i]) {
        return false
      }
    }

    return true
  }

  @JvmStatic
  fun endsWith(text: CharSequence, substring: CharSequence): Boolean {
    if (substring.length > text.length) {
      return false
    }

    var textIndex = text.length - 1
    var substringIndex = substring.length - 1
    while (substringIndex >= 0) {
      if (text[textIndex] != substring[substringIndex]) {
        return false
      }
      substringIndex--
      textIndex--
    }

    return true
  }
}
