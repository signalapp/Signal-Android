/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.view.MotionEvent
import kotlin.math.abs

/**
 * The scrub gesture behind the reaction overlay
 *
 * @param emojiCount Slots in the strip. The last opens the full picker.
 */
class ReactionScrubber(private val emojiCount: Int) {

  /**
   * Where the strip ended up, in the coordinate space the gesture arrives in.
   *
   * [stripStart] and [stripEnd] are in layout direction order, so under RTL start is the greater of
   * the two and the segment width comes out negative. Deliberately not normalised: the sign is what
   * decides which emoji an x belongs to.
   *
   * @param scrubTop Top of the taller band a scrub may wander through, down to [scrubBottom].
   * @param deadZoneSize How far a pointer may drift before a tap becomes a scrub.
   * @param isStripVisible False for a message that takes no reactions, making every point a miss.
   */
  data class Geometry(
    val stripStart: Float = 0f,
    val stripEnd: Float = 0f,
    val stripTop: Float = 0f,
    val stripBottom: Float = 0f,
    val scrubTop: Float = 0f,
    val scrubBottom: Float = 0f,
    val deadZoneSize: Float = 0f,
    val isStripVisible: Boolean = false
  )

  enum class Phase {
    HIDDEN,
    UNINITIALIZED,
    DEADZONE,
    SCRUB,
    TAP
  }

  /**
   * [consumed] is not the same as "something changed": a gesture that began outside the scrubber is
   * watched without being claimed.
   */
  sealed interface Outcome {
    val consumed: Boolean

    data class Scrubbing(override val consumed: Boolean, val previousIndex: Int, val index: Int) : Outcome

    data class Commit(override val consumed: Boolean, val index: Int) : Outcome

    data class Dismiss(override val consumed: Boolean) : Outcome
  }

  var geometry: Geometry = Geometry()

  var phase: Phase = Phase.HIDDEN
    private set

  var selectedIndex: Int = NO_SELECTION
    private set

  private var downIsOurs: Boolean = false

  private var deadZoneX: Float = 0f
  private var deadZoneY: Float = 0f

  val isShowing: Boolean
    get() = phase != Phase.HIDDEN

  fun open() {
    phase = Phase.UNINITIALIZED
    selectedIndex = NO_SELECTION
    downIsOurs = false
  }

  fun close() {
    phase = Phase.HIDDEN
  }

  /** [action] is a raw [MotionEvent] action, so a non-primary pointer is swallowed rather than obeyed. */
  fun apply(action: Int, x: Float, y: Float): Outcome {
    check(isShowing) { "Touch events should only reach the scrubber while it is showing." }

    if (action and MotionEvent.ACTION_POINTER_INDEX_MASK != 0) {
      return unchanged(consumed = true)
    }

    if (phase == Phase.UNINITIALIZED) {
      downIsOurs = false
      deadZoneX = x
      deadZoneY = y
      phase = Phase.DEADZONE
    }

    if (phase == Phase.DEADZONE) {
      val escapedDeadZone = abs(deadZoneX - x) > geometry.deadZoneSize || abs(deadZoneY - y) > geometry.deadZoneSize

      if (escapedDeadZone) {
        phase = Phase.SCRUB
      } else {
        if (action == MotionEvent.ACTION_UP) {
          phase = Phase.TAP

          if (downIsOurs) {
            return endGesture(consumed = true)
          }
        }

        return unchanged(consumed = action == MotionEvent.ACTION_MOVE)
      }
    }

    return when (action) {
      MotionEvent.ACTION_DOWN -> {
        val outcome = moveSelectionTo(selectionAt(x, y, geometry.stripTop, geometry.stripBottom), consumed = true)
        deadZoneX = x
        deadZoneY = y
        phase = Phase.DEADZONE
        downIsOurs = true
        outcome
      }

      MotionEvent.ACTION_MOVE -> moveSelectionTo(selectionAt(x, y, geometry.scrubTop, geometry.scrubBottom), consumed = true)

      MotionEvent.ACTION_UP -> endGesture(consumed = downIsOurs)

      MotionEvent.ACTION_CANCEL -> {
        close()
        Outcome.Dismiss(consumed = downIsOurs)
      }

      else -> unchanged(consumed = false)
    }
  }

  /** A gesture that outlives the strip cannot apply a reaction. */
  private fun endGesture(consumed: Boolean): Outcome {
    if (selectedIndex != NO_SELECTION && geometry.isStripVisible) {
      return Outcome.Commit(consumed = consumed, index = selectedIndex)
    }

    close()
    return Outcome.Dismiss(consumed = consumed)
  }

  private fun moveSelectionTo(index: Int, consumed: Boolean): Outcome {
    val previousIndex = selectedIndex
    selectedIndex = index

    return Outcome.Scrubbing(consumed = consumed, previousIndex = previousIndex, index = index)
  }

  private fun unchanged(consumed: Boolean): Outcome {
    return Outcome.Scrubbing(consumed = consumed, previousIndex = selectedIndex, index = selectedIndex)
  }

  private fun selectionAt(x: Float, y: Float, bandStart: Float, bandEnd: Float): Int {
    if (!geometry.isStripVisible) {
      return NO_SELECTION
    }

    val segmentSize = segmentSize()
    var selection = NO_SELECTION

    for (index in 0 until emojiCount) {
      val segmentStart = segmentSize * index + geometry.stripStart

      if (contains(segmentStart, segmentStart + segmentSize, x) && contains(bandStart, bandEnd, y)) {
        selection = index
      }
    }

    return selection
  }

  private fun segmentSize(): Float {
    return (geometry.stripEnd - geometry.stripStart) / emojiCount
  }

  companion object {
    const val NO_SELECTION = -1

    /** Exclusive on both ends, and indifferent to which of [min] and [max] is greater. */
    private fun contains(min: Float, max: Float, value: Float): Boolean {
      return if (min < max) {
        min < value && max > value
      } else {
        min > value && max < value
      }
    }
  }
}
