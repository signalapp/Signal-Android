/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.net.Uri
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class LocalBackupRestoreViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var resultBus: ResultEventBus
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<LocalBackupRestoreState>
  private lateinit var stateEmitter: (LocalBackupRestoreState) -> Unit

  private val resultKey = "test-result-key"

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    resultBus = ResultEventBus()
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(
    isPreRegistration: Boolean,
    storageCapable: Boolean = true,
    knownAep: AccountEntropyPool? = null,
    parentState: RegistrationFlowState = RegistrationFlowState(storageCapable = storageCapable)
  ): LocalBackupRestoreViewModel {
    return LocalBackupRestoreViewModel(
      repository = mockRepository,
      parentState = flowOf(parentState),
      parentEventEmitter = parentEventEmitter,
      isPreRegistration = isPreRegistration,
      resultBus = resultBus,
      resultKey = resultKey,
      knownAep = knownAep
    )
  }

  // ==================== PickBackupFolder Tests ====================

  @Test
  fun `PickBackupFolder sets launchFolderPicker to true`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PickBackupFolder, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().launchFolderPicker).isTrue()
  }

  // ==================== BackupFolderSelected Tests ====================

  @Test
  fun `BackupFolderSelected sets restorePhase to Scanning and selectedFolderUri`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState()
    val folderUri = mockk<Uri>()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.BackupFolderSelected(folderUri), stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().restorePhase).isEqualTo(LocalBackupRestoreState.RestorePhase.Scanning)
    assertThat(emittedStates.last().selectedFolderUri).isEqualTo(folderUri)
  }

  // ==================== ParentStateChanged Tests ====================

  @Test
  fun `ParentStateChanged copies storageCapable from parent state`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState(storageCapable = true)

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.ParentStateChanged(RegistrationFlowState(storageCapable = false)), stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().storageCapable).isEqualTo(false)
  }

  // ==================== RestoreBackup with V1 Tests ====================

  @Test
  fun `RestoreBackup with V1 backup navigates to EnterLocalBackupV1Passphrase`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V1,
      date = LocalDateTime.now(),
      name = "backup.backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.RestoreBackup, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.EnterLocalBackupV1Passphrase)
  }

  // ==================== RestoreBackup with V2 Tests ====================

  @Test
  fun `RestoreBackup with V2 backup post-registration navigates to EnterAepForLocalBackup without requiring registration`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "backup.bin",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.RestoreBackup, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    val route = assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
    route.isInstanceOf<RegistrationRoute.EnterAepForLocalBackup>().prop(RegistrationRoute.EnterAepForLocalBackup::isPreRegistration).isEqualTo(false)
  }

  @Test
  fun `RestoreBackup with V2 backup pre-registration navigates to EnterAepForLocalBackup requiring registration`() = runTest {
    val viewModel = createViewModel(isPreRegistration = true)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "backup.bin",
      uri = mockk(relaxed = true)
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk(relaxed = true))

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.RestoreBackup, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    val route = assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
    route.isInstanceOf<RegistrationRoute.EnterAepForLocalBackup>().prop(RegistrationRoute.EnterAepForLocalBackup::isPreRegistration).isEqualTo(true)
  }

  // ==================== Deferred restore Tests ====================

  @Test
  fun `RegistrationDeferredToSms forwards the deferral to the phone number screen and navigates back`() = runTest {
    val viewModel = createViewModel(isPreRegistration = true)
    val initialState = LocalBackupRestoreState()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.RegistrationDeferredToSms, stateEmitter)

    val result = resultBus.channelMap[resultKey]?.tryReceive()?.getOrNull()
    assertThat(result).isNotNull().isEqualTo(LocalBackupRestoreResult.DeferredToSms)
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `a completed restore clears the pending restore option so it is not resumed again post-registration`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = false, storageCapable = false, knownAep = AccountEntropyPool(VALID_AEP))
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "signal-backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    every { mockRepository.restoreV2Backup(any(), any(), any()) } returns flowOf(
      LocalBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null)
    )

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted(VALID_AEP), stateEmitter)

    coVerify { mockRepository.setRestoreDecision(RestoreDecision.COMPLETED) }
    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.PendingRestoreOptionSelected(null))
    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PinCreate)
  }

  // ==================== AEP adoption Tests ====================

  @Test
  fun `V2 restore promotes the entered recovery key to the canonical AEP`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = false, storageCapable = false, knownAep = AccountEntropyPool(VALID_AEP))
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "signal-backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    every { mockRepository.restoreV2Backup(any(), any(), any()) } returns flowOf(
      LocalBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null)
    )

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted(VALID_AEP), stateEmitter)

    val verifiedEvents = emittedParentEvents.filterIsInstance<RegistrationFlowEvent.UserSuppliedAepVerified>()
    assertThat(verifiedEvents).hasSize(1)
    assertThat(verifiedEvents.single().aep.value).isEqualTo(VALID_AEP)
  }

  @Test
  fun `post-registration V1 restore does not promote any AEP`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = false, storageCapable = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V1,
      date = LocalDateTime.now(),
      name = "backup.backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    every { mockRepository.restoreV1Backup(any(), any(), any()) } returns flowOf(
      LocalBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null)
    )

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted("passphrase"), stateEmitter)

    assertThat(emittedParentEvents.filterIsInstance<RegistrationFlowEvent.UserSuppliedAepVerified>()).isEmpty()
  }

  // ==================== RestoreBackup with no backup Tests ====================

  @Test
  fun `RestoreBackup with null backupInfo does nothing`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState(backupInfo = null)

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.RestoreBackup, stateEmitter)

    assertThat(emittedParentEvents).isEmpty()
    assertThat(emittedStates).isEmpty()
  }

  // ==================== ChooseDifferentFolder Tests ====================

  @Test
  fun `ChooseDifferentFolder resets state and sets launchFolderPicker to true`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "backup.bin",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(
      restorePhase = LocalBackupRestoreState.RestorePhase.BackupFound,
      backupInfo = backupInfo,
      selectedFolderUri = mockk()
    )

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.ChooseDifferentFolder, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().launchFolderPicker).isTrue()
    assertThat(emittedStates.last().restorePhase).isEqualTo(LocalBackupRestoreState.RestorePhase.SelectFolder)
    assertThat(emittedStates.last().backupInfo).isNull()
    assertThat(emittedStates.last().selectedFolderUri).isNull()
  }

  // ==================== BackupSelected Tests ====================

  @Test
  fun `BackupSelected updates backupInfo in state`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "backup.bin",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.BackupSelected(backupInfo), stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().backupInfo).isEqualTo(backupInfo)
  }

  // ==================== FolderPickerDismissed Tests ====================

  @Test
  fun `FolderPickerDismissed sets launchFolderPicker to false`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState(launchFolderPicker = true)

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.FolderPickerDismissed, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().launchFolderPicker).isEqualTo(false)
  }

  // ==================== Cancel (pre-registration) Tests ====================

  @Test
  fun `Cancel when pre-registration sends Canceled result and emits NavigateBack`() = runTest {
    val viewModel = createViewModel(isPreRegistration = true)
    val initialState = LocalBackupRestoreState()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.Cancel, stateEmitter)

    val result = resultBus.channelMap[resultKey]?.tryReceive()?.getOrNull()
    assertThat(result).isNotNull().isEqualTo(LocalBackupRestoreResult.Canceled)
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== Cancel (post-registration) Tests ====================

  @Test
  fun `Cancel when NOT pre-registration emits NavigateBack without sending result`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = LocalBackupRestoreState()

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.Cancel, stateEmitter)

    assertThat(resultBus.channelMap[resultKey]).isNull()
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== Restore Completion Tests ====================

  @Test
  fun `V1 restore that recovers a PIN records COMPLETED restore decision and finishes registration`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V1,
      date = LocalDateTime.now(),
      name = "backup.backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    every { mockRepository.restoreV1Backup(any(), any(), any()) } returns flowOf(LocalBackupRestoreProgress.Complete(restoredSvrPin = "1234", restoredProfileKey = null))

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted("passphrase"), stateEmitter)

    coVerify { mockRepository.persistRestoredBackupState("1234", null) }
    coVerify { mockRepository.setRestoreDecision(RestoreDecision.COMPLETED) }
    coVerify { mockRepository.restoreAccountRecord(any()) }
    assertThat(emittedParentEvents).contains(RegistrationFlowEvent.RegistrationComplete)
  }

  @Test
  fun `V1 restore without a PIN when storage capable navigates to PinEntryForSvrRestore`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = false, storageCapable = true)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V1,
      date = LocalDateTime.now(),
      name = "backup.backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    every { mockRepository.restoreV1Backup(any(), any(), any()) } returns flowOf(
      LocalBackupRestoreProgress.Preparing,
      LocalBackupRestoreProgress.InProgress(bytesRead = 50, totalBytes = 100),
      LocalBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null)
    )

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted("passphrase"), stateEmitter)

    coVerify { mockRepository.setRestoreDecision(RestoreDecision.COMPLETED) }
    coVerify(exactly = 0) { mockRepository.restoreAccountRecord(any()) }
    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PinEntryForSvrRestore)
  }

  @Test
  fun `V1 restore without a PIN when not storage capable navigates to PinCreate`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = false, storageCapable = false)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V1,
      date = LocalDateTime.now(),
      name = "backup.backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    every { mockRepository.restoreV1Backup(any(), any(), any()) } returns flowOf(
      LocalBackupRestoreProgress.Preparing,
      LocalBackupRestoreProgress.InProgress(bytesRead = 50, totalBytes = 100),
      LocalBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null)
    )

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted("passphrase"), stateEmitter)

    coVerify { mockRepository.setRestoreDecision(RestoreDecision.COMPLETED) }
    coVerify(exactly = 0) { mockRepository.restoreAccountRecord(any()) }
    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PinCreate)
  }

  // ==================== Incorrect Credential Tests ====================

  @Test
  fun `V1 restore with incorrect passphrase surfaces IncorrectCredential and does not complete`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = false)
    backgroundScope.launch { viewModel.state.collect {} }

    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V1,
      date = LocalDateTime.now(),
      name = "backup.backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    every { mockRepository.restoreV1Backup(any(), any(), any()) } returns flowOf(LocalBackupRestoreProgress.IncorrectCredential)

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted("passphrase"), stateEmitter)

    assertThat(viewModel.state.value.restorePhase).isEqualTo(LocalBackupRestoreState.RestorePhase.IncorrectCredential)
    assertThat(emittedParentEvents).isEmpty()
    coVerify(exactly = 0) { mockRepository.setRestoreDecision(any()) }
  }

  @Test
  fun `V2 restore with incorrect recovery key surfaces IncorrectCredential and does not complete`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = false)
    backgroundScope.launch { viewModel.state.collect {} }

    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V2,
      date = LocalDateTime.now(),
      name = "signal-backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    every { mockRepository.restoreV2Backup(any(), any(), any()) } returns flowOf(LocalBackupRestoreProgress.IncorrectCredential)

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted(VALID_AEP), stateEmitter)

    assertThat(viewModel.state.value.restorePhase).isEqualTo(LocalBackupRestoreState.RestorePhase.IncorrectCredential)
    assertThat(emittedParentEvents).isEmpty()
    coVerify(exactly = 0) { mockRepository.setRestoreDecision(any()) }
  }

  @Test
  fun `pre-registration V1 restore persists restored backup state and identity keys`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = true)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V1,
      date = LocalDateTime.now(),
      name = "backup.backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    val profileKey = ProfileKey(ByteArray(32))
    val aciIdentityKey = IdentityKeyPair.generate()
    val pniIdentityKey = IdentityKeyPair.generate()

    every { mockRepository.restoreV1Backup(any(), any(), any()) } returns flowOf(
      LocalBackupRestoreProgress.Complete(
        restoredSvrPin = "1234",
        restoredProfileKey = profileKey,
        restoredAciIdentityKey = aciIdentityKey,
        restoredPniIdentityKey = pniIdentityKey
      )
    )

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted("passphrase"), stateEmitter)

    coVerify { mockRepository.persistRestoredBackupState("1234", profileKey) }
    coVerify { mockRepository.persistRestoredIdentityKeys(aciIdentityKey, pniIdentityKey) }
  }

  @Test
  fun `pre-registration V1 restore sends restored AEP in the success result`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isPreRegistration = true)
    val backupInfo = LocalBackupInfo(
      type = LocalBackupInfo.BackupType.V1,
      date = LocalDateTime.now(),
      name = "backup.backup",
      uri = mockk()
    )
    val initialState = LocalBackupRestoreState(backupInfo = backupInfo, selectedFolderUri = mockk())

    val restoredAep = AccountEntropyPool(VALID_AEP)

    every { mockRepository.restoreV1Backup(any(), any(), any()) } returns flowOf(
      LocalBackupRestoreProgress.Complete(
        restoredSvrPin = null,
        restoredProfileKey = null,
        restoredAccountEntropyPool = restoredAep
      )
    )

    viewModel.applyEvent(initialState, LocalBackupRestoreEvents.PassphraseSubmitted("passphrase"), stateEmitter)

    val result = resultBus.channelMap[resultKey]?.tryReceive()?.getOrNull()
    assertThat(result).isNotNull().isEqualTo(LocalBackupRestoreResult.Success(restoredAep))
  }

  companion object {
    private const val VALID_AEP = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"
  }
}
