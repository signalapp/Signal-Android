/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.runtime.Composable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatus
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusData
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

class MediaRestoreProgressBanner(private val data: MediaRestoreEvent) : Banner() {

  companion object {
    private val TAG = Log.tag(MediaRestoreProgressBanner::class)

    /**
     * Create a Lifecycle-aware [Flow] of [MediaRestoreProgressBanner] that observes the database for changes in attachments and emits banners when attachments are updated.
     */
    @JvmStatic
    fun createLifecycleAwareFlow(lifecycleOwner: LifecycleOwner): Flow<MediaRestoreProgressBanner> {
      if (SignalStore.backup.isRestoreInProgress) {
        val observer = LifecycleObserver()
        lifecycleOwner.lifecycle.addObserver(observer)
        return observer.flow
      } else {
        return flow {
          emit(MediaRestoreProgressBanner(MediaRestoreEvent(0L, 0L)))
        }
      }
    }
  }

  override var enabled: Boolean = data.totalBytes > 0L && data.totalBytes != data.completedBytes

  @Composable
  override fun DisplayBanner() {
    BackupStatus(data = BackupStatusData.RestoringMedia(data.completedBytes, data.totalBytes))
  }

  data class MediaRestoreEvent(val completedBytes: Long, val totalBytes: Long)

  private class LifecycleObserver : DefaultLifecycleObserver {
    private var attachmentObserver: DatabaseObserver.Observer? = null
    private val _mutableSharedFlow = MutableSharedFlow<MediaRestoreEvent>(replay = 1)

    val flow = _mutableSharedFlow.map { MediaRestoreProgressBanner(it) }

    override fun onStart(owner: LifecycleOwner) {
      val queryObserver = DatabaseObserver.Observer {
        owner.lifecycleScope.launch {
          _mutableSharedFlow.emit(loadData())
        }
      }

      attachmentObserver = queryObserver
      queryObserver.onChanged()
      AppDependencies.databaseObserver.registerAttachmentObserver(queryObserver)
    }

    override fun onStop(owner: LifecycleOwner) {
      attachmentObserver?.let {
        AppDependencies.databaseObserver.unregisterObserver(it)
      }
    }

    private suspend fun loadData() = withContext(Dispatchers.IO) {
      // TODO [backups]: define and query data for interrupted/paused restores
      val totalRestoreSize = SignalStore.backup.totalRestorableAttachmentSize
      val remainingAttachmentSize = SignalDatabase.attachments.getTotalRestorableAttachmentSize()
      val completedBytes = totalRestoreSize - remainingAttachmentSize
      MediaRestoreEvent(completedBytes, totalRestoreSize)
    }
  }
}
