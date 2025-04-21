package org.signal.core.util.logging

import android.annotation.SuppressLint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@SuppressLint("LogNotSignal")
class AndroidLogger : Log.Logger() {

  private val serialExecutor: Executor = Executors.newSingleThreadExecutor { Thread(it, "signal-logcat") }

  override fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    serialExecutor.execute {
      android.util.Log.v(tag, message.scrub(), t)
    }
  }

  override fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    serialExecutor.execute {
      android.util.Log.d(tag, message.scrub(), t)
    }
  }

  override fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    serialExecutor.execute {
      android.util.Log.i(tag, message.scrub(), t)
    }
  }

  override fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    serialExecutor.execute {
      android.util.Log.w(tag, message.scrub(), t)
    }
  }

  override fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    serialExecutor.execute {
      android.util.Log.e(tag, message.scrub(), t)
    }
  }

  override fun flush() {
    val latch = CountDownLatch(1)

    serialExecutor.execute {
      latch.countDown()
    }

    try {
      latch.await()
    } catch (e: InterruptedException) {
      android.util.Log.w("AndroidLogger", "Interrupted while waiting for flush()", e)
    }
  }

  private fun String?.scrub(): String? {
    return this?.let { Scrubber.scrub(it).toString() }
  }
}
