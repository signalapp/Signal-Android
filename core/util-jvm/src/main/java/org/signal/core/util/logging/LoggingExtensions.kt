/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.logging

/**
 * Convenience method to replace `.also { Log.v(TAG, "message") }`
 */
fun <T> T.logV(tag: String, message: String, throwable: Throwable? = null): T {
  Log.v(tag, message, throwable)
  return this
}

/**
 * Convenience method to replace `.also { Log.d(TAG, "message") }`
 */
fun <T> T.logD(tag: String, message: String, throwable: Throwable? = null): T {
  Log.d(tag, message, throwable)
  return this
}

/**
 * Convenience method to replace `.also { Log.i(TAG, "message") }`
 */
fun <T> T.logI(tag: String, message: String, throwable: Throwable? = null): T {
  Log.i(tag, message, throwable)
  return this
}

/**
 * Convenience method to replace `.also { Log.w(TAG, "message") }`
 */
fun <T> T.logW(tag: String, message: String, throwable: Throwable? = null, keepLonger: Boolean = false): T {
  Log.w(tag, message, throwable, keepLonger)
  return this
}

/**
 * Convenience method to replace `.also { Log.e(TAG, "message") }`
 */
fun <T> T.logE(tag: String, message: String, throwable: Throwable? = null): T {
  Log.e(tag, message, throwable)
  return this
}
