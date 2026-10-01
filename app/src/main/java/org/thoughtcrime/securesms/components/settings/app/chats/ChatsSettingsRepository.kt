package org.thoughtcrime.securesms.components.settings.app.chats

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import org.signal.core.util.ThrottledDebouncer
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.backup.LocalExportProgress
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFoldersRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.RemoteConfig

object ChatsSettingsRepository {

  private val shortcutRefreshDebouncer by lazy { ThrottledDebouncer(500L) }

  fun isLinkPreviewsEnabled(): Boolean = SignalStore.settings.isLinkPreviewsEnabled

  fun setLinkPreviewsEnabled(enabled: Boolean) {
    SignalStore.settings.isLinkPreviewsEnabled = enabled
    syncLinkPreviewsState()
  }

  fun isAutoplayStickersAndGifsEnabled(): Boolean = SignalStore.settings.isAutoplayStickersAndGifsEnabled

  fun setAutoplayStickersAndGifsEnabled(enabled: Boolean) {
    SignalStore.settings.isAutoplayStickersAndGifsEnabled = enabled
  }

  fun isPreferSystemContactPhotos(): Boolean = SignalStore.settings.isPreferSystemContactPhotos

  fun setPreferSystemContactPhotos(enabled: Boolean) {
    shortcutRefreshDebouncer.publish { ConversationUtil.refreshRecipientShortcuts() }
    SignalStore.settings.isPreferSystemContactPhotos = enabled
    syncPreferSystemContactPhotos()
  }

  fun isKeepMutedChatsArchived(): Boolean = SignalStore.settings.keepMutedChatsArchived

  fun setKeepMutedChatsArchived(enabled: Boolean) {
    SignalStore.settings.keepMutedChatsArchived = enabled
    syncKeepMutedChatsArchivedState()
  }

  fun isPreferSystemEmoji(): Boolean = SignalStore.settings.isPreferSystemEmoji

  fun setPreferSystemEmoji(enabled: Boolean) {
    SignalStore.settings.isPreferSystemEmoji = enabled
  }

  fun isEnterKeySends(): Boolean = SignalStore.settings.isEnterKeySends

  fun setEnterKeySends(enabled: Boolean) {
    SignalStore.settings.isEnterKeySends = enabled
  }

  fun isLocalBackupsEnabled(): Boolean = SignalStore.settings.isBackupEnabled && BackupUtil.canUserAccessBackupDirectory(AppDependencies.application)

  fun getFolderCount(): Int = ChatFoldersRepository.getFolderCount()

  fun isUserUnregistered(): Boolean = SignalStore.account.isUnauthorizedReceived || !SignalStore.account.isRegistered

  fun isClientDeprecated(): Boolean = SignalStore.misc.isClientDeprecated

  fun isPlaintextExportEnabled(): Boolean = RemoteConfig.localPlaintextExport

  fun observePlaintextExportProgress(): StateFlow<LocalBackupCreationProgress> = LocalExportProgress.plaintextProgress

  fun startPlaintextExport(uri: Uri, includeMedia: Boolean) {
    LocalBackupJob.enqueuePlaintextArchive(uri.toString(), includeMedia)
  }

  fun cancelPlaintextExport() {
    AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.PLAINTEXT_ARCHIVE_QUEUE)
  }

  private fun syncLinkPreviewsState() {
    SignalExecutors.BOUNDED.execute {
      val isLinkPreviewsEnabled = SignalStore.settings.isLinkPreviewsEnabled

      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      AppDependencies.jobManager.add(
        MultiDeviceConfigurationUpdateJob(
          SignalStore.settings.isReadReceiptsEnabled,
          SignalStore.settings.isTypingIndicatorsEnabled,
          SignalStore.settings.isShowUnidentifiedDeliveryIndicatorsEnabled,
          isLinkPreviewsEnabled
        )
      )
    }
  }

  private fun syncPreferSystemContactPhotos() {
    SignalExecutors.BOUNDED.execute {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      AppDependencies.jobManager.add(MultiDeviceContactUpdateJob(true))
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  private fun syncKeepMutedChatsArchivedState() {
    SignalExecutors.BOUNDED.execute {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }
}
