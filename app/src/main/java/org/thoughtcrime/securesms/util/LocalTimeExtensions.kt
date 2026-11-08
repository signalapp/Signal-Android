package org.thoughtcrime.securesms.util

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats [LocalTime] as localized time. For example, "1:45 PM" or "13:45"
 */
fun LocalTime.formatHours(context: Context): String {
  return formatHours(is24Hour = DateFormat.is24HourFormat(context), locale = LocaleUtil.getFirstLocale())
}

/**
 * Formats [LocalTime] as localized time without needing a [Context], for callers that already know the 24-hour
 * setting and locale. For example, "1:45 PM" or "13:45"
 */
fun LocalTime.formatHours(is24Hour: Boolean, locale: Locale): String {
  // We have to create our own pattern here, since the formatter instance returned by DateTimeFormatter.ofLocalizedTime() is looked up lazily, is immutable,
  // and is not updated when the system's 24-hour time setting changes.
  val pattern = if (is24Hour) "HH:mm" else "h:mm a"
  return DateTimeFormatter.ofPattern(pattern, locale).format(this)
}
