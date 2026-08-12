/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import java.util.UUID
import kotlin.time.Duration

/**
 * In-memory [ArchiveCacheStore] for [ArchiveServiceTest].
 *
 * A real implementation rather than a mock, because most of what these tests assert is which pieces of local state an error clears, and that reads far better as
 * a state assertion than as a verified call.
 *
 * [CredentialCache.getForTime] ignores the time it's handed and just reports whatever was last added. Day-boundary lookup lives in the app's `BackupValues`, not
 * here, so honoring it would only make these tests depend on the wall clock. The one place rounding actually matters --
 * [ArchiveService.getMessageBackupFileLocationForKey], which matches a credential's `redemptionTime` against the start of the current day -- is covered by
 * building a credential with the matching redemption time.
 */
class FakeArchiveCacheStore(
  override val aci: ACI = ACI.from(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")),
  override val messageBackupKey: MessageBackupKey = MessageBackupKey(ByteArray(32) { 1 }),
  override val mediaRootBackupKey: MediaRootBackupKey = MediaRootBackupKey(ByteArray(32) { 2 }),
  override var messageBackupInitialized: Boolean = true,
  override var mediaBackupInitialized: Boolean = true,
  override var cachedMediaCdnPath: String? = null,
  override val isLinkedDevice: Boolean = false,
  override val isPreRestoreDuringRegistration: Boolean = false
) : ArchiveCacheStore {

  override val messageCredentials = CredentialCache()
  override val mediaCredentials = CredentialCache()

  /** How many times [onNotEntitled] was called. */
  var notEntitledCount: Int = 0
    private set

  override fun onNotEntitled() {
    notEntitledCount++
  }

  class CredentialCache : ArchiveCacheStore.CredentialCache {
    private val credentials = mutableListOf<ArchiveServiceCredential>()

    /** How many times [clearOlderThan] was called, and with what. */
    val clearedOlderThan = mutableListOf<Long>()

    override fun getForTime(currentTime: Duration): ArchiveServiceCredential? = credentials.lastOrNull()

    override fun add(credentials: List<ArchiveServiceCredential>) {
      this.credentials += credentials
    }

    override fun clearOlderThan(startOfDayInSeconds: Long) {
      clearedOlderThan += startOfDayInSeconds
    }

    override fun clearAll() {
      credentials.clear()
      cdnReadCredentials = null
    }

    override var cdnReadCredentials: GetArchiveCdnCredentialsResponse? = null

    val isEmpty: Boolean get() = credentials.isEmpty()
  }
}
