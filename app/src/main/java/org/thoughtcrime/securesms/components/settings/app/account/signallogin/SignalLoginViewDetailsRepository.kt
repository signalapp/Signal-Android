/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Where the view models behind the Signal Login details screen read the credentials that make up the user's Signal
 * Login, and the state the recovery key reset flow depends on.
 */
class SignalLoginViewDetailsRepository {

  fun getAci(): ACI? = SignalStore.account.aci

  fun getAccountEntropyPool(): AccountEntropyPool? = SignalStore.account.accountEntropyPoolOrNull

  fun isOptimizedStorageEnabled(): Boolean = SignalStore.backup.optimizeStorage

  fun areBackupsEnabled(): Boolean = SignalStore.backup.areBackupsEnabled

  suspend fun canResetRecoveryKey(): Boolean = BackupRepository.canRotateBackupKey()

  fun turnOffOptimizedStorageAndDownloadMedia() = BackupRepository.turnOffOptimizedStorageAndDownloadMedia()
}
