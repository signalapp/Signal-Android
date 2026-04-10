/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

/**
 * Treating an Enum as a circular list, returns the "next"
 * value after the caller, wrapping around to the first value
 * in the enum as necessary.
 */
inline fun <reified T : Enum<T>> T.next(): T {
  val values = enumValues<T>()
  val nextOrdinal = (ordinal + 1) % values.size

  return values[nextOrdinal]
}
