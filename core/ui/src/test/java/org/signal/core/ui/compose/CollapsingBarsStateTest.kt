/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

class CollapsingBarsStateTest {

  private var userScrolled = false
  private val state = CollapsingBarsState(onUserScroll = { userScrolled = true }).apply {
    topBarHeightPx = 100
    bottomBarHeightPx = 50
  }

  @Test
  fun `scrolling on collapses the bars by the taller bar's height`() {
    scroll(consumed = -25f)

    assertThat(state.collapsedFraction).isEqualTo(0.25f)
  }

  @Test
  fun `scrolling back reveals the bars, never past fully showing`() {
    scroll(consumed = -60f)
    scroll(consumed = 200f)

    assertThat(state.collapsedFraction).isEqualTo(0f)
  }

  @Test
  fun `scrolling on never collapses past fully gone`() {
    scroll(consumed = -500f)

    assertThat(state.collapsedFraction).isEqualTo(1f)
  }

  @Test
  fun `a drag the content did not scroll for leaves the bars alone`() {
    scroll(consumed = 0f, available = -40f)

    assertThat(state.collapsedFraction).isEqualTo(0f)
    assertThat(userScrolled).isFalse()
  }

  @Test
  fun `only a user scroll counts as moving on from typing`() {
    scroll(consumed = -10f, source = NestedScrollSource.SideEffect)
    assertThat(userScrolled).isFalse()

    scroll(consumed = -10f)
    assertThat(userScrolled).isTrue()
  }

  @Test
  fun `the remaining bar sets the travel once the other is gone`() {
    state.topBarHeightPx = 0

    scroll(consumed = -25f)

    assertThat(state.collapsedFraction).isEqualTo(0.5f)
  }

  @Test
  fun `letting go past halfway settles fully collapsed`() = runTest {
    scroll(consumed = -60f)

    withFrameClock { state.nestedScrollConnection.onPostFling(Velocity.Zero, Velocity.Zero) }

    assertThat(state.collapsedFraction).isEqualTo(1f)
  }

  @Test
  fun `letting go short of halfway settles fully showing`() = runTest {
    scroll(consumed = -40f)

    withFrameClock { state.nestedScrollConnection.onPostFling(Velocity.Zero, Velocity.Zero) }

    assertThat(state.collapsedFraction).isEqualTo(0f)
  }

  @Test
  fun `expand brings collapsed bars back`() = runTest {
    scroll(consumed = -100f)

    withFrameClock { state.expand() }

    assertThat(state.collapsedFraction).isEqualTo(0f)
  }

  private fun scroll(consumed: Float, available: Float = 0f, source: NestedScrollSource = NestedScrollSource.UserInput) {
    state.nestedScrollConnection.onPostScroll(Offset(0f, consumed), Offset(0f, available), source)
  }

  /** Runs [block] with a clock that steps a frame at a time, which is all the settling animation needs to finish. */
  private suspend fun withFrameClock(block: suspend () -> Unit) {
    var frameTimeNanos = 0L
    val clock = object : MonotonicFrameClock {
      override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        frameTimeNanos += 16_000_000L
        return onFrame(frameTimeNanos)
      }
    }

    withContext(clock) { block() }
  }
}
