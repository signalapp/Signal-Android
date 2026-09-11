/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.totpentry

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents

@OptIn(ExperimentalCoroutinesApi::class)
class TotpEntryViewModelTest {

  companion object {
    private const val RESULT_KEY = "totp_code_result"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private val emittedParentEvents = mutableListOf<RegistrationFlowEvent>()
  private val resultBus = ResultEventBus()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `code field state is mirrored into screen state`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(TotpEntryScreenEvents.CodeEntryEvent(CodeEntryFieldEvents.DigitChanged(0, "4")))

    assertThat(viewModel.state.value.codeEntry.digits[0]).isEqualTo("4")
    assertThat(viewModel.state.value.codeEntry.focusedDigitIndex).isEqualTo(1)
  }

  @Test
  fun `a completed code is emitted and pops back to the login screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    "41837".forEachIndexed { index, digit ->
      viewModel.onEvent(TotpEntryScreenEvents.CodeEntryEvent(CodeEntryFieldEvents.DigitChanged(index, digit.toString())))
    }
    assertThat(sentCode()).isNull()
    assertThat(emittedParentEvents).isEmpty()

    viewModel.onEvent(TotpEntryScreenEvents.CodeEntryEvent(CodeEntryFieldEvents.DigitChanged(5, "2")))

    assertThat(sentCode()).isEqualTo("418372")
    assertThat(emittedParentEvents).containsExactly(RegistrationFlowEvent.NavigateBackToScreen(RegistrationRoute.SignalLoginCredentialEntry()))
  }

  @Test
  fun `CancelClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(TotpEntryScreenEvents.CancelClicked)

    assertThat(emittedParentEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
  }

  private fun createViewModel(): TotpEntryViewModel {
    return TotpEntryViewModel(
      parentEventEmitter = { emittedParentEvents.add(it) },
      resultBus = resultBus,
      resultKey = RESULT_KEY
    )
  }

  private fun sentCode(): String? {
    return resultBus.channelMap[RESULT_KEY]?.tryReceive()?.getOrNull() as String?
  }
}
