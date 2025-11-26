package org.thoughtcrime.securesms.conversation

import org.thoughtcrime.securesms.R
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Tracks which drawables to use for the expiring timer in disappearing messages.
 */
class ExpirationTimer(
  val startedAt: Long,
  val expiresIn: Long
) {
  companion object {
    private val frames = intArrayOf(
      R.drawable.ic_timer_00_12,
      R.drawable.ic_timer_05_12,
      R.drawable.ic_timer_10_12,
      R.drawable.ic_timer_15_12,
      R.drawable.ic_timer_20_12,
      R.drawable.ic_timer_25_12,
      R.drawable.ic_timer_30_12,
      R.drawable.ic_timer_35_12,
      R.drawable.ic_timer_40_12,
      R.drawable.ic_timer_45_12,
      R.drawable.ic_timer_50_12,
      R.drawable.ic_timer_55_12,
      R.drawable.ic_timer_60_12
    )

    @JvmStatic
    fun getFrame(progress: Float): Int {
      val percentFull = 1 - progress
      val frame = ceil(percentFull * (frames.size - 1)).toInt()

      val adjustedFrame = max(0, min(frame, frames.size - 1))
      return frames[adjustedFrame]
    }
  }

  fun calculateProgress(): Float {
    val progressed = System.currentTimeMillis() - startedAt
    val percentComplete = progressed.toFloat() / expiresIn.toFloat()

    return max(0f, min(percentComplete, 1f))
  }

  fun calculateAnimationDelay(): Long {
    val progressed = System.currentTimeMillis() - startedAt
    val remaining = expiresIn - progressed
    return (if (remaining < TimeUnit.SECONDS.toMillis(30)) 50 else 1000).toLong()
  }
}
