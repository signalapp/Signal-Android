/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.view.MotionEvent
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import org.junit.Test

/**
 * Pins the scrub gesture's behaviour as the view code had it, ahead of the Compose renderer taking
 * it over. MotionEvent action constants are compile time ints, so none of this needs a device.
 */
class ReactionScrubberTest {

  companion object {
    private const val EMOJI_COUNT = 7

    /** Seven 100 wide segments from 100 to 800, a short strip band and a tall scrub band. */
    private val LTR = ReactionScrubber.Geometry(
      stripStart = 100f,
      stripEnd = 800f,
      stripTop = 200f,
      stripBottom = 300f,
      scrubTop = 200f,
      scrubBottom = 900f,
      deadZoneSize = 20f,
      isStripVisible = true
    )

    /** The same strip laid out right to left, so start is the greater edge. */
    private val RTL = LTR.copy(stripStart = 800f, stripEnd = 100f)
  }

  private fun scrubber(geometry: ReactionScrubber.Geometry = LTR): ReactionScrubber {
    val scrubber = ReactionScrubber(EMOJI_COUNT)
    scrubber.geometry = geometry
    scrubber.open()

    return scrubber
  }

  /** Anchors the dead zone and then escapes it, which is the only way into the scrub phase. */
  private fun ReactionScrubber.beginScrub(): ReactionScrubber {
    apply(MotionEvent.ACTION_MOVE, 400f, 1000f)
    apply(MotionEvent.ACTION_MOVE, 400f, 950f)
    return this
  }

  @Test
  fun `open shows with no selection`() {
    val scrubber = scrubber()

    assertThat(scrubber.isShowing).isTrue()
    assertThat(scrubber.phase).isEqualTo(ReactionScrubber.Phase.UNINITIALIZED)
    assertThat(scrubber.selectedIndex).isEqualTo(ReactionScrubber.NO_SELECTION)
  }

  @Test
  fun `first event only anchors the dead zone`() {
    val scrubber = scrubber()

    val outcome = scrubber.apply(MotionEvent.ACTION_MOVE, 400f, 1000f)

    assertThat(scrubber.phase).isEqualTo(ReactionScrubber.Phase.DEADZONE)
    assertThat(scrubber.selectedIndex).isEqualTo(ReactionScrubber.NO_SELECTION)
    assertThat(outcome.consumed).isTrue()
  }

  @Test
  fun `a down right after open cannot claim the gesture`() {
    val scrubber = scrubber()

    val outcome = scrubber.apply(MotionEvent.ACTION_DOWN, 250f, 250f)

    assertThat(scrubber.phase).isEqualTo(ReactionScrubber.Phase.DEADZONE)
    assertThat(scrubber.selectedIndex).isEqualTo(ReactionScrubber.NO_SELECTION)
    assertThat(outcome.consumed).isFalse()
  }

  @Test
  fun `escaping the dead zone starts scrubbing and selects`() {
    val scrubber = scrubber().beginScrub()

    assertThat(scrubber.phase).isEqualTo(ReactionScrubber.Phase.SCRUB)

    val outcome = scrubber.apply(MotionEvent.ACTION_MOVE, 250f, 250f)

    assertThat(scrubber.selectedIndex).isEqualTo(1)
    assertThat(outcome).isInstanceOf(ReactionScrubber.Outcome.Scrubbing::class)
      .prop(ReactionScrubber.Outcome.Scrubbing::previousIndex).isEqualTo(ReactionScrubber.NO_SELECTION)
  }

  @Test
  fun `each segment maps to its own emoji`() {
    val scrubber = scrubber().beginScrub()

    for (index in 0 until EMOJI_COUNT) {
      val centre = 150f + (100f * index)
      scrubber.apply(MotionEvent.ACTION_MOVE, centre, 250f)

      assertThat(scrubber.selectedIndex).isEqualTo(index)
    }
  }

  @Test
  fun `segment edges belong to neither neighbour`() {
    val scrubber = scrubber().beginScrub()

    scrubber.apply(MotionEvent.ACTION_MOVE, 200f, 250f)

    assertThat(scrubber.selectedIndex).isEqualTo(ReactionScrubber.NO_SELECTION)
  }

  @Test
  fun `laid out right to left the first emoji is the rightmost`() {
    val scrubber = scrubber(RTL).beginScrub()

    scrubber.apply(MotionEvent.ACTION_MOVE, 750f, 250f)
    assertThat(scrubber.selectedIndex).isEqualTo(0)

    scrubber.apply(MotionEvent.ACTION_MOVE, 150f, 250f)
    assertThat(scrubber.selectedIndex).isEqualTo(EMOJI_COUNT - 1)
  }

  @Test
  fun `a scrub outside the band selects nothing`() {
    val scrubber = scrubber().beginScrub()

    scrubber.apply(MotionEvent.ACTION_MOVE, 250f, 250f)
    assertThat(scrubber.selectedIndex).isEqualTo(1)

    scrubber.apply(MotionEvent.ACTION_MOVE, 250f, 1500f)
    assertThat(scrubber.selectedIndex).isEqualTo(ReactionScrubber.NO_SELECTION)
  }

  @Test
  fun `lifting on a selection commits it and leaves the scrubber showing`() {
    val scrubber = scrubber().beginScrub()
    scrubber.apply(MotionEvent.ACTION_MOVE, 250f, 250f)

    val outcome = scrubber.apply(MotionEvent.ACTION_UP, 250f, 250f)

    assertThat(outcome).isInstanceOf(ReactionScrubber.Outcome.Commit::class)
      .prop(ReactionScrubber.Outcome.Commit::index).isEqualTo(1)
    assertThat(scrubber.isShowing).isTrue()
  }

  @Test
  fun `lifting on nothing dismisses and hides`() {
    val scrubber = scrubber().beginScrub()

    val outcome = scrubber.apply(MotionEvent.ACTION_UP, 250f, 1500f)

    assertThat(outcome).isInstanceOf(ReactionScrubber.Outcome.Dismiss::class)
    assertThat(scrubber.isShowing).isFalse()
  }

  @Test
  fun `a second press on the strip claims the gesture and taps through`() {
    val scrubber = scrubber().beginScrub()

    val down = scrubber.apply(MotionEvent.ACTION_DOWN, 250f, 250f)

    assertThat(down.consumed).isTrue()
    assertThat(scrubber.phase).isEqualTo(ReactionScrubber.Phase.DEADZONE)
    assertThat(scrubber.selectedIndex).isEqualTo(1)

    val up = scrubber.apply(MotionEvent.ACTION_UP, 255f, 255f)

    assertThat(scrubber.phase).isEqualTo(ReactionScrubber.Phase.TAP)
    assertThat(up).isInstanceOf(ReactionScrubber.Outcome.Commit::class)
      .prop(ReactionScrubber.Outcome.Commit::index).isEqualTo(1)
  }

  @Test
  fun `cancel dismisses and hides`() {
    val scrubber = scrubber().beginScrub()
    scrubber.apply(MotionEvent.ACTION_MOVE, 250f, 250f)

    val outcome = scrubber.apply(MotionEvent.ACTION_CANCEL, 250f, 250f)

    assertThat(outcome).isInstanceOf(ReactionScrubber.Outcome.Dismiss::class)
    assertThat(scrubber.isShowing).isFalse()
  }

  @Test
  fun `a non primary pointer is swallowed without moving the selection`() {
    val scrubber = scrubber().beginScrub()
    scrubber.apply(MotionEvent.ACTION_MOVE, 250f, 250f)

    val secondPointerDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl 8)
    val outcome = scrubber.apply(secondPointerDown, 650f, 250f)

    assertThat(outcome.consumed).isTrue()
    assertThat(scrubber.selectedIndex).isEqualTo(1)
  }

  @Test
  fun `nothing is selectable while the strip is down`() {
    val scrubber = scrubber(LTR.copy(isStripVisible = false)).beginScrub()

    scrubber.apply(MotionEvent.ACTION_MOVE, 250f, 250f)
    assertThat(scrubber.selectedIndex).isEqualTo(ReactionScrubber.NO_SELECTION)

    val outcome = scrubber.apply(MotionEvent.ACTION_UP, 250f, 250f)
    assertThat(outcome).isInstanceOf(ReactionScrubber.Outcome.Dismiss::class)
  }

  @Test(expected = IllegalStateException::class)
  fun `events before open are a programming error`() {
    ReactionScrubber(EMOJI_COUNT).apply(MotionEvent.ACTION_MOVE, 250f, 250f)
  }
}
