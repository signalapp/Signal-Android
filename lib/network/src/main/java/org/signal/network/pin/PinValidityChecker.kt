/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.pin

import org.whispersystems.signalservice.api.kbs.PinString

/**
 * Rejects PINs that are trivially guessable. A numeric PIN must not be empty, sequential in either
 * direction, or a single repeated digit. Non-numeric PINs are only checked for emptiness.
 */
object PinValidityChecker {

  @JvmStatic
  fun valid(pin: String): Boolean {
    val trimmed = pin.trim()

    if (trimmed.isEmpty()) {
      return false
    }

    if (!PinString.allNumeric(trimmed)) {
      return true
    }

    val arabic = PinString.toArabic(trimmed)

    return !arabic.isSequential() && !arabic.reversed().isSequential() && !arabic.isSingleRepeatedChar()
  }

  private fun String.isSequential(): Boolean {
    return zipWithNext().all { (previous, next) -> next == previous + 1 }
  }

  private fun String.isSingleRepeatedChar(): Boolean {
    return all { it == this[0] }
  }
}
