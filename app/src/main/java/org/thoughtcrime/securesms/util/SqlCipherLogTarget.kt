/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import net.zetetic.database.LogTarget
import net.zetetic.database.Logger
import org.signal.core.util.logging.Log

object SqlCipherLogTarget : LogTarget {
  override fun isLoggable(tag: String?, priority: Int): Boolean {
    // Logger.DEBUG logs are extremely verbose, and include things like status updates on cursors being filled
    return priority >= Logger.INFO
  }

  override fun log(priority: Int, tag: String?, message: String?, throwable: Throwable?) {
    val tag = tag ?: "SqlCipher"
    when (priority) {
      Logger.VERBOSE -> Log.v(tag, message, throwable)
      Logger.DEBUG -> Log.d(tag, message, throwable)
      Logger.INFO -> Log.i(tag, message, throwable)
      Logger.WARN -> Log.w(tag, message, throwable)
      Logger.ERROR -> Log.e(tag, message, throwable)
      Logger.ASSERT -> Log.e(tag, message, throwable)
      else -> Log.d(tag, message, throwable)
    }
  }
}
