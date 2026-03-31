package org.thoughtcrime.securesms.components.settings.app.chats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.backup.LocalExportProgress
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFoldersRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.ThrottledDebouncer

class ChatsSettingsViewModel @JvmOverloads constructor(
  private val repository: ChatsSettingsRepository = ChatsSettingsRepository()
) : ViewModel() {

  private val refreshDebouncer = ThrottledDebouncer(500L)

  private val store = MutableStateFlow(
    ChatsSettingsState(
      generateLinkPreviews = SignalStore.settings.isLinkPreviewsEnabled,
      useAddressBook = SignalStore.settings.isPreferSystemContactPhotos,
      keepMutedChatsArchived = SignalStore.settings.shouldKeepMutedChatsArchived(),
      useSystemEmoji = SignalStore.settings.isPreferSystemEmoji,
      enterKeySends = SignalStore.settings.isEnterKeySends,
      localBackupsEnabled = SignalStore.settings.isBackupEnabled && BackupUtil.canUserAccessBackupDirectory(AppDependencies.application),
      folderCount = 0,
      userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application) || !SignalStore.account.isRegistered,
      clientDeprecated = SignalStore.misc.isClientDeprecated,
      isPlaintextExportEnabled = RemoteConfig.localPlaintextExport,
      chatExportState = ChatExportState.None
    )
  )

  val state: StateFlow<ChatsSettingsState> = store

  init {
    viewModelScope.launch {
      LocalExportProgress.plaintextProgress.collect { progress ->
        store.update {
          it.copy(
            plaintextExportProgress = progress,
            chatExportState = when {
              progress.succeeded != null && it.plaintextExportProgress.succeeded == null -> ChatExportState.Success
              progress.canceled != null -> ChatExportState.None
              else -> it.chatExportState
            }
          )
        }
      }
    }
  }

  fun requestChatExportType() {
    store.update { it.copy(chatExportState = ChatExportState.ConfirmExport) }
  }

  fun setExportTypeAndGoToSelectFolder(includeMediaInExport: Boolean) {
    store.update { it.copy(chatExportState = ChatExportState.ChooseAFolder, includeMediaInExport = includeMediaInExport) }
  }

  fun startChatExportToFolder(uri: Uri) {
    store.update { it.copy(chatExportState = ChatExportState.None) }
    LocalBackupJob.enqueuePlaintextArchive(uri.toString(), store.value.includeMediaInExport)
  }

  fun clearChatExportFlow() {
    store.update { it.copy(chatExportState = ChatExportState.None, includeMediaInExport = false) }
  }

  fun cancelChatExport() {
    store.update { it.copy(chatExportState = ChatExportState.Canceling) }
    AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.PLAINTEXT_ARCHIVE_QUEUE)
  }

  fun setGenerateLinkPreviewsEnabled(enabled: Boolean) {
    store.update { it.copy(generateLinkPreviews = enabled) }
    SignalStore.settings.isLinkPreviewsEnabled = enabled
    repository.syncLinkPreviewsState()
  }

  fun setUseAddressBook(enabled: Boolean) {
    store.update { it.copy(useAddressBook = enabled) }
    refreshDebouncer.publish { ConversationUtil.refreshRecipientShortcuts() }
    SignalStore.settings.isPreferSystemContactPhotos = enabled
    repository.syncPreferSystemContactPhotos()
  }

  fun setKeepMutedChatsArchived(enabled: Boolean) {
    store.update { it.copy(keepMutedChatsArchived = enabled) }
    SignalStore.settings.setKeepMutedChatsArchived(enabled)
    repository.syncKeepMutedChatsArchivedState()
  }

  fun setUseSystemEmoji(enabled: Boolean) {
    store.update { it.copy(useSystemEmoji = enabled) }
    SignalStore.settings.isPreferSystemEmoji = enabled
  }

  fun setEnterKeySends(enabled: Boolean) {
    store.update { it.copy(enterKeySends = enabled) }
    SignalStore.settings.isEnterKeySends = enabled
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      val count = ChatFoldersRepository.getFolderCount()
      val backupsEnabled = SignalStore.settings.isBackupEnabled && BackupUtil.canUserAccessBackupDirectory(AppDependencies.application)

      if (store.value.localBackupsEnabled != backupsEnabled) {
        store.update {
          it.copy(
            folderCount = count,
            localBackupsEnabled = backupsEnabled
          )
        }
      } else {
        store.update {
          it.copy(
            folderCount = count
          )
        }
      }
    }
  }
}
