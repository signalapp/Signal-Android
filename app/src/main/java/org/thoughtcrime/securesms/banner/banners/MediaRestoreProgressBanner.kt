/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.signal.core.util.throttleLatest
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatus
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusData
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.seconds

class MediaRestoreProgressBanner : Banner<BackupStatusData>() {

  override val enabled: Boolean
    get() = SignalStore.backup.isRestoreInProgress

  override val dataFlow: Flow<BackupStatusData>
    get() {
      if (!SignalStore.backup.isRestoreInProgress) {
        return flowOf(BackupStatusData.RestoringMedia(0, 0))
      }

      val dbNotificationFlow = callbackFlow {
        val queryObserver = DatabaseObserver.Observer {
          trySend(Unit)
        }

        queryObserver.onChanged()
        AppDependencies.databaseObserver.registerAttachmentUpdatedObserver(queryObserver)

        awaitClose {
          AppDependencies.databaseObserver.unregisterObserver(queryObserver)
        }
      }

      return dbNotificationFlow
        .throttleLatest(1.seconds)
        .map {
          val totalRestoreSize = SignalStore.backup.totalRestorableAttachmentSize
          val remainingAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize()
          val completedBytes = totalRestoreSize - remainingAttachmentSize

          BackupStatusData.RestoringMedia(completedBytes, totalRestoreSize)
        }
        .flowOn(Dispatchers.IO)
    }

  @Composable
  override fun DisplayBanner(model: BackupStatusData, contentPadding: PaddingValues) {
    BackupStatus(data = model)
  }
}
