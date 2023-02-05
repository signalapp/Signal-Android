package org.thoughtcrime.securesms.conversationlist.chatfilter

import androidx.annotation.FloatRange
import org.whispersystems.signalservice.api.util.Preconditions
import kotlin.time.Duration

/**
 * Velocity tracker based off % progress.
 * Units are thus in %/s
 *
 * This class only supports a single axial value.
 */
class ProgressVelocityTracker(@androidx.annotation.IntRange(from = 0) capacity: Int) {

  private val progressBuffer = RingBuffer<Float>(capacity)
  private val durationBuffer = RingBuffer<Duration>(capacity)

  fun submitProgress(@FloatRange(from = 0.0, to = 1.0) progress: Float, duration: Duration) {
    progressBuffer.add(progress)
    durationBuffer.add(duration)
  }

  fun clear() {
    progressBuffer.clear()
    durationBuffer.clear()
  }

  /**
   * Calculates the average velocity. The units are %/s
   */
  fun calculateVelocity(): Float {
    Preconditions.checkState(progressBuffer.size() == durationBuffer.size())

    if (progressBuffer.size() < 2) {
      return 0f
    }

    var progressDelta: Float
    var timeDelta: Duration

    val percentPerMillisecond = (0 until progressBuffer.size()).windowed(2).map { (indexA, indexB) ->
      progressDelta = progressBuffer[indexB] - progressBuffer[indexA]
      timeDelta = durationBuffer[indexB] - durationBuffer[indexA]
      progressDelta / timeDelta.inWholeMilliseconds
    }.sum() / (progressBuffer.size() - 1)

    return percentPerMillisecond * 1000
  }
}
