/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.remoteconfig

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class SelectionLimits(val recommendedLimit: Int, val hardLimit: Int) : Parcelable {

  fun hasRecommendedLimit(): Boolean = recommendedLimit != NO_LIMIT

  fun hasHardLimit(): Boolean = hardLimit != NO_LIMIT

  fun excludingSelf(): SelectionLimits = excluding(1)

  fun excluding(count: Int): SelectionLimits = SelectionLimits(recommendedLimit - count, hardLimit - count)

  companion object {
    private const val NO_LIMIT = Int.MAX_VALUE

    @JvmField
    val NO_LIMITS = SelectionLimits(NO_LIMIT, NO_LIMIT)
  }
}
