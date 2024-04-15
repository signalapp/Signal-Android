/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import com.google.android.material.datepicker.CalendarConstraints.DateValidator
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logTime
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.util.LRUCache
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.days

/**
 * A calendar validator for jumping to a specific date in a conversation.
 * This is used to prevent the user from jumping to a date where there are no messages.
 *
 * [isValid] is called on the main thread, so we try to race it and fetch the data ahead of time, fetching data in bulk and caching it.
 */
@Parcelize
class JumpToDateValidator(val threadId: Long) : DateValidator {

  companion object {
    private val TAG = Log.tag(JumpToDateValidator::class.java)
  }

  @IgnoredOnParcel
  private val lock = ReentrantLock()

  @IgnoredOnParcel
  private val condition: Condition = lock.newCondition()

  @IgnoredOnParcel
  private val cachedDates: MutableMap<Long, LookupState> = LRUCache(500)

  init {
    val startOfDay = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant(ZoneOffset.UTC).toEpochMilli()
    loadAround(startOfDay, allowPrefetch = true)
  }

  override fun isValid(dateStart: Long): Boolean {
    return lock.withLock {
      var value = cachedDates[dateStart]

      while (value == null || value == LookupState.PENDING) {
        loadAround(dateStart, allowPrefetch = true)
        condition.await()
        value = cachedDates[dateStart]
      }

      cachedDates[dateStart] == LookupState.FOUND
    }
  }

  /**
   * Given a date, this will load all of the dates for entire month the date is in.
   */
  private fun loadAround(dateStart: Long, allowPrefetch: Boolean) {
    SignalExecutors.BOUNDED.execute {
      val startOfDay = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateStart), ZoneOffset.UTC)

      val startOfMonth = startOfDay
        .with(TemporalAdjusters.firstDayOfMonth())
        .withHour(0).withMinute(0).withSecond(0).withNano(0)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

      val endOfMonth = startOfDay
        .with(TemporalAdjusters.lastDayOfMonth())
        .withHour(0).withMinute(0).withSecond(0).withNano(0)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

      val daysOfMonth = (startOfMonth..endOfMonth step 1.days.inWholeMilliseconds).toSet() + dateStart

      val lookupsNeeded = lock.withLock {
        daysOfMonth
          .filter { !cachedDates.containsKey(it) }
          .onEach { cachedDates[it] = LookupState.PENDING }
      }

      if (lookupsNeeded.isEmpty()) {
        return@execute
      }

      val existence = logTime(TAG, "query(${lookupsNeeded.size})", decimalPlaces = 2) {
        SignalDatabase.messages.messageExistsOnDays(threadId, lookupsNeeded)
      }

      lock.withLock {
        cachedDates.putAll(existence.mapValues { if (it.value) LookupState.FOUND else LookupState.NOT_FOUND })

        if (allowPrefetch) {
          val dayInPreviousMonth = startOfMonth - 1.days.inWholeMilliseconds
          if (!cachedDates.containsKey(dayInPreviousMonth)) {
            loadAround(dayInPreviousMonth, allowPrefetch = false)
          }

          val dayInNextMonth = endOfMonth + 1.days.inWholeMilliseconds
          if (!cachedDates.containsKey(dayInNextMonth)) {
            loadAround(dayInNextMonth, allowPrefetch = false)
          }
        }

        condition.signalAll()
      }
    }
  }

  private enum class LookupState {
    FOUND,
    NOT_FOUND,
    PENDING
  }
}
