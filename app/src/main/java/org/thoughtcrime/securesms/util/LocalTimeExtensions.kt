package org.thoughtcrime.securesms.util

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Formats [LocalTime] as localized time. For example, "1:45 PM" or "13:45"
 */
fun LocalTime.formatHours(context: Context): String {
  // We have to create our own pattern here, since the formatter instance returned by DateTimeFormatter.ofLocalizedTime() is looked up lazily, is immutable,
  // and is not updated when the system's 24-hour time setting changes.
  val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
  return DateTimeFormatter.ofPattern(pattern, LocaleUtil.getFirstLocale()).format(this)
}
