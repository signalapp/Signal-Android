/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatus
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusData
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.seconds

class MediaRestoreProgressBanner(private val data: MediaRestoreEvent) : Banner() {

  companion object {
    /**
     * Create a Lifecycle-aware [Flow] of [MediaRestoreProgressBanner] that observes the database for changes in attachments and emits banners when attachments are updated.
     */
    @JvmStatic
    fun createLifecycleAwareFlow(lifecycleOwner: LifecycleOwner): Flow<MediaRestoreProgressBanner> {
      return if (SignalStore.backup.isRestoreInProgress) {
        restoreFlow(lifecycleOwner)
      } else {
        flow {
          emit(MediaRestoreProgressBanner(MediaRestoreEvent(0L, 0L)))
        }
      }
    }

    /**
     * Create a flow that listens for all attachment changes in the db and emits a new banner at most
     * once every 1 second.
     */
    private fun restoreFlow(lifecycleOwner: LifecycleOwner): Flow<MediaRestoreProgressBanner> {
      val flow = callbackFlow {
        val queryObserver = DatabaseObserver.Observer {
          trySend(Unit)
        }

        queryObserver.onChanged()
        AppDependencies.databaseObserver.registerAttachmentUpdatedObserver(queryObserver)

        awaitClose {
          AppDependencies.databaseObserver.unregisterObserver(queryObserver)
        }
      }

      return flow
        .flowWithLifecycle(lifecycleOwner.lifecycle)
        .buffer(1, BufferOverflow.DROP_OLDEST)
        .onEach { delay(1.seconds) }
        .map { MediaRestoreProgressBanner(loadData()) }
        .flowOn(Dispatchers.IO)
    }

    private suspend fun loadData() = withContext(Dispatchers.IO) {
      // TODO [backups]: define and query data for interrupted/paused restores
      val totalRestoreSize = SignalStore.backup.totalRestorableAttachmentSize
      val remainingAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize()
      val completedBytes = totalRestoreSize - remainingAttachmentSize

      MediaRestoreEvent(completedBytes, totalRestoreSize)
    }
  }

  override var enabled: Boolean = data.totalBytes > 0L && data.totalBytes != data.completedBytes

  @Composable
  override fun DisplayBanner(contentPadding: PaddingValues) {
    BackupStatus(data = BackupStatusData.RestoringMedia(data.completedBytes, data.totalBytes))
  }

  data class MediaRestoreEvent(val completedBytes: Long, val totalBytes: Long)
}
