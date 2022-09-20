package org.signal.core.util

import android.app.PendingIntent
import android.os.Build

/**
 * Wrapper class for lower level API compatibility with the new Pending Intents flags.
 *
 * This is meant to be a replacement to using PendingIntent flags independently, and should
 * end up being the only place in our codebase that accesses these values.
 *
 * The "default" value is FLAG_MUTABLE
 */
object PendingIntentFlags {

  @JvmStatic
  fun updateCurrent(): Int {
    return mutable() or PendingIntent.FLAG_UPDATE_CURRENT
  }

  @JvmStatic
  fun cancelCurrent(): Int {
    return mutable() or PendingIntent.FLAG_CANCEL_CURRENT
  }

  @JvmStatic
  fun oneShot(): Int {
    return mutable() or PendingIntent.FLAG_ONE_SHOT
  }

  /**
   * The backwards compatible "default" value for pending intent flags.
   */
  @JvmStatic
  fun mutable(): Int {
    return if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
  }

  @JvmStatic
  fun immutable(): Int {
    return if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
  }
}
