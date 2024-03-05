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
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.Base64
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.BackupMetadata
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.backup.BackupKey
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import kotlin.random.Random

class InternalBackupPlaygroundViewModel : ViewModel() {

  private val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

  var backupData: ByteArray? = null

  val disposables = CompositeDisposable()

  private val _state: MutableState<ScreenState> = mutableStateOf(ScreenState(backupState = BackupState.NONE, uploadState = BackupUploadState.NONE, plaintext = false))
  val state: State<ScreenState> = _state

  private val _mediaState: MutableState<MediaState> = mutableStateOf(MediaState())
  val mediaState: State<MediaState> = _mediaState

  fun export() {
    _state.value = _state.value.copy(backupState = BackupState.EXPORT_IN_PROGRESS)
    val plaintext = _state.value.plaintext

    disposables += Single.fromCallable { BackupRepository.export(plaintext = plaintext) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { data ->
        backupData = data
        _state.value = _state.value.copy(backupState = BackupState.EXPORT_DONE)
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
        .subscribe { nothing ->
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
      .subscribe { nothing ->
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
      .subscribe { nothing ->
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
      .fromCallable { BackupRepository.getRemoteBackupState() }
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

  fun loadMedia() {
    disposables += Single
      .fromCallable { SignalDatabase.attachments.debugGetLatestAttachments() }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .subscribeBy {
        _mediaState.set { update(attachments = it.map { a -> BackupAttachment.from(backupKey, a) }) }
      }

    disposables += Single
      .fromCallable { BackupRepository.debugGetArchivedMediaState() }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .subscribeBy { result ->
        when (result) {
          is NetworkResult.Success -> _mediaState.set { update(archiveStateLoaded = true, backedUpMediaIds = result.result.map { it.mediaId }.toSet()) }
          else -> _mediaState.set { copy(error = MediaStateError(errorText = "$result")) }
        }
      }
  }

  fun backupAttachmentMedia(mediaIds: Set<String>) {
    disposables += Single.fromCallable { mediaIds.mapNotNull { mediaState.value.idToAttachment[it]?.dbAttachment }.toList() }
      .map { BackupRepository.archiveMedia(it) }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .doOnSubscribe { _mediaState.set { update(inProgressMediaIds = inProgressMediaIds + mediaIds) } }
      .doOnTerminate { _mediaState.set { update(inProgressMediaIds = inProgressMediaIds - mediaIds) } }
      .subscribeBy { result ->
        when (result) {
          is NetworkResult.Success -> {
            val response = result.result
            val successes = response.responses.filter { it.status == 200 }
            val failures = response.responses - successes.toSet()

            _mediaState.set {
              var updated = update(backedUpMediaIds = backedUpMediaIds + successes.map { it.mediaId })
              if (failures.isNotEmpty()) {
                updated = updated.copy(error = MediaStateError(errorText = failures.toString()))
              }
              updated
            }
          }

          else -> _mediaState.set { copy(error = MediaStateError(errorText = "$result")) }
        }
      }
  }

  fun backupAttachmentMedia(attachment: BackupAttachment) {
    disposables += Single.fromCallable { BackupRepository.archiveMedia(attachment.dbAttachment) }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .doOnSubscribe { _mediaState.set { update(inProgressMediaIds = inProgressMediaIds + attachment.mediaId) } }
      .doOnTerminate { _mediaState.set { update(inProgressMediaIds = inProgressMediaIds - attachment.mediaId) } }
      .subscribeBy {
        when (it) {
          is NetworkResult.Success -> {
            _mediaState.set { update(backedUpMediaIds = backedUpMediaIds + attachment.mediaId) }
          }

          else -> _mediaState.set { copy(error = MediaStateError(errorText = "$it")) }
        }
      }
  }

  fun deleteBackupAttachmentMedia(mediaIds: Set<String>) {
    deleteBackupAttachmentMedia(mediaIds.mapNotNull { mediaState.value.idToAttachment[it] }.toList())
  }

  fun deleteBackupAttachmentMedia(attachment: BackupAttachment) {
    deleteBackupAttachmentMedia(listOf(attachment))
  }

  private fun deleteBackupAttachmentMedia(attachments: List<BackupAttachment>) {
    val ids = attachments.map { it.mediaId }.toSet()
    disposables += Single.fromCallable { BackupRepository.deleteArchivedMedia(attachments.map { it.dbAttachment }) }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.single())
      .doOnSubscribe { _mediaState.set { update(inProgressMediaIds = inProgressMediaIds + ids) } }
      .doOnTerminate { _mediaState.set { update(inProgressMediaIds = inProgressMediaIds - ids) } }
      .subscribeBy {
        when (it) {
          is NetworkResult.Success -> {
            _mediaState.set { update(backedUpMediaIds = backedUpMediaIds - ids) }
          }

          else -> _mediaState.set { copy(error = MediaStateError(errorText = "$it")) }
        }
      }
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
    NONE, EXPORT_IN_PROGRESS(true), EXPORT_DONE, IMPORT_IN_PROGRESS(true)
  }

  enum class BackupUploadState(val inProgress: Boolean = false) {
    NONE, UPLOAD_IN_PROGRESS(true), UPLOAD_DONE, UPLOAD_FAILED
  }

  sealed class RemoteBackupState {
    object Unknown : RemoteBackupState()
    object NotFound : RemoteBackupState()
    object GeneralError : RemoteBackupState()
    data class Available(val response: BackupMetadata) : RemoteBackupState()
  }

  data class MediaState(
    val backupStateLoaded: Boolean = false,
    val attachments: List<BackupAttachment> = emptyList(),
    val backedUpMediaIds: Set<String> = emptySet(),
    val inProgressMediaIds: Set<String> = emptySet(),
    val error: MediaStateError? = null
  ) {
    val idToAttachment: Map<String, BackupAttachment> = attachments.associateBy { it.mediaId }

    fun update(
      archiveStateLoaded: Boolean = this.backupStateLoaded,
      attachments: List<BackupAttachment> = this.attachments,
      backedUpMediaIds: Set<String> = this.backedUpMediaIds,
      inProgressMediaIds: Set<String> = this.inProgressMediaIds
    ): MediaState {
      val updatedAttachments = if (archiveStateLoaded) {
        attachments.map {
          val state = if (inProgressMediaIds.contains(it.mediaId)) {
            BackupAttachment.State.IN_PROGRESS
          } else if (backedUpMediaIds.contains(it.mediaId)) {
            BackupAttachment.State.UPLOADED
          } else {
            BackupAttachment.State.LOCAL_ONLY
          }

          it.copy(state = state)
        }
      } else {
        attachments
      }

      return copy(
        backupStateLoaded = archiveStateLoaded,
        attachments = updatedAttachments,
        backedUpMediaIds = backedUpMediaIds
      )
    }
  }

  data class BackupAttachment(
    val dbAttachment: DatabaseAttachment,
    val state: State = State.INIT,
    val mediaId: String = Base64.encodeUrlSafeWithPadding(Random.nextBytes(15))
  ) {
    val id: Any = dbAttachment.attachmentId
    val title: String = dbAttachment.attachmentId.toString()

    enum class State {
      INIT,
      LOCAL_ONLY,
      UPLOADED,
      IN_PROGRESS
    }

    companion object {
      fun from(backupKey: BackupKey, dbAttachment: DatabaseAttachment): BackupAttachment {
        return BackupAttachment(
          dbAttachment = dbAttachment,
          mediaId = backupKey.deriveMediaId(Base64.decode(dbAttachment.dataHash!!)).toString()
        )
      }
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
