package org.thoughtcrime.securesms.sharing

import androidx.annotation.Discouraged
import org.signal.core.util.ThreadUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Default multi-share timestamp provider, which will return a different timestamp on each invocation.
 */
open class MultiShareTimestampProvider(
  private val getCurrentTime: () -> Duration = { System.currentTimeMillis().milliseconds },
  private val sleepTimeout: Duration = 5.milliseconds
) {

  open fun getMillis(index: Int): Long {
    return waitForTime().inWholeMilliseconds
  }

  protected fun waitForTime(): Duration {
    ThreadUtil.sleep(sleepTimeout.inWholeMilliseconds)
    return getCurrentTime()
  }

  companion object {
    @JvmStatic
    @Discouraged(message = "This only exists because of Java.")
    fun create(): MultiShareTimestampProvider = MultiShareTimestampProvider()
  }
}
