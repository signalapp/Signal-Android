/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import androidx.annotation.WorkerThread
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.Base64
import org.signal.core.util.EventTimer
import org.signal.core.util.fullWalCheckpoint
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.core.util.withinTransaction
import org.signal.libsignal.messagebackup.MessageBackup
import org.signal.libsignal.messagebackup.MessageBackup.ValidationResult
import org.signal.libsignal.messagebackup.MessageBackupKey
import org.signal.libsignal.protocol.ServiceId.Aci
import org.signal.libsignal.zkgroup.backups.BackupLevel
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.R
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
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupWriter
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupWriter
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeature
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.SignalStore
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
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

object BackupRepository {

  private val TAG = Log.tag(BackupRepository::class.java)
  private const val VERSION = 1L
  private const val MAIN_DB_SNAPSHOT_NAME = "signal-snapshot.db"
  private const val KEYVALUE_DB_SNAPSHOT_NAME = "key-value-snapshot.db"

  private val resetInitializedStateErrorAction: StatusCodeErrorAction = { error ->
    when (error.code) {
      401 -> {
        Log.i(TAG, "Resetting initialized state due to 401.")
        SignalStore.backup.backupsInitialized = false
      }

      403 -> {
        Log.i(TAG, "Bad auth credential. Clearing stored credentials.")
        SignalStore.backup.clearAllCredentials()
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

  private fun createSignalDatabaseSnapshot(): SignalDatabase {
    // Need to do a WAL checkpoint to ensure that the database file we're copying has all pending writes
    if (!SignalDatabase.rawDatabase.fullWalCheckpoint()) {
      Log.w(TAG, "Failed to checkpoint WAL for main database! Not guaranteed to be using the most recent data.")
    }

    // We make a copy of the database within a transaction to ensure that no writes occur while we're copying the file
    return SignalDatabase.rawDatabase.withinTransaction {
      val context = AppDependencies.application

      val existingDbFile = context.getDatabasePath(SignalDatabase.DATABASE_NAME)
      val targetFile = File(existingDbFile.parentFile, MAIN_DB_SNAPSHOT_NAME)

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
        name = MAIN_DB_SNAPSHOT_NAME
      )
    }
  }

  private fun createSignalStoreSnapshot(): SignalStore {
    val context = AppDependencies.application

    // Need to do a WAL checkpoint to ensure that the database file we're copying has all pending writes
    if (!KeyValueDatabase.getInstance(context).writableDatabase.fullWalCheckpoint()) {
      Log.w(TAG, "Failed to checkpoint WAL for KeyValueDatabase! Not guaranteed to be using the most recent data.")
    }

    // We make a copy of the database within a transaction to ensure that no writes occur while we're copying the file
    return KeyValueDatabase.getInstance(context).writableDatabase.withinTransaction {
      val existingDbFile = context.getDatabasePath(KeyValueDatabase.DATABASE_NAME)
      val targetFile = File(existingDbFile.parentFile, KEYVALUE_DB_SNAPSHOT_NAME)

      try {
        existingDbFile.copyTo(targetFile, overwrite = true)
      } catch (e: IOException) {
        // TODO [backup] Gracefully handle this error
        throw IllegalStateException("Failed to copy database file!", e)
      }

      val db = KeyValueDatabase.createWithName(context, KEYVALUE_DB_SNAPSHOT_NAME)
      SignalStore(KeyValueStore(db))
    }
  }

  private fun deleteDatabaseSnapshot() {
    val targetFile = AppDependencies.application.getDatabasePath(MAIN_DB_SNAPSHOT_NAME)
    if (!targetFile.delete()) {
      Log.w(TAG, "Failed to delete main database snapshot!")
    }
  }

  private fun deleteSignalStoreSnapshot() {
    val targetFile = AppDependencies.application.getDatabasePath(KEYVALUE_DB_SNAPSHOT_NAME)
    if (!targetFile.delete()) {
      Log.w(TAG, "Failed to delete key value database snapshot!")
    }
  }

  fun export(outputStream: OutputStream, append: (ByteArray) -> Unit, plaintext: Boolean = false, currentTime: Long = System.currentTimeMillis()) {
    val eventTimer = EventTimer()
    val dbSnapshot: SignalDatabase = createSignalDatabaseSnapshot()
    val signalStoreSnapshot: SignalStore = createSignalStoreSnapshot()

    try {
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

      val exportState = ExportState(backupTime = currentTime, allowMediaBackup = SignalStore.backup.backsUpMedia)

      writer.use {
        writer.write(
          BackupInfo(
            version = VERSION,
            backupTimeMs = exportState.backupTime
          )
        )
        // Note: Without a transaction, we may export inconsistent state. But because we have a transaction,
        // writes from other threads are blocked. This is something to think more about.
        dbSnapshot.rawWritableDatabase.withinTransaction {
          AccountDataProcessor.export(dbSnapshot, signalStoreSnapshot) {
            writer.write(it)
            eventTimer.emit("account")
          }

          RecipientBackupProcessor.export(dbSnapshot, signalStoreSnapshot, exportState) {
            writer.write(it)
            eventTimer.emit("recipient")
          }

          ChatBackupProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            eventTimer.emit("thread")
          }

          AdHocCallBackupProcessor.export(dbSnapshot) { frame ->
            writer.write(frame)
            eventTimer.emit("call")
          }

          StickerBackupProcessor.export(dbSnapshot) { frame ->
            writer.write(frame)
            eventTimer.emit("sticker-pack")
          }

          ChatItemBackupProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            eventTimer.emit("message")
          }
        }
      }

      Log.d(TAG, "export() ${eventTimer.stop().summary}")
    } finally {
      deleteDatabaseSnapshot()
      deleteSignalStoreSnapshot()
    }
  }

  /**
   * Exports to a blob in memory. Should only be used for testing.
   */
  fun debugExport(plaintext: Boolean = false, currentTime: Long = System.currentTimeMillis()): ByteArray {
    val outputStream = ByteArrayOutputStream()
    export(outputStream = outputStream, append = { mac -> outputStream.write(mac) }, plaintext = plaintext, currentTime = currentTime)
    return outputStream.toByteArray()
  }

  /**
   * @return The time the backup was created, or null if the backup could not be read.
   */
  fun import(length: Long, inputStreamFactory: () -> InputStream, selfData: SelfData, plaintext: Boolean = false): ImportResult {
    val eventTimer = EventTimer()

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
      SignalStore.clearAllDataForBackupRestore()
      SignalDatabase.recipients.clearAllDataForBackupRestore()
      SignalDatabase.distributionLists.clearAllDataForBackupRestore()
      SignalDatabase.threads.clearAllDataForBackupRestore()
      SignalDatabase.messages.clearAllDataForBackupRestore()
      SignalDatabase.attachments.clearAllDataForBackupRestore()
      SignalDatabase.stickers.clearAllDataForBackupRestore()

      // Add back self after clearing data
      val selfId: RecipientId = SignalDatabase.recipients.getAndPossiblyMerge(selfData.aci, selfData.pni, selfData.e164, pniVerified = true, changeSelf = true)
      SignalDatabase.recipients.setProfileKey(selfId, selfData.profileKey)
      SignalDatabase.recipients.setProfileSharing(selfId, true)

      eventTimer.emit("setup")
      val backupState = BackupState(backupKey)
      val chatItemInserter: ChatItemImportInserter = ChatItemBackupProcessor.beginImport(backupState)

      val totalLength = frameReader.getStreamLength()
      for (frame in frameReader) {
        when {
          frame.account != null -> {
            AccountDataProcessor.import(frame.account, selfId)
            eventTimer.emit("account")
          }

          frame.recipient != null -> {
            RecipientBackupProcessor.import(frame.recipient, backupState)
            eventTimer.emit("recipient")
          }

          frame.chat != null -> {
            ChatBackupProcessor.import(frame.chat, backupState)
            eventTimer.emit("chat")
          }

          frame.adHocCall != null -> {
            AdHocCallBackupProcessor.import(frame.adHocCall, backupState)
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

      backupState.chatIdToLocalThreadId.values.forEach {
        SignalDatabase.threads.update(it, unarchive = false, allowDeletion = false)
      }
    }

    val groups = SignalDatabase.groups.getGroups()
    while (groups.hasNext()) {
      val group = groups.next()
      if (group.id.isV2) {
        AppDependencies.jobManager.add(RequestGroupV2InfoJob(group.id as GroupId.V2))
      }
    }

    Log.d(TAG, "import() ${eventTimer.stop().summary}")
    return ImportResult.Success(backupTime = header.backupTimeMs)
  }

  fun validate(length: Long, inputStreamFactory: () -> InputStream, selfData: SelfData): ValidationResult {
    val masterKey = SignalStore.svr.getOrCreateMasterKey()
    val key = MessageBackupKey(masterKey.serialize(), Aci.parseFromBinary(selfData.aci.toByteArray()))

    return MessageBackup.validate(key, MessageBackup.Purpose.REMOTE_BACKUP, inputStreamFactory, length)
  }

  fun listRemoteMediaObjects(limit: Int, cursor: String? = null): NetworkResult<ArchiveGetMediaItemsResponse> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getArchiveMediaItemsPage(backupKey, credential, limit, cursor)
      }
  }

  fun getRemoteBackupUsedSpace(): NetworkResult<Long?> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getBackupInfo(backupKey, credential)
          .map { it.usedSpace }
      }
  }

  private fun getBackupTier(): NetworkResult<MessageBackupTier> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .map { credential ->
        val zkCredential = api.getZkCredential(backupKey, credential)
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
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getBackupInfo(backupKey, credential)
          .map { it to credential }
      }
      .then { pair ->
        val (info, credential) = pair
        api.debugGetUploadedMediaItemMetadata(backupKey, credential)
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
  fun uploadBackupFile(backupStream: InputStream, backupStreamLength: Long): Boolean {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getMessageBackupUploadForm(backupKey, credential)
          .also { Log.i(TAG, "UploadFormResult: $it") }
      }
      .then { form ->
        api.getBackupResumableUploadUrl(form)
          .also { Log.i(TAG, "ResumableUploadUrlResult: $it") }
          .map { form to it }
      }
      .then { formAndUploadUrl ->
        val (form, resumableUploadUrl) = formAndUploadUrl
        api.uploadBackupFile(form, resumableUploadUrl, backupStream, backupStreamLength)
          .also { Log.i(TAG, "UploadBackupFileResult: $it") }
      }
      .also { Log.i(TAG, "OverallResult: $it") } is NetworkResult.Success
  }

  fun downloadBackupFile(destination: File, listener: ProgressListener? = null): Boolean {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getBackupInfo(backupKey, credential)
      }
      .then { info -> getCdnReadCredentials(info.cdn ?: Cdn.CDN_3.cdnNumber).map { it.headers to info } }
      .map { pair ->
        val (cdnCredentials, info) = pair
        val messageReceiver = AppDependencies.signalServiceMessageReceiver
        messageReceiver.retrieveBackup(info.cdn!!, cdnCredentials, "backups/${info.backupDir}/${info.backupName}", destination, listener)
      } is NetworkResult.Success
  }

  fun getBackupFileLastModified(): NetworkResult<ZonedDateTime?> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getBackupInfo(backupKey, credential)
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
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.debugGetUploadedMediaItemMetadata(backupKey, credential)
      }
  }

  /**
   * Retrieves an upload spec that can be used to upload attachment media.
   */
  fun getMediaUploadSpec(secretKey: ByteArray? = null): NetworkResult<ResumableUploadSpec> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getMediaUploadForm(backupKey, credential)
      }
      .then { form ->
        api.getResumableUploadSpec(form, secretKey)
      }
  }

  fun archiveThumbnail(thumbnailAttachment: Attachment, parentAttachment: DatabaseAttachment): NetworkResult<ArchiveMediaResponse> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()
    val request = thumbnailAttachment.toArchiveMediaRequest(parentAttachment.getThumbnailMediaName(), backupKey)

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.archiveAttachmentMedia(
          backupKey = backupKey,
          serviceCredential = credential,
          item = request
        )
      }
  }

  fun archiveMedia(attachment: DatabaseAttachment): NetworkResult<Unit> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        val mediaName = attachment.getMediaName()
        val request = attachment.toArchiveMediaRequest(mediaName, backupKey)
        api
          .archiveAttachmentMedia(
            backupKey = backupKey,
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

  fun archiveMedia(databaseAttachments: List<DatabaseAttachment>): NetworkResult<BatchArchiveMediaResult> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
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

        api
          .archiveAttachmentMedia(
            backupKey = backupKey,
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
    val api = AppDependencies.signalServiceAccountManager.archiveApi
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
        api.deleteArchivedMedia(
          backupKey = backupKey,
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
    val api = AppDependencies.signalServiceAccountManager.archiveApi
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
        api.deleteArchivedMedia(
          backupKey = backupKey,
          serviceCredential = credential,
          mediaToDelete = mediaToDelete
        )
      }
      .also { Log.i(TAG, "deleteAbandonedMediaObjectsResult: $it") }
  }

  fun debugDeleteAllArchivedMedia(): NetworkResult<Unit> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi
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
              api.deleteArchivedMedia(
                backupKey = backupKey,
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

    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getCdnReadCredentials(
          cdnNumber = cdnNumber,
          backupKey = backupKey,
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

  fun restoreBackupTier(): MessageBackupTier? {
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
      getBackupTier().successOrThrow()
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

    val api = AppDependencies.signalServiceAccountManager.archiveApi
    val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getBackupInfo(backupKey, credential).map {
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
    return availableBackupTiers.map { getBackupsType(it) }
  }

  suspend fun getBackupsType(tier: MessageBackupTier): MessageBackupsType {
    val backupCurrency = SignalStore.inAppPayments.getSubscriptionCurrency(InAppPaymentSubscriberRecord.Type.BACKUP)
    return when (tier) {
      MessageBackupTier.FREE -> getFreeType(backupCurrency)
      MessageBackupTier.PAID -> getPaidType(backupCurrency)
    }
  }

  private fun getFreeType(currency: Currency): MessageBackupsType {
    return MessageBackupsType(
      tier = MessageBackupTier.FREE,
      pricePerMonth = FiatMoney(BigDecimal.ZERO, currency),
      title = "Text + 30 days of media", // TODO [message-backups] Finalize text (does this come from server?)
      features = persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = "Full text message backup" // TODO [message-backups] Finalize text (does this come from server?)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_album_compact_bold_16,
          label = "Last 30 days of media" // TODO [message-backups] Finalize text (does this come from server?)
        )
      )
    )
  }

  private suspend fun getPaidType(currency: Currency): MessageBackupsType {
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

    val config = serviceResponse.result.get()

    return MessageBackupsType(
      tier = MessageBackupTier.PAID,
      pricePerMonth = FiatMoney(config.currencies[currency.currencyCode.lowercase()]!!.backupSubscription[SubscriptionsConfiguration.BACKUPS_LEVEL]!!, currency),
      title = "Text + All your media", // TODO [message-backups] Finalize text (does this come from server?)
      features = persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = "Full text message backup" // TODO [message-backups] Finalize text (does this come from server?)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_album_compact_bold_16,
          label = "Full media backup" // TODO [message-backups] Finalize text (does this come from server?)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = "1TB of storage (~250K photos)" // TODO [message-backups] Finalize text (does this come from server?)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_heart_compact_bold_16,
          label = "Thanks for supporting Signal!" // TODO [message-backups] Finalize text (does this come from server?)
        )
      )
    )
  }

  /**
   * Ensures that the backupId has been reserved and that your public key has been set, while also returning an auth credential.
   * Should be the basis of all backup operations.
   */
  private fun initBackupAndFetchAuth(backupKey: BackupKey): NetworkResult<ArchiveServiceCredential> {
    val api = AppDependencies.signalServiceAccountManager.archiveApi

    return if (SignalStore.backup.backupsInitialized) {
      getAuthCredential().runOnStatusCodeError(resetInitializedStateErrorAction)
    } else {
      return api
        .triggerBackupIdReservation(backupKey)
        .then { getAuthCredential() }
        .then { credential -> api.setPublicKey(backupKey, credential).map { credential } }
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

    return AppDependencies.signalServiceAccountManager.archiveApi.getServiceCredentials(currentTime).map { result ->
      SignalStore.backup.addCredentials(result.credentials.toList())
      SignalStore.backup.clearCredentialsOlderThan(currentTime)
      SignalStore.backup.credentialsByDay.getForCurrentTime(currentTime.milliseconds)!!
    }
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
}

data class ArchivedMediaObject(val mediaId: String, val cdn: Int)

data class BackupDirectories(val backupDir: String, val mediaDir: String)

class ExportState(val backupTime: Long, val allowMediaBackup: Boolean) {
  val recipientIds = HashSet<Long>()
  val threadIds = HashSet<Long>()
}

class BackupState(val backupKey: BackupKey) {
  val backupToLocalRecipientId = HashMap<Long, RecipientId>()
  val chatIdToLocalThreadId = HashMap<Long, Long>()
  val chatIdToLocalRecipientId = HashMap<Long, RecipientId>()
  val chatIdToBackupRecipientId = HashMap<Long, Long>()
}

class BackupMetadata(
  val usedSpace: Long,
  val mediaCount: Long
)

sealed class ImportResult {
  data class Success(val backupTime: Long) : ImportResult()
  data object Failure : ImportResult()
}
