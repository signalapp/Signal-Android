package org.thoughtcrime.securesms.util

import org.signal.core.util.CharacterIterable
import java.util.regex.Pattern

object NameUtil {

  /**
   * \p{L} is letter, \p{Nd} is digit, \p{S} is symbol (which includes most emoji)
   * https://www.regular-expressions.info/unicode.html#category
   */
  private val PATTERN = Pattern.compile("^[\\p{L}\\p{Nd}\\p{S}]")

  /**
   * Returns an abbreviation of the input, up to two characters long.
   */
  @JvmStatic
  fun getAbbreviation(name: String): String? {
    val parts = name
      .split(" ")
      .mapNotNull { it.trim().firstValidGrapheme() }

    return when {
      parts.isEmpty() -> null
      parts.size == 1 -> parts[0]
      else -> "${parts[0]}${parts[1]}"
    }
  }

  /** Returns the first grapheme cluster that starts with a letter, digit, or symbol. */
  private fun String.firstValidGrapheme(): String? {
    return CharacterIterable(this).firstOrNull { PATTERN.matcher(it).find() }
  }
}
