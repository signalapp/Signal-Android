/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.logging

/**
 * A way to treat N loggers as one. Wraps a bunch of other loggers and forwards the method calls to
 * all of them.
 */
internal class CompoundLogger(private val loggers: List<Log.Logger>) : Log.Logger() {
  override fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    for (logger in loggers) {
      logger.v(tag, message, t, keepLonger)
    }
  }

  override fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    for (logger in loggers) {
      logger.d(tag, message, t, keepLonger)
    }
  }

  override fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    for (logger in loggers) {
      logger.i(tag, message, t, keepLonger)
    }
  }

  override fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    for (logger in loggers) {
      logger.w(tag, message, t, keepLonger)
    }
  }

  override fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    for (logger in loggers) {
      logger.e(tag, message, t, keepLonger)
    }
  }

  override fun flush() {
    for (logger in loggers) {
      logger.flush()
    }
  }
}
