/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import org.signal.core.util.throttleLatest
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tracks the progress of uploading your message archive and provides an observable stream of results.
 */
object ArchiveUploadProgress {

  private val PROGRESS_NONE = ArchiveUploadProgressState(
    state = ArchiveUploadProgressState.State.None
  )

  private val _progress: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1)

  private var uploadProgress: ArchiveUploadProgressState = SignalStore.backup.archiveUploadState ?: PROGRESS_NONE

  /**
   * Observe this to get updates on the current upload progress.
   */
  val progress: Flow<ArchiveUploadProgressState> = _progress
    .throttleLatest(500.milliseconds)
    .map {
      if (uploadProgress.state != ArchiveUploadProgressState.State.UploadingAttachments) {
        return@map uploadProgress
      }

      val pendingCount = SignalDatabase.attachments.getPendingArchiveUploadCount()
      if (pendingCount == uploadProgress.totalAttachments) {
        return@map PROGRESS_NONE
      }

      // It's possible that new attachments may be pending upload after we start a backup.
      // If we wanted the most accurate progress possible, we could maintain a new database flag that indicates whether an attachment has been flagged as part
      // of the current upload batch. However, this gets us pretty close while keeping things simple and not having to juggle extra flags, with the caveat that
      // the progress bar may occasionally be including media that is not actually referenced in the active backup file.
      val totalCount = max(uploadProgress.totalAttachments, pendingCount)

      ArchiveUploadProgressState(
        state = ArchiveUploadProgressState.State.UploadingAttachments,
        completedAttachments = totalCount - pendingCount,
        totalAttachments = totalCount
      )
    }
    .onEach {
      updateState(it, notify = false)
    }
    .flowOn(Dispatchers.IO)
    .shareIn(scope = CoroutineScope(Dispatchers.IO), started = SharingStarted.WhileSubscribed(), replay = 1)

  val inProgress
    get() = uploadProgress.state != ArchiveUploadProgressState.State.None

  fun begin() {
    updateState(
      ArchiveUploadProgressState(
        state = ArchiveUploadProgressState.State.BackingUpMessages
      )
    )
  }

  fun onMessageBackupCreated() {
    updateState(
      ArchiveUploadProgressState(
        state = ArchiveUploadProgressState.State.UploadingMessages
      )
    )
  }

  fun onAttachmentsStarted(attachmentCount: Long) {
    updateState(
      ArchiveUploadProgressState(
        state = ArchiveUploadProgressState.State.UploadingAttachments,
        completedAttachments = 0,
        totalAttachments = attachmentCount
      )
    )
  }

  fun onAttachmentFinished() {
    _progress.tryEmit(Unit)
  }

  fun onMessageBackupFinishedEarly() {
    updateState(PROGRESS_NONE)
  }

  private fun updateState(state: ArchiveUploadProgressState, notify: Boolean = true) {
    uploadProgress = state
    SignalStore.backup.archiveUploadState = state

    if (notify) {
      _progress.tryEmit(Unit)
    }
  }
}
