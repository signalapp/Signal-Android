/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("PinValuesUtil")

package org.thoughtcrime.securesms.keyvalue

import org.signal.core.util.logging.Log
import org.signal.storage.keyvalue.PinValues
import org.thoughtcrime.securesms.lock.SignalPinReminders
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType

private val TAG = Log.tag(PinValues::class)

fun PinValues.onEntrySuccess(pin: String) {
  val nextInterval = SignalPinReminders.getNextInterval(nextReminderInterval)
  Log.i(TAG, "onEntrySuccess() nextInterval: $nextInterval")

  recordSuccessfulEntry(nextInterval)
  SignalStore.svr.setPinIfNotPresent(pin)
}

fun PinValues.onEntrySuccessWithWrongGuess(pin: String) {
  val nextInterval = SignalPinReminders.getPreviousInterval(nextReminderInterval)
  Log.i(TAG, "onEntrySuccessWithWrongGuess() nextInterval: $nextInterval")

  recordSuccessfulEntry(nextInterval)
  SignalStore.svr.setPinIfNotPresent(pin)
}

/**
 * Updates the last reminder time and, in the case of a failed guess, ratchets back the interval until the next reminder.
 */
fun PinValues.onEntrySkip(includedFailure: Boolean) {
  val nextInterval = if (includedFailure) {
    SignalPinReminders.getPreviousInterval(nextReminderInterval)
  } else {
    nextReminderInterval
  }
  Log.i(TAG, "onEntrySkip(includedFailure: $includedFailure) nextInterval: $nextInterval")

  recordSkippedEntry(nextInterval)
}

fun PinValues.resetPinReminders() {
  val nextInterval = SignalPinReminders.INITIAL_INTERVAL
  Log.i(TAG, "resetPinReminders() nextInterval: $nextInterval", Throwable())

  resetReminders(nextInterval)
}

val PinValues.keyboardType: PinKeyboardType
  get() {
    val pin = SignalStore.svr.pin ?: return PinKeyboardType.NUMERIC

    return if (pin.all { it.isDigit() }) {
      PinKeyboardType.NUMERIC
    } else {
      PinKeyboardType.ALPHA_NUMERIC
    }
  }
