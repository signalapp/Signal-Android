/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.signal.core.util.bytes
import org.signal.core.util.throttleLatest
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusBanner
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusData
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.BatteryNotLowConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.WifiConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.safeUnregisterReceiver
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRestoreProgressBanner(private val listener: RestoreProgressBannerListener = EmptyListener) : Banner<BackupStatusData>() {

  private var totalRestoredSize: Long = 0

  override val enabled: Boolean
    get() = SignalStore.backup.isRestoreInProgress || totalRestoredSize > 0

  override val dataFlow: Flow<BackupStatusData> by lazy {
    SignalStore
      .backup
      .totalRestorableAttachmentSizeFlow
      .flatMapLatest { size ->
        when {
          size > 0 -> {
            totalRestoredSize = size
            getActiveRestoreFlow()
          }

          totalRestoredSize > 0 -> {
            flowOf(
              BackupStatusData.RestoringMedia(
                bytesTotal = totalRestoredSize.bytes.also { totalRestoredSize = 0 },
                restoreStatus = BackupStatusData.RestoreStatus.FINISHED
              )
            )
          }

          else -> flowOf(BackupStatusData.RestoringMedia())
        }
      }
  }

  @Composable
  override fun DisplayBanner(model: BackupStatusData, contentPadding: PaddingValues) {
    BackupStatusBanner(
      data = model,
      onSkipClick = listener::onSkip,
      onDismissClick = listener::onDismissComplete
    )
  }

  private fun getActiveRestoreFlow(): Flow<BackupStatusData.RestoringMedia> {
    val flow: Flow<Unit> = callbackFlow {
      val onChange = { trySend(Unit) }

      val observer = DatabaseObserver.Observer {
        onChange()
      }

      val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          onChange()
        }
      }

      onChange()

      AppDependencies.databaseObserver.registerAttachmentUpdatedObserver(observer)
      AppDependencies.application.registerReceiver(receiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
      AppDependencies.application.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

      awaitClose {
        AppDependencies.databaseObserver.unregisterObserver(observer)
        AppDependencies.application.safeUnregisterReceiver(receiver)
      }
    }

    return flow
      .throttleLatest(1.seconds)
      .map {
        when {
          !WifiConstraint.isMet(AppDependencies.application) -> BackupStatusData.RestoringMedia(restoreStatus = BackupStatusData.RestoreStatus.WAITING_FOR_WIFI)
          !NetworkConstraint.isMet(AppDependencies.application) -> BackupStatusData.RestoringMedia(restoreStatus = BackupStatusData.RestoreStatus.WAITING_FOR_INTERNET)
          !BatteryNotLowConstraint.isMet() -> BackupStatusData.RestoringMedia(restoreStatus = BackupStatusData.RestoreStatus.LOW_BATTERY)
          else -> {
            val totalRestoreSize = SignalStore.backup.totalRestorableAttachmentSize
            val remainingAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize()
            val completedBytes = totalRestoreSize - remainingAttachmentSize

            BackupStatusData.RestoringMedia(completedBytes.bytes, totalRestoreSize.bytes)
          }
        }
      }
      .flowOn(Dispatchers.IO)
  }

  interface RestoreProgressBannerListener {
    fun onSkip()
    fun onDismissComplete()
  }

  private object EmptyListener : RestoreProgressBannerListener {
    override fun onSkip() = Unit
    override fun onDismissComplete() = Unit
  }
}
