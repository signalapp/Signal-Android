/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState
import org.signal.core.models.storage.KeepMessagesDuration
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.backup.v2.ui.subscription.BackupUpgradeAvailabilityChecker
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.RestoreOptimizedMediaJob
import org.thoughtcrime.securesms.keyvalue.SignalStore

class ManageStorageSettingsRepository(
  private val scope: CoroutineScope = CoroutineScope(SignalDispatchers.IO + SupervisorJob())
) {

  /** Whether the latest backups subscription is still being processed, during which optimized storage can't be turned on. */
  fun observeIsPaidTierPending(): Flow<Boolean> {
    return InAppPaymentsRepository.observeLatestBackupPayment()
      .map { it.state == InAppPaymentTable.State.PENDING }
      .flowOn(SignalDispatchers.Default)
  }

  fun setKeepMessagesDuration(duration: KeepMessagesDuration) {
    SignalStore.settings.keepMessagesDuration = duration
    AppDependencies.trimThreadsByDateManager.scheduleIfNecessary()
  }

  fun getLengthLimit(): Int {
    return if (SignalStore.settings.isTrimByLengthEnabled) SignalStore.settings.threadTrimLength else ChatLengthLimitSettingsState.NO_LIMIT
  }

  fun setLengthLimit(limit: Int) {
    SignalStore.settings.isTrimByLengthEnabled = limit != ChatLengthLimitSettingsState.NO_LIMIT
    SignalStore.settings.threadTrimLength = limit
  }

  fun trimAllThreads(limit: Int) {
    scope.launch {
      val keepMessagesDuration = SignalStore.settings.keepMessagesDuration

      val trimBeforeDate = if (keepMessagesDuration != KeepMessagesDuration.FOREVER) {
        System.currentTimeMillis() - keepMessagesDuration.duration
      } else {
        ThreadTable.NO_TRIM_BEFORE_DATE_SET
      }

      SignalDatabase.threads.trimAllThreads(limit, trimBeforeDate)
    }
  }

  suspend fun isOptimizeStorageAvailable(): Boolean {
    return SignalStore.account.isPrimaryDevice &&
      SignalStore.backup.areBackupsEnabled &&
      BackupUpgradeAvailabilityChecker.isUpgradeAvailable(AppDependencies.application)
  }

  fun deleteAllConversations() {
    scope.launch {
      SignalDatabase.threads.deleteAllConversations()
      AppDependencies.messageNotifier.updateNotification(AppDependencies.application)
    }
  }

  fun restoreOptimizedMedia() {
    RestoreOptimizedMediaJob.enqueue()
  }

  /** Starts offloading media. Backing up first guarantees a media snapshot exists to reconcile against, and the backup enqueues the offload for us once it succeeds. */
  fun offloadOptimizedMedia() {
    BackupMessagesJob.enqueue()
  }
}
