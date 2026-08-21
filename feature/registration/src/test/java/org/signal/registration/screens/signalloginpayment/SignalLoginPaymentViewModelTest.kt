/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import assertk.assertThat
import assertk.assertions.containsExactly
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SignalLoginPaymentViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var viewModel: SignalLoginPaymentViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    parentEventEmitter = {}
    viewModel = SignalLoginPaymentViewModel(
      repository = mockRepository,
      parentEventEmitter = parentEventEmitter
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun TestScope.collectActions(): List<SignalLoginPaymentScreenActions> {
    val actions = mutableListOf<SignalLoginPaymentScreenActions>()
    backgroundScope.launch(testDispatcher) { viewModel.actions.collect { actions.add(it) } }
    return actions
  }

  @Test
  fun `LearnMoreClicked emits an action to open the learn more article`() = runTest(testDispatcher) {
    val actions = collectActions()

    viewModel.applyEvent(SignalLoginPaymentState(), SignalLoginPaymentScreenEvents.LearnMoreClicked, parentEventEmitter) {}

    assertThat(actions).containsExactly(SignalLoginPaymentScreenActions.OpenLearnMoreArticle)
  }
}
