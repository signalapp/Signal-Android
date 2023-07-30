package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.BuildConfig
import java.util.concurrent.Executors
import androidx.tracing.Trace as AndroidTrace

object SignalTrace {

  private val executor by lazy(LazyThreadSafetyMode.NONE) {
    Executors.newSingleThreadExecutor()
  }

  @JvmStatic
  fun beginSection(methodName: String) {
    if (!BuildConfig.TRACING_ENABLED) {
      return
    }
    executor.execute { AndroidTrace.beginSection(methodName) }
  }

  @JvmStatic
  fun endSection() {
    if (!BuildConfig.TRACING_ENABLED) {
      return
    }
    executor.execute { AndroidTrace.endSection() }
  }
}
