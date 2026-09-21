/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents
import org.thoughtcrime.securesms.components.settings.app.account.signallogin.SignalLoginViewDetailsAction
import org.thoughtcrime.securesms.components.settings.app.account.signallogin.SignalLoginViewDetailsRepository
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MessageBackupsSignalLoginDetailsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private val repository = mockk<SignalLoginViewDetailsRepository>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.getAci() } returns null
    every { repository.getAccountEntropyPool() } returns null
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state maps the stored credentials into display form`() {
    val aep = AccountEntropyPool.generate()
    every { repository.getAci() } returns ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    every { repository.getAccountEntropyPool() } returns aep

    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)

    assertThat(viewModel.state.value.accountKey).isEqualTo("A6B28482-2E32-83D0-7F23-91360A4C2B91")
    assertThat(viewModel.state.value.recoveryKey).isEqualTo(aep.displayValue)
  }

  @Test
  fun `initial state is empty when there are no stored credentials`() {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)

    assertThat(viewModel.state.value.accountKey).isEqualTo("")
    assertThat(viewModel.state.value.recoveryKey).isEqualTo("")
  }

  @Test
  fun `the reset button is never offered during backup setup`() {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)

    assertThat(viewModel.state.value.showResetRecoveryKeyButton).isFalse()
  }

  @Test
  fun `BackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.BackClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.NavigateBack)
  }

  @Test
  fun `SaveToPasswordManagerClicked launches the save to password manager flow`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.LaunchSaveToPasswordManager)
  }

  @Test
  fun `SaveToPasswordManagerClicked without a password manager tells the user there isn't one`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository, isPasswordManagerAvailable = false)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.ShowNoPasswordManagerAvailable)
  }

  @Test
  fun `SaveAsPdfClicked launches the save as PDF flow`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.LaunchSaveAsPdf)
  }

  @Test
  fun `CopyAccountIdClicked copies the account key to the clipboard`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked("A6B28482-2E32-83D0-7F23-91360A4C2B91"))

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.CopyTextToClipboard("A6B28482-2E32-83D0-7F23-91360A4C2B91"))
  }

  @Test
  fun `CopyRecoveryKeyClicked copies the recovery key to the clipboard`() = runTest(testDispatcher) {
    val recoveryKey = AccountEntropyPool.generate().displayValue
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked(recoveryKey))

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.CopyTextToClipboard(recoveryKey))
  }

  @Test
  fun `ResetRecoveryKeyClicked produces no action`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.ResetRecoveryKeyClicked)

    assertThat(actions).isEmpty()
  }

  private fun TestScope.collectActions(viewModel: MessageBackupsSignalLoginDetailsViewModel): List<SignalLoginViewDetailsAction.Shared> {
    val actions = mutableListOf<SignalLoginViewDetailsAction.Shared>()
    backgroundScope.launch { viewModel.actions.toList(actions) }
    return actions
  }
}
