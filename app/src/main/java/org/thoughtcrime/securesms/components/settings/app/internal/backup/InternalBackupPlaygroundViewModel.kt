/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.backup

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.Hex
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.copyTo
import org.signal.core.util.logging.Log
import org.signal.core.util.readNBytesOrThrow
import org.signal.core.util.roundedString
import org.signal.core.util.stream.LimitedInputStream
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.ArchiveValidator
import org.thoughtcrime.securesms.backup.v2.BackupMetadata
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.ArchiveResult
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver.FailureCause
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader.Companion.MAC_SIZE
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob
import org.thoughtcrime.securesms.jobs.BackfillDigestJob
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.BackupRestoreJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.jobs.CopyAttachmentToArchiveJob
import org.thoughtcrime.securesms.jobs.RestoreAttachmentJob
import org.thoughtcrime.securesms.jobs.RestoreAttachmentThumbnailJob
import org.thoughtcrime.securesms.jobs.RestoreLocalAttachmentJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds

class InternalBackupPlaygroundViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(InternalBackupPlaygroundViewModel::class)
  }

  val disposables = CompositeDisposable()

  private val _state: MutableState<ScreenState> = mutableStateOf(
    ScreenState(
      canReadWriteBackupDirectory = SignalStore.settings.signalBackupDirectory?.let {
        val file = DocumentFile.fromTreeUri(AppDependencies.application, it)
        file != null && file.canWrite() && file.canRead()
      } ?: false,
      backupTier = SignalStore.backup.backupTier
    )
  )
  val state: State<ScreenState> = _state

  private val _mediaState: MutableState<MediaState> = mutableStateOf(MediaState())
  val mediaState: State<MediaState> = _mediaState

  enum class DialogState {
    None,
    ImportCredentials
  }

  fun exportEncrypted(openStream: () -> OutputStream, appendStream: () -> OutputStream) {
    _state.value = _state.value.copy(statusMessage = "Exporting encrypted backup to disk...")
    disposables += Single
      .fromCallable {
        BackupRepository.export(
          outputStream = openStream(),
          append = { bytes -> appendStream().use { it.write(bytes) } }
        )
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { data ->
        _state.value = _state.value.copy(statusMessage = "Encrypted backup complete!")
      }
  }

  fun exportPlaintext(openStream: () -> OutputStream, appendStream: () -> OutputStream) {
    _state.value = _state.value.copy(statusMessage = "Exporting plaintext backup to disk...")
    disposables += Single
      .fromCallable {
        BackupRepository.export(
          outputStream = openStream(),
          append = { bytes -> appendStream().use { it.write(bytes) } },
          plaintext = true
        )
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { data ->
        _state.value = _state.value.copy(statusMessage = "Plaintext backup complete!")
      }
  }

  fun validateBackup() {
    _state.value = _state.value.copy(statusMessage = "Exporting to a temporary file...")
    val tempFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)

    disposables += Single
      .fromCallable {
        BackupRepository.export(
          outputStream = FileOutputStream(tempFile),
          append = { bytes -> tempFile.appendBytes(bytes) }
        )
        _state.value = _state.value.copy(statusMessage = "Export complete! Validating...")
        ArchiveValidator.validate(tempFile, SignalStore.backup.messageBackupKey, forTransfer = false)
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { result ->
        val message = when (result) {
          is ArchiveValidator.ValidationResult.ReadError -> "Failed to read backup file!"
          ArchiveValidator.ValidationResult.Success -> "Validation passed!"
          is ArchiveValidator.ValidationResult.MessageValidationError -> {
            Log.w(TAG, "Validation failed! Details: ${result.messageDetails}", result.exception)
            "Validation failed :( Check the logs for details."
          }

          is ArchiveValidator.ValidationResult.RecipientDuplicateE164Error -> {
            Log.w(TAG, "Validation failed with a duplicate recipient! Details: ${result.details}", result.exception)
            "Validation failed :( Check the logs for details."
          }
        }
        _state.value = _state.value.copy(statusMessage = message)
      }
  }

  fun triggerBackupJob() {
    _state.value = _state.value.copy(statusMessage = "Upload job in progress...")

    disposables += Single.fromCallable { AppDependencies.jobManager.runSynchronously(BackupMessagesJob(), 120_000) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { result ->
        _state.value = _state.value.copy(statusMessage = "Upload job complete! Result: ${result.takeIf { it.isPresent }?.get() ?: "N/A"}")
      }
  }

  fun importEncryptedBackup(length: Long, inputStreamFactory: () -> InputStream) {
    val customCredentials: ImportCredentials? = _state.value.customBackupCredentials
    _state.value = _state.value.copy(statusMessage = "Importing encrypted backup...", customBackupCredentials = null)

    val self = Recipient.self()
    val aci = customCredentials?.aci ?: self.aci.get()
    val selfData = BackupRepository.SelfData(aci, self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))
    val backupKey = customCredentials?.messageBackupKey ?: SignalStore.backup.messageBackupKey

    disposables += Single.fromCallable { BackupRepository.import(length, inputStreamFactory, selfData, backupKey) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        _state.value = _state.value.copy(statusMessage = "Encrypted backup import complete!")
      }
  }

  fun import(uri: Uri) {
    _state.value = _state.value.copy(statusMessage = "Importing new-style local backup...")

    val self = Recipient.self()
    val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

    disposables += Single.fromCallable {
      val archiveFileSystem = ArchiveFileSystem.fromUri(AppDependencies.application, uri)!!
      val snapshotInfo = archiveFileSystem.listSnapshots().firstOrNull() ?: return@fromCallable ArchiveResult.failure(FailureCause.MAIN_STREAM)
      val snapshotFileSystem = SnapshotFileSystem(AppDependencies.application, snapshotInfo.file)

      LocalArchiver.import(snapshotFileSystem, selfData)

      val mediaNameToFileInfo = archiveFileSystem.filesFileSystem.allFiles()
      RestoreLocalAttachmentJob.enqueueRestoreLocalAttachmentsJobs(mediaNameToFileInfo)
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        _state.value = _state.value.copy(statusMessage = "New-style local backup import complete!")
      }
  }

  fun haltAllJobs() {
    AppDependencies.jobManager.cancelAllInQueue(BackfillDigestJob.QUEUE)
    AppDependencies.jobManager.cancelAllInQueue("ArchiveAttachmentJobs_0")
    AppDependencies.jobManager.cancelAllInQueue("ArchiveAttachmentJobs_1")
    AppDependencies.jobManager.cancelAllInQueue("ArchiveThumbnailUploadJob")
    AppDependencies.jobManager.cancelAllInQueue("BackupRestoreJob")
    AppDependencies.jobManager.cancelAllInQueue("__LOCAL_BACKUP__")
  }

  fun fetchRemoteBackupAndWritePlaintext(outputStream: OutputStream?) {
    check(outputStream != null)

    SignalExecutors.BOUNDED_IO.execute {
      Log.d(TAG, "Downloading file...")
      val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)

      when (val result = BackupRepository.downloadBackupFile(tempBackupFile)) {
        is NetworkResult.Success -> Log.i(TAG, "Download successful")
        else -> {
          Log.w(TAG, "Failed to download backup file", result.getCause())
          throw IOException(result.getCause())
        }
      }

      val encryptedStream = tempBackupFile.inputStream()
      val iv = encryptedStream.readNBytesOrThrow(16)
      val backupKey = SignalStore.backup.messageBackupKey
      val keyMaterial = backupKey.deriveBackupSecrets(Recipient.self().aci.get())
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(keyMaterial.aesKey, "AES"), IvParameterSpec(iv))
      }

      val plaintextStream = GZIPInputStream(
        CipherInputStream(
          LimitedInputStream(
            wrapped = encryptedStream,
            maxBytes = tempBackupFile.length() - MAC_SIZE
          ),
          cipher
        )
      )

      Log.d(TAG, "Copying...")
      plaintextStream.copyTo(outputStream)
      Log.d(TAG, "Done!")
    }
  }

  fun checkRemoteBackupState() {
    disposables += Single
      .fromCallable {
        BackupRepository.restoreBackupTier(SignalStore.account.requireAci())
        BackupRepository.getRemoteBackupState()
      }
      .subscribeOn(Schedulers.io())
      .subscribe { result ->
        when {
          result is NetworkResult.Success -> {
            _state.value = _state.value.copy(
              statusMessage = "Remote backup exists. ${result.result.mediaCount} media items, using ${result.result.usedSpace} bytes (${result.result.usedSpace.bytes.inMebiBytes.roundedString(3)} MiB)"
            )
          }

          result is NetworkResult.StatusCodeError && result.code == 404 -> {
            _state.value = _state.value.copy(statusMessage = "Remote backup does not exists.")
          }

          else -> {
            Log.w(TAG, "Error checking remote backup state", result.getCause())
            _state.value = _state.value.copy(statusMessage = "Failed to fetch remote backup state.")
          }
        }
      }
  }

  fun wipeAllDataAndRestoreFromRemote() {
    SignalExecutors.BOUNDED_IO.execute {
      restoreFromRemote()
    }
  }

  fun onBackupTierSelected(backupTier: MessageBackupTier?) {
    SignalStore.backup.backupTier = backupTier
    _state.value = _state.value.copy(backupTier = backupTier)
  }

  fun onImportSelected() {
    _state.value = _state.value.copy(dialog = DialogState.ImportCredentials)
  }

  /** True if data is valid, else false */
  fun onImportConfirmed(aci: String, backupKey: String): Boolean {
    val parsedAci: ACI? = ACI.parseOrNull(aci)

    if (aci.isNotBlank() && parsedAci == null) {
      _state.value = _state.value.copy(statusMessage = "Invalid ACI! Cannot import.")
      return false
    }

    val parsedBackupKey: MessageBackupKey? = try {
      val bytes = Hex.fromStringOrThrow(backupKey)
      MessageBackupKey(bytes)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to parse key!", e)
      null
    }

    if (backupKey.isNotBlank() && parsedBackupKey == null) {
      _state.value = _state.value.copy(statusMessage = "Invalid AEP! Cannot import.")
      return false
    }

    _state.value = state.value.copy(
      customBackupCredentials = ImportCredentials(
        messageBackupKey = parsedBackupKey ?: SignalStore.backup.messageBackupKey,
        aci = parsedAci ?: SignalStore.account.aci!!
      )
    )

    return true
  }

  fun onDialogDismissed() {
    _state.value = _state.value.copy(dialog = DialogState.None)
  }

  private fun restoreFromRemote() {
    _state.value = _state.value.copy(statusMessage = "Importing from remote...")

    disposables += Single.fromCallable {
      AppDependencies
        .jobManager
        .startChain(BackupRestoreJob())
        .then(BackupRestoreMediaJob())
        .enqueueAndBlockUntilCompletion(120.seconds.inWholeMilliseconds)
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        _state.value = _state.value.copy(statusMessage = "Import complete!")
      }
  }

  fun loadMedia() {
    disposables += Single
      .fromCallable { SignalDatabase.attachments.debugGetLatestAttachments() }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .subscribeBy {
        _mediaState.set { update(attachments = it.map { a -> BackupAttachment(dbAttachment = a) }) }
      }
  }

  fun archiveAttachmentMedia(attachments: Set<AttachmentId>) {
    disposables += Single
      .fromCallable {
        val toArchive = mediaState.value
          .attachments
          .filter { attachments.contains(it.dbAttachment.attachmentId) }
          .map { it.dbAttachment }

        BackupRepository.copyAttachmentToArchive(toArchive)
      }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .doOnSubscribe { _mediaState.set { update(inProgress = inProgressMediaIds + attachments) } }
      .doOnTerminate { _mediaState.set { update(inProgress = inProgressMediaIds - attachments) } }
      .subscribeBy { result ->
        when (result) {
          is NetworkResult.Success -> {
            loadMedia()
            result
              .result
              .sourceNotFoundResponses
              .forEach {
                reUploadAndArchiveMedia(result.result.mediaIdToAttachmentId(it.mediaId))
              }
          }

          else -> _mediaState.set { copy(error = MediaStateError(errorText = "$result")) }
        }
      }
  }

  fun archiveAttachmentMedia(attachment: BackupAttachment) {
    disposables += Single.fromCallable { BackupRepository.copyAttachmentToArchive(attachment.dbAttachment) }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .doOnSubscribe { _mediaState.set { update(inProgress = inProgressMediaIds + attachment.dbAttachment.attachmentId) } }
      .doOnTerminate { _mediaState.set { update(inProgress = inProgressMediaIds - attachment.dbAttachment.attachmentId) } }
      .subscribeBy { result ->
        when (result) {
          is NetworkResult.Success -> loadMedia()
          is NetworkResult.StatusCodeError -> {
            if (result.code == 410) {
              reUploadAndArchiveMedia(attachment.id)
            } else {
              _mediaState.set { copy(error = MediaStateError(errorText = "$result")) }
            }
          }

          else -> _mediaState.set { copy(error = MediaStateError(errorText = "$result")) }
        }
      }
  }

  private fun reUploadAndArchiveMedia(attachmentId: AttachmentId) {
    disposables += Single
      .fromCallable {
        AppDependencies
          .jobManager
          .startChain(AttachmentUploadJob(attachmentId))
          .then(CopyAttachmentToArchiveJob(attachmentId))
          .enqueueAndBlockUntilCompletion(15.seconds.inWholeMilliseconds)
      }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .doOnSubscribe { _mediaState.set { update(inProgress = inProgressMediaIds + attachmentId) } }
      .doOnTerminate { _mediaState.set { update(inProgress = inProgressMediaIds - attachmentId) } }
      .subscribeBy {
        if (it.isPresent && it.get().isComplete) {
          loadMedia()
        } else {
          _mediaState.set { copy(error = MediaStateError(errorText = "Reupload slow or failed, try again")) }
        }
      }
  }

  fun deleteArchivedMedia(attachmentIds: Set<AttachmentId>) {
    deleteArchivedMedia(mediaState.value.attachments.filter { attachmentIds.contains(it.dbAttachment.attachmentId) })
  }

  fun deleteArchivedMedia(attachment: BackupAttachment) {
    deleteArchivedMedia(listOf(attachment))
  }

  private fun deleteArchivedMedia(attachments: List<BackupAttachment>) {
    val ids = attachments.map { it.dbAttachment.attachmentId }.toSet()
    disposables += Single.fromCallable { BackupRepository.deleteArchivedMedia(attachments.map { it.dbAttachment }) }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .doOnSubscribe { _mediaState.set { update(inProgress = inProgressMediaIds + ids) } }
      .doOnTerminate { _mediaState.set { update(inProgress = inProgressMediaIds - ids) } }
      .subscribeBy {
        when (it) {
          is NetworkResult.Success -> loadMedia()
          else -> _mediaState.set { copy(error = MediaStateError(errorText = "$it")) }
        }
      }
  }

  fun deleteAllArchivedMedia() {
    disposables += Single
      .fromCallable { BackupRepository.debugDeleteAllArchivedMedia() }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .subscribeBy { result ->
        when (result) {
          is NetworkResult.Success -> loadMedia()
          else -> _mediaState.set { copy(error = MediaStateError(errorText = "$result")) }
        }
      }
  }

  fun restoreArchivedMedia(attachment: BackupAttachment, asThumbnail: Boolean) {
    disposables += Completable
      .fromCallable {
        val recipientId = SignalStore.releaseChannel.releaseChannelRecipientId!!
        val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))

        val message = IncomingMessage(
          type = MessageType.NORMAL,
          from = recipientId,
          sentTimeMillis = System.currentTimeMillis(),
          serverTimeMillis = System.currentTimeMillis(),
          receivedTimeMillis = System.currentTimeMillis(),
          body = "Restored from Archive!?",
          serverGuid = UUID.randomUUID().toString()
        )

        val insertMessage = SignalDatabase.messages.insertMessageInbox(message, threadId).get()

        SignalDatabase.attachments.debugCopyAttachmentForArchiveRestore(
          mmsId = insertMessage.messageId,
          attachment = attachment.dbAttachment,
          forThumbnail = asThumbnail
        )

        val archivedAttachment = SignalDatabase.attachments.getAttachmentsForMessage(insertMessage.messageId).first()

        if (asThumbnail) {
          AppDependencies.jobManager.add(
            RestoreAttachmentThumbnailJob(
              messageId = insertMessage.messageId,
              attachmentId = archivedAttachment.attachmentId,
              highPriority = false
            )
          )
        } else {
          AppDependencies.jobManager.add(
            RestoreAttachmentJob.forInitialRestore(
              messageId = insertMessage.messageId,
              attachmentId = archivedAttachment.attachmentId
            )
          )
        }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .subscribeBy(
        onError = {
          _mediaState.set { copy(error = MediaStateError(errorText = "$it")) }
        }
      )
  }

  suspend fun deleteRemoteBackupData(): Boolean = withContext(Dispatchers.IO) {
    when (val result = BackupRepository.debugDeleteAllArchivedMedia()) {
      is NetworkResult.Success -> Log.i(TAG, "Remote data deleted")
      else -> {
        Log.w(TAG, "Unable to delete media", result.getCause())
        return@withContext false
      }
    }

    when (val result = BackupRepository.deleteBackup()) {
      is NetworkResult.Success -> {
        SignalStore.backup.backupsInitialized = false
        SignalStore.backup.messageCredentials.clearAll()
        SignalStore.backup.mediaCredentials.clearAll()
        SignalStore.backup.cachedMediaCdnPath = null
        return@withContext true
      }
      else -> Log.w(TAG, "Unable to delete remote data", result.getCause())
    }

    return@withContext false
  }

  override fun onCleared() {
    disposables.clear()
  }

  data class ScreenState(
    val canReadWriteBackupDirectory: Boolean = false,
    val backupTier: MessageBackupTier? = null,
    val statusMessage: String? = null,
    val customBackupCredentials: ImportCredentials? = null,
    val dialog: DialogState = DialogState.None
  )

  sealed class RemoteBackupState {
    data object Unknown : RemoteBackupState()
    data object NotFound : RemoteBackupState()
    data class Available(val response: BackupMetadata) : RemoteBackupState()
  }

  data class MediaState(
    val attachments: List<BackupAttachment> = emptyList(),
    val inProgressMediaIds: Set<AttachmentId> = emptySet(),
    val error: MediaStateError? = null
  ) {
    fun update(
      attachments: List<BackupAttachment> = this.attachments,
      inProgress: Set<AttachmentId> = this.inProgressMediaIds
    ): MediaState {
      val updatedAttachments = attachments.map {
        val state = if (inProgress.contains(it.dbAttachment.attachmentId)) {
          BackupAttachment.State.IN_PROGRESS
        } else if (it.dbAttachment.archiveTransferState == AttachmentTable.ArchiveTransferState.FINISHED) {
          if (it.dbAttachment.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE) {
            BackupAttachment.State.UPLOADED_FINAL
          } else {
            BackupAttachment.State.UPLOADED_UNDOWNLOADED
          }
        } else if (it.dbAttachment.remoteLocation != null) {
          BackupAttachment.State.ATTACHMENT_CDN
        } else {
          BackupAttachment.State.LOCAL_ONLY
        }

        it.copy(state = state)
      }

      return copy(
        attachments = updatedAttachments
      )
    }
  }

  data class BackupAttachment(
    val dbAttachment: DatabaseAttachment,
    val state: State = State.LOCAL_ONLY
  ) {
    val id: AttachmentId = dbAttachment.attachmentId
    val title: String = dbAttachment.attachmentId.toString()

    enum class State {
      ATTACHMENT_CDN,
      LOCAL_ONLY,
      UPLOADED_UNDOWNLOADED,
      UPLOADED_FINAL,
      IN_PROGRESS
    }
  }

  data class MediaStateError(
    val id: UUID = UUID.randomUUID(),
    val errorText: String
  )

  fun <T> MutableState<T>.set(update: T.() -> T) {
    this.value = this.value.update()
  }

  data class ImportCredentials(
    val messageBackupKey: MessageBackupKey,
    val aci: ACI
  )
}
