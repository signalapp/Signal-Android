/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.logging.Log
import org.signal.network.service.ArchiveCacheStore
import org.thoughtcrime.securesms.keyvalue.BackupValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import kotlin.time.Duration

/**
 * Backs [ArchiveCacheStore] with [SignalStore], which is where all of this state lived before [org.signal.network.service.ArchiveService] existed.
 */
object SignalStoreArchiveCacheStore : ArchiveCacheStore {

  private val TAG = Log.tag(SignalStoreArchiveCacheStore::class)

  override val aci: ACI
    get() = SignalStore.account.requireAci()

  override val messageBackupKey: MessageBackupKey
    get() = SignalStore.backup.messageBackupKey

  override val mediaRootBackupKey: MediaRootBackupKey
    get() = SignalStore.backup.mediaRootBackupKey

  override val messageCredentials: ArchiveCacheStore.CredentialCache
    get() = CredentialCache(SignalStore.backup.messageCredentials)

  override val mediaCredentials: ArchiveCacheStore.CredentialCache
    get() = CredentialCache(SignalStore.backup.mediaCredentials)

  override var messageBackupInitialized: Boolean
    get() = SignalStore.backup.messageBackupInitialized
    set(value) {
      SignalStore.backup.messageBackupInitialized = value
    }

  override var mediaBackupInitialized: Boolean
    get() = SignalStore.backup.mediaBackupInitialized
    set(value) {
      SignalStore.backup.mediaBackupInitialized = value
    }

  override var cachedMediaCdnPath: String?
    get() = SignalStore.backup.cachedMediaCdnPath
    set(value) {
      SignalStore.backup.cachedMediaCdnPath = value
    }

  override val isLinkedDevice: Boolean
    get() = SignalStore.account.isLinkedDevice

  override val isPreRestoreDuringRegistration: Boolean
    get() = !SignalStore.registration.isRegistrationComplete && SignalStore.registration.restoreDecisionState.isDecisionPending

  override fun onNotEntitled() {
    if (SignalStore.backup.backupTierInternalOverride != null) {
      Log.w(TAG, "Received status 403, but the internal override is set, so not doing anything.")
      return
    }

    Log.w(TAG, "Received status 403. The user is not in the media tier. Updating local state.")

    if (SignalStore.backup.backupTier == MessageBackupTier.PAID) {
      Log.w(TAG, "Local device thought it was on PAID tier. Downgrading to FREE tier.")
      SignalStore.backup.backupTier = MessageBackupTier.FREE
      SignalStore.backup.backupExpiredAndDowngraded = true
      BackupRepository.scheduleSyncForAccountChange()
    }

    SignalStore.uiHints.markHasEverEnabledRemoteBackups()
  }

  private class CredentialCache(private val store: BackupValues.CredentialStore) : ArchiveCacheStore.CredentialCache {
    override fun getForTime(currentTime: Duration): ArchiveServiceCredential? = store.byDay.getForCurrentTime(currentTime)

    override fun add(credentials: List<ArchiveServiceCredential>) = store.add(credentials)

    override fun clearOlderThan(startOfDayInSeconds: Long) = store.clearOlderThan(startOfDayInSeconds)

    override fun clearAll() = store.clearAll()

    override var cdnReadCredentials: GetArchiveCdnCredentialsResponse?
      get() = store.cdnReadCredentials
      set(value) {
        store.cdnReadCredentials = value
      }
  }
}
