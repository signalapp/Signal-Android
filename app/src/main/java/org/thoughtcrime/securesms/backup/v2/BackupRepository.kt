/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.core.util.Base64
import org.signal.core.util.EventTimer
import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.signal.libsignal.messagebackup.MessageBackup
import org.signal.libsignal.messagebackup.MessageBackup.ValidationResult
import org.signal.libsignal.messagebackup.MessageBackupKey
import org.signal.libsignal.protocol.ServiceId.Aci
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.database.ChatItemImportInserter
import org.thoughtcrime.securesms.backup.v2.database.clearAllDataForBackupRestore
import org.thoughtcrime.securesms.backup.v2.processor.AccountDataProcessor
import org.thoughtcrime.securesms.backup.v2.processor.CallLogBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatItemBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.RecipientBackupProcessor
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.stream.BackupExportWriter
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupWriter
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupWriter
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.StatusCodeErrorAction
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse
import org.whispersystems.signalservice.api.archive.ArchiveMediaRequest
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
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Duration.Companion.milliseconds

object BackupRepository {

  private val TAG = Log.tag(BackupRepository::class.java)
  private const val VERSION = 1L

  private val resetInitializedStateErrorAction: StatusCodeErrorAction = { error ->
    if (error.code == 401) {
      Log.i(TAG, "Resetting initialized state due to 401.")
      SignalStore.backup().backupsInitialized = false
    }
  }

  fun export(outputStream: OutputStream, append: (ByteArray) -> Unit, plaintext: Boolean = false) {
    val eventTimer = EventTimer()
    val writer: BackupExportWriter = if (plaintext) {
      PlainTextBackupWriter(outputStream)
    } else {
      EncryptedBackupWriter(
        key = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey(),
        aci = SignalStore.account().aci!!,
        outputStream = outputStream,
        append = append
      )
    }

    val exportState = ExportState(backupTime = System.currentTimeMillis(), allowMediaBackup = true)

    writer.use {
      writer.write(
        BackupInfo(
          version = VERSION,
          backupTimeMs = exportState.backupTime
        )
      )
      // Note: Without a transaction, we may export inconsistent state. But because we have a transaction,
      // writes from other threads are blocked. This is something to think more about.
      SignalDatabase.rawDatabase.withinTransaction {
        AccountDataProcessor.export {
          writer.write(it)
          eventTimer.emit("account")
        }

        RecipientBackupProcessor.export(exportState) {
          writer.write(it)
          eventTimer.emit("recipient")
        }

        ChatBackupProcessor.export(exportState) { frame ->
          writer.write(frame)
          eventTimer.emit("thread")
        }

        CallLogBackupProcessor.export { frame ->
          writer.write(frame)
          eventTimer.emit("call")
        }

        ChatItemBackupProcessor.export(exportState) { frame ->
          writer.write(frame)
          eventTimer.emit("message")
        }
      }
    }

    Log.d(TAG, "export() ${eventTimer.stop().summary}")
  }

  fun export(plaintext: Boolean = false): ByteArray {
    val outputStream = ByteArrayOutputStream()
    export(outputStream = outputStream, append = { mac -> outputStream.write(mac) }, plaintext = plaintext)
    return outputStream.toByteArray()
  }

  fun validate(length: Long, inputStreamFactory: () -> InputStream, selfData: SelfData): ValidationResult {
    val masterKey = SignalStore.svr().getOrCreateMasterKey()
    val key = MessageBackupKey(masterKey.serialize(), Aci.parseFromBinary(selfData.aci.toByteArray()))

    return MessageBackup.validate(key, MessageBackup.Purpose.REMOTE_BACKUP, inputStreamFactory, length)
  }

  fun import(length: Long, inputStreamFactory: () -> InputStream, selfData: SelfData, plaintext: Boolean = false) {
    val eventTimer = EventTimer()

    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

    val frameReader = if (plaintext) {
      PlainTextBackupReader(inputStreamFactory())
    } else {
      EncryptedBackupReader(
        key = backupKey,
        aci = selfData.aci,
        streamLength = length,
        dataStream = inputStreamFactory
      )
    }

    val header = frameReader.getHeader()
    if (header == null) {
      Log.e(TAG, "Backup is missing header!")
      return
    } else if (header.version > VERSION) {
      Log.e(TAG, "Backup version is newer than we understand: ${header.version}")
      return
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

      // Add back self after clearing data
      val selfId: RecipientId = SignalDatabase.recipients.getAndPossiblyMerge(selfData.aci, selfData.pni, selfData.e164, pniVerified = true, changeSelf = true)
      SignalDatabase.recipients.setProfileKey(selfId, selfData.profileKey)
      SignalDatabase.recipients.setProfileSharing(selfId, true)

      eventTimer.emit("setup")
      val backupState = BackupState(backupKey)
      val chatItemInserter: ChatItemImportInserter = ChatItemBackupProcessor.beginImport(backupState)

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

          frame.call != null -> {
            CallLogBackupProcessor.import(frame.call, backupState)
            eventTimer.emit("call")
          }

          frame.chatItem != null -> {
            chatItemInserter.insert(frame.chatItem)
            eventTimer.emit("chatItem")
            // TODO if there's stuff in the stream after chatItems, we need to flush the inserter before going to the next phase
          }

          else -> Log.w(TAG, "Unrecognized frame")
        }
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
        ApplicationDependencies.getJobManager().add(RequestGroupV2InfoJob(group.id as GroupId.V2))
      }
    }

    Log.d(TAG, "import() ${eventTimer.stop().summary}")
  }

  fun listRemoteMediaObjects(limit: Int, cursor: String? = null): NetworkResult<ArchiveGetMediaItemsResponse> {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getArchiveMediaItemsPage(backupKey, credential, limit, cursor)
      }
  }

  /**
   * Returns an object with details about the remote backup state.
   */
  fun getRemoteBackupState(): NetworkResult<BackupMetadata> {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

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
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

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
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getBackupInfo(backupKey, credential)
      }
      .then { info -> getCdnReadCredentials(info.cdn ?: Cdn.CDN_3.cdnNumber).map { it.headers to info } }
      .map { pair ->
        val (cdnCredentials, info) = pair
        val messageReceiver = ApplicationDependencies.getSignalServiceMessageReceiver()
        messageReceiver.retrieveBackup(info.cdn!!, cdnCredentials, "backups/${info.backupDir}/${info.backupName}", destination, listener)
      } is NetworkResult.Success
  }

  /**
   * Returns an object with details about the remote backup state.
   */
  fun debugGetArchivedMediaState(): NetworkResult<List<ArchiveGetMediaItemsResponse.StoredMediaObject>> {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.debugGetUploadedMediaItemMetadata(backupKey, credential)
      }
  }

  /**
   * Retrieves an upload spec that can be used to upload attachment media.
   */
  fun getMediaUploadSpec(): NetworkResult<ResumableUploadSpec> {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getMediaUploadForm(backupKey, credential)
      }
      .then { form ->
        api.getResumableUploadSpec(form)
      }
  }

  fun archiveMedia(attachment: DatabaseAttachment): NetworkResult<Unit> {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.setPublicKey(backupKey, credential)
          .map { credential }
      }
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
        SignalDatabase.attachments.setArchiveData(attachmentId = attachment.attachmentId, archiveCdn = response.cdn, archiveMediaName = mediaName.name, archiveMediaId = mediaId)
      }
      .also { Log.i(TAG, "archiveMediaResult: $it") }
  }

  fun archiveMedia(databaseAttachments: List<DatabaseAttachment>): NetworkResult<BatchArchiveMediaResult> {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

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
            SignalDatabase.attachments.setArchiveData(attachmentId = attachmentId, archiveCdn = it.cdn!!, archiveMediaName = mediaName, archiveMediaId = it.mediaId)
          }
        result
      }
      .also { Log.i(TAG, "archiveMediaResult: $it") }
  }

  fun deleteArchivedMedia(attachments: List<DatabaseAttachment>): NetworkResult<Unit> {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

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
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

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
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

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
    val cached = SignalStore.backup().cdnReadCredentials
    if (cached != null) {
      return NetworkResult.Success(cached)
    }

    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

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
          SignalStore.backup().cdnReadCredentials = it.result
        }
      }
      .also { Log.i(TAG, "getCdnReadCredentialsResult: $it") }
  }

  /**
   * Retrieves backupDir and mediaDir, preferring cached value if available.
   *
   * These will only ever change if the backup expires.
   */
  fun getCdnBackupDirectories(): NetworkResult<BackupDirectories> {
    val cachedBackupDirectory = SignalStore.backup().cachedBackupDirectory
    val cachedBackupMediaDirectory = SignalStore.backup().cachedBackupMediaDirectory

    if (cachedBackupDirectory != null && cachedBackupMediaDirectory != null) {
      return NetworkResult.Success(
        BackupDirectories(
          backupDir = cachedBackupDirectory,
          mediaDir = cachedBackupMediaDirectory
        )
      )
    }

    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

    return initBackupAndFetchAuth(backupKey)
      .then { credential ->
        api.getBackupInfo(backupKey, credential).map {
          BackupDirectories(it.backupDir!!, it.mediaDir!!)
        }
      }
      .also {
        if (it is NetworkResult.Success) {
          SignalStore.backup().cachedBackupDirectory = it.result.backupDir
          SignalStore.backup().cachedBackupMediaDirectory = it.result.mediaDir
        }
      }
  }

  /**
   * Ensures that the backupId has been reserved and that your public key has been set, while also returning an auth credential.
   * Should be the basis of all backup operations.
   */
  private fun initBackupAndFetchAuth(backupKey: BackupKey): NetworkResult<ArchiveServiceCredential> {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi

    return if (SignalStore.backup().backupsInitialized) {
      getAuthCredential().runOnStatusCodeError(resetInitializedStateErrorAction)
    } else {
      return api
        .triggerBackupIdReservation(backupKey)
        .then { getAuthCredential() }
        .then { credential -> api.setPublicKey(backupKey, credential).map { credential } }
        .runIfSuccessful { SignalStore.backup().backupsInitialized = true }
        .runOnStatusCodeError(resetInitializedStateErrorAction)
    }
  }

  /**
   * Retrieves an auth credential, preferring a cached value if available.
   */
  private fun getAuthCredential(): NetworkResult<ArchiveServiceCredential> {
    val currentTime = System.currentTimeMillis()

    val credential = SignalStore.backup().credentialsByDay.getForCurrentTime(currentTime.milliseconds)

    if (credential != null) {
      return NetworkResult.Success(credential)
    }

    Log.w(TAG, "No credentials found for today, need to fetch new ones! This shouldn't happen under normal circumstances. We should ensure the routine fetch is running properly.")

    return ApplicationDependencies.getSignalServiceAccountManager().archiveApi.getServiceCredentials(currentTime).map { result ->
      SignalStore.backup().addCredentials(result.credentials.toList())
      SignalStore.backup().clearCredentialsOlderThan(currentTime)
      SignalStore.backup().credentialsByDay.getForCurrentTime(currentTime.milliseconds)!!
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

  private fun DatabaseAttachment.toArchiveMediaRequest(mediaName: MediaName, backupKey: BackupKey): ArchiveMediaRequest {
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
  val callIdToType = HashMap<Long, Long>()
}

class BackupMetadata(
  val usedSpace: Long,
  val mediaCount: Long
)
