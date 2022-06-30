package org.thoughtcrime.securesms.util

import org.signal.core.util.CharacterIterable
import java.util.regex.Pattern

object NameUtil {

  /**
   * \p{L} is letter, \p{Nd} is digit, \p{S} is whitespace/separator
   * https://www.regular-expressions.info/unicode.html#category
   */
  private val PATTERN = Pattern.compile("[^\\p{L}\\p{Nd}\\p{S}]+")

  /**
   * Returns an abbreviation of the input, up to two characters long.
   */
  @JvmStatic
  fun getAbbreviation(name: String): String? {
    val parts = name
      .split(" ")
      .map { it.trim() }
      .map { PATTERN.matcher(it).replaceFirst("") }
      .filter { it.isNotEmpty() }

    return when {
      parts.isEmpty() -> null
      parts.size == 1 -> parts[0].firstGrapheme()
      else -> "${parts[0].firstGrapheme()}${parts[1].firstGrapheme()}"
    }
  }

  private fun String.firstGrapheme(): String {
    return CharacterIterable(this).first()
  }
}
