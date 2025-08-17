/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.app.PendingIntent
import android.database.Cursor
import android.os.Environment
import android.os.StatFs
import androidx.annotation.CheckResult
import androidx.annotation.Discouraged
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.Base64
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.signal.core.util.ByteSize
import org.signal.core.util.CursorUtil
import org.signal.core.util.EventTimer
import org.signal.core.util.PendingIntentFlags.cancelCurrent
import org.signal.core.util.Stopwatch
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.LimitedWorker
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.concurrent.SignalExecutors
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
import org.signal.core.util.requireIntOrNull
import org.signal.core.util.requireNonNullString
import org.signal.core.util.stream.NonClosingOutputStream
import org.signal.core.util.urlEncode
import org.signal.core.util.withinTransaction
import org.signal.libsignal.messagebackup.BackupForwardSecrecyToken
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.backups.BackupLevel
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.BackupRepository.copyAttachmentToArchive
import org.thoughtcrime.securesms.backup.v2.BackupRepository.exportForDebugging
import org.thoughtcrime.securesms.backup.v2.importer.ChatItemArchiveImporter
import org.thoughtcrime.securesms.backup.v2.processor.AccountDataArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.AdHocCallArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatFolderProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatItemArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.NotificationProfileProcessor
import org.thoughtcrime.securesms.backup.v2.processor.RecipientArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.StickerArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.proto.BackupDebugInfo
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.stream.BackupExportWriter
import org.thoughtcrime.securesms.backup.v2.stream.BackupImportReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupWriter
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupWriter
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlert
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable.ArchiveMediaItem
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.KyberPreKeyTable
import org.thoughtcrime.securesms.database.OneTimePreKeyTable
import org.thoughtcrime.securesms.database.SearchTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignedPreKeyTable
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.DataRestoreConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.WifiConstraint
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob
import org.thoughtcrime.securesms.jobs.BackupDeleteJob
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.jobs.CreateReleaseChannelJob
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.jobs.RestoreAttachmentJob
import org.thoughtcrime.securesms.jobs.RestoreOptimizedMediaJob
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.keyvalue.BackupValues.ArchiveServiceCredentials
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.BackupProgressService
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.toMillis
import org.whispersystems.signalservice.api.AccountEntropyPool
import org.whispersystems.signalservice.api.ApplicationErrorAction
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.StatusCodeErrorAction
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse
import org.whispersystems.signalservice.api.archive.ArchiveMediaRequest
import org.whispersystems.signalservice.api.archive.ArchiveMediaResponse
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccess
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccessPair
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.DeleteArchivedMediaRequest
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.link.TransferArchiveResponse
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.svr.SvrBApi
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.ZonedDateTime
import java.util.Currency
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object BackupRepository {

  private val TAG = Log.tag(BackupRepository::class.java)
  private const val VERSION = 1L
  private const val REMOTE_MAIN_DB_SNAPSHOT_NAME = "remote-signal-snapshot"
  private const val REMOTE_KEYVALUE_DB_SNAPSHOT_NAME = "remote-signal-key-value-snapshot"
  private const val LOCAL_MAIN_DB_SNAPSHOT_NAME = "local-signal-snapshot"
  private const val LOCAL_KEYVALUE_DB_SNAPSHOT_NAME = "local-signal-key-value-snapshot"
  private const val RECENT_RECIPIENTS_MAX = 50
  private val MANUAL_BACKUP_NOTIFICATION_THRESHOLD = 30.days

  private val resetInitializedStateErrorAction: StatusCodeErrorAction = { error ->
    when (error.code) {
      401 -> {
        Log.w(TAG, "Received status 401. Resetting initialized state + auth credentials.", error.exception)
        resetInitializedStateAndAuthCredentials()
      }

      403 -> {
        if (SignalStore.backup.backupTierInternalOverride != null) {
          Log.w(TAG, "Received status 403, but the internal override is set, so not doing anything.", error.exception)
        } else {
          Log.w(TAG, "Received status 403. The user is not in the media tier. Updating local state.", error.exception)
          if (SignalStore.backup.backupTier == MessageBackupTier.PAID) {
            Log.w(TAG, "Local device thought it was on PAID tier. Downgrading to FREE tier.")
            SignalStore.backup.backupTier = MessageBackupTier.FREE
            SignalStore.backup.backupExpiredAndDowngraded = true
            scheduleSyncForAccountChange()
          }

          SignalStore.uiHints.markHasEverEnabledRemoteBackups()
        }
      }
    }
  }

  private val clearAuthCredentials: ApplicationErrorAction = { error ->
    if (error.getCause() is VerificationFailedException) {
      Log.w(TAG, "Unable to verify/receive credentials, clearing cache to fetch new.", error.getCause())
      SignalStore.backup.messageCredentials.clearAll()
      SignalStore.backup.mediaCredentials.clearAll()
    }
  }

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
    BackupMessagesJob.enqueue()
  }

  fun resetInitializedStateAndAuthCredentials() {
    SignalStore.backup.backupsInitialized = false
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
   * Triggers backup id reservation. As documented, this is safe to perform multiple times.
   */
  @WorkerThread
  fun triggerBackupIdReservation(): NetworkResult<Unit> {
    val messageBackupKey = SignalStore.backup.messageBackupKey
    val mediaRootBackupKey = SignalStore.backup.mediaRootBackupKey
    return SignalNetwork.archive.triggerBackupIdReservation(messageBackupKey, mediaRootBackupKey, SignalStore.account.requireAci())
  }

  /**
   * Refreshes backup via server
   */
  fun refreshBackup(): NetworkResult<Unit> {
    Log.d(TAG, "Refreshing backup...")

    Log.d(TAG, "Fetching backup auth credential.")
    val credentialResult = initBackupAndFetchAuth()
    if (credentialResult.getCause() != null) {
      Log.w(TAG, "Failed to access backup auth.", credentialResult.getCause())
      return credentialResult.map { Unit }
    }

    val credential = credentialResult.successOrThrow()

    Log.d(TAG, "Fetched backup auth credential. Fetching backup tier.")

    val backupTierResult = getBackupTier()
    if (backupTierResult.getCause() != null) {
      Log.w(TAG, "Failed to access backup tier.", backupTierResult.getCause())
      return backupTierResult.map { Unit }
    }

    val backupTier = backupTierResult.successOrThrow()

    Log.d(TAG, "Fetched backup tier. Refreshing message backup access.")
    val messageBackupAccessResult = AppDependencies.archiveApi.refreshBackup(
      aci = SignalStore.account.requireAci(),
      archiveServiceAccess = credential.messageBackupAccess
    )

    if (messageBackupAccessResult.getCause() != null) {
      Log.d(TAG, "Failed to refresh message backup access.", messageBackupAccessResult.getCause())
      return messageBackupAccessResult
    }

    Log.d(TAG, "Refreshed message backup access.")
    if (backupTier == MessageBackupTier.PAID) {
      Log.d(TAG, "Refreshing media backup access.")

      val mediaBackupAccessResult = AppDependencies.archiveApi.refreshBackup(
        aci = SignalStore.account.requireAci(),
        archiveServiceAccess = credential.mediaBackupAccess
      )

      if (mediaBackupAccessResult.getCause() != null) {
        Log.d(TAG, "Failed to refresh media backup access.", mediaBackupAccessResult.getCause())
      }

      Log.d(TAG, "Refreshed media backup access.")

      return mediaBackupAccessResult
    } else {
      return messageBackupAccessResult
    }
  }

  /**
   * Gets the free storage space in the device's data partition.
   */
  fun getFreeStorageSpace(): ByteSize {
    val statFs = StatFs(Environment.getDataDirectory().absolutePath)
    val free = (statFs.availableBlocksLong) * statFs.blockSizeLong

    return free.bytes
  }

  /**
   * Checks whether or not we do not have enough storage space for our remaining attachments to be downloaded.
   * Caller from the attachment / thumbnail download jobs.
   */
  fun checkForOutOfStorageError(tag: String): Boolean {
    val availableSpace = getFreeStorageSpace()
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
    SignalStore.backup.userManuallySkippedMediaRestore = true

    RestoreAttachmentJob.Queues.ALL.forEach { AppDependencies.jobManager.cancelAllInQueue(it) }
  }

  fun markBackupFailure() {
    SignalStore.backup.markMessageBackupFailure()
    ArchiveUploadProgress.onMainBackupFileUploadFailure()

    if (!SignalStore.backup.hasBackupBeenUploaded) {
      Log.w(TAG, "Failure of initial backup. Displaying notification.")
      displayInitialBackupFailureNotification()
    }
  }

  fun displayManualBackupNotCreatedInThresholdNotification() {
    if (SignalStore.backup.lastBackupTime <= 0) {
      return
    }

    val daysSinceLastBackup = (System.currentTimeMillis().milliseconds - SignalStore.backup.lastBackupTime.milliseconds).inWholeDays.toInt()
    val context = AppDependencies.application
    val pendingIntent = PendingIntent.getActivity(context, 0, AppSettingsActivity.remoteBackups(context), cancelCurrent())
    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.resources.getQuantityString(R.plurals.Notification_no_backup_for_d_days, daysSinceLastBackup, daysSinceLastBackup))
      .setContentText(context.resources.getQuantityString(R.plurals.Notification_you_have_not_completed_a_backup, daysSinceLastBackup, daysSinceLastBackup))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    ServiceUtil.getNotificationManager(context).notify(NotificationIds.MANUAL_BACKUP_NOT_CREATED, notification)
  }

  fun cancelManualBackupNotCreatedInThresholdNotification() {
    ServiceUtil.getNotificationManager(AppDependencies.application).cancel(NotificationIds.MANUAL_BACKUP_NOT_CREATED)
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
    SignalStore.backup.clearMessageBackupFailure()
    ServiceUtil.getNotificationManager(AppDependencies.application).cancel(NotificationIds.INITIAL_BACKUP_FAILED)
  }

  fun markOutOfRemoteStorageSpaceError() {
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

    SignalStore.backup.markNotEnoughRemoteStorageSpace()
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
    if (shouldNotDisplayBackupFailedMessaging() || !SignalStore.backup.hasBackupFailure) {
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
   * Whether the "Backup Failed" row should be displayed in settings.
   * Shown when the initial backup creation has failed
   */
  fun shouldDisplayBackupFailedSettingsRow(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return !SignalStore.backup.hasBackupBeenUploaded && SignalStore.backup.hasBackupFailure
  }

  /**
   * Whether the "Could not complete backup" row should be displayed in settings.
   * Shown when a new backup could not be created but there is an existing one already
   */
  fun shouldDisplayCouldNotCompleteBackupSettingsRow(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return SignalStore.backup.hasBackupBeenUploaded && SignalStore.backup.hasBackupFailure
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
   * Whether or not the "No backup" for manual backups should be displayed.
   * This should only be displayed after a set threshold has passed and the user
   * has set the MANUAL backups frequency.
   */
  fun shouldDisplayNoManualBackupForTimeoutSheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    if (SignalStore.backup.backupFrequency != BackupFrequency.MANUAL) {
      return false
    }

    if (SignalStore.backup.lastBackupTime <= 0) {
      return false
    }

    val isNetworkConstraintMet = if (SignalStore.backup.backupWithCellular) {
      NetworkConstraint.isMet(AppDependencies.application)
    } else {
      WifiConstraint.isMet(AppDependencies.application)
    }

    if (!isNetworkConstraintMet) {
      return false
    }

    val durationSinceLastBackup = System.currentTimeMillis().milliseconds - SignalStore.backup.lastBackupTime.milliseconds
    if (durationSinceLastBackup < MANUAL_BACKUP_NOTIFICATION_THRESHOLD) {
      return false
    }

    val display = !SignalStore.backup.isNoBackupForManualUploadNotified
    SignalStore.backup.isNoBackupForManualUploadNotified = false

    return display
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

    return !SignalStore.backup.hasBackupBeenUploaded && SignalStore.backup.hasBackupFailure && System.currentTimeMillis().milliseconds > SignalStore.backup.nextBackupFailureSheetSnoozeTime
  }

  /**
   * Whether or not the "Could not complete backup" sheet should be displayed.
   */
  @JvmStatic
  fun shouldDisplayCouldNotCompleteBackupSheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return SignalStore.backup.hasBackupBeenUploaded && System.currentTimeMillis().milliseconds > SignalStore.backup.nextBackupFailureSheetSnoozeTime
  }

  fun snoozeDownloadYourBackupData() {
    SignalStore.backup.snoozeDownloadNotifier()
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

    val remainingAttachmentSize = withContext(SignalDispatchers.IO) {
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

  private fun shouldNotDisplayBackupFailedMessaging(): Boolean {
    return !SignalStore.account.isRegistered || !RemoteConfig.messageBackups || !SignalStore.backup.areBackupsEnabled
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
        attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
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
      forTransfer = false
    ) { dbSnapshot ->
      val localArchivableAttachments = dbSnapshot
        .attachmentTable
        .getLocalArchivableAttachments()
        .associateBy { MediaName.fromPlaintextHashAndRemoteKey(it.plaintextHash, it.remoteKey) }

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
    progressEmitter: ExportProgressListener? = null,
    cancellationSignal: () -> Boolean = { false },
    extraExportOperations: ((SignalDatabase) -> Unit)?
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
      forTransfer = false,
      progressEmitter = progressEmitter,
      cancellationSignal = cancellationSignal,
      extraExportOperations = extraExportOperations
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
      forTransfer = true,
      progressEmitter = progressEmitter,
      cancellationSignal = cancellationSignal,
      extraExportOperations = null
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
    forTransfer: Boolean = false,
    progressEmitter: ExportProgressListener? = null,
    cancellationSignal: () -> Boolean = { false },
    extraExportOperations: ((SignalDatabase) -> Unit)? = null
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
      forTransfer = forTransfer,
      progressEmitter = progressEmitter,
      cancellationSignal = cancellationSignal,
      extraExportOperations = extraExportOperations
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
    forTransfer: Boolean,
    progressEmitter: ExportProgressListener?,
    cancellationSignal: () -> Boolean,
    extraExportOperations: ((SignalDatabase) -> Unit)?
  ) {
    val eventTimer = EventTimer()
    val mainDbName = if (isLocal) LOCAL_MAIN_DB_SNAPSHOT_NAME else REMOTE_MAIN_DB_SNAPSHOT_NAME
    val keyValueDbName = if (isLocal) LOCAL_KEYVALUE_DB_SNAPSHOT_NAME else REMOTE_KEYVALUE_DB_SNAPSHOT_NAME

    try {
      val dbSnapshot: SignalDatabase = createSignalDatabaseSnapshot(mainDbName)
      eventTimer.emit("main-db-snapshot")

      val signalStoreSnapshot: SignalStore = createSignalStoreSnapshot(keyValueDbName)
      eventTimer.emit("store-db-snapshot")

      val exportState = ExportState(backupTime = currentTime, forTransfer = forTransfer)
      val selfAci = signalStoreSnapshot.accountValues.aci!!
      val selfRecipientId = dbSnapshot.recipientTable.getByAci(selfAci).get().toLong().let { RecipientId.from(it) }

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
          AccountDataArchiveProcessor.export(dbSnapshot, signalStoreSnapshot) {
            writer.write(it)
            eventTimer.emit("account")
            frameCount++
          }
          if (cancellationSignal()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onRecipient()
          RecipientArchiveProcessor.export(dbSnapshot, signalStoreSnapshot, exportState, selfRecipientId, selfAci) {
            writer.write(it)
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
            eventTimer.emit("thread")
            frameCount++
          }
          if (cancellationSignal()) {
            return@export
          }

          progressEmitter?.onCall()
          AdHocCallArchiveProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
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
            eventTimer.emit("sticker-pack")
            frameCount++
          }
          if (cancellationSignal()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onNotificationProfile()
          NotificationProfileProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            eventTimer.emit("notification-profile")
            frameCount++
          }
          if (cancellationSignal()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onChatFolder()
          ChatFolderProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
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
          ChatItemArchiveProcessor.export(dbSnapshot, exportState, selfRecipientId, cancellationSignal) { frame ->
            writer.write(frame)
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

      extraExportOperations?.invoke(dbSnapshot)

      Log.d(TAG, "[export] totalFrames: $frameCount | ${eventTimer.stop().summary}")
    } finally {
      deleteDatabaseSnapshot(mainDbName)
      deleteDatabaseSnapshot(keyValueDbName)
    }
  }

  /**
   * Imports a local backup file that was exported to disk.
   */
  fun importLocal(mainStreamFactory: () -> InputStream, mainStreamLength: Long, selfData: SelfData): ImportResult {
    val backupKey = SignalStore.backup.messageBackupKey

    val frameReader = try {
      EncryptedBackupReader.createForLocalOrLinking(
        key = backupKey,
        aci = selfData.aci,
        length = mainStreamLength,
        dataStream = mainStreamFactory
      )
    } catch (e: IOException) {
      Log.w(TAG, "Unable to import local archive", e)
      return ImportResult.Failure
    }

    return frameReader.use { reader ->
      import(reader, selfData, cancellationSignal = { false })
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
      import(reader, selfData, cancellationSignal)
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
      import(reader, selfData, cancellationSignal)
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
      import(reader, selfData, cancellationSignal)
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
      import(reader, selfData, cancellationSignal)
    }
  }

  private fun import(
    frameReader: BackupImportReader,
    selfData: SelfData,
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
      val skipTables = setOf(KyberPreKeyTable.TABLE_NAME, OneTimePreKeyTable.TABLE_NAME, SignedPreKeyTable.TABLE_NAME)
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
      val selfId: RecipientId = SignalDatabase.recipients.getAndPossiblyMerge(selfData.aci, selfData.pni, selfData.e164, pniVerified = true, changeSelf = true)
      SignalDatabase.recipients.setProfileKey(selfId, selfData.profileKey)
      SignalDatabase.recipients.setProfileSharing(selfId, true)

      val importState = ImportState(mediaRootBackupKey)
      val chatItemInserter: ChatItemArchiveImporter = ChatItemArchiveProcessor.beginImport(importState)

      Log.d(TAG, "[import] Beginning to read frames.")
      val totalLength = frameReader.getStreamLength()
      var frameCount = 0
      for (frame in frameReader) {
        when {
          frame.account != null -> {
            AccountDataArchiveProcessor.import(frame.account, selfId, importState)
            eventTimer.emit("account")
            frameCount++
          }

          frame.recipient != null -> {
            RecipientArchiveProcessor.import(frame.recipient, importState)
            eventTimer.emit("recipient")
            frameCount++
          }

          frame.chat != null -> {
            ChatArchiveProcessor.import(frame.chat, importState)
            eventTimer.emit("chat")
            frameCount++
          }

          frame.adHocCall != null -> {
            AdHocCallArchiveProcessor.import(frame.adHocCall, importState)
            eventTimer.emit("call")
            frameCount++
          }

          frame.stickerPack != null -> {
            StickerArchiveProcessor.import(frame.stickerPack)
            eventTimer.emit("sticker-pack")
            frameCount++
          }

          frame.notificationProfile != null -> {
            NotificationProfileProcessor.import(frame.notificationProfile, importState)
            eventTimer.emit("notification-profile")
            frameCount++
          }

          frame.chatFolder != null -> {
            ChatFolderProcessor.import(frame.chatFolder, importState)
            eventTimer.emit("chat-folder")
            frameCount++
          }

          frame.chatItem != null -> {
            chatItemInserter.import(frame.chatItem)
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
    } finally {
      if (SignalDatabase.rawDatabase.inTransaction()) {
        SignalDatabase.rawDatabase.endTransaction()
      }

      Log.d(TAG, "[import] Re-enabling foreign keys...")
      SignalDatabase.rawDatabase.forceForeignKeyConstraintsEnabled(true)
    }

    AppDependencies.recipientCache.clear()
    AppDependencies.recipientCache.warmUp()
    SignalDatabase.threads.clearCache()

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

    AppDependencies.jobManager.add(CreateReleaseChannelJob.create())

    val groupJobs = SignalDatabase.groups.getGroups().use { groups ->
      val jobs = mutableListOf<Job>()
      groups
        .asSequence()
        .filter { it.id.isV2 }
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

    SignalStore.backup.firstAppVersion = header.firstAppVersion
    SignalStore.internal.importedBackupDebugInfo = header.debugInfo.let { BackupDebugInfo.ADAPTER.decodeOrNull(it.toByteArray()) }

    Log.d(TAG, "[import] Finished! ${eventTimer.stop().summary}")
    stopwatch.stop(TAG)

    return ImportResult.Success(backupTime = header.backupTimeMs)
  }

  fun listRemoteMediaObjects(limit: Int, cursor: String? = null): NetworkResult<ArchiveGetMediaItemsResponse> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getArchiveMediaItemsPage(SignalStore.account.requireAci(), credential.mediaBackupAccess, limit, cursor)
      }.runOnStatusCodeError {
        SignalStore.backup.mediaCredentials.clearAll()
      }
  }

  /**
   * Grabs the backup tier we think the user is on without performing any kind of authentication clearing
   * on a 403 error. Ensures we can check without rolling the user back during the BackupSubscriptionCheckJob.
   */
  fun getBackupTierWithoutDowngrade(): NetworkResult<MessageBackupTier> {
    return if (SignalStore.backup.areBackupsEnabled) {
      getArchiveServiceAccessPair()
        .then { credential ->
          val zkCredential = SignalNetwork.archive.getZkCredential(Recipient.self().requireAci(), credential.messageBackupAccess)
          val tier = if (zkCredential.backupLevel == BackupLevel.PAID) {
            MessageBackupTier.PAID
          } else {
            MessageBackupTier.FREE
          }

          NetworkResult.Success(tier)
        }
    } else {
      NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
    }
  }

  /**
   * If backups are enabled, sync with the network. Otherwise, return a 404.
   * Used in instrumentation tests.
   *
   * Note that this will set the user's backup tier to FREE if they are not on PAID, so avoid this method if you don't intend that to be the case.
   */
  fun getBackupTier(): NetworkResult<MessageBackupTier> {
    return if (SignalStore.backup.areBackupsEnabled) {
      getBackupTier(Recipient.self().requireAci())
    } else {
      NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
    }
  }

  fun enablePaidBackupTier() {
    Log.i(TAG, "Setting backup tier to PAID", true)
    SignalStore.backup.backupTier = MessageBackupTier.PAID
    SignalStore.backup.lastCheckInMillis = System.currentTimeMillis()
    SignalStore.backup.lastCheckInSnoozeMillis = 0
    SignalStore.backup.clearDownloadNotifierState()
    scheduleSyncForAccountChange()
  }

  /**
   * Grabs the backup tier for the given ACI. Note that this will set the user's backup
   * tier to FREE if they are not on PAID, so avoid this method if you don't intend that
   * to be the case.
   */
  private fun getBackupTier(aci: ACI): NetworkResult<MessageBackupTier> {
    return initBackupAndFetchAuth()
      .map { credential ->
        val zkCredential = SignalNetwork.archive.getZkCredential(aci, credential.messageBackupAccess)
        if (zkCredential.backupLevel == BackupLevel.PAID) {
          MessageBackupTier.PAID
        } else {
          MessageBackupTier.FREE
        }
      }
  }

  /**
   * Returns an object with details about the remote backup state.
   */
  fun debugGetRemoteBackupState(): NetworkResult<DebugBackupMetadata> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(SignalStore.account.requireAci(), credential.mediaBackupAccess)
          .map { it to credential }
      }
      .then { pair ->
        val (mediaBackupInfo, credential) = pair
        SignalNetwork.archive.debugGetUploadedMediaItemMetadata(SignalStore.account.requireAci(), credential.mediaBackupAccess)
          .map { mediaObjects ->
            DebugBackupMetadata(
              usedSpace = mediaBackupInfo.usedSpace ?: 0,
              mediaCount = mediaObjects.size.toLong(),
              mediaSize = mediaObjects.sumOf { it.objectLength }
            )
          }
      }
  }

  fun getResumableMessagesBackupUploadSpec(backupFileSize: Long): NetworkResult<ResumableMessagesBackupUploadSpec> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getMessageBackupUploadForm(SignalStore.account.requireAci(), credential.messageBackupAccess, backupFileSize)
          .also { Log.i(TAG, "UploadFormResult: ${it::class.simpleName}") }
      }
      .then { form ->
        SignalNetwork.archive.getBackupResumableUploadUrl(form)
          .also { Log.i(TAG, "ResumableUploadUrlResult: ${it::class.simpleName}") }
          .map { ResumableMessagesBackupUploadSpec(attachmentUploadForm = form, resumableUri = it) }
      }
  }

  fun downloadBackupFile(destination: File, listener: ProgressListener? = null): NetworkResult<Unit> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(SignalStore.account.requireAci(), credential.messageBackupAccess)
      }
      .then { info -> getCdnReadCredentials(CredentialType.MESSAGE, info.cdn ?: Cdn.CDN_3.cdnNumber).map { it.headers to info } }
      .map { pair ->
        val (cdnCredentials, info) = pair
        val messageReceiver = AppDependencies.signalServiceMessageReceiver
        messageReceiver.retrieveBackup(info.cdn!!, cdnCredentials, "backups/${info.backupDir}/${info.backupName}", destination, listener)
      }
  }

  fun getBackupFileLastModified(): NetworkResult<ZonedDateTime> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(SignalStore.account.requireAci(), credential.messageBackupAccess)
      }
      .then { info -> getCdnReadCredentials(CredentialType.MESSAGE, info.cdn ?: RemoteConfig.backupFallbackArchiveCdn).map { it.headers to info } }
      .then { pair ->
        val (cdnCredentials, info) = pair
        NetworkResult.fromFetch {
          AppDependencies.signalServiceMessageReceiver.getCdnLastModifiedTime(info.cdn!!, cdnCredentials, "backups/${info.backupDir}/${info.backupName}")
        }
      }
  }

  /**
   * Returns an object with details about the remote backup state.
   */
  fun debugGetArchivedMediaState(): NetworkResult<List<ArchiveGetMediaItemsResponse.StoredMediaObject>> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.debugGetUploadedMediaItemMetadata(SignalStore.account.requireAci(), credential.mediaBackupAccess)
      }
  }

  /**
   * Retrieves an [AttachmentUploadForm] that can be used to upload an attachment to the transit cdn.
   * To continue the upload, use [org.whispersystems.signalservice.api.attachment.AttachmentApi.getResumableUploadSpec].
   *
   * It's important to note that in order to get this to the archive cdn, you still need to use [copyAttachmentToArchive].
   */
  fun getAttachmentUploadForm(): NetworkResult<AttachmentUploadForm> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getMediaUploadForm(SignalStore.account.requireAci(), credential.mediaBackupAccess)
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
      SignalDatabase.messages.willMessageExpireBeforeCutoff(messageId) -> false
      else -> true
    }
  }

  /**
   * Copies a thumbnail that has been uploaded to the transit cdn to the archive cdn.
   */
  fun copyThumbnailToArchive(thumbnailAttachment: Attachment, parentAttachment: DatabaseAttachment): NetworkResult<ArchiveMediaResponse> {
    return initBackupAndFetchAuth()
      .then { credential ->
        val request = thumbnailAttachment.toArchiveMediaRequest(parentAttachment.requireThumbnailMediaName(), credential.mediaBackupAccess.backupKey)

        SignalNetwork.archive.copyAttachmentToArchive(
          aci = SignalStore.account.requireAci(),
          archiveServiceAccess = credential.mediaBackupAccess,
          item = request
        )
      }
  }

  /**
   * Copies an attachment that has been uploaded to the transit cdn to the archive cdn.
   */
  fun copyAttachmentToArchive(attachment: DatabaseAttachment): NetworkResult<Unit> {
    return initBackupAndFetchAuth()
      .then { credential ->
        val mediaName = attachment.requireMediaName()
        val request = attachment.toArchiveMediaRequest(mediaName, credential.mediaBackupAccess.backupKey)
        SignalNetwork.archive
          .copyAttachmentToArchive(
            aci = SignalStore.account.requireAci(),
            archiveServiceAccess = credential.mediaBackupAccess,
            item = request
          )
      }
      .map { response ->
        SignalDatabase.attachments.setArchiveCdn(attachmentId = attachment.attachmentId, archiveCdn = response.cdn)
      }
      .also { Log.i(TAG, "archiveMediaResult: ${it::class.simpleName}") }
  }

  fun deleteAbandonedMediaObjects(mediaObjects: Collection<ArchivedMediaObject>): NetworkResult<Unit> {
    val mediaToDelete = mediaObjects
      .map {
        DeleteArchivedMediaRequest.ArchivedMediaObject(
          cdn = it.cdn,
          mediaId = it.mediaId
        )
      }
      .filter { it.cdn == Cdn.CDN_3.cdnNumber }

    if (mediaToDelete.isEmpty()) {
      Log.i(TAG, "No media to delete, quick success")
      return NetworkResult.Success(Unit)
    }

    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.deleteArchivedMedia(
          aci = SignalStore.account.requireAci(),
          archiveServiceAccess = credential.mediaBackupAccess,
          mediaToDelete = mediaToDelete
        )
      }
      .also { Log.i(TAG, "deleteAbandonedMediaObjectsResult: ${it::class.simpleName}") }
  }

  fun deleteBackup(): NetworkResult<Unit> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.deleteBackup(SignalStore.account.requireAci(), credential.messageBackupAccess)
      }
  }

  fun deleteMediaBackup(): NetworkResult<Unit> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.deleteBackup(SignalStore.account.requireAci(), credential.mediaBackupAccess)
      }
  }

  fun debugDeleteAllArchivedMedia(): NetworkResult<Unit> {
    val itemLimit = 1000
    return debugGetArchivedMediaState()
      .then { archivedMedia ->
        val mediaChunksToDelete = archivedMedia
          .map {
            DeleteArchivedMediaRequest.ArchivedMediaObject(
              cdn = it.cdn,
              mediaId = it.mediaId
            )
          }
          .filter { it.cdn == Cdn.CDN_3.cdnNumber }
          .chunked(itemLimit)

        if (mediaChunksToDelete.isEmpty()) {
          Log.i(TAG, "No media to delete, quick success")
          return@then NetworkResult.Success(Unit)
        }

        getArchiveServiceAccessPair()
          .then processChunks@{ credential ->
            mediaChunksToDelete.forEachIndexed { index, chunk ->
              val result = SignalNetwork.archive.deleteArchivedMedia(
                aci = SignalStore.account.requireAci(),
                archiveServiceAccess = credential.mediaBackupAccess,
                mediaToDelete = chunk
              )

              if (result !is NetworkResult.Success) {
                Log.w(TAG, "Error occurred while deleting archived media chunk #$index: $result")
                return@processChunks result
              }
            }
            NetworkResult.Success(Unit)
          }
      }
      .map {
        SignalDatabase.attachments.clearAllArchiveData()
      }
      .also { Log.i(TAG, "debugDeleteAllArchivedMediaResult: ${it::class.simpleName}") }
  }

  /**
   * Retrieve credentials for reading from the backup cdn.
   */
  fun getCdnReadCredentials(credentialType: CredentialType, cdnNumber: Int): NetworkResult<GetArchiveCdnCredentialsResponse> {
    val credentialStore = when (credentialType) {
      CredentialType.MESSAGE -> SignalStore.backup.messageCredentials
      CredentialType.MEDIA -> SignalStore.backup.mediaCredentials
    }

    val cached = credentialStore.cdnReadCredentials
    if (cached != null) {
      return NetworkResult.Success(cached)
    }

    return initBackupAndFetchAuth()
      .then { credential ->
        val archiveServiceAccess = when (credentialType) {
          CredentialType.MESSAGE -> credential.messageBackupAccess
          CredentialType.MEDIA -> credential.mediaBackupAccess
        }

        SignalNetwork.archive.getCdnReadCredentials(
          cdnNumber = cdnNumber,
          aci = SignalStore.account.requireAci(),
          archiveServiceAccess = archiveServiceAccess
        )
      }
      .also {
        if (it is NetworkResult.Success) {
          credentialStore.cdnReadCredentials = it.result
        }
      }
      .also { Log.i(TAG, "getCdnReadCredentialsResult: ${it::class.simpleName}") }
  }

  fun restoreBackupFileTimestamp(): RestoreTimestampResult {
    val timestampResult: NetworkResult<ZonedDateTime> = getBackupFileLastModified()

    when {
      timestampResult is NetworkResult.Success -> {
        SignalStore.backup.lastBackupTime = timestampResult.result.toMillis()
        SignalStore.backup.isBackupTimestampRestored = true
        SignalStore.uiHints.markHasEverEnabledRemoteBackups()
        return RestoreTimestampResult.Success(SignalStore.backup.lastBackupTime)
      }

      timestampResult is NetworkResult.StatusCodeError && timestampResult.code == 404 -> {
        Log.i(TAG, "No backup file exists")
        SignalStore.backup.lastBackupTime = 0L
        SignalStore.backup.isBackupTimestampRestored = true
        return RestoreTimestampResult.NotFound
      }

      else -> {
        Log.w(TAG, "Could not check for backup file.", timestampResult.getCause())
        return RestoreTimestampResult.Failure
      }
    }
  }

  fun verifyBackupKeyAssociatedWithAccount(aci: ACI, aep: AccountEntropyPool): MessageBackupTier? {
    val currentTime = System.currentTimeMillis()
    val messageBackupKey = aep.deriveMessageBackupKey()

    val result: NetworkResult<MessageBackupTier> = SignalNetwork.archive.getServiceCredentials(currentTime)
      .then { result ->
        val credential: ArchiveServiceCredential? = ArchiveServiceCredentials(result.messageCredentials.associateBy { it.redemptionTime }).getForCurrentTime(currentTime.milliseconds)

        if (credential == null) {
          NetworkResult.ApplicationError(NullPointerException("No credential available for current time."))
        } else {
          NetworkResult.Success(
            ArchiveServiceAccess(
              credential = credential,
              backupKey = messageBackupKey
            )
          )
        }
      }
      .map { messageAccess ->
        val zkCredential = SignalNetwork.archive.getZkCredential(aci, messageAccess)
        if (zkCredential.backupLevel == BackupLevel.PAID) {
          MessageBackupTier.PAID
        } else {
          MessageBackupTier.FREE
        }
      }

    return if (result is NetworkResult.Success) {
      result.result
    } else {
      Log.i(TAG, "Unable to verify backup key", result.getCause())
      null
    }
  }

  /**
   * Retrieves media-specific cdn path, preferring cached value if available.
   *
   * This will change if the backup expires, a new backup-id is set, or the delete all endpoint is called.
   */
  fun getArchivedMediaCdnPath(): NetworkResult<String> {
    val cachedMediaPath = SignalStore.backup.cachedMediaCdnPath

    if (cachedMediaPath != null) {
      return NetworkResult.Success(cachedMediaPath)
    }

    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(SignalStore.account.requireAci(), credential.mediaBackupAccess).map {
          "${it.backupDir!!.urlEncode()}/${it.mediaDir!!.urlEncode()}"
        }
      }
      .also {
        if (it is NetworkResult.Success) {
          SignalStore.backup.cachedMediaCdnPath = it.result
        }
      }
  }

  suspend fun getAvailableBackupsTypes(availableBackupTiers: List<MessageBackupTier>): List<MessageBackupsType> {
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
    return AppDependencies.donationsApi
      .getDonationsConfiguration(Locale.getDefault())
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
    return AppDependencies.donationsApi
      .getDonationsConfiguration(Locale.getDefault())
      .map {
        MessageBackupsType.Free(
          mediaRetentionDays = it.backupConfiguration.freeTierMediaDays
        )
      }
  }

  suspend fun getPaidType(): NetworkResult<MessageBackupsType.Paid> {
    val productPrice: FiatMoney? = if (SignalStore.backup.backupTierInternalOverride == MessageBackupTier.PAID) {
      Log.d(TAG, "Accessing price via mock subscription.")
      RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP).getOrNull()?.activeSubscription?.let {
        FiatMoney.fromSignalNetworkAmount(it.amount, Currency.getInstance(it.currency))
      }
    } else {
      Log.d(TAG, "Accessing price via billing api.")
      AppDependencies.billingApi.queryProduct()?.price
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

  /**
   * See [org.whispersystems.signalservice.api.archive.ArchiveApi.getSvrBAuthorization].
   */
  fun getSvrBAuth(): NetworkResult<AuthCredentials> {
    return initBackupAndFetchAuth()
      .then { SignalNetwork.archive.getSvrBAuthorization(SignalStore.account.requireAci(), it.messageBackupAccess) }
  }

  /**
   * During normal operation, ensures that the backupId has been reserved and that your public key has been set,
   * while also returning an archive access data. Should be the basis of all backup operations.
   *
   * When called during registration before backups are initialized, will only fetch access data and not initialize backups. This
   * prevents early initialization with incorrect keys before we have restored them.
   */
  private fun initBackupAndFetchAuth(): NetworkResult<ArchiveServiceAccessPair> {
    return if (!RemoteConfig.messageBackups) {
      NetworkResult.StatusCodeError(555, null, null, emptyMap(), NonSuccessfulResponseCodeException(555, "Backups disabled!"))
    } else if (SignalStore.backup.backupsInitialized || SignalStore.account.isLinkedDevice) {
      getArchiveServiceAccessPair()
        .runOnStatusCodeError(resetInitializedStateErrorAction)
        .runOnApplicationError(clearAuthCredentials)
    } else if (isPreRestoreDuringRegistration()) {
      Log.w(TAG, "Requesting/using auth credentials in pre-restore state", Throwable())
      getArchiveServiceAccessPair()
    } else {
      val messageBackupKey = SignalStore.backup.messageBackupKey
      val mediaRootBackupKey = SignalStore.backup.mediaRootBackupKey

      return SignalNetwork.archive
        .triggerBackupIdReservation(messageBackupKey, mediaRootBackupKey, SignalStore.account.requireAci())
        .then { getArchiveServiceAccessPair() }
        .then { credential -> SignalNetwork.archive.setPublicKey(SignalStore.account.requireAci(), credential.messageBackupAccess).map { credential } }
        .then { credential -> SignalNetwork.archive.setPublicKey(SignalStore.account.requireAci(), credential.mediaBackupAccess).map { credential } }
        .runIfSuccessful { SignalStore.backup.backupsInitialized = true }
        .runOnStatusCodeError(resetInitializedStateErrorAction)
        .runOnApplicationError(clearAuthCredentials)
    }
  }

  /**
   * Retrieves an auth credential, preferring a cached value if available.
   */
  private fun getArchiveServiceAccessPair(): NetworkResult<ArchiveServiceAccessPair> {
    val currentTime = System.currentTimeMillis()

    val messageCredential = SignalStore.backup.messageCredentials.byDay.getForCurrentTime(currentTime.milliseconds)
    val mediaCredential = SignalStore.backup.mediaCredentials.byDay.getForCurrentTime(currentTime.milliseconds)

    if (messageCredential != null && mediaCredential != null) {
      return NetworkResult.Success(
        ArchiveServiceAccessPair(
          messageBackupAccess = ArchiveServiceAccess(messageCredential, SignalStore.backup.messageBackupKey),
          mediaBackupAccess = ArchiveServiceAccess(mediaCredential, SignalStore.backup.mediaRootBackupKey)
        )
      )
    }

    Log.w(TAG, "No credentials found for today, need to fetch new ones! This shouldn't happen under normal circumstances. We should ensure the routine fetch is running properly.")

    return SignalNetwork.archive.getServiceCredentials(currentTime).map { result ->
      SignalStore.backup.messageCredentials.add(result.messageCredentials)
      SignalStore.backup.messageCredentials.clearOlderThan(currentTime)

      SignalStore.backup.mediaCredentials.add(result.mediaCredentials)
      SignalStore.backup.mediaCredentials.clearOlderThan(currentTime)

      ArchiveServiceAccessPair(
        messageBackupAccess = ArchiveServiceAccess(SignalStore.backup.messageCredentials.byDay.getForCurrentTime(currentTime.milliseconds)!!, SignalStore.backup.messageBackupKey),
        mediaBackupAccess = ArchiveServiceAccess(SignalStore.backup.mediaCredentials.byDay.getForCurrentTime(currentTime.milliseconds)!!, SignalStore.backup.mediaRootBackupKey)
      )
    }
  }

  private fun isPreRestoreDuringRegistration(): Boolean {
    return !SignalStore.registration.isRegistrationComplete &&
      SignalStore.registration.restoreDecisionState.isDecisionPending &&
      RemoteConfig.restoreAfterRegistration
  }

  private fun scheduleSyncForAccountChange() {
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

  private fun Attachment.toArchiveMediaRequest(mediaName: MediaName, mediaRootBackupKey: MediaRootBackupKey): ArchiveMediaRequest {
    val mediaSecrets = mediaRootBackupKey.deriveMediaSecrets(mediaName)

    return ArchiveMediaRequest(
      sourceAttachment = ArchiveMediaRequest.SourceAttachment(
        cdn = cdn.cdnNumber,
        key = remoteLocation!!
      ),
      objectLength = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(size)).toInt(),
      mediaId = mediaSecrets.id.encode(),
      hmacKey = Base64.encodeWithPadding(mediaSecrets.macKey),
      encryptionKey = Base64.encodeWithPadding(mediaSecrets.aesKey)
    )
  }

  suspend fun restoreRemoteBackup(): RemoteRestoreResult {
    val context = AppDependencies.application
    SignalStore.backup.restoreState = RestoreState.PENDING

    try {
      DataRestoreConstraint.isRestoringData = true
      return withContext(Dispatchers.IO) {
        return@withContext BackupProgressService.start(context, context.getString(R.string.BackupProgressService_title)).use {
          restoreRemoteBackup(controller = it, cancellationSignal = { !isActive })
        }
      }
    } finally {
      DataRestoreConstraint.isRestoringData = false
    }
  }

  private fun restoreRemoteBackup(controller: BackupProgressService.Controller, cancellationSignal: () -> Boolean): RemoteRestoreResult {
    SignalStore.backup.restoreState = RestoreState.RESTORING_DB

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
    val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)
    when (val result = downloadBackupFile(tempBackupFile, progressListener)) {
      is NetworkResult.Success -> Log.i(TAG, "[remoteRestore] Download successful")
      else -> {
        Log.w(TAG, "[remoteRestore] Failed to download backup file", result.getCause())
        return RemoteRestoreResult.NetworkError
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
    val svrBAuth = when (val result = BackupRepository.getSvrBAuth()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.NetworkError -> return RemoteRestoreResult.NetworkError.logW(TAG, "[remoteRestore] Network error when getting SVRB auth.", result.getCause())
      is NetworkResult.StatusCodeError -> return RemoteRestoreResult.NetworkError.logW(TAG, "[remoteRestore] Status code error when getting SVRB auth.", result.getCause())
      is NetworkResult.ApplicationError -> throw result.throwable
    }

    val forwardSecrecyToken = when (val result = SignalNetwork.svrB.restore(svrBAuth, messageBackupKey, forwardSecrecyMetadata)) {
      is SvrBApi.RestoreResult.Success -> {
        SignalStore.backup.nextBackupSecretData = result.data.nextBackupSecretData
        result.data.forwardSecrecyToken
      }
      is SvrBApi.RestoreResult.NetworkError -> {
        return RemoteRestoreResult.NetworkError.logW(TAG, "[remoteRestore] Network error during SVRB.", result.exception)
      }
      SvrBApi.RestoreResult.DataMissingError,
      is SvrBApi.RestoreResult.RestoreFailedError,
      is SvrBApi.RestoreResult.SvrError,
      is SvrBApi.RestoreResult.UnknownError -> {
        Log.w(TAG, "[remoteRestore] Failed to fetch SVRB data: $result")
        return RemoteRestoreResult.Failure
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
    if (result == ImportResult.Failure) {
      Log.w(TAG, "[remoteRestore] Failed to import backup")
      return RemoteRestoreResult.Failure
    }

    SignalStore.backup.restoreState = RestoreState.RESTORING_MEDIA

    AppDependencies.jobManager.add(BackupRestoreMediaJob())

    Log.i(TAG, "[remoteRestore] Restore successful")
    return RemoteRestoreResult.Success
  }

  suspend fun restoreLinkAndSyncBackup(response: TransferArchiveResponse, ephemeralBackupKey: MessageBackupKey) {
    val context = AppDependencies.application
    SignalStore.backup.restoreState = RestoreState.PENDING

    try {
      DataRestoreConstraint.isRestoringData = true
      return withContext(Dispatchers.IO) {
        return@withContext BackupProgressService.start(context, context.getString(R.string.BackupProgressService_title)).use {
          restoreLinkAndSyncBackup(response, ephemeralBackupKey, controller = it, cancellationSignal = { !isActive })
        }
      }
    } finally {
      DataRestoreConstraint.isRestoringData = false
    }
  }

  private fun restoreLinkAndSyncBackup(response: TransferArchiveResponse, ephemeralBackupKey: MessageBackupKey, controller: BackupProgressService.Controller, cancellationSignal: () -> Boolean): RemoteRestoreResult {
    SignalStore.backup.restoreState = RestoreState.RESTORING_DB

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

    Log.i(TAG, "[restoreLinkAndSyncBackup] Downloading backup")
    val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)
    when (val result = AppDependencies.signalServiceMessageReceiver.retrieveLinkAndSyncBackup(response.cdn, response.key, tempBackupFile, progressListener)) {
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
      title = AppDependencies.application.getString(R.string.BackupProgressService_title),
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

    if (result == ImportResult.Failure) {
      Log.w(TAG, "[restoreLinkAndSyncBackup] Failed to import backup")
      return RemoteRestoreResult.Failure
    }

    SignalStore.backup.restoreState = RestoreState.RESTORING_MEDIA

    AppDependencies.jobManager.add(BackupRestoreMediaJob())

    Log.i(TAG, "[restoreLinkAndSyncBackup] Restore successful")
    return RemoteRestoreResult.Success
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

  enum class CredentialType {
    MESSAGE, MEDIA
  }
}

data class ResumableMessagesBackupUploadSpec(
  val attachmentUploadForm: AttachmentUploadForm,
  val resumableUri: String
)

data class ArchivedMediaObject(val mediaId: String, val cdn: Int)

class ExportState(
  val backupTime: Long,
  val forTransfer: Boolean
) {
  val recipientIds: MutableSet<Long> = hashSetOf()
  val threadIds: MutableSet<Long> = hashSetOf()
  val contactRecipientIds: MutableSet<Long> = hashSetOf()
  val groupRecipientIds: MutableSet<Long> = hashSetOf()
  val threadIdToRecipientId: MutableMap<Long, Long> = hashMapOf()
  val recipientIdToAci: MutableMap<Long, ByteString> = hashMapOf()
  val aciToRecipientId: MutableMap<String, Long> = hashMapOf()
}

class ImportState(val mediaRootBackupKey: MediaRootBackupKey) {
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

class DebugBackupMetadata(
  val usedSpace: Long,
  val mediaCount: Long,
  val mediaSize: Long
)

data class StagedBackupKeyRotations(
  val aep: AccountEntropyPool,
  val mediaRootBackupKey: MediaRootBackupKey
)

sealed class ImportResult {
  data class Success(val backupTime: Long) : ImportResult()
  data object Failure : ImportResult()
}

sealed interface RemoteRestoreResult {
  data object Success : RemoteRestoreResult
  data object NetworkError : RemoteRestoreResult
  data object Canceled : RemoteRestoreResult
  data object Failure : RemoteRestoreResult

  /** SVRB has failed in such a way that recovering a backup is impossible. */
  data object PermanentSvrBFailure : RemoteRestoreResult
}

sealed interface RestoreTimestampResult {
  data class Success(val timestamp: Long) : RestoreTimestampResult
  data object NotFound : RestoreTimestampResult
  data object Failure : RestoreTimestampResult
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

    val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode()
    val thumbnailMediaId = MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode()

    cursor.moveToNext()
    return ArchiveMediaItem(mediaId, thumbnailMediaId, cdn, plaintextHash, remoteKey)
  }
}
