package org.thoughtcrime.securesms.sharing

import androidx.annotation.Discouraged
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Timestamp provider for distribution lists, which will reuse previous
 * timestamps for identical indices.
 */
class DistributionListMultiShareTimestampProvider(
  getCurrentTimeMillis: () -> Duration = { System.currentTimeMillis().milliseconds },
  sleepTimeout: Duration = 5.milliseconds
) : MultiShareTimestampProvider(getCurrentTimeMillis, sleepTimeout) {

  private val timestamps = mutableListOf(getCurrentTimeMillis())

  override fun getMillis(index: Int): Long {
    fillToIndex(index)
    return timestamps[index].inWholeMilliseconds
  }

  private fun fillToIndex(index: Int) {
    if (index in timestamps.indices) {
      return
    }

    (timestamps.size..index).forEach {
      timestamps.add(it, waitForTime())
    }
  }

  companion object {
    @JvmStatic
    @Discouraged(message = "This only exists because of Java.")
    fun create(): DistributionListMultiShareTimestampProvider = DistributionListMultiShareTimestampProvider()
  }
}
