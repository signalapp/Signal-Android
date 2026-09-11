/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.codeentryfield

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CodeEntryFieldPresenterTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private val emittedActions = mutableListOf<CodeEntryFieldAction>()

  @Test
  fun `initial state is empty with focus on the first digit`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    assertThat(presenter.state.value.digits).isEqualTo(CodeEntryFieldState.emptyDigits())
    assertThat(presenter.state.value.focusedDigitIndex).isEqualTo(0)
    assertThat(presenter.state.value.isComplete).isFalse()
  }

  @Test
  fun `entering a digit records it and advances focus`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(0, "4"))

    assertThat(presenter.state.value.digits[0]).isEqualTo("4")
    assertThat(presenter.state.value.focusedDigitIndex).isEqualTo(1)
  }

  @Test
  fun `entering the final digit emits the code`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    "41837".forEachIndexed { index, digit ->
      presenter.onEvent(CodeEntryFieldEvents.DigitChanged(index, digit.toString()))
    }
    assertThat(emittedActions).isEmpty()

    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(5, "2"))

    assertThat(presenter.state.value.isComplete).isTrue()
    assertThat(emittedActions).containsExactly(CodeEntryFieldAction.CodeEntered("418372"))
  }

  @Test
  fun `pasting a full code populates every field and emits the code`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(0, "418372"))

    assertThat(presenter.state.value.digits).isEqualTo(listOf("4", "1", "8", "3", "7", "2"))
    assertThat(presenter.state.value.focusedDigitIndex).isEqualTo(5)
    assertThat(emittedActions).containsExactly(CodeEntryFieldAction.CodeEntered("418372"))
  }

  @Test
  fun `pasting an incomplete code is ignored`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(0, "4183"))

    assertThat(presenter.state.value.digits).isEqualTo(CodeEntryFieldState.emptyDigits())
    assertThat(emittedActions).isEmpty()
  }

  @Test
  fun `non-digit input is ignored`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(0, "a"))

    assertThat(presenter.state.value.digits).isEqualTo(CodeEntryFieldState.emptyDigits())
    assertThat(presenter.state.value.focusedDigitIndex).isEqualTo(0)
  }

  @Test
  fun `a backspace deletes the digit and shifts the following ones left`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(0, "4"))
    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(1, "1"))
    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(2, "8"))

    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(1, ""))

    assertThat(presenter.state.value.digits).isEqualTo(listOf("4", "8", "", "", "", ""))
    assertThat(presenter.state.value.focusedDigitIndex).isEqualTo(0)
  }

  @Test
  fun `a backspace on an empty field deletes the previous digit`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(0, "4"))
    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(1, ""))

    assertThat(presenter.state.value.digits).isEqualTo(CodeEntryFieldState.emptyDigits())
    assertThat(presenter.state.value.focusedDigitIndex).isEqualTo(0)
  }

  @Test
  fun `a backspace on the first empty field does nothing`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.DigitChanged(0, ""))

    assertThat(presenter.state.value.digits).isEqualTo(CodeEntryFieldState.emptyDigits())
    assertThat(presenter.state.value.focusedDigitIndex).isEqualTo(0)
  }

  private fun TestScope.createPresenter(): CodeEntryFieldPresenter {
    val presenter = CodeEntryFieldPresenter(backgroundScope)

    presenter
      .actions
      .onEach { emittedActions += it }
      .launchIn(backgroundScope)

    return presenter
  }
}
