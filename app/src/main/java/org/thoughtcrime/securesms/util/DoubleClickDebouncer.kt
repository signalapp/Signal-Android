/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

/**
 * A class to throttle on click events. If multiple clicks happen in succession (within the threshold)
 * We ignore both and let the caller know it was a double click.
 */
class DoubleClickDebouncer(private val threshold: Long) {
  private val handler = Handler(Looper.getMainLooper())

  constructor(threshold: Long, timeUnit: TimeUnit) : this(timeUnit.toMillis(threshold))

  private var clickEnqueued = false

  /**
   * Returns true if the click is enqueued, otherwise its a double click
   */
  fun onClick(runnable: Runnable?): Boolean {
    handler.removeCallbacksAndMessages(null)
    if (!clickEnqueued) {
      handler.postDelayed({
        runnable!!.run()
        clickEnqueued = false
      }, threshold)
      clickEnqueued = true
    } else {
      clickEnqueued = false
    }
    return clickEnqueued
  }

  fun clear() {
    handler.removeCallbacksAndMessages(null)
  }
}
