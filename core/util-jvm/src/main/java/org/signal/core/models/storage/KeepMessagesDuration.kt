/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.storage

import java.util.concurrent.TimeUnit

/**
 * How long messages are kept before they're automatically deleted. Declared from longest to shortest, so a higher
 * ordinal always deletes more.
 */
enum class KeepMessagesDuration(val id: Int, val duration: Long) {
  FOREVER(0, Long.MAX_VALUE),
  ONE_YEAR(1, TimeUnit.DAYS.toMillis(367)),
  SIX_MONTHS(2, TimeUnit.DAYS.toMillis(183)),
  THIRTY_DAYS(3, TimeUnit.DAYS.toMillis(30));

  companion object {
    @JvmStatic
    fun fromId(id: Int): KeepMessagesDuration = entries[id]
  }
}
