/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsAction
import org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsEvent
import org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsState
import org.signal.appsettings.advancedprivacy.CensorshipCircumventionState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedPrivacySettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val connectionChanges = MutableSharedFlow<Unit>()
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  private var repositoryState = AdvancedPrivacySettingsState(isPrimaryDevice = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(AdvancedPrivacySettingsRepository)

    every { AdvancedPrivacySettingsRepository.getState() } answers { repositoryState }
    every { AdvancedPrivacySettingsRepository.setAlwaysRelayCalls(any()) } answers { repositoryState = repositoryState.copy(alwaysRelayCalls = firstArg()) }
    every { AdvancedPrivacySettingsRepository.setCensorshipCircumventionEnabled(any()) } answers { repositoryState = repositoryState.copy(censorshipCircumventionEnabled = firstArg()) }
    every { AdvancedPrivacySettingsRepository.setShowSealedSenderStatusIcon(any()) } answers { repositoryState = repositoryState.copy(showSealedSenderStatusIcon = firstArg()) }
    every { AdvancedPrivacySettingsRepository.setAllowSealedSenderFromAnyone(any()) } answers { repositoryState = repositoryState.copy(allowSealedSenderFromAnyone = firstArg()) }
    every { AdvancedPrivacySettingsRepository.setAutomaticKeyVerificationEnabled(any()) } answers { repositoryState = repositoryState.copy(allowAutomaticKeyVerification = firstArg()) }
    coEvery { AdvancedPrivacySettingsRepository.syncAutomaticKeyVerification(any()) } just runs
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(AdvancedPrivacySettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    repositoryState = repositoryState.copy(alwaysRelayCalls = true, showSealedSenderStatusIcon = true)

    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(repositoryState)
  }

  @Test
  fun `ScreenResumed re-reads the repository`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    repositoryState = repositoryState.copy(censorshipCircumventionState = CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET)

    viewModel.onEvent(AdvancedPrivacySettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.censorshipCircumventionState).isEqualTo(CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET)
  }

  @Test
  fun `a connection change re-reads the repository`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    repositoryState = repositoryState.copy(censorshipCircumventionState = CensorshipCircumventionState.UNAVAILABLE_CONNECTED)

    connectionChanges.emit(Unit)

    assertThat(viewModel.state.value.censorshipCircumventionState).isEqualTo(CensorshipCircumventionState.UNAVAILABLE_CONNECTED)
  }

  @Test
  fun `AlwaysRelayCallsToggled saves the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPrivacySettingsEvent.AlwaysRelayCallsToggled(true))

    verify { AdvancedPrivacySettingsRepository.setAlwaysRelayCalls(true) }
    assertThat(viewModel.state.value.alwaysRelayCalls).isEqualTo(true)
  }

  @Test
  fun `CensorshipCircumventionToggled saves the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPrivacySettingsEvent.CensorshipCircumventionToggled(true))

    verify { AdvancedPrivacySettingsRepository.setCensorshipCircumventionEnabled(true) }
    assertThat(viewModel.state.value.censorshipCircumventionEnabled).isEqualTo(true)
  }

  @Test
  fun `SealedSenderStatusIconToggled saves the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPrivacySettingsEvent.SealedSenderStatusIconToggled(true))

    verify { AdvancedPrivacySettingsRepository.setShowSealedSenderStatusIcon(true) }
    assertThat(viewModel.state.value.showSealedSenderStatusIcon).isEqualTo(true)
  }

  @Test
  fun `SealedSenderFromAnyoneToggled saves the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPrivacySettingsEvent.SealedSenderFromAnyoneToggled(true))

    verify { AdvancedPrivacySettingsRepository.setAllowSealedSenderFromAnyone(true) }
    assertThat(viewModel.state.value.allowSealedSenderFromAnyone).isEqualTo(true)
  }

  @Test
  fun `AutomaticKeyVerificationToggled saves the setting and syncs it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPrivacySettingsEvent.AutomaticKeyVerificationToggled(false))

    verify { AdvancedPrivacySettingsRepository.setAutomaticKeyVerificationEnabled(false) }
    coVerify { AdvancedPrivacySettingsRepository.syncAutomaticKeyVerification(false) }
    assertThat(viewModel.state.value.allowAutomaticKeyVerification).isEqualTo(false)
  }

  @Test
  fun `SealedSenderLearnMoreClicked opens the explainer`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AdvancedPrivacySettingsEvent.SealedSenderLearnMoreClicked)

    assertThat(actions).containsExactly(AdvancedPrivacySettingsAction.OpenSealedSenderLearnMore)
  }

  @Test
  fun `AutomaticKeyVerificationLearnMoreClicked opens the explainer`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AdvancedPrivacySettingsEvent.AutomaticKeyVerificationLearnMoreClicked)

    assertThat(actions).containsExactly(AdvancedPrivacySettingsAction.OpenAutomaticKeyVerificationLearnMore)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AdvancedPrivacySettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
    assertThat(actions).isEmpty()
  }

  private fun createViewModel(): AdvancedPrivacySettingsViewModel {
    return AdvancedPrivacySettingsViewModel(parentEventEmitter = { navigationEvents += it }, connectionChanges = connectionChanges)
  }

  private fun TestScope.collectActions(actions: Flow<AdvancedPrivacySettingsAction>): List<AdvancedPrivacySettingsAction> {
    val collected = mutableListOf<AdvancedPrivacySettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
