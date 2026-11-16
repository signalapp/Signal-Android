/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import com.google.android.material.datepicker.CalendarConstraints.DateValidator
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration.Companion.days

/**
 * A calendar validator that only allows days a conversation actually has messages on.
 */
@Parcelize
class JumpToDateValidator(private val epochDaysWithMessages: LongArray) : DateValidator {

  companion object {
    private val DAY_MILLIS = 1.days.inWholeMilliseconds

    fun from(epochDaysWithMessages: Set<Long>): JumpToDateValidator {
      return JumpToDateValidator(epochDaysWithMessages.toLongArray())
    }
  }

  @IgnoredOnParcel
  private val epochDays: Set<Long> = epochDaysWithMessages.toHashSet()

  /**
   * The picker hands us UTC midnight of the calendar day it is rendering, which is exactly that day's epoch day number.
   */
  override fun isValid(date: Long): Boolean {
    return epochDays.contains(date.floorDiv(DAY_MILLIS))
  }
}
