/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.database.Cursor
import android.os.Environment
import android.os.StatFs
import androidx.annotation.Discouraged
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.Base64
import org.signal.core.util.ByteSize
import org.signal.core.util.EventTimer
import org.signal.core.util.Stopwatch
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.LimitedWorker
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.forceForeignKeyConstraintsEnabled
import org.signal.core.util.fullWalCheckpoint
import org.signal.core.util.getAllIndexDefinitions
import org.signal.core.util.getAllTableDefinitions
import org.signal.core.util.getAllTriggerDefinitions
import org.signal.core.util.getForeignKeyViolations
import org.signal.core.util.logging.Log
import org.signal.core.util.requireInt
import org.signal.core.util.requireNonNullString
import org.signal.core.util.stream.NonClosingOutputStream
import org.signal.core.util.urlEncode
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.backups.BackupLevel
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.importer.ChatItemArchiveImporter
import org.thoughtcrime.securesms.backup.v2.processor.AccountDataArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.AdHocCallArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatFolderProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatItemArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.NotificationProfileProcessor
import org.thoughtcrime.securesms.backup.v2.processor.RecipientArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.processor.StickerArchiveProcessor
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.stream.BackupExportWriter
import org.thoughtcrime.securesms.backup.v2.stream.BackupImportReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupWriter
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupWriter
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.SearchTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.jobs.RestoreAttachmentJob
import org.thoughtcrime.securesms.keyvalue.BackupValues.ArchiveServiceCredentials
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.toMillis
import org.whispersystems.signalservice.api.AccountEntropyPool
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
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

object BackupRepository {

  private val TAG = Log.tag(BackupRepository::class.java)
  private const val VERSION = 1L
  private const val REMOTE_MAIN_DB_SNAPSHOT_NAME = "remote-signal-snapshot"
  private const val REMOTE_KEYVALUE_DB_SNAPSHOT_NAME = "remote-signal-key-value-snapshot"
  private const val LOCAL_MAIN_DB_SNAPSHOT_NAME = "local-signal-snapshot"
  private const val LOCAL_KEYVALUE_DB_SNAPSHOT_NAME = "local-signal-key-value-snapshot"

  private val resetInitializedStateErrorAction: StatusCodeErrorAction = { error ->
    when (error.code) {
      401 -> {
        Log.w(TAG, "Received status 401. Resetting initialized state + auth credentials.", error.exception)
        SignalStore.backup.backupsInitialized = false
        SignalStore.backup.messageCredentials.clearAll()
        SignalStore.backup.mediaCredentials.clearAll()
        SignalStore.backup.cachedMediaCdnPath = null
      }

      403 -> {
        Log.w(TAG, "Received status 403. The user is not in the media tier. Updating local state.", error.exception)
        SignalStore.backup.backupTier = MessageBackupTier.FREE
        SignalStore.uiHints.markHasEverEnabledRemoteBackups()
        // TODO [backup] If the user thought they were in media tier but aren't, feels like we should have a special UX flow for this?
      }
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
    return initBackupAndFetchAuth()
      .then { accessPair ->
        AppDependencies.archiveApi.refreshBackup(
          aci = SignalStore.account.requireAci(),
          archiveServiceAccess = accessPair.messageBackupAccess
        )
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

  /**
   * Cancels any relevant jobs for media restore
   */
  @JvmStatic
  fun skipMediaRestore() {
    SignalStore.backup.userManuallySkippedMediaRestore = true

    AppDependencies.jobManager.cancelAllInQueue(RestoreAttachmentJob.constructQueueString(RestoreAttachmentJob.RestoreOperation.RESTORE_OFFLOADED))
    AppDependencies.jobManager.cancelAllInQueue(RestoreAttachmentJob.constructQueueString(RestoreAttachmentJob.RestoreOperation.INITIAL_RESTORE))
    AppDependencies.jobManager.cancelAllInQueue(RestoreAttachmentJob.constructQueueString(RestoreAttachmentJob.RestoreOperation.MANUAL))
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
   * Whether or not the "Backup failed" sheet should be displayed.
   * Should only be displayed if this is the failure of the initial backup creation.
   */
  @JvmStatic
  fun shouldDisplayBackupFailedSheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return !SignalStore.backup.hasBackupBeenUploaded && System.currentTimeMillis().milliseconds > SignalStore.backup.nextBackupFailureSheetSnoozeTime
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

  fun snoozeYourMediaWillBeDeletedTodaySheet() {
    SignalStore.backup.lastCheckInSnoozeMillis = System.currentTimeMillis()
  }

  /**
   * Whether or not the "Your media will be deleted today" sheet should be displayed.
   */
  suspend fun shouldDisplayYourMediaWillBeDeletedTodaySheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging() || !SignalStore.backup.hasBackupBeenUploaded || !SignalStore.backup.optimizeStorage) {
      return false
    }

    val paidType = try {
      withContext(Dispatchers.IO) {
        getPaidType()
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to retrieve paid type.", e)
      return false
    }

    if (paidType == null) {
      Log.w(TAG, "Paid type is not available on this device.")
      return false
    }

    val lastCheckIn = SignalStore.backup.lastCheckInMillis.milliseconds
    if (lastCheckIn == 0.milliseconds) {
      Log.w(TAG, "LastCheckIn has not yet been set.")
      return false
    }

    val lastSnoozeTime = SignalStore.backup.lastCheckInSnoozeMillis.milliseconds
    val now = System.currentTimeMillis().milliseconds
    val mediaTtl = paidType.mediaTtl
    val mediaExpiration = lastCheckIn + mediaTtl

    val isNowAfterSnooze = now < lastSnoozeTime || now >= lastSnoozeTime + 4.hours
    val isNowWithin24HoursOfMediaExpiration = now < mediaExpiration && (mediaExpiration - now) <= 1.days

    return isNowAfterSnooze && isNowWithin24HoursOfMediaExpiration
  }

  private fun shouldNotDisplayBackupFailedMessaging(): Boolean {
    return !RemoteConfig.messageBackups || !SignalStore.backup.areBackupsEnabled
  }

  /**
   * If the user is on a paid tier, this method will unsubscribe them from that tier.
   * It will then disable backups.
   *
   * Returns true if we were successful, false otherwise.
   */
  @WorkerThread
  fun turnOffAndDisableBackups(): Boolean {
    return try {
      Log.d(TAG, "Attempting to disable backups.")
      if (SignalStore.backup.backupTier == MessageBackupTier.PAID) {
        Log.d(TAG, "User is currently on a paid tier. Canceling.")
        RecurringInAppPaymentRepository.cancelActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP)
        Log.d(TAG, "Successfully canceled paid tier.")
      }

      Log.d(TAG, "Disabling backups.")
      SignalStore.backup.disableBackups()
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to turn off backups.", e)
      false
    }
  }

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

  private fun deleteDatabaseSnapshot(name: String) {
    AppDependencies.application.getDatabasePath("$name.db")
      .parentFile
      ?.deleteAllFilesWithPrefix(name)
  }

  fun localExport(
    main: OutputStream,
    localBackupProgressEmitter: ExportProgressListener,
    cancellationSignal: () -> Boolean = { false },
    archiveAttachment: (AttachmentTable.LocalArchivableAttachment, () -> InputStream?) -> Unit
  ) {
    val writer = EncryptedBackupWriter(
      key = SignalStore.backup.messageBackupKey,
      aci = SignalStore.account.aci!!,
      outputStream = NonClosingOutputStream(main),
      append = { main.write(it) }
    )

    export(currentTime = System.currentTimeMillis(), isLocal = true, writer = writer, progressEmitter = localBackupProgressEmitter, cancellationSignal = cancellationSignal) { dbSnapshot ->
      val localArchivableAttachments = dbSnapshot
        .attachmentTable
        .getLocalArchivableAttachments()
        .associateBy { MediaName.fromDigest(it.remoteDigest) }

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

  @JvmOverloads
  fun export(
    outputStream: OutputStream,
    append: (ByteArray) -> Unit,
    messageBackupKey: MessageBackupKey = SignalStore.backup.messageBackupKey,
    plaintext: Boolean = false,
    currentTime: Long = System.currentTimeMillis(),
    mediaBackupEnabled: Boolean = SignalStore.backup.backsUpMedia,
    forTransfer: Boolean = false,
    progressEmitter: ExportProgressListener? = null,
    cancellationSignal: () -> Boolean = { false },
    exportExtras: ((SignalDatabase) -> Unit)? = null
  ) {
    val writer: BackupExportWriter = if (plaintext) {
      PlainTextBackupWriter(outputStream)
    } else {
      EncryptedBackupWriter(
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
      progressEmitter = progressEmitter,
      mediaBackupEnabled = mediaBackupEnabled,
      forTransfer = forTransfer,
      cancellationSignal = cancellationSignal,
      exportExtras = exportExtras
    )
  }

  /**
   * Exports to a blob in memory. Should only be used for testing.
   */
  fun debugExport(plaintext: Boolean = false, currentTime: Long = System.currentTimeMillis()): ByteArray {
    val outputStream = ByteArrayOutputStream()
    export(outputStream = outputStream, append = { mac -> outputStream.write(mac) }, plaintext = plaintext, currentTime = currentTime)
    return outputStream.toByteArray()
  }

  private fun export(
    currentTime: Long,
    isLocal: Boolean,
    writer: BackupExportWriter,
    mediaBackupEnabled: Boolean = SignalStore.backup.backsUpMedia,
    forTransfer: Boolean = false,
    progressEmitter: ExportProgressListener? = null,
    cancellationSignal: () -> Boolean = { false },
    exportExtras: ((SignalDatabase) -> Unit)? = null
  ) {
    val eventTimer = EventTimer()
    val mainDbName = if (isLocal) LOCAL_MAIN_DB_SNAPSHOT_NAME else REMOTE_MAIN_DB_SNAPSHOT_NAME
    val keyValueDbName = if (isLocal) LOCAL_KEYVALUE_DB_SNAPSHOT_NAME else REMOTE_KEYVALUE_DB_SNAPSHOT_NAME

    try {
      val dbSnapshot: SignalDatabase = createSignalDatabaseSnapshot(mainDbName)
      eventTimer.emit("main-db-snapshot")

      val signalStoreSnapshot: SignalStore = createSignalStoreSnapshot(keyValueDbName)
      eventTimer.emit("store-db-snapshot")

      val exportState = ExportState(backupTime = currentTime, mediaBackupEnabled = mediaBackupEnabled, forTransfer = forTransfer)
      val selfAci = signalStoreSnapshot.accountValues.aci!!
      val selfRecipientId = dbSnapshot.recipientTable.getByAci(selfAci).get().toLong().let { RecipientId.from(it) }

      var frameCount = 0L

      writer.use {
        writer.write(
          BackupInfo(
            version = VERSION,
            backupTimeMs = exportState.backupTime,
            mediaRootBackupKey = SignalStore.backup.mediaRootBackupKey.value.toByteString(),
            firstAppVersion = SignalStore.backup.firstAppVersion
          )
        )
        frameCount++

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

      exportExtras?.invoke(dbSnapshot)

      Log.d(TAG, "[export] totalFrames: $frameCount | ${eventTimer.stop().summary}")
    } finally {
      deleteDatabaseSnapshot(mainDbName)
      deleteDatabaseSnapshot(keyValueDbName)
    }
  }

  fun localImport(mainStreamFactory: () -> InputStream, mainStreamLength: Long, selfData: SelfData): ImportResult {
    val backupKey = SignalStore.backup.messageBackupKey

    val frameReader = try {
      EncryptedBackupReader(
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
   * @param backupKey  The key used to encrypt the backup. If `null`, we assume that the file is plaintext.
   */
  fun import(
    length: Long,
    inputStreamFactory: () -> InputStream,
    selfData: SelfData,
    backupKey: MessageBackupKey?,
    cancellationSignal: () -> Boolean = { false }
  ): ImportResult {
    val frameReader = if (backupKey == null) {
      PlainTextBackupReader(inputStreamFactory(), length)
    } else {
      EncryptedBackupReader(
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
      val tableMetadata = SignalDatabase.rawDatabase.getAllTableDefinitions().filter { !it.name.startsWith(SearchTable.FTS_TABLE_NAME + "_") }
      for (table in tableMetadata) {
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

  fun getRemoteBackupUsedSpace(): NetworkResult<Long?> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(SignalStore.account.requireAci(), credential.mediaBackupAccess)
          .map { it.usedSpace }
      }
  }

  /**
   * If backups are enabled, sync with the network. Otherwise, return a 404.
   * Used in instrumentation tests.
   */
  fun getBackupTier(): NetworkResult<MessageBackupTier> {
    return if (SignalStore.backup.areBackupsEnabled) {
      getBackupTier(Recipient.self().requireAci())
    } else {
      NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
    }
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
  fun getRemoteBackupState(): NetworkResult<BackupMetadata> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(SignalStore.account.requireAci(), credential.mediaBackupAccess)
          .map { it to credential }
      }
      .then { pair ->
        val (mediaBackupInfo, credential) = pair
        SignalNetwork.archive.debugGetUploadedMediaItemMetadata(SignalStore.account.requireAci(), credential.mediaBackupAccess)
          .also { Log.i(TAG, "MediaItemMetadataResult: $it") }
          .map { mediaObjects ->
            BackupMetadata(
              usedSpace = mediaBackupInfo.usedSpace ?: 0,
              mediaCount = mediaObjects.size.toLong()
            )
          }
      }
  }

  fun getResumableMessagesBackupUploadSpec(): NetworkResult<ResumableMessagesBackupUploadSpec> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getMessageBackupUploadForm(SignalStore.account.requireAci(), credential.messageBackupAccess)
          .also { Log.i(TAG, "UploadFormResult: $it") }
      }
      .then { form ->
        SignalNetwork.archive.getBackupResumableUploadUrl(form)
          .also { Log.i(TAG, "ResumableUploadUrlResult: $it") }
          .map { ResumableMessagesBackupUploadSpec(attachmentUploadForm = form, resumableUri = it) }
      }
  }

  fun uploadBackupFile(
    resumableSpec: ResumableMessagesBackupUploadSpec,
    backupStream: InputStream,
    backupStreamLength: Long,
    progressListener: ProgressListener? = null
  ): NetworkResult<Unit> {
    val (form, resumableUploadUrl) = resumableSpec
    return SignalNetwork.archive.uploadBackupFile(form, resumableUploadUrl, backupStream, backupStreamLength, progressListener)
      .also { Log.i(TAG, "UploadBackupFileResult: $it") }
  }

  /**
   * A simple test method that just hits various network endpoints. Only useful for the playground.
   *
   * @return True if successful, otherwise false.
   */
  @Discouraged("This will upload the entire backup file on every execution.")
  fun debugUploadBackupFile(backupStream: InputStream, backupStreamLength: Long): NetworkResult<Unit> {
    return getResumableMessagesBackupUploadSpec()
      .then { formAndUploadUrl ->
        val (form, resumableUploadUrl) = formAndUploadUrl
        SignalNetwork.archive.uploadBackupFile(form, resumableUploadUrl, backupStream, backupStreamLength)
          .also { Log.i(TAG, "UploadBackupFileResult: $it") }
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

  fun getBackupFileLastModified(): NetworkResult<ZonedDateTime?> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(SignalStore.account.requireAci(), credential.messageBackupAccess)
      }
      .then { info -> getCdnReadCredentials(CredentialType.MESSAGE, info.cdn ?: Cdn.CDN_3.cdnNumber).map { it.headers to info } }
      .then { pair ->
        val (cdnCredentials, info) = pair
        val messageReceiver = AppDependencies.signalServiceMessageReceiver
        NetworkResult.fromFetch {
          messageReceiver.getCdnLastModifiedTime(info.cdn!!, cdnCredentials, "backups/${info.backupDir}/${info.backupName}")
        }
      }
  }

  /**
   * Returns an object with details about the remote backup state.
   */
  private fun debugGetArchivedMediaState(): NetworkResult<List<ArchiveGetMediaItemsResponse.StoredMediaObject>> {
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
   * Copies a thumbnail that has been uploaded to the transit cdn to the archive cdn.
   */
  fun copyThumbnailToArchive(thumbnailAttachment: Attachment, parentAttachment: DatabaseAttachment): NetworkResult<ArchiveMediaResponse> {
    return initBackupAndFetchAuth()
      .then { credential ->
        val request = thumbnailAttachment.toArchiveMediaRequest(parentAttachment.getThumbnailMediaName(), credential.mediaBackupAccess.backupKey)

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
        val mediaName = attachment.getMediaName()
        val request = attachment.toArchiveMediaRequest(mediaName, credential.mediaBackupAccess.backupKey)
        SignalNetwork.archive
          .copyAttachmentToArchive(
            aci = SignalStore.account.requireAci(),
            archiveServiceAccess = credential.mediaBackupAccess,
            item = request
          )
          .map { credential to Triple(mediaName, request.mediaId, it) }
      }
      .map { (credential, triple) ->
        val (mediaName, mediaId, response) = triple
        val thumbnailId = credential.mediaBackupAccess.backupKey.deriveMediaId(attachment.getThumbnailMediaName()).encode()
        SignalDatabase.attachments.setArchiveData(attachmentId = attachment.attachmentId, archiveCdn = response.cdn, archiveMediaName = mediaName.name, archiveMediaId = mediaId, archiveThumbnailMediaId = thumbnailId)
      }
      .also { Log.i(TAG, "archiveMediaResult: $it") }
  }

  fun copyAttachmentToArchive(databaseAttachments: List<DatabaseAttachment>): NetworkResult<BatchArchiveMediaResult> {
    return initBackupAndFetchAuth()
      .then { credential ->
        val requests = mutableListOf<ArchiveMediaRequest>()
        val mediaIdToAttachmentId = mutableMapOf<String, AttachmentId>()
        val attachmentIdToMediaName = mutableMapOf<AttachmentId, String>()

        databaseAttachments.forEach {
          val mediaName = it.getMediaName()
          val request = it.toArchiveMediaRequest(mediaName, credential.mediaBackupAccess.backupKey)
          requests += request
          mediaIdToAttachmentId[request.mediaId] = it.attachmentId
          attachmentIdToMediaName[it.attachmentId] = mediaName.name
        }

        SignalNetwork.archive
          .copyAttachmentToArchive(
            aci = SignalStore.account.requireAci(),
            archiveServiceAccess = credential.mediaBackupAccess,
            items = requests
          )
          .map { credential to BatchArchiveMediaResult(it, mediaIdToAttachmentId, attachmentIdToMediaName) }
      }
      .map { (credential, result) ->
        result
          .successfulResponses
          .forEach {
            val attachmentId = result.mediaIdToAttachmentId(it.mediaId)
            val mediaName = result.attachmentIdToMediaName(attachmentId)
            val thumbnailId = credential.mediaBackupAccess.backupKey.deriveMediaId(MediaName.forThumbnailFromMediaName(mediaName = mediaName)).encode()
            SignalDatabase.attachments.setArchiveData(attachmentId = attachmentId, archiveCdn = it.cdn!!, archiveMediaName = mediaName, archiveMediaId = it.mediaId, thumbnailId)
          }
        result
      }
      .also { Log.i(TAG, "archiveMediaResult: $it") }
  }

  fun deleteArchivedMedia(attachments: List<DatabaseAttachment>): NetworkResult<Unit> {
    val mediaToDelete = attachments
      .filter { it.archiveMediaId != null }
      .map {
        DeleteArchivedMediaRequest.ArchivedMediaObject(
          cdn = it.archiveCdn,
          mediaId = it.archiveMediaId!!
        )
      }

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
      .map {
        SignalDatabase.attachments.clearArchiveData(attachments.map { it.attachmentId })
      }
      .also { Log.i(TAG, "deleteArchivedMediaResult: $it") }
  }

  fun deleteAbandonedMediaObjects(mediaObjects: Collection<ArchivedMediaObject>): NetworkResult<Unit> {
    val mediaToDelete = mediaObjects
      .map {
        DeleteArchivedMediaRequest.ArchivedMediaObject(
          cdn = it.cdn,
          mediaId = it.mediaId
        )
      }

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
      .also { Log.i(TAG, "deleteAbandonedMediaObjectsResult: $it") }
  }

  fun deleteBackup(): NetworkResult<Unit> {
    return initBackupAndFetchAuth()
      .then { credential ->
        SignalNetwork.archive.deleteBackup(SignalStore.account.requireAci(), credential.messageBackupAccess)
      }
  }

  fun debugDeleteAllArchivedMedia(): NetworkResult<Unit> {
    return debugGetArchivedMediaState()
      .then { archivedMedia ->
        val mediaToDelete = archivedMedia
          .map {
            DeleteArchivedMediaRequest.ArchivedMediaObject(
              cdn = it.cdn,
              mediaId = it.mediaId
            )
          }

        if (mediaToDelete.isEmpty()) {
          Log.i(TAG, "No media to delete, quick success")
          NetworkResult.Success(Unit)
        } else {
          getArchiveServiceAccessPair()
            .then { credential ->
              SignalNetwork.archive.deleteArchivedMedia(
                aci = SignalStore.account.requireAci(),
                archiveServiceAccess = credential.mediaBackupAccess,
                mediaToDelete = mediaToDelete
              )
            }
        }
      }
      .map {
        SignalDatabase.attachments.clearAllArchiveData()
      }
      .also { Log.i(TAG, "debugDeleteAllArchivedMediaResult: $it") }
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
      .also { Log.i(TAG, "getCdnReadCredentialsResult: $it") }
  }

  fun restoreBackupTier(aci: ACI): MessageBackupTier? {
    val tierResult = getBackupTier(aci)
    when {
      tierResult is NetworkResult.Success -> {
        SignalStore.backup.backupTier = tierResult.result
        Log.d(TAG, "Backup tier restored: ${SignalStore.backup.backupTier}")
      }

      tierResult is NetworkResult.StatusCodeError && tierResult.code == 404 -> {
        Log.i(TAG, "Backups not enabled")
        SignalStore.backup.backupTier = null
      }

      else -> {
        Log.w(TAG, "Could not retrieve backup tier.", tierResult.getCause())
        return SignalStore.backup.backupTier
      }
    }

    SignalStore.backup.isBackupTierRestored = true

    if (SignalStore.backup.backupTier != null) {
      val timestampResult = getBackupFileLastModified()
      when {
        timestampResult is NetworkResult.Success -> {
          SignalStore.backup.lastBackupTime = timestampResult.result?.toMillis() ?: 0L
        }

        timestampResult is NetworkResult.StatusCodeError && timestampResult.code == 404 -> {
          Log.i(TAG, "No backup file exists")
          SignalStore.backup.lastBackupTime = 0L
        }

        else -> {
          Log.w(TAG, "Could not check for backup file.", timestampResult.getCause())
        }
      }

      SignalStore.uiHints.markHasEverEnabledRemoteBackups()
    }

    return SignalStore.backup.backupTier
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
          SignalStore.backup.usedBackupMediaSpace = it.usedSpace ?: 0L
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
    return availableBackupTiers.mapNotNull { getBackupsType(it) }
  }

  suspend fun getBackupsType(tier: MessageBackupTier): MessageBackupsType? {
    return when (tier) {
      MessageBackupTier.FREE -> getFreeType()
      MessageBackupTier.PAID -> getPaidType()
    }
  }

  private suspend fun getFreeType(): MessageBackupsType.Free {
    val config = getSubscriptionsConfiguration()

    return MessageBackupsType.Free(
      mediaRetentionDays = config.backupConfiguration.freeTierMediaDays
    )
  }

  private suspend fun getPaidType(): MessageBackupsType.Paid? {
    val config = getSubscriptionsConfiguration()
    val product = AppDependencies.billingApi.queryProduct() ?: return null
    val backupLevelConfiguration = config.backupConfiguration.backupLevelConfigurationMap[SubscriptionsConfiguration.BACKUPS_LEVEL] ?: return null

    return MessageBackupsType.Paid(
      pricePerMonth = product.price,
      storageAllowanceBytes = backupLevelConfiguration.storageAllowanceBytes,
      mediaTtl = backupLevelConfiguration.mediaTtlDays.days
    )
  }

  private suspend fun getSubscriptionsConfiguration(): SubscriptionsConfiguration {
    val serviceResponse = withContext(Dispatchers.IO) {
      AppDependencies
        .donationsService
        .getDonationsConfiguration(Locale.getDefault())
    }

    if (serviceResponse.result.isEmpty) {
      if (serviceResponse.applicationError.isPresent) {
        throw serviceResponse.applicationError.get()
      }

      if (serviceResponse.executionError.isPresent) {
        throw serviceResponse.executionError.get()
      }

      error("Unhandled error occurred while downloading configuration.")
    }

    return serviceResponse.result.get()
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
    } else if (SignalStore.backup.backupsInitialized) {
      getArchiveServiceAccessPair().runOnStatusCodeError(resetInitializedStateErrorAction)
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

  private fun File.deleteAllFilesWithPrefix(prefix: String) {
    this.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
  }

  data class SelfData(
    val aci: ACI,
    val pni: PNI,
    val e164: String,
    val profileKey: ProfileKey
  )

  fun DatabaseAttachment.getMediaName(): MediaName {
    return MediaName.fromDigest(remoteDigest!!)
  }

  fun DatabaseAttachment.getThumbnailMediaName(): MediaName {
    return MediaName.fromDigestForThumbnail(remoteDigest!!)
  }

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
  val mediaBackupEnabled: Boolean,
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

class BackupMetadata(
  val usedSpace: Long,
  val mediaCount: Long
)

sealed class ImportResult {
  data class Success(val backupTime: Long) : ImportResult()
  data object Failure : ImportResult()
}

/**
 * Iterator that reads values from the given cursor. Expects that ARCHIVE_MEDIA_ID and ARCHIVE_CDN are both
 * present and non-null in the cursor.
 *
 * This class does not assume ownership of the cursor. Recommended usage is within a use statement:
 *
 *
 * ```
 * databaseCall().use { cursor ->
 *   val iterator = ArchivedMediaObjectIterator(cursor)
 *   // Use the iterator...
 * }
 * // Cursor is closed after use block.
 * ```
 */
class ArchivedMediaObjectIterator(private val cursor: Cursor) : Iterator<ArchivedMediaObject> {

  init {
    cursor.moveToFirst()
  }

  override fun hasNext(): Boolean = !cursor.isAfterLast

  override fun next(): ArchivedMediaObject {
    val mediaId = cursor.requireNonNullString(AttachmentTable.ARCHIVE_MEDIA_ID)
    val cdn = cursor.requireInt(AttachmentTable.ARCHIVE_CDN)
    cursor.moveToNext()
    return ArchivedMediaObject(mediaId, cdn)
  }
}
