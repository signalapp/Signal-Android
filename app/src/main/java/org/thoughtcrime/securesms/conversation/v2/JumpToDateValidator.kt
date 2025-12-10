/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import com.google.android.material.datepicker.CalendarConstraints.DateValidator
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.util.LRUCache
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.days

/**
 * Interface for looking up whether messages exist on given dates.
 * Allows for easy testing without database dependencies.
 */
private typealias MessageDateLookup = (Collection<Long>) -> Map<Long, Boolean>

/**
 * A calendar validator for jumping to a specific date in a conversation.
 * This is used to prevent the user from jumping to a date where there are no messages.
 *
 * [isValid] is called on the main thread, so we try to race it and fetch the data ahead of time, fetching data in bulk and caching it.
 * If data is not yet cached, we return true to avoid blocking the main thread.
 */
@Parcelize
class JumpToDateValidator private constructor(
  private val threadId: Long,
  @IgnoredOnParcel private val messageExistanceLookup: MessageDateLookup = createDefaultLookup(threadId),
  @IgnoredOnParcel private val executor: Executor = SignalExecutors.BOUNDED,
  private val zoneId: ZoneId = ZoneId.systemDefault()
) : DateValidator {

  companion object {

    fun createDefaultLookup(threadId: Long): MessageDateLookup {
      return { dayStarts -> SignalDatabase.messages.messageExistsOnDays(threadId, dayStarts) }
    }

    @JvmStatic
    fun create(threadId: Long): JumpToDateValidator {
      return JumpToDateValidator(
        threadId = threadId,
        messageExistanceLookup = createDefaultLookup(threadId),
        executor = SignalExecutors.BOUNDED,
        zoneId = ZoneId.systemDefault()
      ).also {
        it.performInitialPrefetch()
      }
    }

    @JvmStatic
    fun createForTesting(lookup: MessageDateLookup, executor: Executor, zoneId: ZoneId): JumpToDateValidator {
      return JumpToDateValidator(
        threadId = -1,
        messageExistanceLookup = lookup,
        executor = executor,
        zoneId = zoneId
      ).also {
        it.performInitialPrefetch()
      }
    }
  }

  @IgnoredOnParcel
  private val cachedDates: MutableMap<Long, LookupState> = LRUCache(1000)

  @IgnoredOnParcel
  private val pendingMonths: MutableSet<Long> = mutableSetOf()

  override fun isValid(dateStart: Long): Boolean {
    val localMidnightTimestamp = normalizeToLocalMidnight(dateStart)

    return synchronized(this) {
      when (cachedDates[localMidnightTimestamp]) {
        LookupState.FOUND -> true
        LookupState.NOT_FOUND -> false
        LookupState.PENDING, null -> {
          loadAround(localMidnightTimestamp, allowPrefetch = true)
          true
        }
      }
    }
  }

  private fun performInitialPrefetch() {
    val today = LocalDate.now(zoneId)
    val monthsToPrefetch = 6

    for (i in 0 until monthsToPrefetch) {
      val targetMonth = today.minusMonths(i.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
      loadAround(targetMonth, allowPrefetch = false)
    }
  }

  private fun normalizeToLocalMidnight(timestamp: Long): Long {
    return Instant.ofEpochMilli(timestamp)
      .atZone(zoneId)
      .toLocalDate()
      .atStartOfDay(zoneId)
      .toInstant()
      .toEpochMilli()
  }

  /**
   * Given a date, this will load all of the dates for entire month the date is in.
   */
  private fun loadAround(dateStart: Long, allowPrefetch: Boolean) {
    val startOfDay = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateStart), zoneId)

    val startOfMonth = startOfDay
      .with(TemporalAdjusters.firstDayOfMonth())
      .atZone(zoneId)
      .toLocalDate()
      .atStartOfDay(zoneId)
      .toInstant()
      .toEpochMilli()

    val endOfMonth = startOfDay
      .with(TemporalAdjusters.lastDayOfMonth())
      .atZone(zoneId)
      .toLocalDate()
      .atStartOfDay(zoneId)
      .toInstant()
      .toEpochMilli()

    synchronized(this) {
      if (pendingMonths.contains(startOfMonth)) {
        return
      }
      pendingMonths.add(startOfMonth)
    }

    executor.execute {
      val daysOfMonth = (startOfMonth..endOfMonth step 1.days.inWholeMilliseconds).toSet() + dateStart

      val lookupsNeeded = synchronized(this) {
        daysOfMonth
          .filter { cachedDates[it] == null }
          .onEach { cachedDates[it] = LookupState.PENDING }
      }

      if (lookupsNeeded.isEmpty()) {
        return@execute
      }

      val existence = messageExistanceLookup.invoke(lookupsNeeded)

      synchronized(this) {
        cachedDates.putAll(existence.mapValues { if (it.value) LookupState.FOUND else LookupState.NOT_FOUND })

        if (allowPrefetch) {
          val dayInPreviousMonth = startOfMonth - 1.days.inWholeMilliseconds
          loadAround(dayInPreviousMonth, allowPrefetch = false)

          val dayInNextMonth = endOfMonth + 1.days.inWholeMilliseconds
          loadAround(dayInNextMonth, allowPrefetch = false)
        }
      }
    }
  }

  private enum class LookupState {
    FOUND,
    NOT_FOUND,
    PENDING
  }
}
