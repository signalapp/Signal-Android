/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.computed.FormattedDate
import java.text.DateFormatSymbols
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Utility methods to help display dates in a nice, easily readable way.
 */
object DateUtils : android.text.format.DateUtils() {
  private val TAG = Log.tag(DateUtils::class.java)
  private val MAX_RELATIVE_TIMESTAMP = 3.minutes.inWholeMilliseconds
  private const val HALF_A_YEAR_IN_DAYS = 182

  private val sameDayDateFormat: SimpleDateFormat by lazy { SimpleDateFormat("yyyyMMdd") }

  private val localizedTemplateCache: MutableMap<TemplateLocale, String> = mutableMapOf()
  private val dateFormatCache: MutableMap<TemplateLocale, SimpleDateFormat> = mutableMapOf()
  private val dateFormatSymbolsCache: MutableMap<Locale, DateFormatSymbols> = mutableMapOf()

  private var is24HourDateCache: Is24HourDateEntry? = null

  /**
   * A relative timestamp to use in space-constrained areas, like the conversation list.
   */
  @JvmStatic
  fun getBriefRelativeTimeSpanString(c: Context, locale: Locale, timestamp: Long): String {
    return when {
      isNow(timestamp) -> {
        c.getString(R.string.DateUtils_just_now)
      }
      timestamp.isWithin(1.hours) -> {
        val minutes = timestamp.convertDeltaTo(DurationUnit.MINUTES)
        c.resources.getString(R.string.DateUtils_minutes_ago, minutes)
      }
      timestamp.isWithin(1.days) -> {
        val hours = timestamp.convertDeltaTo(DurationUnit.HOURS)
        c.resources.getQuantityString(R.plurals.hours_ago, hours, hours)
      }
      timestamp.isWithin(6.days) -> {
        timestamp.toDateString("EEE", locale)
      }
      timestamp.isWithin(365.days) -> {
        timestamp.toDateString("MMM d", locale)
      }
      else -> {
        timestamp.toDateString("MMM d, yyyy", locale)
      }
    }
  }

  /**
   * Similar to [getBriefRelativeTimeSpanString], except this will include additional time information in longer formats.
   */
  @JvmStatic
  fun getExtendedRelativeTimeSpanString(context: Context, locale: Locale, timestamp: Long): String {
    return when {
      isNow(timestamp) -> {
        context.getString(R.string.DateUtils_just_now)
      }
      timestamp.isWithin(1.hours) -> {
        val minutes = timestamp.convertDeltaTo(DurationUnit.MINUTES)
        context.resources.getString(R.string.DateUtils_minutes_ago, minutes)
      }
      else -> {
        val format = StringBuilder()

        if (timestamp.isWithin(6.days)) {
          format.append("EEE ")
        } else if (timestamp.isWithin(365.days)) {
          format.append("MMM d, ")
        } else {
          format.append("MMM d, yyyy, ")
        }

        if (context.is24HourFormat()) {
          format.append("HH:mm")
        } else {
          format.append("hh:mm a")
        }

        timestamp.toDateString(format.toString(), locale)
      }
    }
  }

  /**
   * Returns a relative time string that will only use the time of day for longer intervals. The assumption is that it would be used in a context
   * that communicates the date elsewhere.
   */
  @JvmStatic
  fun getDatelessRelativeTimeSpanString(context: Context, locale: Locale, timestamp: Long): String {
    return getDatelessRelativeTimeSpanFormattedDate(context, locale, timestamp).value
  }

  @JvmStatic
  fun getDatelessRelativeTimeSpanFormattedDate(context: Context, locale: Locale, timestamp: Long): FormattedDate {
    return when {
      isNow(timestamp) -> {
        FormattedDate(isRelative = true, isNow = true, value = context.getString(R.string.DateUtils_just_now))
      }
      timestamp.isWithin(1.hours) -> {
        val minutes = timestamp.convertDeltaTo(DurationUnit.MINUTES)
        FormattedDate(isRelative = true, isNow = false, value = context.resources.getString(R.string.DateUtils_minutes_ago, minutes))
      }
      else -> {
        FormattedDate(isRelative = false, isNow = false, value = getOnlyTimeString(context, timestamp))
      }
    }
  }

  /**
   * Formats a given timestamp as just the time.
   *
   * For example:
   * For 12 hour locale: 7:23 pm
   * For 24 hour locale: 19:23
   */
  @JvmStatic
  fun getOnlyTimeString(context: Context, timestamp: Long): String {
    return timestamp.toLocalTime().formatHours(context)
  }

  /**
   * If on the same day, will return just the time. Otherwise it'll include relative date info.
   */
  @JvmStatic
  fun getTimeString(context: Context, locale: Locale, timestamp: Long): String {
    val format = StringBuilder()

    if (isSameDay(System.currentTimeMillis(), timestamp)) {
      format.append("")
    } else if (timestamp.isWithinAbs(6.days)) {
      format.append("EEE ")
    } else if (timestamp.isWithinAbs(364.days)) {
      format.append("MMM d, ")
    } else {
      format.append("MMM d, yyyy, ")
    }

    if (context.is24HourFormat()) {
      format.append("HH:mm")
    } else {
      format.append("hh:mm a")
    }

    return timestamp.toDateString(format.toString(), locale)
  }

  /**
   * Formats the passed timestamp based on the current time at a day precision.
   *
   * For example:
   * - Today
   * - Wed
   * - Mon
   * - Jan 31
   * - Feb 4
   * - Jan 12, 2033
   */
  fun getDayPrecisionTimeString(context: Context, locale: Locale, timestamp: Long): String {
    return if (isSameDay(System.currentTimeMillis(), timestamp)) {
      context.getString(R.string.DeviceListItem_today)
    } else {
      val format: String = when {
        timestamp.isWithinAbs(6.days) -> "EEE "
        timestamp.isWithinAbs(365.days) -> "MMM d"
        else -> "MMM d, yyy"
      }
      timestamp.toDateString(format, locale)
    }
  }

  @JvmStatic
  fun getDayPrecisionTimeSpanString(context: Context, locale: Locale, timestamp: Long): String {
    return if (isSameDay(System.currentTimeMillis(), timestamp)) {
      context.getString(R.string.DeviceListItem_today)
    } else {
      timestamp.toDateString("dd/MM/yy", locale)
    }
  }

  @JvmStatic
  fun getDetailedDateFormatter(context: Context, locale: Locale): SimpleDateFormat {
    val dateFormatPattern: String = if (context.is24HourFormat()) {
      "MMM d, yyyy HH:mm:ss zzz".localizeTemplate(locale)
    } else {
      "MMM d, yyyy hh:mm:ss a zzz".localizeTemplate(locale)
    }
    return SimpleDateFormat(dateFormatPattern, locale)
  }

  @JvmStatic
  fun getConversationDateHeaderString(
    context: Context,
    locale: Locale,
    timestamp: Long
  ): String {
    return if (isToday(timestamp)) {
      context.getString(R.string.DateUtils_today)
    } else if (isYesterday(timestamp)) {
      context.getString(R.string.DateUtils_yesterday)
    } else if (timestamp.isWithin(HALF_A_YEAR_IN_DAYS.days)) {
      formatDateWithDayOfWeek(locale, timestamp)
    } else {
      formatDateWithYear(locale, timestamp)
    }
  }

  @JvmStatic
  fun getScheduledMessagesDateHeaderString(context: Context, locale: Locale, timestamp: Long): String {
    return if (isToday(timestamp)) {
      context.getString(R.string.DateUtils_today)
    } else if (timestamp.isWithinAbs(HALF_A_YEAR_IN_DAYS.days)) {
      formatDateWithDayOfWeek(locale, timestamp)
    } else {
      formatDateWithYear(locale, timestamp)
    }
  }

  fun getScheduledMessageDateString(context: Context, timestamp: Long): String {
    val localDateTime = timestamp.toLocalDateTime()

    val dayModifier: String = if (isToday(timestamp)) {
      if (localDateTime.hour >= 19) {
        context.getString(R.string.DateUtils_tonight)
      } else {
        context.getString(R.string.DateUtils_today)
      }
    } else {
      context.getString(R.string.DateUtils_tomorrow)
    }
    val time = localDateTime.toLocalTime().formatHours(context)
    return context.getString(R.string.DateUtils_schedule_at, dayModifier, time)
  }

  fun formatDateWithDayOfWeek(locale: Locale, timestamp: Long): String {
    return timestamp.toDateString("EEE, MMM d", locale)
  }

  fun formatDateWithYear(locale: Locale, timestamp: Long): String {
    return timestamp.toDateString("MMM d, yyyy", locale)
  }

  @JvmStatic
  fun formatDate(locale: Locale, timestamp: Long): String {
    return timestamp.toDateString("EEE, MMM d, yyyy", locale)
  }

  @JvmStatic
  fun formatDateWithoutDayOfWeek(locale: Locale, timestamp: Long): String {
    return timestamp.toDateString("MMM d yyyy", locale)
  }

  /**
   * True if the two timestamps occur on the same day, otherwise false.
   */
  @JvmStatic
  fun isSameDay(t1: Long, t2: Long): Boolean {
    val d1 = sameDayDateFormat.format(Date(t1))
    val d2 = sameDayDateFormat.format(Date(t2))
    return d1 == d2
  }

  @JvmStatic
  fun isSameExtendedRelativeTimestamp(second: Long, first: Long): Boolean {
    return second - first < MAX_RELATIVE_TIMESTAMP
  }

  /**
   * e.g. 2020-09-04T19:17:51Z
   * https://www.iso.org/iso-8601-date-and-time-format.html
   *
   * Note: SDK_INT == 0 check needed to pass unit tests due to JVM date parser differences.
   *
   * @return The timestamp if able to be parsed, otherwise -1.
   */
  @JvmStatic
  @SuppressLint("ObsoleteSdkInt", "NewApi")
  fun parseIso8601(date: String?): Long {
    val format: SimpleDateFormat = if (Build.VERSION.SDK_INT == 0 || Build.VERSION.SDK_INT >= 24) {
      "yyyy-MM-dd'T'HH:mm:ssX".toSimpleDateFormat(Locale.getDefault())
    } else {
      "yyyy-MM-dd'T'HH:mm:ssZ".toSimpleDateFormat(Locale.getDefault())
    }

    return if (date.isNullOrBlank()) {
      -1
    } else {
      try {
        format.parse(date)?.time ?: -1
      } catch (e: ParseException) {
        Log.w(TAG, "Failed to parse date.", e)
        -1
      }
    }
  }

  /**
   * This exposes "now" (defined here as a one minute window) to other classes.
   * This is because certain locales use different linguistic constructions for "modified n minutes ago" and "modified just now",
   * and therefore the caller will need to load different string resources in these situations.
   *
   * @param timestamp a Unix timestamp
   */
  @JvmStatic
  fun isNow(timestamp: Long) = timestamp.isWithin(1.minutes)

  private fun Long.isWithin(duration: Duration): Boolean {
    return System.currentTimeMillis() - this <= duration.inWholeMilliseconds
  }

  private fun Long.isWithinAbs(duration: Duration): Boolean {
    return abs(System.currentTimeMillis() - this) <= duration.inWholeMilliseconds
  }

  private fun isYesterday(time: Long): Boolean {
    return isToday(time + TimeUnit.DAYS.toMillis(1))
  }

  private fun Context.is24HourFormat(): Boolean {
    is24HourDateCache?.let {
      if (it.lastUpdated.isWithin(10.seconds)) {
        return it.value
      }
    }

    val result = DateFormat.is24HourFormat(this)
    is24HourDateCache = Is24HourDateEntry(result, System.currentTimeMillis())
    return result
  }

  private fun Long.convertDeltaTo(unit: DurationUnit): Int {
    return (System.currentTimeMillis() - this).milliseconds.toInt(unit)
  }

  private fun Long.toDateString(template: String, locale: Locale): String {
    return template
      .localizeTemplate(locale)
      .toSimpleDateFormat(locale)
      .setLowercaseAmPmStrings(locale)
      .format(Date(this))
  }

  private fun String.localizeTemplate(locale: Locale): String {
    val key = TemplateLocale(this, locale)
    return localizedTemplateCache.getOrPut(key) {
      DateFormat.getBestDateTimePattern(key.locale, key.template)
    }
  }

  private fun String.toSimpleDateFormat(locale: Locale): SimpleDateFormat {
    val key = TemplateLocale(this, locale)
    return dateFormatCache.getOrPut(key) {
      SimpleDateFormat(key.template, key.locale)
    }
  }

  private fun SimpleDateFormat.setLowercaseAmPmStrings(locale: Locale): SimpleDateFormat {
    val symbols = dateFormatSymbolsCache.getOrPut(locale) {
      DateFormatSymbols(locale).apply {
        amPmStrings = arrayOf("am", "pm")
      }
    }
    this.dateFormatSymbols = symbols
    return this
  }

  private data class TemplateLocale(val template: String, val locale: Locale)

  private data class Is24HourDateEntry(val value: Boolean, val lastUpdated: Long)
}
