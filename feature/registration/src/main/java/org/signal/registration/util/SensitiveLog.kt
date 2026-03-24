/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.util

import org.signal.core.util.logging.Log
import org.signal.core.util.logging.NoopLogger

/**
 * A logger that can be used to log sensitive information for debugging purposes.
 * The actual application will use a NoopLogger, while the demo app will provide actual logging capabilities to ease debugging.
 */
object SensitiveLog : Log.Logger() {
  private var logger: Log.Logger = NoopLogger()

  fun init(logger: Log.Logger?) {
    this.logger = logger ?: NoopLogger()
  }

  override fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    this.logger.v(tag, "[SENSITIVE] $message", t, keepLonger)
  }

  override fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    this.logger.d(tag, "[SENSITIVE] $message", t, keepLonger)
  }

  override fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    this.logger.i(tag, "[SENSITIVE] $message", t, keepLonger)
  }

  override fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    this.logger.w(tag, "[SENSITIVE] $message", t, keepLonger)
  }

  override fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    this.logger.e(tag, "[SENSITIVE] $message", t, keepLonger)
  }

  override fun flush() {
    this.logger.flush()
  }
}
