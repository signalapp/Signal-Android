/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository
import org.thoughtcrime.securesms.database.MediaTable

private const val SHARED_MEDIA_LIMIT = 100

/**
 * Loads the shared media rail for a thread, and lets the host ask for a reload after the user comes back from the
 * media viewer. Held by each conversation settings view model, which applies the results to its own state.
 *
 * Nothing is emitted until the thread id has been resolved, so that a screen can tell "still loading" apart from
 * "this chat has no media" and reserve space for the rail accordingly.
 */
class SharedMediaLoader(private val repository: ConversationSettingsRepository) {

  private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
  private val threadId = MutableStateFlow<Long?>(null)

  fun onThreadIdLoaded(threadId: Long) {
    this.threadId.value = threadId
  }

  fun refresh() {
    refreshTrigger.tryEmit(Unit)
  }

  fun observe(): Flow<List<MediaTable.MediaRecord>> {
    return combine(threadId.filterNotNull().distinctUntilChanged(), refreshTrigger) { id, _ -> id }
      .map { repository.getSharedMedia(it, SHARED_MEDIA_LIMIT) }
  }
}
