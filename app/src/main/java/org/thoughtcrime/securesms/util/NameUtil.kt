package org.thoughtcrime.securesms.util

import android.text.TextUtils
import java.util.regex.Pattern

object NameUtil {
  private val PATTERN = Pattern.compile("[^\\p{L}\\p{Nd}\\p{S}]+")

  /**
   * Returns an abbreviation of the input, up to two characters long.
   */
  @JvmStatic
  fun getAbbreviation(name: String): String? {
    val parts = name.split(" ").toTypedArray()
    val builder = StringBuilder()
    var count = 0
    var i = 0

    while (i < parts.size && count < 2) {
      val cleaned = PATTERN.matcher(parts[i]).replaceFirst("")
      if (!TextUtils.isEmpty(cleaned)) {
        builder.appendCodePoint(cleaned.codePointAt(0))
        count++
      }
      i++
    }

    return if (builder.isEmpty()) {
      null
    } else {
      builder.toString()
    }
  }
}
