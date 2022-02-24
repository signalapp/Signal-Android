package org.thoughtcrime.securesms.util

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
