package org.thoughtcrime.securesms.calls.links

/**
 * Utility object for call links to try to keep some common logic in one place.
 */
object CallLinks {
  fun url(identifier: String) = "https://calls.signal.org/#$identifier"
}
