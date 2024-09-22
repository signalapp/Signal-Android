/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.Base64
import org.signal.core.util.EventTimer
import org.signal.core.util.concurrent.LimitedWorker
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.fullWalCheckpoint
import org.signal.core.util.logging.Log
import org.signal.core.util.stream.NonClosingOutputStream
import org.signal.core.util.withinTransaction
import org.signal.libsignal.messagebackup.MessageBackup
import org.signal.libsignal.messagebackup.MessageBackup.ValidationResult
import org.signal.libsignal.messagebackup.MessageBackupKey
import org.signal.libsignal.protocol.ServiceId.Aci
import org.signal.libsignal.zkgroup.backups.BackupLevel
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.database.ChatItemImportInserter
import org.thoughtcrime.securesms.backup.v2.database.clearAllDataForBackupRestore
import org.thoughtcrime.securesms.backup.v2.processor.AccountDataProcessor
import org.thoughtcrime.securesms.backup.v2.processor.AdHocCallBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatItemBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.RecipientBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.StickerBackupProcessor
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
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.toMillis
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.StatusCodeErrorAction
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse
import org.whispersystems.signalservice.api.archive.ArchiveMediaRequest
import org.whispersystems.signalservice.api.archive.ArchiveMediaResponse
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.DeleteArchivedMediaRequest
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import org.whispersystems.signalservice.api.backup.BackupKey
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
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
        SignalStore.backup.clearAllCredentials()
      }

      403 -> {
        Log.w(TAG, "Received status 403. The user is not in the media tier. Updating local state.", error.exception)
        SignalStore.backup.backupTier = MessageBackupTier.FREE
        // TODO [backup] If the user thought they were in media tier but aren't, feels like we should have a special UX flow for this?
      }
    }
  }

  @WorkerThread
  fun canAccessRemoteBackupSettings(): Boolean {
    // TODO [message-backups]

    // We need to check whether the user can access remote backup settings.

    // 1. Do they have a receipt they need to be able to view?
    // 2. Do they have a backup they need to be able to manage?

    // The easy thing to do here would actually be to set a ui hint.

    return SignalStore.backup.areBackupsEnabled
  }

  @WorkerThread
  fun turnOffAndDeleteBackup() {
    RecurringInAppPaymentRepository.cancelActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP)
    SignalStore.backup.areBackupsEnabled = false
    SignalStore.backup.backupTier = null
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
    archiveAttachment: (AttachmentTable.LocalArchivableAttachment, () -> InputStream?) -> Unit
  ) {
    val writer = EncryptedBackupWriter(
      key = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey(),
      aci = SignalStore.account.aci!!,
      outputStream = NonClosingOutputStream(main),
      append = { main.write(it) }
    )

    export(currentTime = System.currentTimeMillis(), isLocal = true, writer = writer, progressEmitter = localBackupProgressEmitter) { dbSnapshot ->
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

  fun export(outputStream: OutputStream, append: (ByteArray) -> Unit, plaintext: Boolean = false, currentTime: Long = System.currentTimeMillis()) {
    val writer: BackupExportWriter = if (plaintext) {
      PlainTextBackupWriter(outputStream)
    } else {
      EncryptedBackupWriter(
        key = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey(),
        aci = SignalStore.account.aci!!,
        outputStream = outputStream,
        append = append
      )
    }

    export(currentTime = currentTime, isLocal = false, writer = writer)
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
    progressEmitter: ExportProgressListener? = null,
    exportExtras: ((SignalDatabase) -> Unit)? = null
  ) {
    val eventTimer = EventTimer()
    val mainDbName = if (isLocal) LOCAL_MAIN_DB_SNAPSHOT_NAME else REMOTE_MAIN_DB_SNAPSHOT_NAME
    val keyValueDbName = if (isLocal) LOCAL_KEYVALUE_DB_SNAPSHOT_NAME else REMOTE_KEYVALUE_DB_SNAPSHOT_NAME

    try {
      val dbSnapshot: SignalDatabase = createSignalDatabaseSnapshot(mainDbName)
      val signalStoreSnapshot: SignalStore = createSignalStoreSnapshot(keyValueDbName)

      val exportState = ExportState(backupTime = currentTime, mediaBackupEnabled = SignalStore.backup.backsUpMedia)

      writer.use {
        writer.write(
          BackupInfo(
            version = VERSION,
            backupTimeMs = exportState.backupTime
          )
        )

        // We're using a snapshot, so the transaction is more for perf than correctness
        dbSnapshot.rawWritableDatabase.withinTransaction {
          progressEmitter?.onAccount()
          AccountDataProcessor.export(dbSnapshot, signalStoreSnapshot) {
            writer.write(it)
            eventTimer.emit("account")
          }

          progressEmitter?.onRecipient()
          RecipientBackupProcessor.export(dbSnapshot, signalStoreSnapshot, exportState) {
            writer.write(it)
            eventTimer.emit("recipient")
          }

          progressEmitter?.onThread()
          ChatBackupProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            eventTimer.emit("thread")
          }

          progressEmitter?.onCall()
          AdHocCallBackupProcessor.export(dbSnapshot) { frame ->
            writer.write(frame)
            eventTimer.emit("call")
          }

          progressEmitter?.onSticker()
          StickerBackupProcessor.export(dbSnapshot) { frame ->
            writer.write(frame)
            eventTimer.emit("sticker-pack")
          }

          progressEmitter?.onMessage()
          ChatItemBackupProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            eventTimer.emit("message")
          }
        }
      }

      exportExtras?.invoke(dbSnapshot)

      Log.d(TAG, "export() ${eventTimer.stop().summary}")
    } finally {
      deleteDatabaseSnapshot(mainDbName)
      deleteDatabaseSnapshot(keyValueDbName)
    }
  }

  fun localImport(mainStreamFactory: () -> InputStream, mainStreamLength: Long, selfData: SelfData): ImportResult {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

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
      import(backupKey, reader, selfData)
    }
  }

  fun import(length: Long, inputStreamFactory: () -> InputStream, selfData: SelfData, plaintext: Boolean = false): ImportResult {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    val frameReader = if (plaintext) {
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
      import(backupKey, reader, selfData)
    }
  }

  private fun import(
    backupKey: BackupKey,
    frameReader: BackupImportReader,
    selfData: SelfData
  ): ImportResult {
    val eventTimer = EventTimer()

    val header = frameReader.getHeader()
    if (header == null) {
      Log.e(TAG, "Backup is missing header!")
      return ImportResult.Failure
    } else if (header.version > VERSION) {
      Log.e(TAG, "Backup version is newer than we understand: ${header.version}")
      return ImportResult.Failure
    }

    // Note: Without a transaction, bad imports could lead to lost data. But because we have a transaction,
    // writes from other threads are blocked. This is something to think more about.
    SignalDatabase.rawDatabase.withinTransaction {
      SignalDatabase.recipients.clearAllDataForBackupRestore()
      SignalDatabase.distributionLists.clearAllDataForBackupRestore()
      SignalDatabase.threads.clearAllDataForBackupRestore()
      SignalDatabase.messages.clearAllDataForBackupRestore()
      SignalDatabase.attachments.clearAllDataForBackupRestore()
      SignalDatabase.stickers.clearAllDataForBackupRestore()
      SignalDatabase.reactions.clearAllDataForBackupRestore()
      SignalDatabase.inAppPayments.clearAllDataForBackupRestore()
      SignalDatabase.chatColors.clearAllDataForBackupRestore()

      // Add back self after clearing data
      val selfId: RecipientId = SignalDatabase.recipients.getAndPossiblyMerge(selfData.aci, selfData.pni, selfData.e164, pniVerified = true, changeSelf = true)
      SignalDatabase.recipients.setProfileKey(selfId, selfData.profileKey)
      SignalDatabase.recipients.setProfileSharing(selfId, true)

      eventTimer.emit("setup")
      val importState = ImportState(backupKey)
      val chatItemInserter: ChatItemImportInserter = ChatItemBackupProcessor.beginImport(importState)

      val totalLength = frameReader.getStreamLength()
      for (frame in frameReader) {
        when {
          frame.account != null -> {
            AccountDataProcessor.import(frame.account, selfId, importState)
            eventTimer.emit("account")
          }

          frame.recipient != null -> {
            RecipientBackupProcessor.import(frame.recipient, importState)
            eventTimer.emit("recipient")
          }

          frame.chat != null -> {
            ChatBackupProcessor.import(frame.chat, importState)
            eventTimer.emit("chat")
          }

          frame.adHocCall != null -> {
            AdHocCallBackupProcessor.import(frame.adHocCall, importState)
            eventTimer.emit("call")
          }

          frame.stickerPack != null -> {
            StickerBackupProcessor.import(frame.stickerPack)
            eventTimer.emit("sticker-pack")
          }

          frame.chatItem != null -> {
            chatItemInserter.insert(frame.chatItem)
            eventTimer.emit("chatItem")
            // TODO if there's stuff in the stream after chatItems, we need to flush the inserter before going to the next phase
          }

          else -> Log.w(TAG, "Unrecognized frame")
        }
        EventBus.getDefault().post(RestoreV2Event(RestoreV2Event.Type.PROGRESS_RESTORE, frameReader.getBytesRead(), totalLength))
      }

      if (chatItemInserter.flush()) {
        eventTimer.emit("chatItem")
      }

      importState.chatIdToLocalThreadId.values.forEach {
        SignalDatabase.threads.update(it, unarchive = false, allowDeletion = false)
      }
    }

    Log.d(TAG, "import() ${eventTimer.stop().summary}")

    val groupJobs = SignalDatabase.groups.getGroups().use { groups ->
      groups
        .asSequence()
        .mapNotNull { group ->
          if (group.id.isV2) {
            RequestGroupV2InfoJob(group.id as GroupId.V2)
          } else {
            null
          }
        }
        .toList()
    }
    AppDependencies.jobManager.addAll(groupJobs)

    return ImportResult.Success(backupTime = header.backupTimeMs)
  }

  fun validate(length: Long, inputStreamFactory: () -> InputStream, selfData: SelfData): ValidationResult {
    val masterKey = SignalStore.svr.getOrCreateMasterKey()
    val key = MessageBackupKey(masterKey.serialize(), Aci.parseFromBinary(selfData.aci.toByteArray()))

    return MessageBackup.validate(key, MessageBackup.Purpose.REMOTE_BACKUP, inputStreamFactory, length)
  }

  fun listRemoteMediaObjects(limit: Int, cursor: String? = null): NetworkResult<ArchiveGetMediaItemsResponse> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.getArchiveMediaItemsPage(backupKey, SignalStore.account.requireAci(), credential, limit, cursor)
      }
  }

  fun getRemoteBackupUsedSpace(): NetworkResult<Long?> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(backupKey, SignalStore.account.requireAci(), credential)
          .map { it.usedSpace }
      }
  }

  private fun getBackupTier(aci: ACI): NetworkResult<MessageBackupTier> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .map { credential ->
        val zkCredential = SignalNetwork.archive.getZkCredential(backupKey, aci, credential)
        if (zkCredential.backupLevel == BackupLevel.MEDIA) {
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
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(backupKey, SignalStore.account.requireAci(), credential)
          .map { it to credential }
      }
      .then { pair ->
        val (info, credential) = pair
        SignalNetwork.archive.debugGetUploadedMediaItemMetadata(backupKey, SignalStore.account.requireAci(), credential)
          .also { Log.i(TAG, "MediaItemMetadataResult: $it") }
          .map { mediaObjects ->
            BackupMetadata(
              usedSpace = info.usedSpace ?: 0,
              mediaCount = mediaObjects.size.toLong()
            )
          }
      }
  }

  /**
   * A simple test method that just hits various network endpoints. Only useful for the playground.
   *
   * @return True if successful, otherwise false.
   */
  fun uploadBackupFile(backupStream: InputStream, backupStreamLength: Long): NetworkResult<Unit> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.getMessageBackupUploadForm(backupKey, SignalStore.account.requireAci(), credential)
          .also { Log.i(TAG, "UploadFormResult: $it") }
      }
      .then { form ->
        SignalNetwork.archive.getBackupResumableUploadUrl(form)
          .also { Log.i(TAG, "ResumableUploadUrlResult: $it") }
          .map { form to it }
      }
      .then { formAndUploadUrl ->
        val (form, resumableUploadUrl) = formAndUploadUrl
        SignalNetwork.archive.uploadBackupFile(form, resumableUploadUrl, backupStream, backupStreamLength)
          .also { Log.i(TAG, "UploadBackupFileResult: $it") }
      }
  }

  fun downloadBackupFile(destination: File, listener: ProgressListener? = null): Boolean {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(backupKey, SignalStore.account.requireAci(), credential)
      }
      .then { info -> getCdnReadCredentials(info.cdn ?: Cdn.CDN_3.cdnNumber).map { it.headers to info } }
      .map { pair ->
        val (cdnCredentials, info) = pair
        val messageReceiver = AppDependencies.signalServiceMessageReceiver
        messageReceiver.retrieveBackup(info.cdn!!, cdnCredentials, "backups/${info.backupDir}/${info.backupName}", destination, listener)
      } is NetworkResult.Success
  }

  fun getBackupFileLastModified(): NetworkResult<ZonedDateTime?> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(backupKey, SignalStore.account.requireAci(), credential)
      }
      .then { info -> getCdnReadCredentials(info.cdn ?: Cdn.CDN_3.cdnNumber).map { it.headers to info } }
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
  fun debugGetArchivedMediaState(): NetworkResult<List<ArchiveGetMediaItemsResponse.StoredMediaObject>> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.debugGetUploadedMediaItemMetadata(backupKey, SignalStore.account.requireAci(), credential)
      }
  }

  /**
   * Retrieves an [AttachmentUploadForm] that can be used to upload an attachment to the transit cdn.
   * To continue the upload, use [org.whispersystems.signalservice.api.attachment.AttachmentApi.getResumableUploadSpec].
   *
   * It's important to note that in order to get this to the archive cdn, you still need to use [copyAttachmentToArchive].
   */
  fun getAttachmentUploadForm(): NetworkResult<AttachmentUploadForm> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.getMediaUploadForm(backupKey, SignalStore.account.requireAci(), credential)
      }
  }

  /**
   * Copies a thumbnail that has been uploaded to the transit cdn to the archive cdn.
   */
  fun copyThumbnailToArchive(thumbnailAttachment: Attachment, parentAttachment: DatabaseAttachment): NetworkResult<ArchiveMediaResponse> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()
    val request = thumbnailAttachment.toArchiveMediaRequest(parentAttachment.getThumbnailMediaName(), backupKey)

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.copyAttachmentToArchive(
          backupKey = backupKey,
          aci = SignalStore.account.requireAci(),
          serviceCredential = credential,
          item = request
        )
      }
  }

  /**
   * Copies an attachment that has been uploaded to the transit cdn to the archive cdn.
   */
  fun copyAttachmentToArchive(attachment: DatabaseAttachment): NetworkResult<Unit> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        val mediaName = attachment.getMediaName()
        val request = attachment.toArchiveMediaRequest(mediaName, backupKey)
        SignalNetwork.archive
          .copyAttachmentToArchive(
            backupKey = backupKey,
            aci = SignalStore.account.requireAci(),
            serviceCredential = credential,
            item = request
          )
          .map { Triple(mediaName, request.mediaId, it) }
      }
      .map { (mediaName, mediaId, response) ->
        val thumbnailId = backupKey.deriveMediaId(attachment.getThumbnailMediaName()).encode()
        SignalDatabase.attachments.setArchiveData(attachmentId = attachment.attachmentId, archiveCdn = response.cdn, archiveMediaName = mediaName.name, archiveMediaId = mediaId, archiveThumbnailMediaId = thumbnailId)
      }
      .also { Log.i(TAG, "archiveMediaResult: $it") }
  }

  fun copyAttachmentToArchive(databaseAttachments: List<DatabaseAttachment>): NetworkResult<BatchArchiveMediaResult> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        val requests = mutableListOf<ArchiveMediaRequest>()
        val mediaIdToAttachmentId = mutableMapOf<String, AttachmentId>()
        val attachmentIdToMediaName = mutableMapOf<AttachmentId, String>()

        databaseAttachments.forEach {
          val mediaName = it.getMediaName()
          val request = it.toArchiveMediaRequest(mediaName, backupKey)
          requests += request
          mediaIdToAttachmentId[request.mediaId] = it.attachmentId
          attachmentIdToMediaName[it.attachmentId] = mediaName.name
        }

        SignalNetwork.archive
          .copyAttachmentToArchive(
            backupKey = backupKey,
            aci = SignalStore.account.requireAci(),
            serviceCredential = credential,
            items = requests
          )
          .map { BatchArchiveMediaResult(it, mediaIdToAttachmentId, attachmentIdToMediaName) }
      }
      .map { result ->
        result
          .successfulResponses
          .forEach {
            val attachmentId = result.mediaIdToAttachmentId(it.mediaId)
            val mediaName = result.attachmentIdToMediaName(attachmentId)
            val thumbnailId = backupKey.deriveMediaId(MediaName.forThumbnailFromMediaName(mediaName = mediaName)).encode()
            SignalDatabase.attachments.setArchiveData(attachmentId = attachmentId, archiveCdn = it.cdn!!, archiveMediaName = mediaName, archiveMediaId = it.mediaId, thumbnailId)
          }
        result
      }
      .also { Log.i(TAG, "archiveMediaResult: $it") }
  }

  fun deleteArchivedMedia(attachments: List<DatabaseAttachment>): NetworkResult<Unit> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

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

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.deleteArchivedMedia(
          backupKey = backupKey,
          aci = SignalStore.account.requireAci(),
          serviceCredential = credential,
          mediaToDelete = mediaToDelete
        )
      }
      .map {
        SignalDatabase.attachments.clearArchiveData(attachments.map { it.attachmentId })
      }
      .also { Log.i(TAG, "deleteArchivedMediaResult: $it") }
  }

  fun deleteAbandonedMediaObjects(mediaObjects: Collection<ArchivedMediaObject>): NetworkResult<Unit> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

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

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.deleteArchivedMedia(
          backupKey = backupKey,
          aci = SignalStore.account.requireAci(),
          serviceCredential = credential,
          mediaToDelete = mediaToDelete
        )
      }
      .also { Log.i(TAG, "deleteAbandonedMediaObjectsResult: $it") }
  }

  fun debugDeleteAllArchivedMedia(): NetworkResult<Unit> {
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

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
          getAuthCredential()
            .then { credential ->
              SignalNetwork.archive.deleteArchivedMedia(
                backupKey = backupKey,
                aci = SignalStore.account.requireAci(),
                serviceCredential = credential,
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
  fun getCdnReadCredentials(cdnNumber: Int): NetworkResult<GetArchiveCdnCredentialsResponse> {
    val cached = SignalStore.backup.cdnReadCredentials
    if (cached != null) {
      return NetworkResult.Success(cached)
    }

    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.getCdnReadCredentials(
          cdnNumber = cdnNumber,
          backupKey = backupKey,
          aci = SignalStore.account.requireAci(),
          serviceCredential = credential
        )
      }
      .also {
        if (it is NetworkResult.Success) {
          SignalStore.backup.cdnReadCredentials = it.result
        }
      }
      .also { Log.i(TAG, "getCdnReadCredentialsResult: $it") }
  }

  fun restoreBackupTier(aci: ACI): MessageBackupTier? {
    // TODO: more complete error handling
    try {
      val lastModified = getBackupFileLastModified().successOrThrow()
      if (lastModified != null) {
        SignalStore.backup.lastBackupTime = lastModified.toMillis()
      }
    } catch (e: Exception) {
      Log.i(TAG, "Could not check for backup file.", e)
      SignalStore.backup.backupTier = null
      return null
    }
    SignalStore.backup.backupTier = try {
      getBackupTier(aci).successOrThrow()
    } catch (e: Exception) {
      Log.i(TAG, "Could not retrieve backup tier.", e)
      null
    }
    return SignalStore.backup.backupTier
  }

  /**
   * Retrieves backupDir and mediaDir, preferring cached value if available.
   *
   * These will only ever change if the backup expires.
   */
  fun getCdnBackupDirectories(): NetworkResult<BackupDirectories> {
    val cachedBackupDirectory = SignalStore.backup.cachedBackupDirectory
    val cachedBackupMediaDirectory = SignalStore.backup.cachedBackupMediaDirectory

    if (cachedBackupDirectory != null && cachedBackupMediaDirectory != null) {
      return NetworkResult.Success(
        BackupDirectories(
          backupDir = cachedBackupDirectory,
          mediaDir = cachedBackupMediaDirectory
        )
      )
    }

    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        SignalNetwork.archive.getBackupInfo(backupKey, SignalStore.account.requireAci(), credential).map {
          SignalStore.backup.usedBackupMediaSpace = it.usedSpace ?: 0L
          BackupDirectories(it.backupDir!!, it.mediaDir!!)
        }
      }
      .also {
        if (it is NetworkResult.Success) {
          SignalStore.backup.cachedBackupDirectory = it.result.backupDir
          SignalStore.backup.cachedBackupMediaDirectory = it.result.mediaDir
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

  private suspend fun getFreeType(): MessageBackupsType {
    val config = getSubscriptionsConfiguration()

    return MessageBackupsType.Free(
      mediaRetentionDays = config.backupConfiguration.freeTierMediaDays
    )
  }

  private suspend fun getPaidType(): MessageBackupsType? {
    val config = getSubscriptionsConfiguration()
    val product = AppDependencies.billingApi.queryProduct() ?: return null

    return MessageBackupsType.Paid(
      pricePerMonth = product.price,
      storageAllowanceBytes = config.backupConfiguration.backupLevelConfigurationMap[SubscriptionsConfiguration.BACKUPS_LEVEL]!!.storageAllowanceBytes
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
   * Ensures that the backupId has been reserved and that your public key has been set, while also returning an auth credential.
   * Should be the basis of all backup operations.
   */
  private fun initBackupAndFetchAuth(backupKey: BackupKey): NetworkResult<ArchiveServiceCredential> {
    return if (SignalStore.backup.backupsInitialized) {
      getAuthCredential().runOnStatusCodeError(resetInitializedStateErrorAction)
    } else {
      return SignalNetwork.archive
        .triggerBackupIdReservation(backupKey, SignalStore.account.requireAci())
        .then { getAuthCredential() }
        .then { credential -> SignalNetwork.archive.setPublicKey(backupKey, SignalStore.account.requireAci(), credential).map { credential } }
        .runIfSuccessful { SignalStore.backup.backupsInitialized = true }
        .runOnStatusCodeError(resetInitializedStateErrorAction)
    }
  }

  /**
   * Retrieves an auth credential, preferring a cached value if available.
   */
  private fun getAuthCredential(): NetworkResult<ArchiveServiceCredential> {
    val currentTime = System.currentTimeMillis()

    val credential = SignalStore.backup.credentialsByDay.getForCurrentTime(currentTime.milliseconds)

    if (credential != null) {
      return NetworkResult.Success(credential)
    }

    Log.w(TAG, "No credentials found for today, need to fetch new ones! This shouldn't happen under normal circumstances. We should ensure the routine fetch is running properly.")

    return SignalNetwork.archive.getServiceCredentials(currentTime).map { result ->
      SignalStore.backup.addCredentials(result.credentials.toList())
      SignalStore.backup.clearCredentialsOlderThan(currentTime)
      SignalStore.backup.credentialsByDay.getForCurrentTime(currentTime.milliseconds)!!
    }
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

  private fun Attachment.toArchiveMediaRequest(mediaName: MediaName, backupKey: BackupKey): ArchiveMediaRequest {
    val mediaSecrets = backupKey.deriveMediaSecrets(mediaName)

    return ArchiveMediaRequest(
      sourceAttachment = ArchiveMediaRequest.SourceAttachment(
        cdn = cdn.cdnNumber,
        key = remoteLocation!!
      ),
      objectLength = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(size)).toInt(),
      mediaId = mediaSecrets.id.encode(),
      hmacKey = Base64.encodeWithPadding(mediaSecrets.macKey),
      encryptionKey = Base64.encodeWithPadding(mediaSecrets.cipherKey),
      iv = Base64.encodeWithPadding(mediaSecrets.iv)
    )
  }

  interface ExportProgressListener {
    fun onAccount()
    fun onRecipient()
    fun onThread()
    fun onCall()
    fun onSticker()
    fun onMessage()
    fun onAttachment(currentProgress: Long, totalCount: Long)
  }
}

data class ArchivedMediaObject(val mediaId: String, val cdn: Int)

data class BackupDirectories(val backupDir: String, val mediaDir: String)

class ExportState(val backupTime: Long, val mediaBackupEnabled: Boolean) {
  val recipientIds: MutableSet<Long> = hashSetOf()
  val threadIds: MutableSet<Long> = hashSetOf()
  val localToRemoteCustomChatColors: MutableMap<Long, Int> = hashMapOf()
}

class ImportState(val backupKey: BackupKey) {
  val remoteToLocalRecipientId: MutableMap<Long, RecipientId> = hashMapOf()
  val chatIdToLocalThreadId: MutableMap<Long, Long> = hashMapOf()
  val chatIdToLocalRecipientId: MutableMap<Long, RecipientId> = hashMapOf()
  val chatIdToBackupRecipientId: MutableMap<Long, Long> = hashMapOf()
  val remoteToLocalColorId: MutableMap<Long, Long> = hashMapOf()
}

class BackupMetadata(
  val usedSpace: Long,
  val mediaCount: Long
)

sealed class ImportResult {
  data class Success(val backupTime: Long) : ImportResult()
  data object Failure : ImportResult()
}
