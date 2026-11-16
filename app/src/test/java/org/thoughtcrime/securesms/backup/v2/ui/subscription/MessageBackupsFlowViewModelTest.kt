/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.keyvalue.BackupValues
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import java.util.UUID

/**
 * Covers the stage machine, and in particular how it forks for accounts that already have a Signal Login and therefore
 * already know their recovery key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageBackupsFlowViewModelTest {

  companion object {
    private val ACI = ServiceId.ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    private val AEP = AccountEntropyPool("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t")
  }

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalStore = MockSignalStoreRule(relaxed = setOf(BackupValues::class))

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(StandardTestDispatcher())

  @Before
  fun setUp() {
    every { signalStore.backup.backupTier } returns null
    every { signalStore.backup.deletionStateFlow } returns emptyFlow()
    every { signalStore.account.accountEntropyPool } returns AEP
    every { signalStore.account.requireAci() } returns ACI

    mockkObject(BackupRepository)
    coEvery { BackupRepository.getBackupTypes(any()) } returns emptyList()
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `an account with a Signal Login confirms its existing key instead of recording a new one`() {
    val viewModel = createViewModel(isPhoneNumberless = true)

    viewModel.goToNextStage()

    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.CONFIRM_RECOVERY_KEY)
  }

  @Test
  fun `an account with a phone number still records a new key`() {
    val viewModel = createViewModel(isPhoneNumberless = false)

    viewModel.goToNextStage()

    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.BACKUP_KEY_EDUCATION)
  }

  @Test
  fun `confirming the recovery key goes straight to plan selection`() {
    val viewModel = createViewModel(isPhoneNumberless = true)
    viewModel.goToNextStage()

    viewModel.onRecoveryKeyConfirmed()

    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.TYPE_SELECTION)
  }

  @Test
  fun `entering the recovery key manually leads to plan selection by way of the verify screen`() {
    val viewModel = createViewModel(isPhoneNumberless = true)
    viewModel.goToNextStage()

    viewModel.goToEnterRecoveryKeyManually()
    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.BACKUP_KEY_VERIFY)

    viewModel.goToNextStage()
    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.TYPE_SELECTION)
  }

  @Test
  fun `backing out of the verify screen returns to the confirmation screen`() {
    val viewModel = createViewModel(isPhoneNumberless = true)
    viewModel.goToNextStage()
    viewModel.goToEnterRecoveryKeyManually()

    viewModel.goToPreviousStage()

    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.CONFIRM_RECOVERY_KEY)
  }

  @Test
  fun `viewing the full login details is a detour off the confirmation screen`() {
    val viewModel = createViewModel(isPhoneNumberless = true)
    viewModel.goToNextStage()

    viewModel.goToSignalLoginViewDetails()
    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.SIGNAL_LOGIN_VIEW_DETAILS)

    viewModel.goToPreviousStage()
    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.CONFIRM_RECOVERY_KEY)
  }

  @Test
  fun `backing out of the confirmation screen returns to education`() {
    val viewModel = createViewModel(isPhoneNumberless = true)
    viewModel.goToNextStage()

    viewModel.goToPreviousStage()

    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.EDUCATION)
  }

  @Test
  fun `backing out of plan selection returns to the confirmation screen`() {
    val viewModel = createViewModel(isPhoneNumberless = true)
    viewModel.goToNextStage()
    viewModel.onRecoveryKeyConfirmed()

    viewModel.goToPreviousStage()

    assertThat(viewModel.stateFlow.value.stage).isEqualTo(MessageBackupsStage.CONFIRM_RECOVERY_KEY)
  }

  private fun createViewModel(isPhoneNumberless: Boolean): MessageBackupsFlowViewModel {
    return MessageBackupsFlowViewModel(
      initialTierSelection = null,
      googlePlayApiAvailability = 0,
      isCredentialManagerSupported = true,
      isPhoneNumberless = isPhoneNumberless,
      startScreen = MessageBackupsStage.EDUCATION
    )
  }
}
