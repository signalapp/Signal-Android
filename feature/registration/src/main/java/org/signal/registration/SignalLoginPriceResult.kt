/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

/**
 * Outcome of looking up what a Signal Login costs.
 */
sealed interface SignalLoginPriceResult {
  /** @param formattedPrice Already formatted in the currency and locale of the user's Play account. */
  data class Available(val formattedPrice: String) : SignalLoginPriceResult

  /** A Signal Login can never be bought in this build, or the product isn't published. Retrying won't help. */
  data object Unavailable : SignalLoginPriceResult

  /** The lookup failed for a reason that may not recur, e.g. no network or a rate limit. Worth a retry. */
  data object TransientError : SignalLoginPriceResult
}
