/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob
import org.thoughtcrime.securesms.util.AttachmentUtil

/**
 * Drives a bulk media-delete operation and exposes [State] so a Compose dialog can show real
 * X / Y progress instead of an indeterminate spinner.
 */
class MediaDeleteProgressViewModel : ViewModel() {

  data class State(
    val processed: Int = 0,
    val total: Int = 0,
    val isDone: Boolean = false
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  private var job: Job? = null

  fun start(records: Collection<MediaTable.MediaRecord>) {
    if (job?.isActive == true) return
    val snapshot = records.toList()
    _state.value = State(total = snapshot.size)

    job = viewModelScope.launch(SignalDispatchers.IO) {
      val deletedMessageRecords = AttachmentUtil.deleteAttachments(snapshot) { processed ->
        _state.update { it.copy(processed = processed) }
      }

      if (deletedMessageRecords.isNotEmpty()) {
        MultiDeviceDeleteSyncJob.enqueueMessageDeletes(deletedMessageRecords)
      }

      _state.update { it.copy(isDone = true) }
    }
  }
}
