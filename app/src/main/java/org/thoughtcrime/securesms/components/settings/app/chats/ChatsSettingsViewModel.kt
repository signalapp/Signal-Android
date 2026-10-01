package org.thoughtcrime.securesms.components.settings.app.chats

import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.signal.appsettings.backups.BackupCreationProgress
import org.signal.appsettings.chats.ChatExportState
import org.signal.appsettings.chats.ChatsSettingsEvents
import org.signal.appsettings.chats.ChatsSettingsState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.toBackupCreationProgress

class ChatsSettingsViewModel : EventDrivenViewModel<ChatsSettingsEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(ChatsSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(
    ChatsSettingsState(
      generateLinkPreviews = ChatsSettingsRepository.isLinkPreviewsEnabled(),
      useAddressBook = ChatsSettingsRepository.isPreferSystemContactPhotos(),
      keepMutedChatsArchived = ChatsSettingsRepository.isKeepMutedChatsArchived(),
      useSystemEmoji = ChatsSettingsRepository.isPreferSystemEmoji(),
      enterKeySends = ChatsSettingsRepository.isEnterKeySends(),
      localBackupsEnabled = ChatsSettingsRepository.isLocalBackupsEnabled(),
      folderCount = 0,
      userUnregistered = ChatsSettingsRepository.isUserUnregistered(),
      clientDeprecated = ChatsSettingsRepository.isClientDeprecated(),
      isPlaintextExportEnabled = ChatsSettingsRepository.isPlaintextExportEnabled(),
      plaintextExportProgress = ChatsSettingsRepository.observePlaintextExportProgress().value.toBackupCreationProgress(),
      chatExportState = ChatExportState.None,
      shouldAutoplayStickersAndGifs = ChatsSettingsRepository.isAutoplayStickersAndGifsEnabled()
    )
  )

  val state: StateFlow<ChatsSettingsState> = _state.asStateFlow()

  init {
    ChatsSettingsRepository.observePlaintextExportProgress()
      .onEach { onEvent(ChatsSettingsEvents.PlaintextExportProgressChanged(it.toBackupCreationProgress())) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: ChatsSettingsEvents) {
    when (event) {
      ChatsSettingsEvents.Refresh -> refresh()
      is ChatsSettingsEvents.PlaintextExportProgressChanged -> applyPlaintextExportProgress(event.progress)
      is ChatsSettingsEvents.GenerateLinkPreviewsChanged -> setGenerateLinkPreviewsEnabled(event.enabled)
      is ChatsSettingsEvents.UseAddressBookChanged -> setUseAddressBook(event.enabled)
      is ChatsSettingsEvents.KeepMutedChatsArchivedChanged -> setKeepMutedChatsArchived(event.enabled)
      is ChatsSettingsEvents.UseSystemEmojiChanged -> setUseSystemEmoji(event.enabled)
      is ChatsSettingsEvents.EnterKeySendsChanged -> setEnterKeySends(event.enabled)
      ChatsSettingsEvents.ExportChatHistoryAuthenticated -> _state.update { it.copy(chatExportState = ChatExportState.ConfirmExport) }
      ChatsSettingsEvents.CancelInFlightExportClicked -> cancelChatExport()
      is ChatsSettingsEvents.ExportConfirmed -> _state.update { it.copy(chatExportState = ChatExportState.ChooseAFolder, includeMediaInExport = event.withMedia) }
      is ChatsSettingsEvents.ExportFolderSelected -> startChatExportToFolder(event.uri)
      ChatsSettingsEvents.StartExportCanceled,
      ChatsSettingsEvents.ExportCompletionConfirmed -> _state.update { it.copy(chatExportState = ChatExportState.None, includeMediaInExport = false) }
      is ChatsSettingsEvents.AutoplayStickersAndGifsChanged -> setAutoplayStickersAndGifsEnabled(event.enabled)
      ChatsSettingsEvents.ChatFoldersClicked -> error("Handled in the fragment.")
    }
  }

  private fun applyPlaintextExportProgress(progress: BackupCreationProgress) {
    _state.update {
      it.copy(
        plaintextExportProgress = progress,
        chatExportState = when (progress) {
          is BackupCreationProgress.Succeeded if it.plaintextExportProgress !is BackupCreationProgress.Succeeded -> ChatExportState.Success
          is BackupCreationProgress.Canceled -> ChatExportState.None
          else -> it.chatExportState
        }
      )
    }
  }

  private fun startChatExportToFolder(uri: Uri) {
    _state.update { it.copy(chatExportState = ChatExportState.None) }
    ChatsSettingsRepository.startPlaintextExport(uri, _state.value.includeMediaInExport)
  }

  private fun cancelChatExport() {
    _state.update { it.copy(chatExportState = ChatExportState.Canceling) }
    ChatsSettingsRepository.cancelPlaintextExport()
  }

  private fun setGenerateLinkPreviewsEnabled(enabled: Boolean) {
    _state.update { it.copy(generateLinkPreviews = enabled) }
    ChatsSettingsRepository.setLinkPreviewsEnabled(enabled)
  }

  private fun setAutoplayStickersAndGifsEnabled(enabled: Boolean) {
    _state.update { it.copy(shouldAutoplayStickersAndGifs = enabled) }
    ChatsSettingsRepository.setAutoplayStickersAndGifsEnabled(enabled)
  }

  private fun setUseAddressBook(enabled: Boolean) {
    _state.update { it.copy(useAddressBook = enabled) }
    ChatsSettingsRepository.setPreferSystemContactPhotos(enabled)
  }

  private fun setKeepMutedChatsArchived(enabled: Boolean) {
    _state.update { it.copy(keepMutedChatsArchived = enabled) }
    ChatsSettingsRepository.setKeepMutedChatsArchived(enabled)
  }

  private fun setUseSystemEmoji(enabled: Boolean) {
    _state.update { it.copy(useSystemEmoji = enabled) }
    ChatsSettingsRepository.setPreferSystemEmoji(enabled)
  }

  private fun setEnterKeySends(enabled: Boolean) {
    _state.update { it.copy(enterKeySends = enabled) }
    ChatsSettingsRepository.setEnterKeySends(enabled)
  }

  private suspend fun refresh() {
    val (count, backupsEnabled) = withContext(SignalDispatchers.Default) {
      ChatsSettingsRepository.getFolderCount() to ChatsSettingsRepository.isLocalBackupsEnabled()
    }

    _state.update { it.copy(folderCount = count, localBackupsEnabled = backupsEnabled) }
  }
}
