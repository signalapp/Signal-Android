/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats

import android.net.Uri
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.backups.BackupCreationProgress
import org.signal.appsettings.chats.ChatExportState
import org.signal.appsettings.chats.ChatsSettingsEvents
import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsSettingsViewModelTest {

  companion object {
    private val IDLE = LocalBackupCreationProgress(idle = LocalBackupCreationProgress.Idle())
    private val EXPORTING = LocalBackupCreationProgress(exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.MESSAGE))
    private val SUCCEEDED = LocalBackupCreationProgress(succeeded = LocalBackupCreationProgress.Succeeded())
    private val CANCELED = LocalBackupCreationProgress(canceled = LocalBackupCreationProgress.Canceled())
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val plaintextExportProgress = MutableStateFlow(IDLE)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(ChatsSettingsRepository)

    every { ChatsSettingsRepository.isLinkPreviewsEnabled() } returns true
    every { ChatsSettingsRepository.isAutoplayStickersAndGifsEnabled() } returns true
    every { ChatsSettingsRepository.isPreferSystemContactPhotos() } returns false
    every { ChatsSettingsRepository.isKeepMutedChatsArchived() } returns false
    every { ChatsSettingsRepository.isPreferSystemEmoji() } returns false
    every { ChatsSettingsRepository.isEnterKeySends() } returns false
    every { ChatsSettingsRepository.isLocalBackupsEnabled() } returns false
    every { ChatsSettingsRepository.getFolderCount() } returns 1
    every { ChatsSettingsRepository.isUserUnregistered() } returns false
    every { ChatsSettingsRepository.isClientDeprecated() } returns false
    every { ChatsSettingsRepository.isPlaintextExportEnabled() } returns true
    every { ChatsSettingsRepository.observePlaintextExportProgress() } returns plaintextExportProgress
    every { ChatsSettingsRepository.setLinkPreviewsEnabled(any()) } just Runs
    every { ChatsSettingsRepository.setAutoplayStickersAndGifsEnabled(any()) } just Runs
    every { ChatsSettingsRepository.setPreferSystemContactPhotos(any()) } just Runs
    every { ChatsSettingsRepository.setKeepMutedChatsArchived(any()) } just Runs
    every { ChatsSettingsRepository.setPreferSystemEmoji(any()) } just Runs
    every { ChatsSettingsRepository.setEnterKeySends(any()) } just Runs
    every { ChatsSettingsRepository.startPlaintextExport(any(), any()) } just Runs
    every { ChatsSettingsRepository.cancelPlaintextExport() } just Runs
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(ChatsSettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()
    val state = viewModel.state.value

    assertThat(state.generateLinkPreviews).isTrue()
    assertThat(state.shouldAutoplayStickersAndGifs).isTrue()
    assertThat(state.useAddressBook).isFalse()
    assertThat(state.isPlaintextExportEnabled).isTrue()
    assertThat(state.plaintextExportProgress).isEqualTo(BackupCreationProgress.Idle)
  }

  @Test
  fun `Refresh reloads the folder count and local backup state`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()
    every { ChatsSettingsRepository.getFolderCount() } returns 3
    every { ChatsSettingsRepository.isLocalBackupsEnabled() } returns true

    viewModel.onEvent(ChatsSettingsEvents.Refresh)

    assertThat(viewModel.state.value.folderCount).isEqualTo(3)
    assertThat(viewModel.state.value.localBackupsEnabled).isTrue()
  }

  @Test
  fun `GenerateLinkPreviewsChanged updates state and writes through`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.GenerateLinkPreviewsChanged(false))

    assertThat(viewModel.state.value.generateLinkPreviews).isFalse()
    verify { ChatsSettingsRepository.setLinkPreviewsEnabled(false) }
  }

  @Test
  fun `AutoplayStickersAndGifsChanged updates state and writes through`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.AutoplayStickersAndGifsChanged(false))

    assertThat(viewModel.state.value.shouldAutoplayStickersAndGifs).isFalse()
    verify { ChatsSettingsRepository.setAutoplayStickersAndGifsEnabled(false) }
  }

  @Test
  fun `UseAddressBookChanged updates state and writes through`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.UseAddressBookChanged(true))

    assertThat(viewModel.state.value.useAddressBook).isTrue()
    verify { ChatsSettingsRepository.setPreferSystemContactPhotos(true) }
  }

  @Test
  fun `KeepMutedChatsArchivedChanged updates state and writes through`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.KeepMutedChatsArchivedChanged(true))

    assertThat(viewModel.state.value.keepMutedChatsArchived).isTrue()
    verify { ChatsSettingsRepository.setKeepMutedChatsArchived(true) }
  }

  @Test
  fun `UseSystemEmojiChanged updates state and writes through`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.UseSystemEmojiChanged(true))

    assertThat(viewModel.state.value.useSystemEmoji).isTrue()
    verify { ChatsSettingsRepository.setPreferSystemEmoji(true) }
  }

  @Test
  fun `EnterKeySendsChanged updates state and writes through`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.EnterKeySendsChanged(true))

    assertThat(viewModel.state.value.enterKeySends).isTrue()
    verify { ChatsSettingsRepository.setEnterKeySends(true) }
  }

  @Test
  fun `ExportChatHistoryAuthenticated asks the user to confirm the export`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.ExportChatHistoryAuthenticated)

    assertThat(viewModel.state.value.chatExportState).isEqualTo(ChatExportState.ConfirmExport)
  }

  @Test
  fun `ExportConfirmed remembers the media choice and asks for a folder`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.ExportChatHistoryAuthenticated)
    viewModel.onEvent(ChatsSettingsEvents.ExportConfirmed(withMedia = true))

    assertThat(viewModel.state.value.chatExportState).isEqualTo(ChatExportState.ChooseAFolder)
    assertThat(viewModel.state.value.includeMediaInExport).isTrue()
  }

  @Test
  fun `ExportFolderSelected starts the export with the chosen media option`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()
    val uri = mockk<Uri>()

    viewModel.onEvent(ChatsSettingsEvents.ExportConfirmed(withMedia = true))
    viewModel.onEvent(ChatsSettingsEvents.ExportFolderSelected(uri))

    assertThat(viewModel.state.value.chatExportState).isEqualTo(ChatExportState.None)
    verify { ChatsSettingsRepository.startPlaintextExport(uri, true) }
  }

  @Test
  fun `StartExportCanceled clears the export flow`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.ExportConfirmed(withMedia = true))
    viewModel.onEvent(ChatsSettingsEvents.StartExportCanceled)

    assertThat(viewModel.state.value.chatExportState).isEqualTo(ChatExportState.None)
    assertThat(viewModel.state.value.includeMediaInExport).isFalse()
  }

  @Test
  fun `CancelInFlightExportClicked shows the canceling state and cancels the export`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.CancelInFlightExportClicked)

    assertThat(viewModel.state.value.chatExportState).isEqualTo(ChatExportState.Canceling)
    verify { ChatsSettingsRepository.cancelPlaintextExport() }
  }

  @Test
  fun `export progress is mapped into state`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    plaintextExportProgress.value = EXPORTING

    assertThat(viewModel.state.value.plaintextExportProgress).isInstanceOf<BackupCreationProgress.Exporting>()
  }

  @Test
  fun `a newly succeeded export shows the completion dialog`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    plaintextExportProgress.value = EXPORTING
    plaintextExportProgress.value = SUCCEEDED

    assertThat(viewModel.state.value.chatExportState).isEqualTo(ChatExportState.Success)
  }

  @Test
  fun `an export that already succeeded before the screen opened does not show the completion dialog`() = runTest(testDispatcher) {
    plaintextExportProgress.value = SUCCEEDED

    val viewModel = ChatsSettingsViewModel()

    assertThat(viewModel.state.value.chatExportState).isEqualTo(ChatExportState.None)
  }

  @Test
  fun `a canceled export dismisses the canceling dialog`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    viewModel.onEvent(ChatsSettingsEvents.CancelInFlightExportClicked)
    plaintextExportProgress.value = CANCELED

    assertThat(viewModel.state.value.chatExportState).isEqualTo(ChatExportState.None)
  }

  @Test
  fun `ExportCompletionConfirmed dismisses the completion dialog`() = runTest(testDispatcher) {
    val viewModel = ChatsSettingsViewModel()

    plaintextExportProgress.value = EXPORTING
    plaintextExportProgress.value = SUCCEEDED
    viewModel.onEvent(ChatsSettingsEvents.ExportCompletionConfirmed)

    assertThat(viewModel.state.value.chatExportState).isEqualTo(ChatExportState.None)
  }
}
