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
import assertk.assertions.isNull
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
  fun `initial state is empty`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    assertThat(presenter.state.value.code).isEqualTo("")
    assertThat(presenter.state.value.pendingOverwrite).isNull()
    assertThat(presenter.state.value.isComplete).isFalse()
  }

  @Test
  fun `a partial code is recorded without being emitted`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.CodeChanged("418"))

    assertThat(presenter.state.value.code).isEqualTo("418")
    assertThat(emittedActions).isEmpty()
  }

  @Test
  fun `completing the code emits it`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.CodeChanged("41837"))
    presenter.onEvent(CodeEntryFieldEvents.CodeChanged("418372"))

    assertThat(presenter.state.value.isComplete).isTrue()
    assertThat(emittedActions).containsExactly(CodeEntryFieldAction.CodeEntered("418372"))
  }

  @Test
  fun `the same complete code is only emitted once`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.CodeChanged("418372"))
    presenter.onEvent(CodeEntryFieldEvents.CodeChanged("418372"))

    assertThat(emittedActions).containsExactly(CodeEntryFieldAction.CodeEntered("418372"))
  }

  @Test
  fun `SetCode replaces the code, asks the field to show it, and emits it when complete`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.onEvent(CodeEntryFieldEvents.CodeChanged("12"))

    presenter.onEvent(CodeEntryFieldEvents.SetCode("418372"))

    assertThat(presenter.state.value.code).isEqualTo("418372")
    assertThat(presenter.state.value.pendingOverwrite).isEqualTo("418372")
    assertThat(emittedActions).containsExactly(CodeEntryFieldAction.CodeEntered("418372"))
  }

  @Test
  fun `SetCode with something other than a code is ignored`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.onEvent(CodeEntryFieldEvents.SetCode("4183721"))
    presenter.onEvent(CodeEntryFieldEvents.SetCode("418-372"))

    assertThat(presenter.state.value.code).isEqualTo("")
    assertThat(presenter.state.value.pendingOverwrite).isNull()
  }

  @Test
  fun `Clear empties the code and asks the field to clear`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.onEvent(CodeEntryFieldEvents.CodeChanged("418372"))

    presenter.onEvent(CodeEntryFieldEvents.Clear)

    assertThat(presenter.state.value.code).isEqualTo("")
    assertThat(presenter.state.value.pendingOverwrite).isEqualTo("")
  }

  @Test
  fun `what the field reports while an overwrite is pending is ignored`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.onEvent(CodeEntryFieldEvents.SetCode("418372"))

    presenter.onEvent(CodeEntryFieldEvents.CodeChanged("12"))
    presenter.onEvent(CodeEntryFieldEvents.OverwriteApplied)
    presenter.onEvent(CodeEntryFieldEvents.CodeChanged("418372"))

    assertThat(presenter.state.value.code).isEqualTo("418372")
    assertThat(presenter.state.value.pendingOverwrite).isNull()
    assertThat(emittedActions).containsExactly(CodeEntryFieldAction.CodeEntered("418372"))
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
