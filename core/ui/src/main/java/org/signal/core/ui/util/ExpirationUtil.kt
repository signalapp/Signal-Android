/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import org.signal.core.ui.R
import java.util.concurrent.TimeUnit

object ExpirationUtil {

  private val SECONDS_IN_WEEK = TimeUnit.DAYS.toSeconds(7).toInt()
  private val SECONDS_IN_DAY = TimeUnit.DAYS.toSeconds(1).toInt()
  private val SECONDS_IN_HOUR = TimeUnit.HOURS.toSeconds(1).toInt()
  private val SECONDS_IN_MINUTE = TimeUnit.MINUTES.toSeconds(1).toInt()

  @JvmStatic
  fun getExpirationDisplayValue(context: Context, expirationTime: Int): String {
    if (expirationTime <= 0) {
      return context.getString(R.string.expiration_off)
    }

    var displayValue = ""
    var secondsRemaining = expirationTime

    val weeks = secondsRemaining / SECONDS_IN_WEEK
    displayValue = getDisplayValue(context, displayValue, R.plurals.expiration_weeks, weeks)
    secondsRemaining -= weeks * SECONDS_IN_WEEK

    val days = secondsRemaining / SECONDS_IN_DAY
    displayValue = getDisplayValue(context, displayValue, R.plurals.expiration_days, days)
    if (weeks > 0) {
      return displayValue
    }
    secondsRemaining -= days * SECONDS_IN_DAY

    val hours = secondsRemaining / SECONDS_IN_HOUR
    displayValue = getDisplayValue(context, displayValue, R.plurals.expiration_hours, hours)
    if (days > 0) {
      return displayValue
    }
    secondsRemaining -= hours * SECONDS_IN_HOUR

    val minutes = secondsRemaining / SECONDS_IN_MINUTE
    displayValue = getDisplayValue(context, displayValue, R.plurals.expiration_minutes, minutes)
    if (hours > 0) {
      return displayValue
    }
    secondsRemaining -= minutes * SECONDS_IN_MINUTE

    return getDisplayValue(context, displayValue, R.plurals.expiration_seconds, secondsRemaining)
  }

  @JvmStatic
  fun getExpirationAbbreviatedDisplayValue(context: Context, expirationTime: Int): String {
    if (expirationTime <= 0) {
      return context.getString(R.string.expiration_off)
    }

    var displayValue = ""
    var secondsRemaining = expirationTime

    val weeks = secondsRemaining / SECONDS_IN_WEEK
    displayValue = getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_weeks_abbreviated, weeks)
    secondsRemaining -= weeks * SECONDS_IN_WEEK

    val days = secondsRemaining / SECONDS_IN_DAY
    displayValue = getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_days_abbreviated, days)
    secondsRemaining -= days * SECONDS_IN_DAY

    val hours = secondsRemaining / SECONDS_IN_HOUR
    displayValue = getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_hours_abbreviated, hours)
    secondsRemaining -= hours * SECONDS_IN_HOUR

    val minutes = secondsRemaining / SECONDS_IN_MINUTE
    displayValue = getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_minutes_abbreviated, minutes)
    secondsRemaining -= minutes * SECONDS_IN_MINUTE

    return getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_seconds_abbreviated, secondsRemaining)
  }

  private fun getDisplayValue(context: Context, currentValue: String, @PluralsRes plurals: Int, duration: Int): String {
    if (duration <= 0) {
      return currentValue
    }

    val durationString = context.resources.getQuantityString(plurals, duration, duration)
    return if (currentValue.isEmpty()) durationString else context.getString(R.string.expiration_combined, currentValue, durationString)
  }

  private fun getAbbreviatedDisplayValue(context: Context, currentValue: String, @StringRes abbreviation: Int, duration: Int): String {
    if (duration <= 0) {
      return currentValue
    }

    val durationString = context.getString(abbreviation, duration)
    return if (currentValue.isEmpty()) durationString else context.getString(R.string.expiration_combined, currentValue, durationString)
  }
}
