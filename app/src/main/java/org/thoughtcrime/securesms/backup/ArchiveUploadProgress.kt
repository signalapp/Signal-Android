/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.signal.core.util.throttleLatest
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ArchiveCommitAttachmentDeletesJob
import org.thoughtcrime.securesms.jobs.ArchiveThumbnailUploadJob
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.UploadAttachmentToArchiveJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks the progress of uploading your message archive and provides an observable stream of results.
 */
object ArchiveUploadProgress {

  private val TAG = Log.tag(ArchiveUploadProgress::class)

  private val PROGRESS_NONE = ArchiveUploadProgressState(
    state = ArchiveUploadProgressState.State.None
  )

  private val _progress: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1)

  private var uploadProgress: ArchiveUploadProgressState = SignalStore.backup.archiveUploadState ?: PROGRESS_NONE

  private val partialMediaProgress: MutableMap<AttachmentId, Long> = ConcurrentHashMap()

  /**
   * Observe this to get updates on the current upload progress.
   */
  val progress: Flow<ArchiveUploadProgressState> = _progress
    .throttleLatest(500.milliseconds) {
      uploadProgress.state == ArchiveUploadProgressState.State.None ||
        (uploadProgress.state == ArchiveUploadProgressState.State.UploadBackupFile && uploadProgress.backupFileUploadedBytes == 0L) ||
        (uploadProgress.state == ArchiveUploadProgressState.State.UploadMedia && uploadProgress.mediaUploadedBytes == 0L)
    }
    .map {
      if (uploadProgress.state != ArchiveUploadProgressState.State.UploadMedia) {
        return@map uploadProgress
      }

      if (!SignalStore.backup.backsUpMedia) {
        Log.i(TAG, "Doesn't upload media. Done!")
        return@map PROGRESS_NONE
      }

      val pendingMediaUploadBytes = SignalDatabase.attachments.getPendingArchiveUploadBytes() - partialMediaProgress.values.sum()
      if (pendingMediaUploadBytes <= 0) {
        Log.i(TAG, "No more pending bytes. Done!")
        return@map PROGRESS_NONE
      }

      // It's possible that new attachments may be pending upload after we start a backup.
      // If we wanted the most accurate progress possible, we could maintain a new database flag that indicates whether an attachment has been flagged as part
      // of the current upload batch. However, this gets us pretty close while keeping things simple and not having to juggle extra flags, with the caveat that
      // the progress bar may occasionally be including media that is not actually referenced in the active backup file.
      val totalMediaUploadBytes = max(uploadProgress.mediaTotalBytes, pendingMediaUploadBytes)

      ArchiveUploadProgressState(
        state = ArchiveUploadProgressState.State.UploadMedia,
        mediaUploadedBytes = totalMediaUploadBytes - pendingMediaUploadBytes,
        mediaTotalBytes = totalMediaUploadBytes
      )
    }
    .onEach { updated ->
      updateState(notify = false) { updated }
    }
    .onStart { emit(uploadProgress) }
    .flowOn(Dispatchers.IO)

  val inProgress
    get() = uploadProgress.state != ArchiveUploadProgressState.State.None

  fun begin() {
    updateState(overrideCancel = true) {
      ArchiveUploadProgressState(
        state = ArchiveUploadProgressState.State.Export
      )
    }
  }

  fun cancel() {
    updateState {
      ArchiveUploadProgressState(
        state = ArchiveUploadProgressState.State.UserCanceled
      )
    }

    BackupMessagesJob.cancel()

    AppDependencies.jobManager.cancelAllInQueue(ArchiveCommitAttachmentDeletesJob.ARCHIVE_ATTACHMENT_QUEUE)
    AppDependencies.jobManager.cancelAllInQueues(UploadAttachmentToArchiveJob.QUEUES)
    AppDependencies.jobManager.cancelAllInQueue(ArchiveThumbnailUploadJob.KEY)
  }

  @WorkerThread
  suspend fun cancelAndBlock() {
    Log.d(TAG, "Canceling upload.")
    cancel()

    withContext(Dispatchers.IO) {
      Log.d(TAG, "Flushing job manager queue...")
      AppDependencies.jobManager.flush()

      val queues = UploadAttachmentToArchiveJob.QUEUES + ArchiveThumbnailUploadJob.QUEUES + ArchiveCommitAttachmentDeletesJob.ARCHIVE_ATTACHMENT_QUEUE
      Log.d(TAG, "Waiting for cancelations to occur...")
      while (!AppDependencies.jobManager.areQueuesEmpty(queues)) {
        delay(1.seconds)
      }
    }
  }

  fun onMessageBackupCreated(backupFileSize: Long) {
    updateState {
      it.copy(
        state = ArchiveUploadProgressState.State.UploadBackupFile,
        backupFileTotalBytes = backupFileSize,
        backupFileUploadedBytes = 0
      )
    }
  }

  fun onMessageBackupUploadProgress(progress: AttachmentTransferProgress) {
    updateState {
      it.copy(
        state = ArchiveUploadProgressState.State.UploadBackupFile,
        backupFileUploadedBytes = progress.transmitted.inWholeBytes,
        backupFileTotalBytes = progress.total.inWholeBytes
      )
    }
  }

  fun onAttachmentsStarted(totalAttachmentBytes: Long) {
    updateState {
      it.copy(
        state = ArchiveUploadProgressState.State.UploadMedia,
        mediaUploadedBytes = 0,
        mediaTotalBytes = totalAttachmentBytes
      )
    }
  }

  fun onAttachmentProgress(attachmentId: AttachmentId, bytesUploaded: Long) {
    partialMediaProgress[attachmentId] = bytesUploaded
    _progress.tryEmit(Unit)
  }

  fun onAttachmentFinished(attachmentId: AttachmentId) {
    partialMediaProgress.remove(attachmentId)
    _progress.tryEmit(Unit)
  }

  fun onMessageBackupFinishedEarly() {
    updateState { PROGRESS_NONE }
  }

  fun onValidationFailure() {
    updateState { PROGRESS_NONE }
  }

  fun onMainBackupFileUploadFailure() {
    updateState { PROGRESS_NONE }
  }

  private fun updateState(
    notify: Boolean = true,
    overrideCancel: Boolean = false,
    transform: (ArchiveUploadProgressState) -> ArchiveUploadProgressState
  ) {
    val newState = transform(uploadProgress).let { state ->
      val oldArchiveState = uploadProgress.state
      if (oldArchiveState == ArchiveUploadProgressState.State.UserCanceled && !overrideCancel) {
        state.copy(state = ArchiveUploadProgressState.State.UserCanceled)
      } else {
        state
      }
    }

    if (uploadProgress == newState) {
      return
    }

    uploadProgress = newState
    SignalStore.backup.archiveUploadState = newState

    if (notify) {
      _progress.tryEmit(Unit)
    }
  }

  object ArchiveBackupProgressListener : BackupRepository.ExportProgressListener {
    override fun onAccount() {
      updatePhase(ArchiveUploadProgressState.BackupPhase.Account)
    }

    override fun onRecipient() {
      updatePhase(ArchiveUploadProgressState.BackupPhase.Recipient)
    }

    override fun onThread() {
      updatePhase(ArchiveUploadProgressState.BackupPhase.Thread)
    }

    override fun onCall() {
      updatePhase(ArchiveUploadProgressState.BackupPhase.Call)
    }

    override fun onSticker() {
      updatePhase(ArchiveUploadProgressState.BackupPhase.Sticker)
    }

    override fun onNotificationProfile() {
      updatePhase(ArchiveUploadProgressState.BackupPhase.NotificationProfile)
    }

    override fun onChatFolder() {
      updatePhase(ArchiveUploadProgressState.BackupPhase.ChatFolder)
    }

    override fun onMessage(currentProgress: Long, approximateCount: Long) {
      updatePhase(ArchiveUploadProgressState.BackupPhase.Message, currentProgress, approximateCount)
    }

    override fun onAttachment(currentProgress: Long, totalCount: Long) {
      updatePhase(ArchiveUploadProgressState.BackupPhase.BackupPhaseNone)
    }

    private fun updatePhase(
      phase: ArchiveUploadProgressState.BackupPhase,
      exportedFrames: Long = 0L,
      totalFrames: Long = 0L
    ) {
      updateState {
        ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = phase,
          frameExportCount = exportedFrames,
          frameTotalCount = totalFrames
        )
      }
    }
  }
}
