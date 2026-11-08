/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsAction
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsEvent
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsState
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsState.Dialog
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsState.Snackbar
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneNumberPrivacySettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val refreshTicks = MutableSharedFlow<Unit>()

  private var sharing = false
  private var discoverable = true

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(PhoneNumberPrivacySettingsRepository)

    every { PhoneNumberPrivacySettingsRepository.isPhoneNumberSharingEnabled() } answers { sharing }
    every { PhoneNumberPrivacySettingsRepository.isDiscoverableByPhoneNumber() } answers { discoverable }
    coEvery { PhoneNumberPrivacySettingsRepository.setPhoneNumberSharingEnabled(any()) } answers { sharing = firstArg() }
    coEvery { PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(any()) } answers { discoverable = firstArg() }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(PhoneNumberPrivacySettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    sharing = true
    discoverable = true

    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    assertThat(viewModel.state.value).isEqualTo(PhoneNumberPrivacySettingsState(phoneNumberSharing = true, discoverableByPhoneNumber = true))
  }

  @Test
  fun `state is refreshed periodically to pick up changes from linked devices`() = runTest(testDispatcher) {
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    sharing = true
    refreshTicks.emit(Unit)

    assertThat(viewModel.state.value.phoneNumberSharing).isTrue()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(PhoneNumberPrivacySettingsAction.NavigateBack)
  }

  @Test
  fun `EveryoneCanSeeMyNumberClicked enables sharing and discovery`() = runTest(testDispatcher) {
    discoverable = false
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.EveryoneCanSeeMyNumberClicked)

    coVerifyOrder {
      PhoneNumberPrivacySettingsRepository.setPhoneNumberSharingEnabled(true)
      PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(true)
    }
    assertThat(viewModel.state.value).isEqualTo(PhoneNumberPrivacySettingsState(phoneNumberSharing = true, discoverableByPhoneNumber = true))
  }

  @Test
  fun `NobodyCanSeeMyNumberClicked disables sharing and leaves discovery alone`() = runTest(testDispatcher) {
    sharing = true
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanSeeMyNumberClicked)

    coVerify { PhoneNumberPrivacySettingsRepository.setPhoneNumberSharingEnabled(false) }
    coVerify(exactly = 0) { PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(any()) }
    assertThat(viewModel.state.value.phoneNumberSharing).isFalse()
    assertThat(viewModel.state.value.discoverableByPhoneNumber).isTrue()
  }

  @Test
  fun `EveryoneCanFindMeByNumberClicked enables discovery`() = runTest(testDispatcher) {
    discoverable = false
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.EveryoneCanFindMeByNumberClicked)

    coVerify { PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(true) }
    assertThat(viewModel.state.value.discoverableByPhoneNumber).isTrue()
  }

  @Test
  fun `NobodyCanFindMeByNumberClicked asks the user to hide their number first while it is shared`() = runTest(testDispatcher) {
    sharing = true
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberClicked)

    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.HideNumberFirst)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    coVerify(exactly = 0) { PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(any()) }
  }

  @Test
  fun `SnackbarDismissed clears the snackbar`() = runTest(testDispatcher) {
    sharing = true
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberClicked)
    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.None)
  }

  @Test
  fun `NobodyCanFindMeByNumberClicked asks for confirmation when the number is hidden`() = runTest(testDispatcher) {
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmNotDiscoverable)
    assertThat(actions).isEmpty()
    coVerify(exactly = 0) { PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(any()) }
  }

  @Test
  fun `NobodyCanFindMeByNumberConfirmed disables discovery and closes the dialog`() = runTest(testDispatcher) {
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberClicked)
    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberConfirmed)

    coVerify { PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(false) }
    assertThat(viewModel.state.value).isEqualTo(PhoneNumberPrivacySettingsState(phoneNumberSharing = false, discoverableByPhoneNumber = false))
  }

  @Test
  fun `NobodyCanFindMeByNumberConfirmed is ignored without the confirmation dialog`() = runTest(testDispatcher) {
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberConfirmed)

    coVerify(exactly = 0) { PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(any()) }
    assertThat(viewModel.state.value.discoverableByPhoneNumber).isTrue()
  }

  @Test
  fun `DialogDismissed closes the dialog without changing anything`() = runTest(testDispatcher) {
    val viewModel = PhoneNumberPrivacySettingsViewModel(refreshTicks)

    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberClicked)
    viewModel.onEvent(PhoneNumberPrivacySettingsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    coVerify(exactly = 0) { PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(any()) }
  }

  private fun TestScope.collectActions(actions: Flow<PhoneNumberPrivacySettingsAction>): List<PhoneNumberPrivacySettingsAction> {
    val collected = mutableListOf<PhoneNumberPrivacySettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
