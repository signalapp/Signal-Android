/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.fakes

import org.signal.core.util.logging.Log

/**
 * Writes logs to stdout so they show up in unit test output.
 */
class SystemOutLogger : Log.Logger() {
  override fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = print("V", tag, message, t)
  override fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = print("D", tag, message, t)
  override fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = print("I", tag, message, t)
  override fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = print("W", tag, message, t)
  override fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = print("E", tag, message, t)
  override fun flush() = Unit

  private fun print(level: String, tag: String, message: String?, t: Throwable?) {
    println("[$level][$tag] $message")
    t?.printStackTrace(System.out)
  }
}
