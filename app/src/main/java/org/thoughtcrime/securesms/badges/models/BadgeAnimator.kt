package org.thoughtcrime.securesms.badges.models

import org.thoughtcrime.securesms.util.Util

class BadgeAnimator {

  val duration = 250L

  var state: State = State.START
    private set

  private var startTime: Long = 0L

  fun getFraction(): Float {
    return when (state) {
      State.START -> 0f
      State.END -> 1f
      State.FORWARD -> Util.clamp((System.currentTimeMillis() - startTime) / duration.toFloat(), 0f, 1f)
      State.REVERSE -> 1f - Util.clamp((System.currentTimeMillis() - startTime) / duration.toFloat(), 0f, 1f)
    }
  }

  fun setState(newState: State) {
    shouldInvalidate()

    if (state == newState) {
      return
    }

    if (newState == State.END || newState == State.START) {
      state = newState
      startTime = 0L
      return
    }

    if (state == State.START && newState == State.REVERSE) {
      return
    }

    if (state == State.END && newState == State.FORWARD) {
      return
    }

    if (state == State.START && newState == State.FORWARD) {
      state = State.FORWARD
      startTime = System.currentTimeMillis()
      return
    }

    if (state == State.END && newState == State.REVERSE) {
      state = State.REVERSE
      startTime = System.currentTimeMillis()
      return
    }

    if (state == State.FORWARD && newState == State.REVERSE) {
      val elapsed = System.currentTimeMillis() - startTime
      val delta = duration - elapsed
      startTime -= delta
      state = State.REVERSE
      return
    }

    if (state == State.REVERSE && newState == State.FORWARD) {
      val elapsed = System.currentTimeMillis() - startTime
      val delta = duration - elapsed
      startTime -= delta
      state = State.FORWARD
      return
    }
  }

  fun shouldInvalidate(): Boolean {
    if (state == State.START || state == State.END) {
      return false
    }

    if (state == State.FORWARD && getFraction() == 1f) {
      state = State.END
      return false
    }

    if (state == State.REVERSE && getFraction() == 0f) {
      state = State.START
      return false
    }

    return true
  }

  enum class State {
    START,
    FORWARD,
    REVERSE,
    END
  }
}
