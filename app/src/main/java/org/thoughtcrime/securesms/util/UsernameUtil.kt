package org.thoughtcrime.securesms.util

import org.signal.core.util.logging.Log
import java.util.Locale
import java.util.regex.Pattern

object UsernameUtil {
  private val TAG = Log.tag(UsernameUtil::class.java)
  const val MIN_LENGTH = 3
  const val MAX_LENGTH = 32
  private val FULL_PATTERN = Pattern.compile(String.format(Locale.US, "^[a-zA-Z_][a-zA-Z0-9_]{%d,%d}$", MIN_LENGTH - 1, MAX_LENGTH - 1), Pattern.CASE_INSENSITIVE)
  private val DIGIT_START_PATTERN = Pattern.compile("^[0-9].*$")
  private const val BASE_URL_SCHEMELESS = "signal.me/#eu/"
  private const val BASE_URL = "https://$BASE_URL_SCHEMELESS"

  private val SEARCH_PATTERN = Pattern.compile(
    String.format(
      Locale.US,
      "^@?[a-zA-Z_][a-zA-Z0-9_]{%d,%d}(.[0-9]+)?$",
      MIN_LENGTH - 1,
      MAX_LENGTH - 1,
      Pattern.CASE_INSENSITIVE
    )
  )

  @JvmStatic
  fun isValidUsernameForSearch(value: String): Boolean {
    return value.isNotEmpty() && SEARCH_PATTERN.matcher(value).matches()
  }

  @JvmStatic
  fun sanitizeUsernameFromSearch(value: String): String {
    return value.replace("[^a-zA-Z0-9_.]".toRegex(), "")
  }

  @JvmStatic
  fun checkUsername(value: String?): InvalidReason? {
    return when {
      value == null -> {
        InvalidReason.TOO_SHORT
      }
      value.length < MIN_LENGTH -> {
        InvalidReason.TOO_SHORT
      }
      value.length > MAX_LENGTH -> {
        InvalidReason.TOO_LONG
      }
      DIGIT_START_PATTERN.matcher(value).matches() -> {
        InvalidReason.STARTS_WITH_NUMBER
      }
      !FULL_PATTERN.matcher(value).matches() -> {
        InvalidReason.INVALID_CHARACTERS
      }
      else -> {
        null
      }
    }
  }

  enum class InvalidReason {
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARACTERS,
    STARTS_WITH_NUMBER
  }
}
