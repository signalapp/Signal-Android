/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.app.PendingIntent
import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.annotation.Discouraged
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.greenrobot.eventbus.EventBus
import org.signal.archive.proto.BackupDebugInfo
import org.signal.archive.proto.BackupInfo
import org.signal.archive.proto.Frame
import org.signal.archive.stream.BackupExportWriter
import org.signal.archive.stream.BackupImportReader
import org.signal.archive.stream.EncryptedBackupReader
import org.signal.archive.stream.EncryptedBackupWriter
import org.signal.archive.stream.PlainTextBackupReader
import org.signal.archive.stream.PlainTextBackupWriter
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.models.backup.BackupId
import org.signal.core.models.backup.MediaId
import org.signal.core.models.backup.MediaName
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.signal.core.util.CursorUtil
import org.signal.core.util.DiskUtil
import org.signal.core.util.EventTimer
import org.signal.core.util.PendingIntentFlags.cancelCurrent
import org.signal.core.util.ServiceUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.LimitedWorker
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.crypto.AttachmentSecretProvider
import org.signal.core.util.decodeOrNull
import org.signal.core.util.forceForeignKeyConstraintsEnabled
import org.signal.core.util.fullWalCheckpoint
import org.signal.core.util.getAllIndexDefinitions
import org.signal.core.util.getAllTableDefinitions
import org.signal.core.util.getAllTriggerDefinitions
import org.signal.core.util.getForeignKeyViolations
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.logW
import org.signal.core.util.money.FiatMoney
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireIntOrNull
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.stream.NonClosingOutputStream
import org.signal.core.util.withinTransaction
import org.signal.libsignal.messagebackup.BackupForwardSecrecyToken
import org.signal.libsignal.net.DeleteBackupMediaItem
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.zkgroup.backups.BackupLevel
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.network.NetworkResult
import org.signal.network.api.ArchiveApiV2
import org.signal.network.api.SvrBApi
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.service.ArchiveError
import org.signal.network.service.ArchiveService
import org.signal.network.service.toArchiveResult
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.BackupRepository.exportForDebugging
import org.thoughtcrime.securesms.backup.v2.importer.ChatItemArchiveImporter
import org.thoughtcrime.securesms.backup.v2.processor.AccountDataArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.AdHocCallArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatFolderArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatItemArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.NotificationProfileArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.RecipientArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.StickerArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlert
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.crypto.AppAttachmentSecretStore
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable.ArchiveMediaItem
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.KyberPreKeyTable
import org.thoughtcrime.securesms.database.OneTimePreKeyTable
import org.thoughtcrime.securesms.database.SearchTable
import org.thoughtcrime.securesms.database.SessionTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignedPreKeyTable
import org.thoughtcrime.securesms.database.StickerTables
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.DataRestoreConstraint
import org.thoughtcrime.securesms.jobs.ArchiveAttachmentBackfillJob
import org.thoughtcrime.securesms.jobs.ArchiveThumbnailBackfillJob
import org.thoughtcrime.securesms.jobs.ArchiveThumbnailUploadJob
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob
import org.thoughtcrime.securesms.jobs.BackfillCollapsedMessageJob
import org.thoughtcrime.securesms.jobs.BackupDeleteJob
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.jobs.CancelRestoreMediaJob
import org.thoughtcrime.securesms.jobs.CreateReleaseChannelJob
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.jobs.MultiDeviceKeysUpdateJob
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.jobs.ResetSvrGuessCountJob
import org.thoughtcrime.securesms.jobs.RestoreOptimizedMediaJob
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob
import org.thoughtcrime.securesms.jobs.StorageForcePushJob
import org.thoughtcrime.securesms.jobs.Svr2MirrorJob
import org.thoughtcrime.securesms.jobs.UploadAttachmentToArchiveJob
import org.thoughtcrime.securesms.keyvalue.BackupValues
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.BackupMediaRestoreService
import org.thoughtcrime.securesms.service.BackupProgressService
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.toMillis
import org.whispersystems.signalservice.api.link.TransferArchiveResponse
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.Currency
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.signal.registration.R as RegistrationR

object BackupRepository {

  private val TAG = Log.tag(BackupRepository::class.java)
  const val VERSION = 1L
  private const val REMOTE_MAIN_DB_SNAPSHOT_NAME = "remote-signal-snapshot"
  private const val REMOTE_KEYVALUE_DB_SNAPSHOT_NAME = "remote-signal-key-value-snapshot"
  private const val LOCAL_MAIN_DB_SNAPSHOT_NAME = "local-signal-snapshot"
  private const val LOCAL_KEYVALUE_DB_SNAPSHOT_NAME = "local-signal-key-value-snapshot"
  private const val RECENT_RECIPIENTS_MAX = 50
  private val MANUAL_BACKUP_NOTIFICATION_THRESHOLD = 30.days

  private val archiveService: ArchiveService
    get() = AppDependencies.archiveService

  /**
   * Generates a new AEP that the user can choose to confirm.
   */
  @CheckResult
  fun stageBackupKeyRotations(): StagedBackupKeyRotations {
    return StagedBackupKeyRotations(
      aep = AccountEntropyPool.generate(),
      mediaRootBackupKey = MediaRootBackupKey.generate()
    )
  }

  /**
   * Saves the AEP to the local storage and kicks off a backup upload.
   */
  suspend fun commitAEPKeyRotation(stagedKeyRotations: StagedBackupKeyRotations) {
    haltAllJobs()
    resetInitializedStateAndAuthCredentials()
    SignalStore.account.rotateAccountEntropyPool(stagedKeyRotations.aep)
    SignalStore.backup.mediaRootBackupKey = stagedKeyRotations.mediaRootBackupKey
    resetSvrBChain()

    refreshMasterKeyDependents()
    BackupMessagesJob.enqueue()
  }

  private fun refreshMasterKeyDependents() {
    val jobs = buildList {
      add(Svr2MirrorJob())
      if (SignalStore.account.isMultiDevice) {
        add(MultiDeviceKeysUpdateJob())
      }
      add(StorageForcePushJob())
    }

    AppDependencies.jobManager.addAll(jobs)
  }

  /**
   * Discards our local SVRB state so that the next backup starts a brand new chain.
   */
  fun resetSvrBChain() {
    Log.i(TAG, "Resetting SVRB chain.", true)
    SignalStore.backup.nextBackupSecretData = null
    SignalStore.backup.backupSecretRestoreRequired = false
  }

  fun resetInitializedStateAndAuthCredentials() {
    SignalStore.backup.messageBackupInitialized = false
    SignalStore.backup.mediaBackupInitialized = false
    SignalStore.backup.messageCredentials.clearAll()
    SignalStore.backup.mediaCredentials.clearAll()
    SignalStore.backup.cachedMediaCdnPath = null
  }

  private suspend fun haltAllJobs() {
    ArchiveUploadProgress.cancelAndBlock()
    AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)

    Log.d(TAG, "Waiting for local backup job cancelations to occur...")
    while (!AppDependencies.jobManager.areQueuesEmpty(setOf(LocalBackupJob.QUEUE))) {
      delay(1.seconds)
    }
  }

  /**
   * Checks whether or not we do not have enough storage space for our remaining attachments to be downloaded.
   * Caller from the attachment / thumbnail download jobs.
   */
  fun checkForOutOfStorageError(tag: String): Boolean {
    val availableSpace = DiskUtil.getAvailableSpace(AppDependencies.application)
    val remainingAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize().bytes

    return if (availableSpace < remainingAttachmentSize) {
      Log.w(tag, "Possibly out of space. ${availableSpace.toUnitString()} available.", true)
      SignalStore.backup.spaceAvailableOnDiskBytes = availableSpace.bytes
      true
    } else {
      false
    }
  }

  @JvmStatic
  fun resumeMediaRestore() {
    SignalStore.backup.userManuallySkippedMediaRestore = false
    RestoreOptimizedMediaJob.enqueue()
  }

  /**
   * Cancels any relevant jobs for media restore
   */
  @JvmStatic
  fun skipMediaRestore() {
    CancelRestoreMediaJob.enqueue()
  }

  fun markBackupCreationFailed(error: BackupValues.BackupCreationError) {
    SignalStore.backup.markBackupCreationFailed(error)
    ArchiveUploadProgress.onMainBackupFileUploadFailure()

    if (!SignalStore.backup.hasBackupBeenUploaded) {
      Log.w(TAG, "Failure of initial backup. Displaying notification.")
      displayInitialBackupFailureNotification()
    }
  }

  @Discouraged("This is only public to allow internal settings to call it directly.")
  fun displayInitialBackupFailureNotification() {
    val context = AppDependencies.application

    val pendingIntent = PendingIntent.getActivity(context, 0, AppSettingsActivity.remoteBackups(context), cancelCurrent())
    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.Notification_backup_failed))
      .setContentText(context.getString(R.string.Notification_an_error_occurred_and_your_backup))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    ServiceUtil.getNotificationManager(context).notify(NotificationIds.INITIAL_BACKUP_FAILED, notification)
  }

  fun clearBackupFailure() {
    SignalStore.backup.backupCreationError = null
    ServiceUtil.getNotificationManager(AppDependencies.application).cancel(NotificationIds.INITIAL_BACKUP_FAILED)
  }

  fun markOutOfRemoteStorageSpaceError() {
    if (SignalStore.backup.isNotEnoughRemoteStorageSpace) {
      return
    }

    SignalStore.backup.markNotEnoughRemoteStorageSpace()

    val context = AppDependencies.application

    val pendingIntent = PendingIntent.getActivity(context, 0, AppSettingsActivity.remoteBackups(context), cancelCurrent())
    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.Notification_backup_storage_full))
      .setContentText(context.getString(R.string.Notification_youve_reached_your_backup_storage_limit))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    ServiceUtil.getNotificationManager(context).notify(NotificationIds.OUT_OF_REMOTE_STORAGE, notification)
  }

  fun clearOutOfRemoteStorageSpaceError() {
    SignalStore.backup.clearNotEnoughRemoteStorageSpace()
    ServiceUtil.getNotificationManager(AppDependencies.application).cancel(NotificationIds.OUT_OF_REMOTE_STORAGE)
  }

  fun shouldDisplayOutOfRemoteStorageSpaceUx(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return SignalStore.backup.isNotEnoughRemoteStorageSpace
  }

  fun shouldDisplayOutOfRemoteStorageSpaceSheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return SignalStore.backup.shouldDisplayNotEnoughRemoteStorageSpaceSheet
  }

  fun dismissOutOfRemoteStorageSpaceSheet() {
    SignalStore.backup.dismissNotEnoughRemoteStorageSpaceSheet()
  }

  /**
   * Whether the yellow dot should be displayed on the conversation list avatar.
   */
  @JvmStatic
  fun shouldDisplayBackupFailedIndicator(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging() || !SignalStore.backup.hasBackupCreationError) {
      return false
    }

    val now = System.currentTimeMillis().milliseconds
    val alertAfter = SignalStore.backup.nextBackupFailureSnoozeTime

    return alertAfter <= now
  }

  @JvmStatic
  fun shouldDisplayBackupAlreadyRedeemedIndicator(): Boolean {
    return !(shouldNotDisplayBackupFailedMessaging() || !SignalStore.backup.hasBackupAlreadyRedeemedError)
  }

  /**
   * Displayed when the user falls out of the grace period for backups after their subscription
   * expires.
   */
  fun shouldDisplayBackupExpiredAndDowngradedSheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return SignalStore.backup.backupExpiredAndDowngraded
  }

  fun markBackupAlreadyRedeemedIndicatorClicked() {
    SignalStore.backup.hasBackupAlreadyRedeemedError = false
  }

  /**
   * Updates the watermark for the indicator display.
   */
  @JvmStatic
  fun markBackupFailedIndicatorClicked() {
    SignalStore.backup.updateMessageBackupFailureWatermark()
  }

  /**
   * Updates the watermark for the sheet display.
   */
  fun markBackupFailedSheetDismissed() {
    SignalStore.backup.updateMessageBackupFailureSheetWatermark()
  }

  /**
   * User closed backup expiration alert sheet
   */
  fun markBackupExpiredAndDowngradedSheetDismissed() {
    SignalStore.backup.backupExpiredAndDowngraded = false
  }

  /**
   * Whether or not the "Backup failed" sheet should be displayed.
   * Should only be displayed if this is the failure of the initial backup creation.
   */
  @JvmStatic
  fun shouldDisplayBackupFailedSheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return SignalStore.backup.hasBackupCreationError && SignalStore.backup.backupCreationError != BackupValues.BackupCreationError.TRANSIENT && System.currentTimeMillis().milliseconds > SignalStore.backup.nextBackupFailureSheetSnoozeTime
  }

  /**
   * Whether or not the "Could not complete backup" sheet should be displayed.
   */
  @JvmStatic
  fun shouldDisplayCouldNotCompleteBackupSheet(): Boolean {
    // Temporarily disabling. May re-enable in the future.
    if (true) {
      return false
    }

    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    val isRegistered = SignalStore.account.isRegistered && !TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application)
    if (!isRegistered) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet for unregistered user.")
      return false
    }

    if (SignalStore.backup.lastBackupTime <= 0) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet as the last backup time is unset.")
      return false
    }

    if (!SignalStore.backup.hasBackupBeenUploaded) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet as a backup has never been uploaded.")
      return false
    }

    val now = System.currentTimeMillis().milliseconds
    val lastBackupTime = SignalStore.backup.lastBackupTime.milliseconds
    val nextSnoozeTime = SignalStore.backup.nextBackupFailureSnoozeTime

    val isLastBackupTimeAtLeastAWeekAgo = now - 7.days > lastBackupTime
    if (!isLastBackupTimeAtLeastAWeekAgo) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet as the last backup time is less than a week ago.")
      return false
    }

    val isNextSnoozeTimeBeforeNow = nextSnoozeTime < now
    if (!isNextSnoozeTimeBeforeNow) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet as the next snooze time is in the future.")
      return false
    }

    return true
  }

  fun snoozeDownloadYourBackupData() {
    SignalStore.backup.snoozeDownloadNotifier()
  }

  @JvmStatic
  fun maybeFixAnyDanglingUploadProgress() {
    if (SignalStore.account.isLinkedDevice) {
      return
    }

    if (SignalStore.backup.archiveUploadState?.backupPhase == ArchiveUploadProgressState.BackupPhase.Message && AppDependencies.jobManager.find { it.factoryKey == BackupMessagesJob.KEY }.isEmpty()) {
      Log.w(TAG, "Found a situation where message backup was in progress, but there's no active BackupMessageJob! Re-enqueueing.")
      SignalStore.backup.archiveUploadState = null
      BackupMessagesJob.enqueue()
      return
    }

    if (!SignalStore.backup.backsUpMedia) {
      return
    }

    if (!AppDependencies.jobManager.areQueuesEmpty(UploadAttachmentToArchiveJob.QUEUES)) {
      if (SignalStore.backup.archiveUploadState?.state == ArchiveUploadProgressState.State.None) {
        Log.w(TAG, "Found a situation where attachment uploads are in progress, but the progress state was None! Fixing.")
        ArchiveUploadProgress.onAttachmentSectionStarted(SignalDatabase.attachments.getPendingArchiveUploadBytes())
      }
      return
    }

    if (AppDependencies.jobManager.areQueuesEmpty(ArchiveThumbnailUploadJob.QUEUES) && SignalDatabase.attachments.areAnyThumbnailsPendingUpload()) {
      Log.w(TAG, "Found a situation where there's no thumbnail jobs in progress, but thumbnails are in the pending upload state! Clearing the pending state and re-enqueueing.")
      SignalDatabase.attachments.clearArchiveThumbnailTransferStateForInProgressItems()
      AppDependencies.jobManager.add(ArchiveThumbnailBackfillJob())
    }

    val pendingBytes = SignalDatabase.attachments.getPendingArchiveUploadBytes()
    if (pendingBytes == 0L) {
      return
    }

    Log.w(TAG, "There are ${pendingBytes.bytes.toUnitString(maxPlaces = 2)} of attachments that need to be uploaded to the archive, but no jobs for them! Attempting to fix.")
    val resetCount = SignalDatabase.attachments.clearArchiveTransferStateForInProgressItems()
    Log.w(TAG, "Cleared the archive transfer state of $resetCount attachments.")
    AppDependencies.jobManager.add(ArchiveAttachmentBackfillJob())
  }

  /**
   * Whether or not the "Your media will be deleted today" sheet should be displayed.
   */
  suspend fun getDownloadYourBackupData(): BackupAlert.DownloadYourBackupData? {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return null
    }

    val state = SignalStore.backup.backupDownloadNotifierState ?: return null
    val nextSheetDisplayTime = state.lastSheetDisplaySeconds.seconds + state.intervalSeconds.seconds

    val remainingAttachmentSize = withContext(SignalDispatchers.Default) {
      SignalDatabase.attachments.getRemainingRestorableAttachmentSize()
    }

    if (remainingAttachmentSize <= 0L) {
      SignalStore.backup.clearDownloadNotifierState()
      return null
    }

    val now = System.currentTimeMillis().milliseconds

    return if (nextSheetDisplayTime <= now) {
      val lastDay = state.entitlementExpirationSeconds.seconds - 1.days

      BackupAlert.DownloadYourBackupData(
        isLastDay = now >= lastDay,
        formattedSize = remainingAttachmentSize.bytes.toUnitString(),
        type = state.type
      )
    } else {
      null
    }
  }

  fun shouldNotDisplayBackupFailedMessaging(): Boolean {
    return !SignalStore.account.isRegistered || !SignalStore.backup.areBackupsEnabled
  }

  /**
   * Initiates backup disable via [BackupDeleteJob]
   */
  suspend fun turnOffAndDisableBackups() {
    ArchiveUploadProgress.cancelAndBlock()
    SignalStore.backup.userManuallySkippedMediaRestore = false
    SignalStore.backup.deletionState = DeletionState.CLEAR_LOCAL_STATE
    AppDependencies.jobManager.add(BackupDeleteJob())
  }

  /**
   * To be called if the user skips media restore during the deletion process.
   */
  fun continueTurningOffAndDisablingBackups() {
    AppDependencies.jobManager.add(BackupDeleteJob())
  }

  @WorkerThread
  private fun createSignalDatabaseSnapshot(baseName: String): SignalDatabase {
    // Need to do a WAL checkpoint to ensure that the database file we're copying has all pending writes
    if (!SignalDatabase.rawDatabase.fullWalCheckpoint()) {
      Log.w(TAG, "Failed to checkpoint WAL for main database! Not guaranteed to be using the most recent data.")
    }

    // We make a copy of the database within a transaction to ensure that no writes occur while we're copying the file
    return SignalDatabase.rawDatabase.withinTransaction {
      val context = AppDependencies.application

      val existingDbFile = context.getDatabasePath(SignalDatabase.DATABASE_NAME)
      val targetFile = File(existingDbFile.parentFile, "$baseName.db")

      existingDbFile.parentFile?.deleteAllFilesWithPrefix(baseName)

      try {
        existingDbFile.copyTo(targetFile, overwrite = true)
      } catch (e: IOException) {
        // TODO [backup] Gracefully handle this error
        throw IllegalStateException("Failed to copy database file!", e)
      }

      SignalDatabase(
        context = context,
        databaseSecret = DatabaseSecretProvider.getOrCreateDatabaseSecret(context),
        attachmentSecret = AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore).getOrCreateAttachmentSecret(),
        name = "$baseName.db"
      )
    }
  }

  @WorkerThread
  private fun createSignalStoreSnapshot(baseName: String): SignalStore {
    val context = AppDependencies.application

    SignalStore.blockUntilAllWritesFinished()

    // Need to do a WAL checkpoint to ensure that the database file we're copying has all pending writes
    if (!KeyValueDatabase.getInstance(context).writableDatabase.fullWalCheckpoint()) {
      Log.w(TAG, "Failed to checkpoint WAL for KeyValueDatabase! Not guaranteed to be using the most recent data.")
    }

    // We make a copy of the database within a transaction to ensure that no writes occur while we're copying the file
    return KeyValueDatabase.getInstance(context).writableDatabase.withinTransaction {
      val existingDbFile = context.getDatabasePath(KeyValueDatabase.DATABASE_NAME)
      val targetFile = File(existingDbFile.parentFile, "$baseName.db")

      existingDbFile.parentFile?.deleteAllFilesWithPrefix(baseName)

      try {
        existingDbFile.copyTo(targetFile, overwrite = true)
      } catch (e: IOException) {
        // TODO [backup] Gracefully handle this error
        throw IllegalStateException("Failed to copy database file!", e)
      }

      val db = KeyValueDatabase.createWithName(context, "$baseName.db")
      SignalStore(context, KeyValueStore(db))
    }
  }

  @WorkerThread
  private fun deleteDatabaseSnapshot(name: String) {
    AppDependencies.application.getDatabasePath("$name.db")
      .parentFile
      ?.deleteAllFilesWithPrefix(name)
  }

  @WorkerThread
  fun exportForLocalBackup(
    main: OutputStream,
    localBackupProgressEmitter: ExportProgressListener,
    cancellationSignal: () -> Boolean = { false },
    archiveAttachment: (AttachmentTable.LocalArchivableAttachment, () -> InputStream?) -> Unit
  ) {
    val writer = EncryptedBackupWriter.createForLocalOrLinking(
      key = SignalStore.backup.messageBackupKey,
      aci = SignalStore.account.aci!!,
      outputStream = NonClosingOutputStream(main),
      append = { main.write(it) }
    )

    export(
      currentTime = System.currentTimeMillis(),
      isLocal = true,
      writer = writer,
      progressEmitter = localBackupProgressEmitter,
      cancellationSignal = cancellationSignal,
      backupMode = BackupMode.LOCAL,
      extraFrameOperation = null,
      messageInclusionCutoffTime = 0
    ) { dbSnapshot ->
      val localArchivableAttachments = dbSnapshot
        .attachmentTable
        .getLocalArchivableAttachments()
        .associateBy { MediaName.forLocalBackupFilename(it.plaintextHash, it.localBackupKey.key) }

      localBackupProgressEmitter.onAttachment(0, localArchivableAttachments.size.toLong())

      val progress = AtomicLong(0)

      LimitedWorker.execute(SignalExecutors.BOUNDED_IO, 4, localArchivableAttachments.values) { attachment ->
        try {
          archiveAttachment(attachment) { dbSnapshot.attachmentTable.getAttachmentStream(attachment) }
        } catch (e: IOException) {
          Log.w(TAG, "Unable to open attachment, skipping", e)
        }

        val currentProgress = progress.incrementAndGet()
        localBackupProgressEmitter.onAttachment(currentProgress, localArchivableAttachments.size.toLong())
      }
    }
  }

  @WorkerThread
  fun exportForLocalPlaintextArchive(
    outputStream: OutputStream,
    progressEmitter: ExportProgressListener?,
    cancellationSignal: () -> Boolean,
    includeMedia: Boolean
  ): List<AttachmentTable.LocalArchivableAttachment> {
    val writer = LibSignalJsonBackupWriter(NonClosingOutputStream(outputStream))
    val collectedAttachments = mutableListOf<AttachmentTable.LocalArchivableAttachment>()

    export(
      currentTime = System.currentTimeMillis(),
      isLocal = true,
      writer = writer,
      backupMode = BackupMode.PLAINTEXT_EXPORT,
      progressEmitter = progressEmitter,
      cancellationSignal = cancellationSignal,
      extraFrameOperation = null,
      messageInclusionCutoffTime = 0
    ) { dbSnapshot ->
      if (includeMedia) {
        collectedAttachments.addAll(dbSnapshot.attachmentTable.getLocalArchivableAttachmentsForPlaintextExport())
      }
    }

    return collectedAttachments
  }

  /**
   * Export a backup that will be uploaded to the archive CDN.
   */
  fun exportForSignalBackup(
    outputStream: OutputStream,
    append: (ByteArray) -> Unit,
    messageBackupKey: MessageBackupKey,
    forwardSecrecyToken: BackupForwardSecrecyToken,
    forwardSecrecyMetadata: ByteArray,
    currentTime: Long,
    messageInclusionCutoffTime: Long = 0,
    progressEmitter: ExportProgressListener? = null,
    cancellationSignal: () -> Boolean = { false },
    extraFrameOperation: ((Frame) -> Unit)?
  ) {
    val writer = EncryptedBackupWriter.createForSignalBackup(
      key = messageBackupKey,
      aci = SignalStore.account.aci!!,
      outputStream = outputStream,
      forwardSecrecyToken = forwardSecrecyToken,
      forwardSecrecyMetadata = forwardSecrecyMetadata,
      append = append
    )

    return export(
      currentTime = currentTime,
      isLocal = false,
      writer = writer,
      backupMode = BackupMode.REMOTE,
      progressEmitter = progressEmitter,
      cancellationSignal = cancellationSignal,
      extraFrameOperation = extraFrameOperation,
      endingExportOperation = null,
      messageInclusionCutoffTime = messageInclusionCutoffTime
    )
  }

  /**
   * Export a backup that will be uploaded to the archive CDN.
   */
  fun exportForLinkAndSync(
    outputStream: OutputStream,
    append: (ByteArray) -> Unit,
    messageBackupKey: MessageBackupKey,
    currentTime: Long,
    progressEmitter: ExportProgressListener? = null,
    cancellationSignal: () -> Boolean = { false }
  ) {
    val writer = EncryptedBackupWriter.createForLocalOrLinking(
      key = messageBackupKey,
      aci = SignalStore.account.aci!!,
      outputStream = outputStream,
      append = append
    )

    return export(
      currentTime = currentTime,
      isLocal = false,
      writer = writer,
      backupMode = BackupMode.LINK_SYNC,
      progressEmitter = progressEmitter,
      cancellationSignal = cancellationSignal,
      extraFrameOperation = null,
      endingExportOperation = null,
      messageInclusionCutoffTime = 0
    )
  }

  @WorkerThread
  @JvmOverloads
  fun exportForDebugging(
    outputStream: OutputStream,
    append: (ByteArray) -> Unit,
    messageBackupKey: MessageBackupKey = SignalStore.backup.messageBackupKey,
    plaintext: Boolean = false,
    currentTime: Long = System.currentTimeMillis(),
    progressEmitter: ExportProgressListener? = null,
    cancellationSignal: () -> Boolean = { false }
  ) {
    val writer: BackupExportWriter = if (plaintext) {
      PlainTextBackupWriter(outputStream)
    } else {
      EncryptedBackupWriter.createForLocalOrLinking(
        key = messageBackupKey,
        aci = SignalStore.account.aci!!,
        outputStream = outputStream,
        append = append
      )
    }

    export(
      currentTime = currentTime,
      isLocal = false,
      writer = writer,
      backupMode = BackupMode.REMOTE,
      progressEmitter = progressEmitter,
      cancellationSignal = cancellationSignal,
      extraFrameOperation = null,
      endingExportOperation = null,
      messageInclusionCutoffTime = 0
    )
  }

  /**
   * Exports to a blob in memory. Should only be used for testing.
   */
  @WorkerThread
  fun exportInMemoryForTests(plaintext: Boolean = false, currentTime: Long = System.currentTimeMillis()): ByteArray {
    val outputStream = ByteArrayOutputStream()
    exportForDebugging(outputStream = outputStream, append = { mac -> outputStream.write(mac) }, plaintext = plaintext, currentTime = currentTime)
    return outputStream.toByteArray()
  }

  @WorkerThread
  private fun export(
    currentTime: Long,
    isLocal: Boolean,
    writer: BackupExportWriter,
    backupMode: BackupMode,
    messageInclusionCutoffTime: Long,
    progressEmitter: ExportProgressListener?,
    cancellationSignal: () -> Boolean,
    extraFrameOperation: ((Frame) -> Unit)?,
    endingExportOperation: ((SignalDatabase) -> Unit)?
  ) {
    val eventTimer = EventTimer()
    val mainDbName = if (isLocal) LOCAL_MAIN_DB_SNAPSHOT_NAME else REMOTE_MAIN_DB_SNAPSHOT_NAME
    val keyValueDbName = if (isLocal) LOCAL_KEYVALUE_DB_SNAPSHOT_NAME else REMOTE_KEYVALUE_DB_SNAPSHOT_NAME

    try {
      val dbSnapshot: SignalDatabase = createSignalDatabaseSnapshot(mainDbName)
      eventTimer.emit("main-db-snapshot")

      val signalStoreSnapshot: SignalStore = createSignalStoreSnapshot(keyValueDbName)
      eventTimer.emit("store-db-snapshot")

      val selfAci = signalStoreSnapshot.accountValues.aci!!
      val selfRecipientId = dbSnapshot.recipientTable.getByAci(selfAci).get().toLong().let { RecipientId.from(it) }
      val exportState = ExportState(backupTime = currentTime, backupMode = backupMode, selfRecipientId = selfRecipientId)

      var frameCount = 0L

      writer.use {
        val debugInfo = buildDebugInfo()
        eventTimer.emit("debug-info")

        writer.write(
          BackupInfo(
            version = VERSION,
            backupTimeMs = exportState.backupTime,
            mediaRootBackupKey = SignalStore.backup.mediaRootBackupKey.value.toByteString(),
            firstAppVersion = SignalStore.backup.firstAppVersion,
            debugInfo = debugInfo
          )
        )
        frameCount++
        eventTimer.emit("header")

        // We're using a snapshot, so the transaction is more for perf than correctness
        dbSnapshot.rawWritableDatabase.withinTransaction {
          progressEmitter?.onAccount()
          AccountDataArchiveProcessor.export(dbSnapshot, signalStoreSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("account")
            frameCount++
          }
          if (cancellationSignal()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onRecipient()
          RecipientArchiveProcessor.export(dbSnapshot, signalStoreSnapshot, exportState, selfAci) {
            writer.write(it)
            extraFrameOperation?.invoke(it)
            eventTimer.emit("recipient")
            frameCount++
          }
          if (cancellationSignal()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onThread()
          ChatArchiveProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("thread")
            frameCount++
          }
          if (cancellationSignal()) {
            return@export
          }

          progressEmitter?.onCall()
          AdHocCallArchiveProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("call")
            frameCount++
          }
          if (cancellationSignal()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onSticker()
          StickerArchiveProcessor.export(dbSnapshot) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("sticker-pack")
            frameCount++
          }
          if (cancellationSignal()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onNotificationProfile()
          NotificationProfileArchiveProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("notification-profile")
            frameCount++
          }
          if (cancellationSignal()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onChatFolder()
          ChatFolderArchiveProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("chat-folder")
            frameCount++
          }
          if (cancellationSignal()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          val approximateMessageCount = dbSnapshot.messageTable.getApproximateExportableMessageCount(exportState.threadIds)
          val frameCountStart = frameCount
          progressEmitter?.onMessage(0, approximateMessageCount)
          ChatItemArchiveProcessor.export(dbSnapshot, exportState, selfRecipientId, messageInclusionCutoffTime, cancellationSignal) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("message")
            frameCount++

            if (frameCount % 1000 == 0L) {
              Log.d(TAG, "[export] Exported $frameCount frames so far.")
              progressEmitter?.onMessage(frameCount - frameCountStart, approximateMessageCount)
              if (cancellationSignal()) {
                Log.w(TAG, "[export] Cancelled! Stopping")
                return@export
              }
            }
          }
        }
      }

      endingExportOperation?.invoke(dbSnapshot)

      Log.d(TAG, "[export] totalFrames: $frameCount | ${eventTimer.stop().summary}")
    } finally {
      deleteDatabaseSnapshot(mainDbName)
      deleteDatabaseSnapshot(keyValueDbName)
    }
  }

  /**
   * Imports a local backup file that was exported to disk.
   */
  fun importLocal(mainStreamFactory: () -> InputStream, mainStreamLength: Long, selfData: SelfData, backupId: BackupId, messageBackupKey: MessageBackupKey): ImportResult {
    val backupKey = messageBackupKey

    val frameReader = try {
      EncryptedBackupReader.createForLocalOrLinking(
        key = backupKey,
        backupId = backupId,
        length = mainStreamLength,
        dataStream = mainStreamFactory
      )
    } catch (e: IOException) {
      Log.w(TAG, "Unable to import local archive", e)
      return ImportResult.Failure
    }

    return frameReader.use { reader ->
      import(reader, selfData, backupMode = BackupMode.LOCAL, cancellationSignal = { false })
    }
  }

  /**
   * Imports a backup stored on the archive CDN.
   *
   * @param backupKey  The key used to encrypt the backup. If `null`, we assume that the file is plaintext.
   */
  fun importSignalBackup(
    length: Long,
    inputStreamFactory: () -> InputStream,
    selfData: SelfData,
    backupKey: MessageBackupKey?,
    forwardSecrecyToken: BackupForwardSecrecyToken,
    cancellationSignal: () -> Boolean = { false }
  ): ImportResult {
    try {
      val frameReader = if (backupKey == null) {
        PlainTextBackupReader(inputStreamFactory(), length)
      } else {
        EncryptedBackupReader.createForSignalBackup(
          key = backupKey,
          aci = selfData.aci,
          forwardSecrecyToken = forwardSecrecyToken,
          length = length,
          dataStream = inputStreamFactory
        )
      }

      return frameReader.use { reader ->
        import(reader, selfData, backupMode = BackupMode.REMOTE, cancellationSignal = cancellationSignal)
      }
    } catch (e: IOException) {
      Log.w(TAG, "Unable to restore signal backup", e)
      return ImportResult.Failure
    }
  }

  /**
   * Imports a link and sync backup stored on the transit CDN.
   *
   * @param backupKey  The key used to encrypt the backup. If `null`, we assume that the file is plaintext.
   */
  fun importLinkAndSyncSignalBackup(
    length: Long,
    inputStreamFactory: () -> InputStream,
    selfData: SelfData,
    backupKey: MessageBackupKey,
    cancellationSignal: () -> Boolean = { false }
  ): ImportResult {
    val frameReader = EncryptedBackupReader.createForLocalOrLinking(
      key = backupKey,
      aci = selfData.aci,
      length = length,
      dataStream = inputStreamFactory
    )

    return frameReader.use { reader ->
      import(reader, selfData, backupMode = BackupMode.LINK_SYNC, cancellationSignal = cancellationSignal)
    }
  }

  /**
   * Imports a backup that was exported via [exportForDebugging].
   */
  fun importForDebugging(
    length: Long,
    inputStreamFactory: () -> InputStream,
    selfData: SelfData,
    backupKey: MessageBackupKey?,
    cancellationSignal: () -> Boolean = { false }
  ): ImportResult {
    val frameReader = if (backupKey == null) {
      PlainTextBackupReader(inputStreamFactory(), length)
    } else {
      EncryptedBackupReader.createForLocalOrLinking(
        key = backupKey,
        aci = selfData.aci,
        length = length,
        dataStream = inputStreamFactory
      )
    }

    return frameReader.use { reader ->
      import(reader, selfData, backupMode = BackupMode.REMOTE, cancellationSignal = cancellationSignal)
    }
  }

  /**
   * Imports a plaintext backup only used for testing.
   */
  fun importPlaintextTest(
    length: Long,
    inputStreamFactory: () -> InputStream,
    selfData: SelfData,
    cancellationSignal: () -> Boolean = { false }
  ): ImportResult {
    val frameReader = PlainTextBackupReader(inputStreamFactory(), length)

    return frameReader.use { reader ->
      import(reader, selfData, backupMode = BackupMode.PLAINTEXT_EXPORT, cancellationSignal = cancellationSignal)
    }
  }

  private fun import(
    frameReader: BackupImportReader,
    selfData: SelfData,
    backupMode: BackupMode,
    cancellationSignal: () -> Boolean
  ): ImportResult {
    val stopwatch = Stopwatch("import")
    val eventTimer = EventTimer()

    val header = frameReader.getHeader()
    if (header == null) {
      Log.e(TAG, "[import] Backup is missing header!")
      SignalStore.backup.hasInvalidBackupVersion = false
      return ImportResult.Failure
    } else if (header.version > VERSION) {
      Log.e(TAG, "[import] Backup version is newer than we understand: ${header.version}")
      SignalStore.backup.hasInvalidBackupVersion = true
      return ImportResult.Failure
    }
    SignalStore.backup.hasInvalidBackupVersion = false
    val selfId: RecipientId

    var transactionSuccessful = false
    try {
      // Removing all the data from the various tables is *very* expensive (i.e. can take *several* minutes) if we don't do some pre-work.
      // SQLite optimizes deletes if there's no foreign keys, triggers, or WHERE clause, so that's the environment we're gonna create.

      Log.d(TAG, "[import] Disabling foreign keys...")
      SignalDatabase.rawDatabase.forceForeignKeyConstraintsEnabled(false)

      Log.d(TAG, "[import] Acquiring transaction...")
      SignalDatabase.rawDatabase.beginTransaction()

      Log.d(TAG, "[import] Inside transaction.")
      stopwatch.split("get-transaction")

      Log.d(TAG, "[import] --- Dropping all indices ---")
      val indexMetadata = SignalDatabase.rawDatabase.getAllIndexDefinitions()
      for (index in indexMetadata) {
        Log.d(TAG, "[import] Dropping index ${index.name}...")
        SignalDatabase.rawDatabase.execSQL("DROP INDEX IF EXISTS ${index.name}")
      }
      stopwatch.split("drop-indices")

      if (cancellationSignal()) {
        return ImportResult.Failure
      }

      Log.d(TAG, "[import] --- Dropping all triggers ---")
      val triggerMetadata = SignalDatabase.rawDatabase.getAllTriggerDefinitions()
      for (trigger in triggerMetadata) {
        Log.d(TAG, "[import] Dropping trigger ${trigger.name}...")
        SignalDatabase.rawDatabase.execSQL("DROP TRIGGER IF EXISTS ${trigger.name}")
      }
      stopwatch.split("drop-triggers")

      if (cancellationSignal()) {
        return ImportResult.Failure
      }

      Log.d(TAG, "[import] --- Recreating all tables ---")
      val skipTables = buildSet {
        add(KyberPreKeyTable.TABLE_NAME)
        add(OneTimePreKeyTable.TABLE_NAME)
        add(SignedPreKeyTable.TABLE_NAME)

        // Preserve the session established with the primary during linking
        if (backupMode.isLinkAndSync) {
          add(SessionTable.TABLE_NAME)
        }
      }
      val tableMetadata = SignalDatabase.rawDatabase.getAllTableDefinitions().filter { !it.name.startsWith(SearchTable.FTS_TABLE_NAME + "_") }
      for (table in tableMetadata) {
        if (skipTables.contains(table.name)) {
          Log.d(TAG, "[import] Skipping drop/create of table ${table.name}")
          continue
        }

        Log.d(TAG, "[import] Dropping table ${table.name}...")
        SignalDatabase.rawDatabase.execSQL("DROP TABLE IF EXISTS ${table.name}")

        Log.d(TAG, "[import] Creating table ${table.name}...")
        SignalDatabase.rawDatabase.execSQL(table.statement)
      }

      RecipientId.clearCache()
      SignalDatabase.remappedRecords.clearCache()
      AppDependencies.recipientCache.clear()
      AppDependencies.recipientCache.clearSelf()
      SignalDatabase.threads.clearCache()

      stopwatch.split("drop-data")

      if (cancellationSignal()) {
        return ImportResult.Failure
      }

      val mediaRootBackupKey = MediaRootBackupKey(header.mediaRootBackupKey.toByteArray())
      SignalStore.backup.mediaRootBackupKey = mediaRootBackupKey

      // Add back self after clearing data
      selfId = SignalDatabase.recipients.getAndPossiblyMerge(selfData.aci, selfData.pni, selfData.e164, pniVerified = true, changeSelf = true)
      SignalDatabase.recipients.setProfileKey(selfId, selfData.profileKey)
      SignalDatabase.recipients.setProfileSharing(selfId, true)

      val importState = ImportState(mediaRootBackupKey, backupMode)
      val chatItemInserter: ChatItemArchiveImporter = ChatItemArchiveProcessor.beginImport(importState)

      Log.d(TAG, "[import] Beginning to read frames.")
      val totalLength = frameReader.getStreamLength()
      var frameCount = 0
      for (frame in frameReader) {
        val frameAccount = frame.account
        val frameRecipient = frame.recipient
        val frameChat = frame.chat
        val frameAdHocCall = frame.adHocCall
        val frameStickerPack = frame.stickerPack
        val frameNotificationProfile = frame.notificationProfile
        val frameChatFolder = frame.chatFolder
        val frameChatItem = frame.chatItem
        when {
          frameAccount != null -> {
            AccountDataArchiveProcessor.import(frameAccount, selfId, importState)
            eventTimer.emit("account")
            frameCount++
          }

          frameRecipient != null -> {
            RecipientArchiveProcessor.import(frameRecipient, importState)
            eventTimer.emit("recipient")
            frameCount++
          }

          frameChat != null -> {
            ChatArchiveProcessor.import(frameChat, importState)
            eventTimer.emit("chat")
            frameCount++
          }

          frameAdHocCall != null -> {
            AdHocCallArchiveProcessor.import(frameAdHocCall, importState)
            eventTimer.emit("call")
            frameCount++
          }

          frameStickerPack != null -> {
            StickerArchiveProcessor.import(frameStickerPack)
            eventTimer.emit("sticker-pack")
            frameCount++
          }

          frameNotificationProfile != null -> {
            NotificationProfileArchiveProcessor.import(frameNotificationProfile, importState)
            eventTimer.emit("notification-profile")
            frameCount++
          }

          frameChatFolder != null -> {
            ChatFolderArchiveProcessor.import(frameChatFolder, importState)
            eventTimer.emit("chat-folder")
            frameCount++
          }

          frameChatItem != null -> {
            chatItemInserter.import(frameChatItem)
            eventTimer.emit("chatItem")
            frameCount++

            if (frameCount % 1000 == 0) {
              if (cancellationSignal()) {
                return ImportResult.Failure
              }
              Log.d(TAG, "Imported $frameCount frames so far.")
            }
            // TODO if there's stuff in the stream after chatItems, we need to flush the inserter before going to the next phase
          }

          else -> Log.w(TAG, "Unrecognized frame")
        }
        EventBus.getDefault().post(RestoreV2Event(RestoreV2Event.Type.PROGRESS_RESTORE, frameReader.getBytesRead().bytes, totalLength.bytes))
      }

      if (chatItemInserter.flush()) {
        eventTimer.emit("chatItem")
      }

      EventBus.getDefault().post(RestoreV2Event(RestoreV2Event.Type.PROGRESS_FINALIZING, 0.bytes, 0.bytes))

      if (!importState.importedChatFolders) {
        // Add back default All Chats chat folder after clearing data if missing
        SignalDatabase.chatFolders.insertAllChatFolder()
      }

      stopwatch.split("frames")

      Log.d(TAG, "[import] Remove duplicate messages...")
      SignalDatabase.messages.removeDuplicatesPostBackupRestore()

      Log.d(TAG, "[import] Rebuilding FTS index...")
      SignalDatabase.messageSearch.rebuildIndex()

      Log.d(TAG, "[import] --- Recreating indices ---")
      for (index in indexMetadata) {
        Log.d(TAG, "[import] Creating index ${index.name}...")
        SignalDatabase.rawDatabase.execSQL(index.statement)
      }
      stopwatch.split("recreate-indices")

      Log.d(TAG, "[import] --- Recreating triggers ---")
      for (trigger in triggerMetadata) {
        Log.d(TAG, "[import] Creating trigger ${trigger.name}...")
        SignalDatabase.rawDatabase.execSQL(trigger.statement)
      }
      stopwatch.split("recreate-triggers")

      Log.d(TAG, "[import] Updating threads...")
      importState.chatIdToLocalThreadId.values.forEach {
        SignalDatabase.threads.update(it, unarchive = false, allowDeletion = false)
      }
      stopwatch.split("thread-updates")

      val foreignKeyViolations = SignalDatabase.rawDatabase.getForeignKeyViolations()
      if (foreignKeyViolations.isNotEmpty()) {
        throw IllegalStateException("Foreign key check failed! Violations: $foreignKeyViolations")
      }
      stopwatch.split("fk-check")

      SignalDatabase.rawDatabase.setTransactionSuccessful()
      transactionSuccessful = true
    } finally {
      if (SignalDatabase.rawDatabase.inTransaction()) {
        SignalDatabase.rawDatabase.endTransaction()
      }

      if (!transactionSuccessful) {
        Log.w(TAG, "[import] Transaction failed, clearing release channel recipient ID from key-value store.")
        SignalStore.releaseChannel.clearReleaseChannelRecipientId()
      }

      Log.d(TAG, "[import] Re-enabling foreign keys...")
      SignalDatabase.rawDatabase.forceForeignKeyConstraintsEnabled(true)
    }

    SignalDatabase.remappedRecords.clearCache()
    SignalDatabase.remappedRecords.trimStaleMappings()
    AppDependencies.recipientCache.clear()
    AppDependencies.recipientCache.warmUp()
    SignalDatabase.threads.clearCache()

    if (SignalStore.svr.pin?.isNotBlank() == true) {
      AppDependencies.jobManager.add(ResetSvrGuessCountJob())
    }

    val stickerJobs = SignalDatabase.stickers.getAllStickerPacks().use { cursor ->
      val reader = StickerTables.StickerPackRecordReader(cursor)
      reader
        .filter { it.isInstalled }
        .map {
          StickerPackDownloadJob.forInstall(it.packId, it.packKey, false)
        }
    }
    AppDependencies.jobManager.addAll(stickerJobs)
    stopwatch.split("sticker-jobs")

    val recipientIds = SignalDatabase.threads.getRecentConversationList(
      limit = RECENT_RECIPIENTS_MAX,
      includeInactiveGroups = false,
      individualsOnly = true,
      groupsOnly = false,
      hideV1Groups = true,
      hideSms = true,
      hideSelf = true
    ).use {
      val recipientSet = mutableSetOf<RecipientId>()
      while (it.moveToNext()) {
        recipientSet.add(RecipientId.from(CursorUtil.requireLong(it, ThreadTable.RECIPIENT_ID)))
      }
      recipientSet
    }

    RetrieveProfileJob.enqueue(recipientIds, skipDebounce = false)
    stopwatch.split("profile-jobs")

    AppDependencies.jobManager.add(CreateReleaseChannelJob.create())

    val groupJobs = SignalDatabase.groups.getGroups().use { groups ->
      val jobs = mutableListOf<Job>()
      groups
        .asSequence()
        .filter { it.id.isV2 && it.hasV2GroupProperties }
        .forEach { group ->
          jobs.add(RequestGroupV2InfoJob(group.id as GroupId.V2))
          val avatarKey = group.requireV2GroupProperties().avatarKey
          if (avatarKey.isNotEmpty()) {
            jobs.add(AvatarGroupsV2DownloadJob(group.id.requireV2(), avatarKey))
          }
        }
      jobs
    }
    AppDependencies.jobManager.addAll(groupJobs)
    stopwatch.split("group-jobs")

    AppDependencies.jobManager.add(BackfillCollapsedMessageJob())

    SignalStore.backup.firstAppVersion = header.firstAppVersion
    SignalStore.internal.importedBackupDebugInfo = header.debugInfo.let { BackupDebugInfo.ADAPTER.decodeOrNull(it.toByteArray()) }

    Log.d(TAG, "[import] Finished! ${eventTimer.stop().summary}")
    stopwatch.stop(TAG)

    return ImportResult.Success(backupTime = header.backupTimeMs, selfRecipientId = selfId)
  }

  /**
   * Grabs the backup tier we think the user is on without performing any kind of authentication clearing
   * on a 403 error. Ensures we can check without rolling the user back during the BackupSubscriptionCheckJob.
   */
  fun getBackupTierWithoutDowngrade(): Either<ArchiveError.CredentialError, MessageBackupTier> {
    if (!SignalStore.backup.areBackupsEnabled) {
      return ArchiveError.CredentialError.NotFound(NonSuccessfulResponseCodeException(404)).left()
    }

    return runBlocking { archiveService.getBackupLevelWithoutDowngrade() }.map { it.toMessageBackupTier() }
  }

  /**
   * If backups are enabled, sync with the network. Otherwise, return a 404.
   * Used in instrumentation tests.
   *
   * Note that this will set the user's backup tier to FREE if they are not on PAID, so avoid this method if you don't intend that to be the case.
   */
  fun getBackupTier(): Either<ArchiveError.CredentialError, MessageBackupTier> {
    if (!SignalStore.backup.areBackupsEnabled) {
      return ArchiveError.CredentialError.NotFound(NonSuccessfulResponseCodeException(404)).left()
    }

    return runBlocking { archiveService.getBackupLevel() }.map { it.toMessageBackupTier() }
  }

  fun enablePaidBackupTier() {
    Log.i(TAG, "Setting backup tier to PAID", true)
    resetInitializedStateAndAuthCredentials()
    SignalStore.backup.backupTier = MessageBackupTier.PAID
    SignalStore.backup.lastCheckInMillis = System.currentTimeMillis()
    SignalStore.backup.lastCheckInSnoozeMillis = 0
    SignalStore.backup.clearDownloadNotifierState()
    scheduleSyncForAccountChange()
  }

  fun downloadBackupFile(destination: File, listener: ProgressListener? = null): Either<ArchiveError.BackupFileError, Unit> {
    return runBlocking { archiveService.getMessageBackupFileLocation() }
      .flatMap { location ->
        NetworkResult.fromFetch {
          AppDependencies.signalServiceMessageReceiver.retrieveBackup(location.cdn, location.cdnCredentials, location.path, destination, listener)
        }.toArchiveResult()
      }
  }

  fun getBackupFileLastModified(): Either<ArchiveError.BackupFileError, ZonedDateTime> {
    return runBlocking { archiveService.getMessageBackupFileLocation() }
      .flatMap { location -> location.getLastModified() }
  }

  /**
   * Stores the remote backup's last-modified time in [BackupValues.lastBackupTime].
   */
  fun refreshBackupFileTimestamp(): Either<ArchiveError.BackupFileError, ZonedDateTime> {
    return getBackupFileLastModified()
      .onRight { SignalStore.backup.lastBackupTime = it.toMillis() }
      .onLeft { error ->
        when (error) {
          is ArchiveError.CredentialError.NotFound,
          is ArchiveError.CredentialError.Unauthorized -> {
            SignalStore.backup.lastBackupTime = 0L
          }
          is ArchiveError.EntitlementError.NotEntitled,
          is ArchiveError.CredentialError.InvalidRequest,
          is ArchiveError.CredentialError.RateLimited,
          is ArchiveError.BackupFileError.UnexpectedResponse,
          is ArchiveError.CredentialError.ZkVerificationFailed,
          is ArchiveError.NetworkError,
          is ArchiveError.ApplicationError -> {
            Log.w(TAG, "Failed to refresh last backup time from remote: ${error::class.simpleName}")
          }
        }
      }
  }

  /**
   * Returns if an attachment should be copied to the archive if it meets certain requirements eg
   * not a story, not already uploaded to the archive cdn, not a preuploaded attachment, etc.
   */
  @JvmStatic
  fun shouldCopyAttachmentToArchive(attachmentId: AttachmentId, messageId: Long): Boolean {
    if (!SignalStore.backup.backsUpMedia) {
      return false
    }

    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)

    return when {
      attachment == null -> false
      attachment.archiveTransferState == AttachmentTable.ArchiveTransferState.FINISHED -> false
      !DatabaseAttachmentArchiveUtil.hadIntegrityCheckPerformed(attachment) -> false
      messageId == AttachmentTable.PREUPLOAD_MESSAGE_ID -> false
      SignalDatabase.messages.isStory(messageId) -> false
      SignalDatabase.messages.isViewOnce(messageId) -> false
      SignalDatabase.messages.willMessageExpireBeforeCutoff(messageId) -> false
      else -> true
    }
  }

  /**
   * Copies an attachment that has been uploaded to the transit cdn to the archive cdn, recording the cdn it landed on.
   */
  suspend fun copyAttachmentToArchive(attachment: DatabaseAttachment): Either<ArchiveError.CopyMediaError, Unit> {
    return archiveService.copyToArchive(
      cdnNumber = attachment.cdn.cdnNumber,
      remoteLocation = attachment.remoteLocation!!,
      plaintextSize = attachment.size,
      mediaName = attachment.requireMediaName()
    )
      .map { archiveCdn -> SignalDatabase.attachments.setArchiveCdn(attachmentId = attachment.attachmentId, archiveCdn = archiveCdn) }
      .also { Log.i(TAG, "archiveMediaResult: ${it.describe()}") }
  }

  suspend fun debugDeleteAllArchivedMedia(): Either<ArchiveError.EntitlementError, Unit> {
    return archiveService
      .debugGetArchivedMediaState()
      .flatMap { archivedMedia ->
        archiveService.deleteArchivedMedia(
          archivedMedia
            .filter { it.cdn == Cdn.CDN_3.cdnNumber }
            .map { ArchivedMediaObject(mediaId = it.mediaId, cdn = it.cdn).toDeleteBackupMediaItem() }
        )
      }
      .map { SignalDatabase.attachments.clearAllArchiveData() }
      .also { Log.i(TAG, "debugDeleteAllArchivedMediaResult: ${it.describe()}") }
  }

  fun restoreBackupFileTimestamp(): RestoreTimestampResult {
    val result = getBackupFileLastModified().toRestoreTimestampResult()

    when (result) {
      is RestoreTimestampResult.Success -> {
        SignalStore.backup.lastBackupTime = result.timestamp
        SignalStore.backup.isBackupTimestampRestored = true
        SignalStore.uiHints.markHasEverEnabledRemoteBackups()
      }

      RestoreTimestampResult.NotFound, RestoreTimestampResult.BackupsNotEnabled -> {
        SignalStore.backup.lastBackupTime = 0L
        SignalStore.backup.isBackupTimestampRestored = true
      }

      else -> Unit
    }

    return result
  }

  fun verifyBackupKeyAssociatedWithAccount(aci: ACI, aep: AccountEntropyPool): RestoreTimestampResult {
    Log.i(TAG, "Verifying enter aep is associated with account")
    val result: RestoreTimestampResult = getBackupTimestampToVerifyAepAssociatedWithAccountAndHasBackup(aci, aep)

    if (result !is RestoreTimestampResult.VerificationFailure) {
      return result
    }

    Log.w(TAG, "Resetting backup id reservation due to zk verification failure")

    return when (val triggerResult = runBlocking { SignalNetwork.archiveV2.triggerBackupIdReservation(aep.deriveMessageBackupKey(), null, aci) }) {
      is RequestResult.Success -> {
        Log.i(TAG, "Reset successful, retrying aep verification")
        SignalStore.backup.messageCredentials.clearAll()
        getBackupTimestampToVerifyAepAssociatedWithAccountAndHasBackup(aci, aep)
      }

      is RequestResult.NonSuccess -> when (val error = triggerResult.error) {
        is ArchiveApiV2.SetBackupIdError.RateLimited -> {
          Log.w(TAG, "Rate limited when resetting backup id, failing operation")
          RestoreTimestampResult.RateLimited(error.retryAfter)
        }

        ArchiveApiV2.SetBackupIdError.InvalidCredential -> {
          Log.w(TAG, "Reset backup id rejected the credential, failing operation")
          result
        }

        ArchiveApiV2.SetBackupIdError.Unauthorized -> {
          Log.w(TAG, "Reset backup id rejected our account auth, failing operation")
          result
        }
      }

      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Reset backup id hit a network error, failing operation", triggerResult.networkError)
        result
      }

      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Reset backup id failed, failing operation", triggerResult.cause)
        result
      }
    }
  }

  private fun getBackupTimestampToVerifyAepAssociatedWithAccountAndHasBackup(aci: ACI, aep: AccountEntropyPool): RestoreTimestampResult {
    return runBlocking { archiveService.getMessageBackupFileLocationForKey(aci, aep.deriveMessageBackupKey()) }
      .flatMap { location -> location.getLastModified() }
      .toRestoreTimestampResult()
  }

  suspend fun getBackupTypes(availableBackupTiers: List<MessageBackupTier>): List<MessageBackupsType> {
    return availableBackupTiers.mapNotNull {
      val type = getBackupsType(it)

      if (type is NetworkResult.Success) type.result else null
    }
  }

  private suspend fun getBackupsType(tier: MessageBackupTier): NetworkResult<out MessageBackupsType> {
    return when (tier) {
      MessageBackupTier.FREE -> getFreeType()
      MessageBackupTier.PAID -> getPaidType()
    }
  }

  @WorkerThread
  fun getBackupLevelConfiguration(): NetworkResult<SubscriptionsConfiguration.BackupLevelConfiguration> {
    return AppDependencies.donationsService
      .getDonationsConfiguration(Locale.getDefault())
      .toNetworkResult()
      .then {
        val config = it.backupConfiguration.backupLevelConfigurationMap[SubscriptionsConfiguration.BACKUPS_LEVEL]
        if (config != null) {
          NetworkResult.Success(config)
        } else {
          NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
        }
      }
  }

  @WorkerThread
  fun getFreeType(): NetworkResult<MessageBackupsType.Free> {
    return AppDependencies.donationsService
      .getDonationsConfiguration(Locale.getDefault())
      .toNetworkResult()
      .map {
        MessageBackupsType.Free(
          mediaRetentionDays = it.backupConfiguration.freeTierMediaDays
        )
      }
  }

  suspend fun getPaidType(): NetworkResult<MessageBackupsType.Paid> {
    val productPrice: FiatMoney? = if (SignalStore.backup.backupTierInternalOverride == MessageBackupTier.PAID) {
      Log.d(TAG, "Accessing price via mock subscription.")
      RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP).successOrNull()?.activeSubscription?.let {
        FiatMoney.fromSignalNetworkAmount(it.amount, Currency.getInstance(it.currency))
      }
    } else if (AppDependencies.billingApi.getApiAvailability().isSuccess) {
      Log.d(TAG, "Accessing price via billing api.")
      AppDependencies.billingApi.queryProduct()?.price
    } else {
      FiatMoney(BigDecimal.ZERO, SignalStore.inAppPayments.getRecurringDonationCurrency())
    }

    if (productPrice == null) {
      Log.w(TAG, "No pricing available. Exiting.")
      return NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
    }

    return getBackupLevelConfiguration()
      .map {
        MessageBackupsType.Paid(
          pricePerMonth = productPrice,
          storageAllowanceBytes = it.storageAllowanceBytes,
          mediaTtl = it.mediaTtlDays.days
        )
      }
  }

  internal fun scheduleSyncForAccountChange() {
    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  private fun File.deleteAllFilesWithPrefix(prefix: String) {
    this.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
  }

  data class SelfData(
    val aci: ACI,
    val pni: PNI,
    val e164: String,
    val profileKey: ProfileKey
  )

  suspend fun restoreRemoteBackup(): RemoteRestoreResult {
    val context = AppDependencies.application
    ArchiveRestoreProgress.onRestorePending()

    try {
      DataRestoreConstraint.isRestoringData = true
      return withContext(Dispatchers.IO) {
        val result = BackupProgressService.start(context, context.getString(R.string.BackupProgressService_title)).use {
          restoreRemoteBackup(controller = it, cancellationSignal = { !isActive })
        }
        if (result !is RemoteRestoreResult.Success) {
          ArchiveRestoreProgress.onRestoreFailed()
        }
        return@withContext result
      }
    } finally {
      DataRestoreConstraint.isRestoringData = false
    }
  }

  private suspend fun restoreRemoteBackup(controller: BackupProgressService.Controller, cancellationSignal: () -> Boolean): RemoteRestoreResult {
    ArchiveRestoreProgress.onRestoringDb()

    val progressListener = object : ProgressListener {
      override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
        controller.update(
          title = AppDependencies.application.getString(R.string.BackupProgressService_title_downloading),
          progress = progress.value,
          indeterminate = false
        )
        EventBus.getDefault().post(RestoreV2Event(RestoreV2Event.Type.PROGRESS_DOWNLOAD, progress.transmitted, progress.total))
      }

      override fun shouldCancel() = cancellationSignal()
    }

    Log.i(TAG, "[remoteRestore] Downloading backup")
    val tempBackupFile = AppDependencies.blobs.forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)
    when (val result = downloadBackupFile(tempBackupFile, progressListener)) {
      is Either.Right -> Log.i(TAG, "[remoteRestore] Download successful")
      is Either.Left -> {
        Log.w(TAG, "[remoteRestore] Failed to download backup file", result.value.cause)
        return result.value.toRemoteRestoreFailure()
      }
    }

    if (cancellationSignal()) {
      return RemoteRestoreResult.Canceled
    }

    controller.update(
      title = AppDependencies.application.getString(R.string.BackupProgressService_title),
      progress = 0f,
      indeterminate = true
    )

    val forwardSecrecyMetadata = EncryptedBackupReader.readForwardSecrecyMetadata(tempBackupFile.inputStream())
    if (forwardSecrecyMetadata == null) {
      Log.w(TAG, "Failed to read forward secrecy metadata!")
      return RemoteRestoreResult.Failure
    }

    val messageBackupKey = SignalStore.backup.messageBackupKey

    Log.i(TAG, "[remoteRestore] Fetching SVRB data")
    val svrBAuth = when (val result = archiveService.getSvrBAuth()) {
      is Either.Right -> result.value
      is Either.Left -> when (val error = result.value) {
        is ArchiveError.ApplicationError -> throw error.exception
        else -> return error.toRemoteRestoreFailure().logW(TAG, "[remoteRestore] Failed to get SVRB auth: ${error::class.simpleName}", error.cause)
      }
    }

    val forwardSecrecyToken = when (val result = SignalNetwork.svrB.restore(svrBAuth, messageBackupKey, forwardSecrecyMetadata)) {
      is SvrBApi.RestoreResult.Success -> {
        SignalStore.backup.nextBackupSecretData = result.data.nextBackupSecretData
        result.data.forwardSecrecyToken
      }

      is SvrBApi.RestoreResult.NetworkError -> {
        Log.w(TAG, "[remoteRestore] Network error during SVRB.", result.exception)
        return RemoteRestoreResult.NetworkError
      }

      is SvrBApi.RestoreResult.RestoreFailedError,
      SvrBApi.RestoreResult.InvalidDataError -> {
        Log.w(TAG, "[remoteRestore] Permanent SVRB error! $result")
        return RemoteRestoreResult.PermanentSvrBFailure
      }

      SvrBApi.RestoreResult.DataMissingError,
      is SvrBApi.RestoreResult.SvrError -> {
        Log.w(TAG, "[remoteRestore] Failed to fetch SVRB data: $result")
        return RemoteRestoreResult.Failure
      }

      is SvrBApi.RestoreResult.UnknownError -> {
        Log.e(TAG, "[remoteRestore] Unknown SVRB result! Crashing.", result.throwable)
        throw result.throwable
      }
    }

    val self = Recipient.self()
    val selfData = SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))
    Log.i(TAG, "[remoteRestore] Importing backup")
    val result = importSignalBackup(
      length = tempBackupFile.length(),
      inputStreamFactory = tempBackupFile::inputStream,
      selfData = selfData,
      backupKey = SignalStore.backup.messageBackupKey,
      forwardSecrecyToken = forwardSecrecyToken,
      cancellationSignal = cancellationSignal
    )

    return when (result) {
      is ImportResult.Failure -> {
        Log.w(TAG, "[remoteRestore] Failed to import backup")
        RemoteRestoreResult.Failure
      }

      is ImportResult.Success -> {
        Log.i(TAG, "[remoteRestore] Restore successful")
        BackupMediaRestoreService.resetTimeout()
        AppDependencies.jobManager.add(BackupRestoreMediaJob())
        RemoteRestoreResult.Success(result.selfRecipientId)
      }
    }
  }

  suspend fun restoreLinkAndSyncBackup(response: TransferArchiveResponse, ephemeralBackupKey: MessageBackupKey): RemoteRestoreResult {
    val context = AppDependencies.application
    ArchiveRestoreProgress.onRestorePending()

    try {
      DataRestoreConstraint.isRestoringData = true
      return withContext(Dispatchers.IO) {
        return@withContext BackupProgressService.start(context, context.getString(RegistrationR.string.MessageSyncScreen__syncing_messages)).use {
          restoreLinkAndSyncBackup(response, ephemeralBackupKey, controller = it, cancellationSignal = { !isActive })
        }
      }
    } finally {
      DataRestoreConstraint.isRestoringData = false
    }
  }

  private fun restoreLinkAndSyncBackup(response: TransferArchiveResponse, ephemeralBackupKey: MessageBackupKey, controller: BackupProgressService.Controller, cancellationSignal: () -> Boolean): RemoteRestoreResult {
    ArchiveRestoreProgress.onRestoringDb()

    val progressListener = object : ProgressListener {
      override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
        controller.update(
          title = AppDependencies.application.getString(RegistrationR.string.MessageSyncScreen__syncing_messages),
          progress = progress.value,
          indeterminate = false
        )
        EventBus.getDefault().post(RestoreV2Event(RestoreV2Event.Type.PROGRESS_DOWNLOAD, progress.transmitted, progress.total))
      }

      override fun shouldCancel() = cancellationSignal()
    }

    val cdn = response.cdn
    val key = response.key
    if (cdn == null || key == null) {
      Log.w(TAG, "[restoreLinkAndSyncBackup] Response has no archive location (error=${response.error}); nothing to download.")
      return RemoteRestoreResult.Failure
    }

    Log.i(TAG, "[restoreLinkAndSyncBackup] Downloading backup")
    val tempBackupFile = AppDependencies.blobs.forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)
    when (val result = AppDependencies.signalServiceMessageReceiver.retrieveLinkAndSyncBackup(cdn, key, tempBackupFile, progressListener)) {
      is NetworkResult.Success -> Log.i(TAG, "[restoreLinkAndSyncBackup] Download successful")
      else -> {
        Log.w(TAG, "[restoreLinkAndSyncBackup] Failed to download backup file", result.getCause())
        return RemoteRestoreResult.NetworkError
      }
    }

    if (cancellationSignal()) {
      return RemoteRestoreResult.Canceled
    }

    controller.update(
      title = AppDependencies.application.getString(RegistrationR.string.MessageSyncScreen__syncing_messages),
      progress = 0f,
      indeterminate = true
    )

    val self = Recipient.self()
    val selfData = SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))
    Log.i(TAG, "[restoreLinkAndSyncBackup] Importing backup")
    val result = importLinkAndSyncSignalBackup(
      length = tempBackupFile.length(),
      inputStreamFactory = tempBackupFile::inputStream,
      selfData = selfData,
      backupKey = ephemeralBackupKey,
      cancellationSignal = cancellationSignal
    )

    return when (result) {
      is ImportResult.Failure -> {
        Log.w(TAG, "[restoreLinkAndSyncBackup] Failed to import backup")
        RemoteRestoreResult.Failure
      }

      is ImportResult.Success -> {
        Log.i(TAG, "[restoreLinkAndSyncBackup] Restore successful")
        BackupMediaRestoreService.resetTimeout()
        AppDependencies.jobManager.add(BackupRestoreMediaJob())
        RemoteRestoreResult.Success(result.selfRecipientId)
      }
    }
  }

  private fun buildDebugInfo(): ByteString {
    if (!RemoteConfig.internalUser) {
      return ByteString.EMPTY
    }

    var debuglogUrl: String? = null

    if (SignalStore.internal.includeDebuglogInBackup) {
      Log.i(TAG, "User has debuglog inclusion enabled. Generating a debuglog.")
      val latch = CountDownLatch(1)
      SubmitDebugLogRepository().buildAndSubmitLog { url ->
        debuglogUrl = url.getOrNull()
        latch.countDown()
      }

      try {
        val success = latch.await(10, TimeUnit.SECONDS)
        if (!success) {
          Log.w(TAG, "Timed out waiting for debuglog!")
        }
      } catch (e: Exception) {
        Log.w(TAG, "Hit an error while generating the debuglog!")
      }
    }

    return BackupDebugInfo(
      debuglogUrl = debuglogUrl ?: "",
      attachmentDetails = SignalDatabase.attachments.debugAttachmentStatsForBackupProto(),
      usingPaidTier = SignalStore.backup.backupTier == MessageBackupTier.PAID
    ).encodeByteString()
  }

  suspend fun getRemoteBackupForwardSecrecyMetadata(): Either<ArchiveError.BackupFileError, ByteArray?> {
    return archiveService.getMessageBackupFileLocation()
      .flatMap { location ->
        val headers = location.cdnCredentials.toMutableMap().apply {
          this["range"] = "bytes=0-${EncryptedBackupReader.BACKUP_SECRET_METADATA_UPPERBOUND - 1}"
        }

        AppDependencies.signalServiceMessageReceiver
          .retrieveBackupForwardSecretMetadataBytes(location.cdn, headers, location.path, EncryptedBackupReader.BACKUP_SECRET_METADATA_UPPERBOUND)
          .toArchiveResult()
      }
      .map { bytes -> EncryptedBackupReader.readForwardSecrecyMetadata(ByteArrayInputStream(bytes)) }
  }

  private fun BackupLevel.toMessageBackupTier(): MessageBackupTier {
    return if (this == BackupLevel.PAID) MessageBackupTier.PAID else MessageBackupTier.FREE
  }

  private fun ArchiveService.BackupFileLocation.getLastModified(): Either<ArchiveError.BackupFileError, ZonedDateTime> {
    return NetworkResult
      .fromFetch { AppDependencies.signalServiceMessageReceiver.getCdnLastModifiedTime(cdn, cdnCredentials, path) }
      .toArchiveResult()
  }

  private fun Either<ArchiveError.BackupFileError, ZonedDateTime>.toRestoreTimestampResult(): RestoreTimestampResult {
    return fold(
      ifRight = { RestoreTimestampResult.Success(it.toMillis()) },
      ifLeft = { error ->
        when (error) {
          is ArchiveError.CredentialError.NotFound -> {
            Log.i(TAG, "No backup file exists")
            RestoreTimestampResult.NotFound
          }
          is ArchiveError.CredentialError.Unauthorized -> {
            Log.i(TAG, "Backups not enabled")
            RestoreTimestampResult.BackupsNotEnabled
          }
          is ArchiveError.CredentialError.ZkVerificationFailed -> {
            Log.w(TAG, "Entered AEP fails zk verification", error.exception)
            RestoreTimestampResult.VerificationFailure
          }
          is ArchiveError.EntitlementError.NotEntitled,
          is ArchiveError.CredentialError.InvalidRequest,
          is ArchiveError.CredentialError.RateLimited,
          is ArchiveError.BackupFileError.UnexpectedResponse,
          is ArchiveError.NetworkError,
          is ArchiveError.ApplicationError -> {
            Log.w(TAG, "Could not check for backup file: ${error::class.simpleName}", error.cause)
            RestoreTimestampResult.Failure
          }
        }
      }
    )
  }

  private fun Either<ArchiveError, *>.describe(): String {
    return fold(ifRight = { "Success" }, ifLeft = { it::class.simpleName ?: "Error" })
  }

  /**
   * Whether a failed restore step is worth telling the user to check their connection over.
   *
   * [RemoteRestoreResult.NetworkError] drives "couldn't reach the server, try again" messaging, so only genuinely transient failures may map to it -- a rejected
   * credential or a missing backup is a [RemoteRestoreResult.Failure] no amount of retrying fixes.
   */
  private fun ArchiveError.BackupFileError.toRemoteRestoreFailure(): RemoteRestoreResult {
    return when (this) {
      is ArchiveError.NetworkError,
      is ArchiveError.CredentialError.RateLimited -> {
        RemoteRestoreResult.NetworkError
      }
      is ArchiveError.CredentialError.Unauthorized,
      is ArchiveError.EntitlementError.NotEntitled,
      is ArchiveError.CredentialError.NotFound,
      is ArchiveError.CredentialError.InvalidRequest,
      is ArchiveError.BackupFileError.UnexpectedResponse,
      is ArchiveError.CredentialError.ZkVerificationFailed,
      is ArchiveError.ApplicationError -> {
        RemoteRestoreResult.Failure
      }
    }
  }

  interface ExportProgressListener {
    fun onAccount()
    fun onRecipient()
    fun onThread()
    fun onCall()
    fun onSticker()
    fun onNotificationProfile()
    fun onChatFolder()
    fun onMessage(currentProgress: Long, approximateCount: Long)
    fun onAttachment(currentProgress: Long, totalCount: Long)
  }
}

data class ResumableMessagesBackupUploadSpec(
  val attachmentUploadForm: AttachmentUploadForm,
  val resumableUri: String
)

data class ArchivedMediaObject(val mediaId: String, val cdn: Int) {
  fun toDeleteBackupMediaItem(): DeleteBackupMediaItem {
    return DeleteBackupMediaItem(mediaId = MediaId(mediaId).value, cdn = cdn)
  }
}

class ExportState(
  val backupTime: Long,
  val backupMode: BackupMode,
  val selfRecipientId: RecipientId
) {
  val recipientIds: MutableSet<Long> = hashSetOf()
  val threadIds: MutableSet<Long> = hashSetOf()
  val contactRecipientIds: MutableSet<Long> = hashSetOf()
  val groupRecipientIds: MutableSet<Long> = hashSetOf()
  val threadIdToRecipientId: MutableMap<Long, Long> = hashMapOf()
  val recipientIdToAci: MutableMap<Long, ByteString> = hashMapOf()
  val aciToRecipientId: MutableMap<String, Long> = hashMapOf()
  val recipientIdToE164: MutableMap<Long, Long> = hashMapOf()
  val customChatColorIds: MutableSet<Long> = hashSetOf()
  var releaseNoteRecipientId: Long? = null
}

class ImportState(val mediaRootBackupKey: MediaRootBackupKey, val backupMode: BackupMode) {
  val remoteToLocalRecipientId: MutableMap<Long, RecipientId> = hashMapOf()
  val chatIdToLocalThreadId: MutableMap<Long, Long> = hashMapOf()
  val chatIdToLocalRecipientId: MutableMap<Long, RecipientId> = hashMapOf()
  val chatIdToBackupRecipientId: MutableMap<Long, Long> = hashMapOf()
  val remoteToLocalColorId: MutableMap<Long, Long> = hashMapOf()
  val recipientIdToLocalThreadId: MutableMap<RecipientId, Long> = hashMapOf()
  val recipientIdToIsGroup: MutableMap<RecipientId, Boolean> = hashMapOf()

  private var chatFolderPosition: Int = 0
  val importedChatFolders: Boolean
    get() = chatFolderPosition > 0

  fun requireLocalRecipientId(remoteId: Long): RecipientId {
    return remoteToLocalRecipientId[remoteId] ?: throw IllegalArgumentException("There is no local recipientId for remote recipientId $remoteId!")
  }

  fun getNextChatFolderPosition(): Int {
    return chatFolderPosition++
  }
}

data class StagedBackupKeyRotations(
  val aep: AccountEntropyPool,
  val mediaRootBackupKey: MediaRootBackupKey
)

sealed class ImportResult {
  data class Success(val backupTime: Long, val selfRecipientId: RecipientId) : ImportResult()
  data object Failure : ImportResult()
}

sealed interface RemoteRestoreResult {
  data class Success(val selfRecipientId: RecipientId) : RemoteRestoreResult
  data object NetworkError : RemoteRestoreResult
  data object Canceled : RemoteRestoreResult
  data object Failure : RemoteRestoreResult

  /** SVRB has failed in such a way that recovering a backup is impossible. */
  data object PermanentSvrBFailure : RemoteRestoreResult
}

sealed interface RestoreTimestampResult {
  data class Success(val timestamp: Long) : RestoreTimestampResult
  data object NotFound : RestoreTimestampResult
  data object BackupsNotEnabled : RestoreTimestampResult
  data object VerificationFailure : RestoreTimestampResult
  data class RateLimited(val retryAfter: Duration?) : RestoreTimestampResult
  data object Failure : RestoreTimestampResult
}

enum class BackupMode {
  REMOTE,
  LINK_SYNC,
  LOCAL,
  PLAINTEXT_EXPORT;

  val isLinkAndSync: Boolean
    get() = this == LINK_SYNC

  val isLocalBackup: Boolean
    get() = this == LOCAL

  val isPlaintextExport: Boolean
    get() = this == PLAINTEXT_EXPORT
}

/**
 * Iterator that reads values from the given cursor. Expects that REMOTE_DIGEST is present and non-null, and ARCHIVE_CDN is present.
 *
 * This class does not assume ownership of the cursor. Recommended usage is within a use statement:
 *
 * ```
 * databaseCall().use { cursor ->
 *   val iterator = ArchivedMediaObjectIterator(cursor)
 *   // Use the iterator...
 * }
 * // Cursor is closed after use block.
 * ```
 */
class ArchiveMediaItemIterator(private val cursor: Cursor) : Iterator<ArchiveMediaItem> {

  init {
    cursor.moveToFirst()
  }

  override fun hasNext(): Boolean = !cursor.isAfterLast

  override fun next(): ArchiveMediaItem {
    val plaintextHash = cursor.requireNonNullString(AttachmentTable.DATA_HASH_END).decodeBase64OrThrow()
    val remoteKey = cursor.requireNonNullString(AttachmentTable.REMOTE_KEY).decodeBase64OrThrow()
    val cdn = cursor.requireIntOrNull(AttachmentTable.ARCHIVE_CDN)
    val quote = cursor.requireBoolean(AttachmentTable.QUOTE)
    val contentType = cursor.requireString(AttachmentTable.CONTENT_TYPE)

    val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode()
    val thumbnailMediaId = MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode()

    cursor.moveToNext()

    return ArchiveMediaItem(
      mediaId = mediaId,
      thumbnailMediaId = thumbnailMediaId,
      cdn = cdn,
      plaintextHash = plaintextHash,
      remoteKey = remoteKey,
      quote = quote,
      contentType = contentType
    )
  }
}

data class UploadedThumbnailInfo(
  val cdnNumber: Int,
  val remoteLocation: String,
  val size: Long
)
