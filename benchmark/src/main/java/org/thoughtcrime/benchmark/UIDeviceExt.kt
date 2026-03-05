/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.benchmark

import android.os.SystemClock
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice

/**
 * Inspired by [androidx.test.uiautomator.WaitMixin]
 */
fun UiDevice.waitForAny(timeout: Long, vararg conditions: BySelector): Boolean {
  val startTime = SystemClock.uptimeMillis()

  var result = conditions.any { this.hasObject(it) }
  var elapsedTime = 0L
  while (!result && elapsedTime < timeout) {
    SystemClock.sleep(100)
    result = conditions.any { this.hasObject(it) }
    elapsedTime = SystemClock.uptimeMillis() - startTime
  }

  return result
}
