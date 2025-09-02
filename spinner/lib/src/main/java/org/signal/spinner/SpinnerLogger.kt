/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.spinner

import android.os.Looper
import android.util.Log
import org.signal.core.util.logging.Log.Logger

object SpinnerLogger : Logger() {

  private val cachedThreadString: ThreadLocal<String> = ThreadLocal()

  override fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    Spinner.log(
      SpinnerLogItem(
        level = Log.VERBOSE,
        time = System.currentTimeMillis(),
        thread = getThreadString(),
        tag = tag,
        message = message,
        throwable = t
      )
    )
  }

  override fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    Spinner.log(
      SpinnerLogItem(
        level = Log.DEBUG,
        time = System.currentTimeMillis(),
        thread = getThreadString(),
        tag = tag,
        message = message,
        throwable = t
      )
    )
  }

  override fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    Spinner.log(
      SpinnerLogItem(
        level = Log.INFO,
        time = System.currentTimeMillis(),
        thread = getThreadString(),
        tag = tag,
        message = message,
        throwable = t
      )
    )
  }

  override fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    Spinner.log(
      SpinnerLogItem(
        level = Log.WARN,
        time = System.currentTimeMillis(),
        thread = getThreadString(),
        tag = tag,
        message = message,
        throwable = t
      )
    )
  }

  override fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    Spinner.log(
      SpinnerLogItem(
        level = Log.ERROR,
        time = System.currentTimeMillis(),
        thread = getThreadString(),
        tag = tag,
        message = message,
        throwable = t
      )
    )
  }

  override fun flush() = Unit

  fun getThreadString(): String {
    var threadString = cachedThreadString.get()

    if (cachedThreadString.get() == null) {
      threadString = if (Looper.myLooper() == Looper.getMainLooper()) {
        "main "
      } else {
        String.format("%-5s", Thread.currentThread().id)
      }

      cachedThreadString.set(threadString)
    }

    return threadString!!
  }
}
