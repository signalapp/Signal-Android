/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.clockskew

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClockSkewViewModelTest {

  companion object {
    private const val DAY_ONE = 0L
    private const val DAY_ONE_HUNDRED = 100L * 24 * 60 * 60 * 1000
  }

  private val testDispatcher = StandardTestDispatcher()

  private var now: Long = DAY_ONE
  private val detected = MutableStateFlow(true)

  private val emittedStates = mutableListOf<ClockSkewState>()
  private val emittedActions = mutableListOf<ClockSkewScreenAction>()
  private val stateEmitter: (ClockSkewState) -> Unit = { emittedStates.add(it) }
  private val actionEmitter: (ClockSkewScreenAction) -> Unit = { emittedActions.add(it) }

  private lateinit var viewModel: ClockSkewViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    now = DAY_ONE
    detected.value = true
    emittedStates.clear()
    emittedActions.clear()
    viewModel = ClockSkewViewModel(clock = { now }, detected = detected)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state populates the device date time`() {
    assertThat(viewModel.state.value.deviceDateTime).isNotEmpty()
  }

  @Test
  fun `AdjustDateSelected opens the date settings`() {
    viewModel.applyEvent(ClockSkewState(), ClockSkewScreenEvent.AdjustDateSelected, actionEmitter, stateEmitter)

    assertThat(emittedActions).containsExactly(ClockSkewScreenAction.OpenDateSettings)
    assertThat(emittedStates).isEmpty()
  }

  @Test
  fun `SkewStateChanged finishes the screen once the clock is no longer skewed`() {
    viewModel.applyEvent(ClockSkewState(), ClockSkewScreenEvent.SkewStateChanged(detected = false), actionEmitter, stateEmitter)

    assertThat(emittedActions).containsExactly(ClockSkewScreenAction.Finish)
  }

  @Test
  fun `SkewStateChanged does nothing while the clock is still skewed`() {
    viewModel.applyEvent(ClockSkewState(), ClockSkewScreenEvent.SkewStateChanged(detected = true), actionEmitter, stateEmitter)

    assertThat(emittedActions).isEmpty()
  }

  @Test
  fun `ScreenResumed recomputes the device date time`() {
    val initial = viewModel.state.value.deviceDateTime
    now = DAY_ONE_HUNDRED

    viewModel.applyEvent(ClockSkewState(deviceDateTime = initial), ClockSkewScreenEvent.ScreenResumed, actionEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().deviceDateTime).isNotEqualTo(initial)
    assertThat(emittedActions).isEmpty()
  }
}
