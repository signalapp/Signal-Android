/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okio.ByteString
import org.junit.rules.ExternalResource
import org.signal.core.models.storageservice.StorageKey
import org.signal.core.util.Util
import org.signal.network.NetworkResult
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.service.StorageServiceService
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberSharingMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.StorageSyncLoopState
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.storage.RecordIkm
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageServiceApi
import org.whispersystems.signalservice.internal.storage.protos.ReadOperation
import org.whispersystems.signalservice.internal.storage.protos.StorageItem
import org.whispersystems.signalservice.internal.storage.protos.StorageItems
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest
import org.whispersystems.signalservice.internal.storage.protos.WriteOperation
import java.io.IOException
import java.util.Currency
import kotlin.time.Duration.Companion.days

/**
 * An in-memory stand-in for the storage service, installed over [SignalNetwork.storageService].
 *
 * Records are stored encrypted with [storageKey], exactly as they would be remotely, so tests get a real
 * encrypt/decrypt round-trip. Seed remote state with [setRemoteState], and inspect the results of a sync
 * with [manifest] and [records].
 */
class FakeStorageServiceRule(val storageKey: StorageKey = StorageKey(Util.getSecretBytes(32))) : ExternalResource() {

  val api: StorageServiceApi = mockk()

  /** The same higher-level service the app uses, pointed at this fake. Useful for building expected values. */
  val service: StorageServiceService by lazy { StorageServiceService(api) }

  var writeCount: Int = 0
    private set

  var readCount: Int = 0
    private set

  /** When true, the next write is rejected with a 409, as if someone else wrote first. */
  var failNextWriteWithConflict: Boolean = false

  /** When true, the next write is rejected with a network error. */
  var failNextWriteWithNetworkError: Boolean = false

  private var storedManifest: StorageManifest? = null
  private val storedItems: MutableMap<ByteString, StorageItem> = LinkedHashMap()
  private var seeding: Boolean = false

  /** The current remote manifest, decrypted, or null if nothing has ever been written. */
  val manifest: SignalStorageManifest?
    get() = storedManifest?.let {
      when (val result = service.getStorageManifest(storageKey)) {
        is StorageServiceService.ManifestResult.Success -> result.manifest
        else -> throw AssertionError("Failed to read back the manifest: $result")
      }
    }

  /** The current remote records, decrypted. */
  val records: List<SignalStorageRecord>
    get() {
      val manifest = this.manifest ?: return emptyList()
      return when (val result = service.readStorageRecords(storageKey, manifest.recordIkm, manifest.storageIds)) {
        is StorageServiceService.StorageRecordResult.Success -> result.records
        else -> throw AssertionError("Failed to read back records: $result")
      }
    }

  override fun before() {
    every { api.getAuth() } returns NetworkResult.Success("auth")
    every { api.getStorageManifest(any()) } answers { getStorageManifest() }
    every { api.getStorageManifestIfDifferentVersion(any(), any()) } answers { getStorageManifestIfDifferentVersion(secondArg()) }
    every { api.readStorageItems(any(), any()) } answers { readStorageItems(secondArg()) }
    every { api.writeStorageItems(any(), any()) } answers { writeStorageItems(secondArg()) }

    mockkObject(SignalNetwork)
    every { SignalNetwork.storageService } returns api
  }

  override fun after() {
    unmockkObject(SignalNetwork)
    unmockkObject(RemoteConfig)
  }

  /**
   * Stubs every [SignalStore] and [RemoteConfig] value that a storage service sync touches, with the storage
   * key pointed at this fake and the local manifest backed by real read/write state.
   *
   * Sane defaults for everything -- individual tests can re-stub whatever they're actually exercising.
   * Must be called after [MockSignalStoreRule] has been applied, i.e. from your test's setup.
   */
  fun stubDefaults(store: MockSignalStoreRule) {
    var localManifest = SignalStorageManifest.EMPTY
    var syncLoopState = StorageSyncLoopState()
    var notSyncedRotatedSelfProfileKey: ByteArray? = null

    every { store.storageService.manifest } answers { localManifest }
    every { store.storageService.manifest = any() } answers { localManifest = firstArg() }
    every { store.storageService.storageKey } returns storageKey
    every { store.storageService.storageKeyForInitialDataRestore } returns null

    every { store.storageService.syncLoopState } answers { syncLoopState }
    every { store.storageService.syncLoopState = any() } answers { syncLoopState = firstArg() }

    every { store.svr.hasPin() } returns true
    every { store.svr.hasOptedOut() } returns false

    every { store.account.notSyncedRotatedSelfProfileKey } answers { notSyncedRotatedSelfProfileKey }
    every { store.account.notSyncedRotatedSelfProfileKey = any() } answers { notSyncedRotatedSelfProfileKey = firstArg() }

    every { store.account.isRegistered } returns true
    every { store.account.isPrimaryDevice } returns true
    every { store.account.isLinkedDevice } returns false
    every { store.account.isMultiDevice } returns false
    every { store.account.deviceId } returns 1
    every { store.account.restoredAccountEntropyPool } returns false
    every { store.account.restoredAccountEntropyPoolFromPrimary } returns false
    every { store.account.pni } returns null
    every { store.account.username } returns null
    every { store.account.usernameLink } returns null

    every { store.internal.storageServiceDisabled } returns false

    // Read while building our own AccountRecord.
    every { store.story.viewedReceiptsEnabled } returns false
    every { store.story.userHasBeenNotifiedAboutStories } returns false
    every { store.story.userHasViewedOnboardingStory } returns false
    every { store.story.isFeatureDisabled } returns false
    every { store.story.userHasSeenGroupStoryEducationSheet } returns false
    every { store.uiHints.hasCompletedUsernameOnboarding() } returns false
    every { store.uiHints.hasSeenAdminDeleteEducationDialog() } returns false
    every { store.payments.mobileCoinPaymentsEnabled() } returns false
    every { store.payments.paymentsEntropy } returns null
    every { store.emoji.reactions } returns emptyList()
    every { store.inAppPayments.getDisplayBadgesOnProfile() } returns false
    every { store.inAppPayments.isDonationSubscriptionManuallyCancelled() } returns false
    every { store.inAppPayments.isBackupSubscriptionManuallyCancelled() } returns false
    every { store.inAppPayments.getRecurringDonationCurrency() } returns Currency.getInstance("USD")
    every { store.inAppPayments.getSubscriber(any()) } returns null
    every { store.backup.areBackupsEnabled } returns false
    every { store.backup.backupTier } returns null
    every { store.backup.backupTierInternalOverride } returns null
    every { store.phoneNumberPrivacy.phoneNumberDiscoverabilityMode } returns PhoneNumberDiscoverabilityMode.DISCOVERABLE
    every { store.phoneNumberPrivacy.phoneNumberSharingMode } returns PhoneNumberSharingMode.EVERYBODY
    every { store.notificationProfile.manuallyEnabledProfile } returns 0
    every { store.notificationProfile.manuallyEnabledUntil } returns 0
    every { store.notificationProfile.manuallyDisabledAt } returns 0
    every { store.releaseChannel.releaseChannelRecipientId } returns null
    every { store.settings.isLinkPreviewsEnabled } returns true
    every { store.settings.isPreferSystemContactPhotos } returns false
    every { store.settings.universalExpireTimer } returns 0
    every { store.settings.shouldKeepMutedChatsArchived() } returns false
    every { store.settings.automaticVerificationEnabled } returns true

    mockkObject(RemoteConfig)
    every { RemoteConfig.messageQueueTime } returns 45.days.inWholeMilliseconds
  }

  /**
   * Replaces all remote state with [records] at the provided manifest [version], as if another device had written it.
   */
  fun setRemoteState(
    records: List<SignalStorageRecord>,
    version: Long = 1,
    recordIkm: RecordIkm? = RecordIkm.generate(),
    sourceDeviceId: Int = 2
  ) {
    val manifest = SignalStorageManifest(
      version = version,
      sourceDeviceId = sourceDeviceId,
      recordIkm = recordIkm,
      storageIds = records.map { it.id }
    )

    seeding = true
    try {
      val result = service.resetAndWriteStorageRecords(storageKey, manifest, records.filterNot { it.isUnknown })
      check(result is StorageServiceService.WriteStorageRecordsResult.Success) { "Failed to seed remote state: $result" }
    } finally {
      seeding = false
    }

    resetCounters()
  }

  /**
   * Adds [records] to the existing remote state at the next manifest version, as if another device had written them.
   */
  fun addRemoteRecords(records: List<SignalStorageRecord>) {
    val current = manifest ?: error("There's no remote manifest yet! Call setRemoteState() first.")

    val updated = SignalStorageManifest(
      version = current.version + 1,
      sourceDeviceId = 2,
      recordIkm = current.recordIkm,
      storageIds = current.storageIds + records.map { it.id }
    )

    seeding = true
    try {
      val result = service.writeStorageRecords(storageKey, updated, records.filterNot { it.isUnknown }, emptyList())
      check(result is StorageServiceService.WriteStorageRecordsResult.Success) { "Failed to add remote records: $result" }
    } finally {
      seeding = false
    }
  }

  /** Zeroes out [writeCount] and [readCount], so setup traffic doesn't count against a test's assertions. */
  fun resetCounters() {
    writeCount = 0
    readCount = 0
  }

  private fun getStorageManifest(): NetworkResult<StorageManifest> {
    return storedManifest?.let { NetworkResult.Success(it) } ?: statusCodeError(404)
  }

  private fun getStorageManifestIfDifferentVersion(version: Long): NetworkResult<StorageManifest> {
    val current = storedManifest ?: return statusCodeError(404)
    return if (current.version == version) {
      statusCodeError(204)
    } else {
      NetworkResult.Success(current)
    }
  }

  private fun readStorageItems(operation: ReadOperation): NetworkResult<StorageItems> {
    readCount++
    val items = operation.readKey.mapNotNull { storedItems[it] }
    return NetworkResult.Success(StorageItems(items = items))
  }

  private fun writeStorageItems(operation: WriteOperation): NetworkResult<Unit> {
    if (!seeding) {
      writeCount++

      if (failNextWriteWithConflict) {
        failNextWriteWithConflict = false
        return statusCodeError(409)
      }

      if (failNextWriteWithNetworkError) {
        failNextWriteWithNetworkError = false
        return NetworkResult.NetworkError(IOException("Test network failure"))
      }

      val expectedVersion = (storedManifest?.version ?: 0) + 1
      if (operation.manifest!!.version != expectedVersion) {
        return statusCodeError(409)
      }
    }

    if (operation.clearAll) {
      storedItems.clear()
    }

    operation.deleteKey.forEach { storedItems.remove(it) }
    operation.insertItem.forEach { storedItems[it.key] = it }
    storedManifest = operation.manifest

    return NetworkResult.Success(Unit)
  }

  private fun <T> statusCodeError(code: Int): NetworkResult<T> {
    return NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(code))
  }
}
