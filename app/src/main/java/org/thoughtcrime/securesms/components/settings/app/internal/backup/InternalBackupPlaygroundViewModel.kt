/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.backup

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.BackupMetadata
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ArchiveAttachmentJob
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.BackupRestoreJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.jobs.SyncArchivedMediaJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.backup.MediaName
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class InternalBackupPlaygroundViewModel : ViewModel() {

  var backupData: ByteArray? = null

  val disposables = CompositeDisposable()

  private val _state: MutableState<ScreenState> = mutableStateOf(ScreenState(backupState = BackupState.NONE, uploadState = BackupUploadState.NONE, plaintext = false))
  val state: State<ScreenState> = _state

  private val _mediaState: MutableState<MediaState> = mutableStateOf(MediaState())
  val mediaState: State<MediaState> = _mediaState

  fun export() {
    _state.value = _state.value.copy(backupState = BackupState.EXPORT_IN_PROGRESS)
    val plaintext = _state.value.plaintext

    disposables += Single.fromCallable { BackupRepository.debugExport(plaintext = plaintext) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { data ->
        backupData = data
        _state.value = _state.value.copy(backupState = BackupState.EXPORT_DONE)
      }
  }

  fun triggerBackupJob() {
    _state.value = _state.value.copy(backupState = BackupState.EXPORT_IN_PROGRESS)

    disposables += Single.fromCallable { AppDependencies.jobManager.runSynchronously(BackupMessagesJob(), 120_000) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        _state.value = _state.value.copy(backupState = BackupState.BACKUP_JOB_DONE)
      }
  }

  fun import() {
    backupData?.let {
      _state.value = _state.value.copy(backupState = BackupState.IMPORT_IN_PROGRESS)
      val plaintext = _state.value.plaintext

      val self = Recipient.self()
      val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

      disposables += Single.fromCallable { BackupRepository.import(it.size.toLong(), { ByteArrayInputStream(it) }, selfData, plaintext = plaintext) }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy {
          backupData = null
          _state.value = _state.value.copy(backupState = BackupState.NONE)
        }
    }
  }

  fun import(length: Long, inputStreamFactory: () -> InputStream) {
    _state.value = _state.value.copy(backupState = BackupState.IMPORT_IN_PROGRESS)
    val plaintext = _state.value.plaintext

    val self = Recipient.self()
    val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

    disposables += Single.fromCallable { BackupRepository.import(length, inputStreamFactory, selfData, plaintext = plaintext) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        backupData = null
        _state.value = _state.value.copy(backupState = BackupState.NONE)
      }
  }

  fun validate(length: Long, inputStreamFactory: () -> InputStream) {
    val self = Recipient.self()
    val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

    disposables += Single.fromCallable { BackupRepository.validate(length, inputStreamFactory, selfData) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        backupData = null
        _state.value = _state.value.copy(backupState = BackupState.NONE)
      }
  }

  fun onPlaintextToggled() {
    _state.value = _state.value.copy(plaintext = !_state.value.plaintext)
  }

  fun uploadBackupToRemote() {
    _state.value = _state.value.copy(uploadState = BackupUploadState.UPLOAD_IN_PROGRESS)

    disposables += Single
      .fromCallable { BackupRepository.uploadBackupFile(backupData!!.inputStream(), backupData!!.size.toLong()) }
      .subscribeOn(Schedulers.io())
      .subscribe { success ->
        _state.value = _state.value.copy(uploadState = if (success) BackupUploadState.UPLOAD_DONE else BackupUploadState.UPLOAD_FAILED)
      }
  }

  fun checkRemoteBackupState() {
    _state.value = _state.value.copy(remoteBackupState = RemoteBackupState.Unknown)

    disposables += Single
      .fromCallable {
        BackupRepository.restoreBackupTier()
        BackupRepository.getRemoteBackupState()
      }
      .subscribeOn(Schedulers.io())
      .subscribe { result ->
        when {
          result is NetworkResult.Success -> {
            _state.value = _state.value.copy(remoteBackupState = RemoteBackupState.Available(result.result))
          }

          result is NetworkResult.StatusCodeError && result.code == 404 -> {
            _state.value = _state.value.copy(remoteBackupState = RemoteBackupState.NotFound)
          }

          else -> {
            _state.value = _state.value.copy(remoteBackupState = RemoteBackupState.GeneralError)
          }
        }
      }
  }

  fun restoreFromRemote() {
    _state.value = _state.value.copy(backupState = BackupState.IMPORT_IN_PROGRESS)

    disposables += Single.fromCallable {
      AppDependencies
        .jobManager
        .startChain(BackupRestoreJob())
        .then(SyncArchivedMediaJob())
        .then(BackupRestoreMediaJob())
        .enqueueAndBlockUntilCompletion(120.seconds.inWholeMilliseconds)
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        _state.value = _state.value.copy(backupState = BackupState.NONE)
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

        BackupRepository.archiveMedia(toArchive)
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
    disposables += Single.fromCallable { BackupRepository.archiveMedia(attachment.dbAttachment) }
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
          .then(ArchiveAttachmentJob(attachmentId))
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

  fun restoreArchivedMedia(attachment: BackupAttachment) {
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
          insertMessage.messageId,
          attachment.dbAttachment
        )

        val archivedAttachment = SignalDatabase.attachments.getAttachmentsForMessage(insertMessage.messageId).first()

        AppDependencies.jobManager.add(
          AttachmentDownloadJob(
            messageId = insertMessage.messageId,
            attachmentId = archivedAttachment.attachmentId,
            manual = false,
            forceArchiveDownload = true
          )
        )
      }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .subscribeBy(
        onError = {
          _mediaState.set { copy(error = MediaStateError(errorText = "$it")) }
        }
      )
  }

  override fun onCleared() {
    disposables.clear()
  }

  data class ScreenState(
    val backupState: BackupState = BackupState.NONE,
    val uploadState: BackupUploadState = BackupUploadState.NONE,
    val remoteBackupState: RemoteBackupState = RemoteBackupState.Unknown,
    val plaintext: Boolean
  )

  enum class BackupState(val inProgress: Boolean = false) {
    NONE,
    EXPORT_IN_PROGRESS(true),
    EXPORT_DONE,
    BACKUP_JOB_DONE,
    IMPORT_IN_PROGRESS(true)
  }

  enum class BackupUploadState(val inProgress: Boolean = false) {
    NONE,
    UPLOAD_IN_PROGRESS(true),
    UPLOAD_DONE,
    UPLOAD_FAILED
  }

  sealed class RemoteBackupState {
    object Unknown : RemoteBackupState()
    object NotFound : RemoteBackupState()
    object GeneralError : RemoteBackupState()
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
      val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()

      val updatedAttachments = attachments.map {
        val state = if (inProgress.contains(it.dbAttachment.attachmentId)) {
          BackupAttachment.State.IN_PROGRESS
        } else if (it.dbAttachment.archiveMediaName != null) {
          if (it.dbAttachment.remoteDigest != null) {
            val mediaId = backupKey.deriveMediaId(MediaName(it.dbAttachment.archiveMediaName)).encode()
            if (it.dbAttachment.archiveMediaId == mediaId) {
              BackupAttachment.State.UPLOADED_FINAL
            } else {
              BackupAttachment.State.UPLOADED_UNDOWNLOADED
            }
          } else {
            BackupAttachment.State.UPLOADED_UNDOWNLOADED
          }
        } else if (it.dbAttachment.dataHash == null) {
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
}
