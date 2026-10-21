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
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState
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
  fun `SaveToPasswordManagerClicked asks the user to confirm the save`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.showSaveToPasswordManagerConfirmation).isTrue()
  }

  @Test
  fun `SaveToPasswordManagerClicked without a password manager tells the user there isn't one`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository, isPasswordManagerAvailable = false)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.ShowNoPasswordManagerAvailable)
  }

  @Test
  fun `SaveToPasswordManagerConfirmed hides the confirmation and launches the save`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerConfirmed)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.LaunchSaveToPasswordManager)
    assertThat(viewModel.state.value.showSaveToPasswordManagerConfirmation).isFalse()
  }

  @Test
  fun `SaveToPasswordManagerConfirmationDismissed hides the confirmation without saving`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerConfirmationDismissed)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.showSaveToPasswordManagerConfirmation).isFalse()
  }

  @Test
  fun `SaveAsPdfClicked raises the do-not-share warning before saving`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.doNotShareWarningAction).isEqualTo(SignalLoginViewDetailsState.DoNotShareWarningAction.SAVE_AS_PDF)
  }

  @Test
  fun `acknowledging the warning after SaveAsPdfClicked launches the save as PDF flow`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.LaunchSaveAsPdf)
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `CopyAccountIdClicked raises the do-not-share warning before copying`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.doNotShareWarningAction).isEqualTo(SignalLoginViewDetailsState.DoNotShareWarningAction.COPY_ACCOUNT_ID)
  }

  @Test
  fun `acknowledging the warning after CopyAccountIdClicked copies the account key to the clipboard`() = runTest(testDispatcher) {
    every { repository.getAci() } returns ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.CopyTextToClipboard("A6B28482-2E32-83D0-7F23-91360A4C2B91"))
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `acknowledging the warning after CopyRecoveryKeyClicked copies the recovery key to the clipboard`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool.generate()
    every { repository.getAccountEntropyPool() } returns aep
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.CopyTextToClipboard(aep.displayValue))
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `DoNotShareWarningLearnMoreClicked drops the warning and opens the help article without copying`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningLearnMoreClicked)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.OpenDoNotShareLearnMoreArticle)
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `DoNotShareWarningDismissed drops the warning without copying`() = runTest(testDispatcher) {
    val viewModel = MessageBackupsSignalLoginDetailsViewModel(repository)
    val actions = collectActions(viewModel)

    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked)
    viewModel.onEvent(SignalLoginViewDetailsScreenEvents.DoNotShareWarningDismissed)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
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
