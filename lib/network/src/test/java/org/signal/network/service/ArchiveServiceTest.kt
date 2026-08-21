/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.left
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.core.models.backup.MediaName
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.signal.libsignal.net.CopyBackupMediaOutcome
import org.signal.libsignal.net.DeleteBackupMediaItem
import org.signal.libsignal.net.MediaBackupInfo
import org.signal.libsignal.net.MessageBackupInfo
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.net.ServerSideErrorException
import org.signal.libsignal.net.UploadTooLargeException
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.backups.BackupAuthCredential
import org.signal.libsignal.zkgroup.backups.BackupLevel
import org.signal.network.api.ArchiveApiV2
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.time.Duration as JavaDuration

/**
 * Most of these assert *which local state an error clears*, because that's the contract every caller depends on and the one that isn't visible from any single
 * call site. See [FakeArchiveCacheStore].
 */
class ArchiveServiceTest {

  private val archiveApi: ArchiveApiV2 = mockk()

  private fun serviceFor(store: FakeArchiveCacheStore) = ArchiveService(archiveApi, store)

  // region Credential cache reuse

  @Test
  fun `cached credentials are reused without a network fetch`() = runTest {
    val store = storeWithCredentials()

    givenSuccessfulRefresh()

    val result = serviceFor(store).refreshBackup()

    assertThat(result.isRight()).isTrue()
    coVerify(exactly = 0) { archiveApi.getServiceCredentials(any()) }
  }

  @Test
  fun `missing credentials are fetched, added, and trimmed`() = runTest {
    val store = FakeArchiveCacheStore()

    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.Success(
      ArchiveApiV2.ArchiveCredentials(
        messageCredentials = listOf(credential()),
        mediaCredentials = listOf(credential())
      )
    )
    givenSuccessfulRefresh()

    val result = serviceFor(store).refreshBackup()

    assertThat(result.isRight()).isTrue()
    coVerify(exactly = 1) { archiveApi.getServiceCredentials(any()) }
    assertThat(store.messageCredentials.isEmpty).isFalse()
    assertThat(store.mediaCredentials.isEmpty).isFalse()
    assertThat(store.messageCredentials.clearedOlderThan).hasSize(1)
    assertThat(store.mediaCredentials.clearedOlderThan).hasSize(1)
  }

  // endregion

  // region Failed zk derivation resets the initialized state of whichever archive it came from

  @Test
  fun `zk verification failure on a message request resets only the message initialized state`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    // libsignal surfaces a failed derivation as an ApplicationError, from any anonymous endpoint. This one is the message credential.
    coEvery { archiveApi.getSvrBAuthorization(any(), any()) } returns RequestResult.ApplicationError(VerificationFailedException())

    val result = serviceFor(store).getSvrBAuth()

    val error = result.error()
    assertThat(error).isInstanceOf(ArchiveError.CredentialError.ZkVerificationFailed::class)
    assertThat((error as ArchiveError.CredentialError.ZkVerificationFailed).credentialType).isEqualTo(ArchiveService.CredentialType.MESSAGE)

    // Re-fetching can only produce another credential issued against the stale backupId, so the initialized state has to go too, or the next call fails the same
    // way forever.
    assertThat(store.messageBackupInitialized).isFalse()
    assertThat(store.messageCredentials.isEmpty).isTrue()

    // The media backupId is derived from an independent key, so it's still good.
    assertThat(store.mediaBackupInitialized).isTrue()
    assertThat(store.mediaCredentials.isEmpty).isFalse()
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
  }

  @Test
  fun `zk verification failure on a media request resets only the media initialized state`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    coEvery { archiveApi.copyMediaToArchive(any(), any(), any()) } returns RequestResult.ApplicationError(VerificationFailedException())

    val result = serviceFor(store).copyToArchive(2, "location", 100, MediaName("name"))

    val error = result.error()
    assertThat(error).isInstanceOf(ArchiveError.CredentialError.ZkVerificationFailed::class)
    assertThat((error as ArchiveError.CredentialError.ZkVerificationFailed).credentialType).isEqualTo(ArchiveService.CredentialType.MEDIA)

    assertThat(store.mediaBackupInitialized).isFalse()
    assertThat(store.mediaCredentials.isEmpty).isTrue()
    assertThat(store.cachedMediaCdnPath).isNull()

    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.messageCredentials.isEmpty).isFalse()
  }

  @Test
  fun `zk verification failure while deriving the backup level resets the message initialized state`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    every { archiveApi.getZkCredential(any(), any()) } returns ArchiveApiV2.ZkCredentialResult.Failure.VerificationFailed(VerificationFailedException())

    val result = serviceFor(store).getBackupLevel()

    // The level is read off the message credential, so that's the only side implicated.
    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.ZkVerificationFailed::class)
    assertThat(store.messageBackupInitialized).isFalse()
    assertThat(store.messageCredentials.isEmpty).isTrue()
    assertThat(store.mediaBackupInitialized).isTrue()
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
  }

  @Test
  fun `zk verification failure while checking the backup level without downgrade leaves local state alone`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    every { archiveApi.getZkCredential(any(), any()) } returns ArchiveApiV2.ZkCredentialResult.Failure.VerificationFailed(VerificationFailedException())

    val result = serviceFor(store).getBackupLevelWithoutDowngrade()

    // This check exists to run periodically without changing anything, so it's exempt from the healing every other path gets.
    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.ZkVerificationFailed::class)
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.mediaBackupInitialized).isTrue()
    assertThat(store.messageCredentials.isEmpty).isFalse()
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
  }

  @Test
  fun `zk verification failure for a caller-supplied key leaves initialized state alone`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")
    val startOfDay = System.currentTimeMillis().milliseconds.inWholeDays.days.inWholeSeconds

    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.Success(
      ArchiveApiV2.ArchiveCredentials(
        messageCredentials = listOf(credential(redemptionTime = startOfDay)),
        mediaCredentials = emptyList()
      )
    )
    coEvery { archiveApi.getMessageBackupInfo(any(), any()) } returns RequestResult.ApplicationError(VerificationFailedException())

    val result = serviceFor(store).getMessageBackupInfoForKey(store.aci, MessageBackupKey(ByteArray(32) { 9 }))

    // The key came from the caller, so a failure here says that key doesn't belong to the account. Re-committing our own backupId over it could strand the very
    // backup the caller was reaching for.
    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.ZkVerificationFailed::class)
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.mediaBackupInitialized).isTrue()
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
  }

  @Test
  fun `zk verification failure during pre-restore leaves initialized state alone`() = runTest {
    val store = storeWithCredentials(isPreRestoreDuringRegistration = true)

    coEvery { archiveApi.getSvrBAuthorization(any(), any()) } returns RequestResult.ApplicationError(VerificationFailedException())

    val result = serviceFor(store).getSvrBAuth()

    // The stored key isn't necessarily the one the backup belongs to until restore finishes, so registration heals this itself.
    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.ZkVerificationFailed::class)
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.messageCredentials.isEmpty).isFalse()
  }

  // endregion

  // region Rejected credential handling, and its scope

  @Test
  fun `unauthorized while fetching service credentials resets initialized state and caches`() = runTest {
    val store = FakeArchiveCacheStore(cachedMediaCdnPath = "dir/media")

    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.NonSuccess(ArchiveApiV2.GetServiceCredentialsError.Unauthorized)

    val result = serviceFor(store).getArchiveServiceAccess()

    // Our account auth was rejected rather than one archive's credential, so there's nothing to scope the cleanup to.
    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.Unauthorized::class)
    assertThat(store.messageBackupInitialized).isFalse()
    assertThat(store.mediaBackupInitialized).isFalse()
    assertThat(store.messageCredentials.isEmpty).isTrue()
    assertThat(store.mediaCredentials.isEmpty).isTrue()
    assertThat(store.cachedMediaCdnPath).isNull()
  }

  @Test
  fun `unauthorized while fetching service credentials during pre-restore leaves local state alone`() = runTest {
    val store = FakeArchiveCacheStore(
      messageBackupInitialized = false,
      mediaBackupInitialized = false,
      isPreRestoreDuringRegistration = true,
      cachedMediaCdnPath = "dir/media"
    )

    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.NonSuccess(ArchiveApiV2.GetServiceCredentialsError.Unauthorized)

    val result = serviceFor(store).getArchiveServiceAccess()

    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.Unauthorized::class)
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
    coVerify(exactly = 0) { archiveApi.triggerBackupIdReservation(any(), any(), any()) }
  }

  @Test
  fun `unauthorized while using an established message credential resets only the message initialized state`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    coEvery { archiveApi.getSvrBAuthorization(any(), any()) } returns RequestResult.NonSuccess(RequestUnauthorizedException("nope"))

    val result = serviceFor(store).getSvrBAuth()

    val error = result.error()
    assertThat(error).isInstanceOf(ArchiveError.CredentialError.Unauthorized::class)
    assertThat((error as ArchiveError.CredentialError.Unauthorized).credentialType).isEqualTo(ArchiveService.CredentialType.MESSAGE)
    assertThat(store.messageBackupInitialized).isFalse()
    assertThat(store.messageCredentials.isEmpty).isTrue()
    assertThat(store.mediaBackupInitialized).isTrue()
    assertThat(store.mediaCredentials.isEmpty).isFalse()
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
  }

  @Test
  fun `unauthorized while reading the backup file location leaves local state alone`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    coEvery { archiveApi.getMessageBackupInfo(any(), any()) } returns RequestResult.NonSuccess(RequestUnauthorizedException("nope"))

    val result = serviceFor(store).getMessageBackupFileLocation()

    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.Unauthorized::class)

    // This read exists to discover whether a backup exists at all, and the server can't distinguish a bad credential from an unprovisioned backupId. Callers read
    // a rejection here as "backups aren't set up", so it must not look like our credential went bad.
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.mediaBackupInitialized).isTrue()
    assertThat(store.messageCredentials.isEmpty).isFalse()
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
  }

  @Test
  fun `unauthorized while checking the backup level without downgrade leaves local state alone`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.NonSuccess(ArchiveApiV2.GetServiceCredentialsError.Unauthorized)
    store.messageCredentials.clearAll()
    store.mediaCredentials.clearAll()

    val result = serviceFor(store).getBackupLevelWithoutDowngrade()

    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.Unauthorized::class)
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.mediaBackupInitialized).isTrue()
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
  }

  @Test
  fun `unauthorized while copying media resets only the media initialized state`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    coEvery { archiveApi.copyMediaToArchive(any(), any(), any()) } returns RequestResult.NonSuccess(RequestUnauthorizedException("nope"))

    val result = serviceFor(store).copyToArchive(2, "location", 100, MediaName("name"))

    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.Unauthorized::class)
    assertThat(store.mediaBackupInitialized).isFalse()
    assertThat(store.cachedMediaCdnPath).isNull()
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.messageCredentials.isEmpty).isFalse()
  }

  @Test
  fun `network error clears nothing`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    coEvery { archiveApi.getSvrBAuthorization(any(), any()) } returns RequestResult.RetryableNetworkError(IOException("down"))

    val result = serviceFor(store).getSvrBAuth()

    assertThat(result.error()).isInstanceOf(ArchiveError.NetworkError::class)
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.mediaBackupInitialized).isTrue()
    assertThat(store.messageCredentials.isEmpty).isFalse()
    assertThat(store.mediaCredentials.isEmpty).isFalse()
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
  }

  @Test
  fun `rate limit clears nothing`() = runTest {
    val store = storeWithCredentials(cachedMediaCdnPath = "dir/media")

    coEvery { archiveApi.getSvrBAuthorization(any(), any()) } returns RequestResult.RetryableNetworkError(IOException("slow down"), JavaDuration.ofSeconds(30))

    val result = serviceFor(store).getSvrBAuth()

    val error = result.error()
    assertThat(error).isInstanceOf(ArchiveError.CredentialError.RateLimited::class)
    assertThat((error as ArchiveError.CredentialError.RateLimited).retryAfter).isEqualTo(30.seconds)
    assertThat(store.messageCredentials.isEmpty).isFalse()
    assertThat(store.cachedMediaCdnPath).isEqualTo("dir/media")
  }

  // endregion

  // region Error mapping

  @Test
  fun `retryable network error without a retry-after becomes NetworkError`() = runTest {
    val store = storeWithCredentials()
    val cause = IOException("boom")

    coEvery { archiveApi.getMessageBackupUploadForm(any(), any(), any()) } returns RequestResult.RetryableNetworkError(cause)

    val result = serviceFor(store).getMessageBackupUploadForm(100)

    val error = result.error()
    assertThat(error).isInstanceOf(ArchiveError.NetworkError::class)
    assertThat((error as ArchiveError.NetworkError).exception).isSameInstanceAs(cause)
  }

  @Test
  fun `upload too large becomes TooLarge`() = runTest {
    val store = storeWithCredentials()

    coEvery { archiveApi.getMessageBackupUploadForm(any(), any(), any()) } returns RequestResult.NonSuccess(UploadTooLargeException("too big"))

    val result = serviceFor(store).getMessageBackupUploadForm(Long.MAX_VALUE)

    assertThat(result.error()).isInstanceOf(ArchiveError.UploadFormError.TooLarge::class)
  }

  @Test
  fun `unauthorized upload form becomes Unauthorized rather than TooLarge`() = runTest {
    val store = storeWithCredentials()

    coEvery { archiveApi.getMediaUploadForm(any(), any(), any()) } returns RequestResult.NonSuccess(RequestUnauthorizedException("nope"))

    val result = serviceFor(store).getMediaUploadForm(100)

    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.Unauthorized::class)
  }

  @Test
  fun `reserving a backupId maps each modeled error`() = runTest {
    val cases = mapOf(
      ArchiveApiV2.SetBackupIdError.InvalidCredential to ArchiveError.CredentialError.InvalidRequest::class,
      ArchiveApiV2.SetBackupIdError.Unauthorized to ArchiveError.CredentialError.Unauthorized::class,
      ArchiveApiV2.SetBackupIdError.RateLimited(5.seconds) to ArchiveError.CredentialError.RateLimited::class
    )

    for ((apiError, expected) in cases) {
      val store = FakeArchiveCacheStore()
      coEvery { archiveApi.triggerBackupIdReservation(any(), any(), any()) } returns RequestResult.NonSuccess(apiError)

      val result = serviceFor(store).triggerBackupIdReservation()

      assertThat(result.leftOrNull()!!::class, name = "$apiError").isEqualTo(expected)
    }
  }

  @Test
  fun `fetching service credentials maps each modeled error`() = runTest {
    val cases = mapOf(
      ArchiveApiV2.GetServiceCredentialsError.InvalidRedemptionTimes to ArchiveError.CredentialError.InvalidRequest::class,
      ArchiveApiV2.GetServiceCredentialsError.Unauthorized to ArchiveError.CredentialError.Unauthorized::class,
      ArchiveApiV2.GetServiceCredentialsError.BackupIdNotFound to ArchiveError.CredentialError.NotFound::class,
      ArchiveApiV2.GetServiceCredentialsError.RateLimited(5.seconds) to ArchiveError.CredentialError.RateLimited::class
    )

    for ((apiError, expected) in cases) {
      val store = FakeArchiveCacheStore()
      coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.NonSuccess(apiError)

      val result = serviceFor(store).getArchiveServiceAccess()

      assertThat(result.leftOrNull()!!::class, name = "$apiError").isEqualTo(expected)
    }
  }

  // endregion

  // region First-time initialization

  @Test
  fun `uninitialized account reserves the backupId, sets both public keys, and marks initialized`() = runTest {
    val store = FakeArchiveCacheStore(messageBackupInitialized = false, mediaBackupInitialized = false)

    coEvery { archiveApi.triggerBackupIdReservation(any(), any(), any()) } returns RequestResult.Success(Unit)
    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.Success(
      ArchiveApiV2.ArchiveCredentials(listOf(credential()), listOf(credential()))
    )
    coEvery { archiveApi.setPublicKey(any(), any()) } returns RequestResult.Success(Unit)

    val result = serviceFor(store).getArchiveServiceAccess()

    assertThat(result.isRight()).isTrue()
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.mediaBackupInitialized).isTrue()
    coVerifyOrder {
      archiveApi.triggerBackupIdReservation(store.messageBackupKey, store.mediaRootBackupKey, store.aci)
      archiveApi.getServiceCredentials(any())
      archiveApi.setPublicKey(any(), any())
      archiveApi.setPublicKey(any(), any())
    }
    coVerify(exactly = 2) { archiveApi.setPublicKey(any(), any()) }
  }

  @Test
  fun `a failure while setting the public key does not mark backups initialized`() = runTest {
    val store = FakeArchiveCacheStore(messageBackupInitialized = false, mediaBackupInitialized = false)

    coEvery { archiveApi.triggerBackupIdReservation(any(), any(), any()) } returns RequestResult.Success(Unit)
    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.Success(
      ArchiveApiV2.ArchiveCredentials(listOf(credential()), listOf(credential()))
    )
    coEvery { archiveApi.setPublicKey(any(), any()) } returns RequestResult.NonSuccess(RequestUnauthorizedException("nope"))

    val result = serviceFor(store).getArchiveServiceAccess()

    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.Unauthorized::class)
    assertThat(store.messageBackupInitialized).isFalse()
    assertThat(store.mediaBackupInitialized).isFalse()
  }

  @Test
  fun `an account that only needs media initialization reserves just the media key`() = runTest {
    val store = FakeArchiveCacheStore(messageBackupInitialized = true, mediaBackupInitialized = false)

    coEvery { archiveApi.triggerBackupIdReservation(any(), any(), any()) } returns RequestResult.Success(Unit)
    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.Success(
      ArchiveApiV2.ArchiveCredentials(listOf(credential()), listOf(credential()))
    )
    coEvery { archiveApi.setPublicKey(any(), any()) } returns RequestResult.Success(Unit)

    val result = serviceFor(store).getArchiveServiceAccess()

    assertThat(result.isRight()).isTrue()
    assertThat(store.mediaBackupInitialized).isTrue()

    coVerify(exactly = 1) { archiveApi.triggerBackupIdReservation(null, store.mediaRootBackupKey, store.aci) }
    coVerify(exactly = 1) { archiveApi.setPublicKey(any(), any()) }
  }

  @Test
  fun `a media failure during initialization keeps the message side initialized`() = runTest {
    val store = FakeArchiveCacheStore(messageBackupInitialized = false, mediaBackupInitialized = false)

    coEvery { archiveApi.triggerBackupIdReservation(any(), any(), any()) } returns RequestResult.Success(Unit)
    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.Success(
      ArchiveApiV2.ArchiveCredentials(listOf(credential()), listOf(credential()))
    )
    coEvery { archiveApi.setPublicKey(any(), match { it.backupKey is MessageBackupKey }) } returns RequestResult.Success(Unit)
    coEvery { archiveApi.setPublicKey(any(), match { it.backupKey is MediaRootBackupKey }) } returns RequestResult.RetryableNetworkError(IOException("down"))

    val result = serviceFor(store).getArchiveServiceAccess()

    // The retry has only the media side left to do.
    assertThat(result.error()).isInstanceOf(ArchiveError.NetworkError::class)
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.mediaBackupInitialized).isFalse()
  }

  @Test
  fun `triggering a reservation without media skips the media key and leaves media credentials alone`() = runTest {
    val store = storeWithCredentials()

    coEvery { archiveApi.triggerBackupIdReservation(any(), any(), any()) } returns RequestResult.Success(Unit)

    val result = serviceFor(store).triggerBackupIdReservation(includeMedia = false)

    assertThat(result.isRight()).isTrue()
    coVerify { archiveApi.triggerBackupIdReservation(store.messageBackupKey, null, store.aci) }
    assertThat(store.messageCredentials.isEmpty).isTrue()
    assertThat(store.mediaCredentials.isEmpty).isFalse()
  }

  // endregion

  // region Copying media

  @Test
  fun `copy outcomes map to their errors`() = runTest {
    val cases = listOf<Pair<CopyBackupMediaOutcome, Any>>(
      CopyBackupMediaOutcome.Success(ByteArray(15), 3) to 3,
      CopyBackupMediaOutcome.SourceNotFound(ByteArray(15)) to ArchiveError.CopyMediaError.SourceNotFound,
      CopyBackupMediaOutcome.WrongSourceLength(ByteArray(15)) to ArchiveError.CopyMediaError.WrongSourceLength,
      CopyBackupMediaOutcome.OutOfSpace(ByteArray(15)) to ArchiveError.CopyMediaError.OutOfRemoteSpace
    )

    for ((outcome, expected) in cases) {
      val store = storeWithCredentials()
      coEvery { archiveApi.copyMediaToArchive(any(), any(), any()) } returns RequestResult.Success(listOf(outcome))

      val result = serviceFor(store).copyToArchive(2, "location", 100, MediaName("name"))

      assertThat(result.fold(ifLeft = { it }, ifRight = { it }), name = "$outcome").isEqualTo(expected)
    }
  }

  @Test
  fun `a copy that produced no outcome is an application error`() = runTest {
    val store = storeWithCredentials()

    coEvery { archiveApi.copyMediaToArchive(any(), any(), any()) } returns RequestResult.Success(emptyList())

    val result = serviceFor(store).copyToArchive(2, "location", 100, MediaName("name"))

    assertThat(result.error()).isInstanceOf(ArchiveError.ApplicationError::class)
  }

  // endregion

  // region Listing and deleting media

  @Test
  fun `a lack of entitlement while listing media notifies the store`() = runTest {
    val store = storeWithCredentials()

    coEvery { archiveApi.getArchiveMediaItemsPage(any(), any(), any(), any()) } returns RequestResult.NonSuccess(ArchiveApiV2.GetMediaItemsError.Forbidden)

    val result = serviceFor(store).listRemoteMediaObjects(limit = 10)

    assertThat(result.error()).isInstanceOf(ArchiveError.EntitlementError.NotEntitled::class)
    assertThat(store.notEntitledCount).isEqualTo(1)
  }

  @Test
  fun `a rejected credential while listing media clears the media credentials`() = runTest {
    val store = storeWithCredentials()

    coEvery { archiveApi.getArchiveMediaItemsPage(any(), any(), any(), any()) } returns RequestResult.NonSuccess(ArchiveApiV2.GetMediaItemsError.Unauthorized)

    val result = serviceFor(store).listRemoteMediaObjects(limit = 10)

    assertThat(result.error()).isInstanceOf(ArchiveError.CredentialError.Unauthorized::class)
    assertThat(store.mediaCredentials.isEmpty).isTrue()
    assertThat(store.mediaBackupInitialized).isFalse()
    assertThat(store.messageBackupInitialized).isTrue()
    assertThat(store.notEntitledCount).isEqualTo(0)
  }

  @Test
  fun `deleting nothing is a success that never touches the network`() = runTest {
    val store = storeWithCredentials()

    val result = serviceFor(store).deleteArchivedMedia(emptyList())

    assertThat(result.isRight()).isTrue()
    coVerify(exactly = 0) { archiveApi.deleteArchivedMedia(any(), any(), any()) }
  }

  @Test
  fun `deletes are chunked at the server limit`() = runTest {
    val store = storeWithCredentials()
    val items = (1..2500).map { DeleteBackupMediaItem(ByteArray(15), 3) }
    val chunkSizes = mutableListOf<Int>()

    coEvery { archiveApi.deleteArchivedMedia(any(), any(), any()) } answers {
      val chunk = arg<List<DeleteBackupMediaItem>>(2)
      chunkSizes += chunk.size
      RequestResult.Success(chunk)
    }

    val result = serviceFor(store).deleteArchivedMedia(items)

    assertThat(result.isRight()).isTrue()
    assertThat(chunkSizes).isEqualTo(listOf(1000, 1000, 500))
  }

  @Test
  fun `a failed delete chunk stops the remaining chunks`() = runTest {
    val store = storeWithCredentials()
    val items = (1..2500).map { DeleteBackupMediaItem(ByteArray(15), 3) }

    coEvery { archiveApi.deleteArchivedMedia(any(), any(), any()) } returns RequestResult.RetryableNetworkError(IOException("down"))

    val result = serviceFor(store).deleteArchivedMedia(items)

    assertThat(result.error()).isInstanceOf(ArchiveError.NetworkError::class)
    coVerify(exactly = 1) { archiveApi.deleteArchivedMedia(any(), any(), any()) }
  }

  // endregion

  // region Cached derived values

  @Test
  fun `the archived media cdn path is cached after the first fetch`() = runTest {
    val store = storeWithCredentials()

    coEvery { archiveApi.getMediaBackupInfo(any(), any()) } returns RequestResult.Success(MediaBackupInfo("abc123", "def456", 100))

    val service = serviceFor(store)
    val first = service.getArchivedMediaCdnPath()
    val second = service.getArchivedMediaCdnPath()

    assertThat(first.getOrNull()).isEqualTo("abc123/def456")
    assertThat(second.getOrNull()).isEqualTo("abc123/def456")
    assertThat(store.cachedMediaCdnPath).isEqualTo("abc123/def456")
    coVerify(exactly = 1) { archiveApi.getMediaBackupInfo(any(), any()) }
  }

  @Test
  fun `cached cdn read credentials are preferred over a fetch`() = runTest {
    val cached = GetArchiveCdnCredentialsResponse(mapOf("cached" to "yes"))
    val store = storeWithCredentials().apply { messageCredentials.cdnReadCredentials = cached }

    val result = serviceFor(store).getCdnReadCredentials(ArchiveService.CredentialType.MESSAGE, cdnNumber = 3)

    assertThat(result.getOrNull()).isSameInstanceAs(cached)
    coVerify(exactly = 0) { archiveApi.getCdnReadCredentials(any(), any(), any()) }
  }

  @Test
  fun `freshly fetched cdn read credentials are cached against the right credential type`() = runTest {
    val store = storeWithCredentials()
    val fetched = GetArchiveCdnCredentialsResponse(mapOf("fresh" to "yes"))

    coEvery { archiveApi.getCdnReadCredentials(any(), any(), any()) } returns RequestResult.Success(fetched)

    val result = serviceFor(store).getCdnReadCredentials(ArchiveService.CredentialType.MEDIA, cdnNumber = 3)

    assertThat(result.getOrNull()).isSameInstanceAs(fetched)
    assertThat(store.mediaCredentials.cdnReadCredentials).isSameInstanceAs(fetched)
    assertThat(store.messageCredentials.cdnReadCredentials).isNull()
  }

  // endregion

  // region Backup file location

  @Test
  fun `the message backup file location is built from the backup info`() = runTest {
    val store = storeWithCredentials()

    coEvery { archiveApi.getMessageBackupInfo(any(), any()) } returns RequestResult.Success(MessageBackupInfo("dir", 3, "name"))
    coEvery { archiveApi.getCdnReadCredentials(any(), any(), any()) } returns RequestResult.Success(GetArchiveCdnCredentialsResponse(mapOf("a" to "b")))

    val location = serviceFor(store).getMessageBackupFileLocation().getOrNull()

    assertThat(location).isNotNull()
    assertThat(location!!.cdn).isEqualTo(3)
    assertThat(location.path).isEqualTo("backups/dir/name")
    assertThat(location.cdnCredentials).isEqualTo(mapOf("a" to "b"))
  }

  @Test
  fun `a location lookup for a specific key uses a freshly fetched credential rather than the cache`() = runTest {
    val store = storeWithCredentials()
    val currentTime = System.currentTimeMillis()
    val startOfDay = currentTime.milliseconds.inWholeDays.days.inWholeSeconds

    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.Success(
      ArchiveApiV2.ArchiveCredentials(
        messageCredentials = listOf(credential(redemptionTime = startOfDay)),
        mediaCredentials = emptyList()
      )
    )
    coEvery { archiveApi.getMessageBackupInfo(any(), any()) } returns RequestResult.Success(MessageBackupInfo("dir", 3, "name"))
    coEvery { archiveApi.getCdnReadCredentials(any(), any(), any()) } returns RequestResult.Success(GetArchiveCdnCredentialsResponse(mapOf("a" to "b")))

    val result = serviceFor(store).getMessageBackupFileLocationForKey(store.aci, store.messageBackupKey)

    assertThat(result.getOrNull()?.path).isEqualTo("backups/dir/name")
    coVerify(exactly = 1) { archiveApi.getServiceCredentials(any()) }

    // The point of this path is to check a key the user typed, so nothing about it may be written back to the cache.
    assertThat(store.messageCredentials.clearedOlderThan).isEmpty()
    assertThat(store.messageCredentials.cdnReadCredentials).isNull()
  }

  @Test
  fun `a location lookup for a specific key fails when no credential covers today`() = runTest {
    val store = storeWithCredentials()

    coEvery { archiveApi.getServiceCredentials(any()) } returns RequestResult.Success(
      ArchiveApiV2.ArchiveCredentials(
        messageCredentials = listOf(credential(redemptionTime = 1)),
        mediaCredentials = emptyList()
      )
    )

    val result = serviceFor(store).getMessageBackupFileLocationForKey(store.aci, store.messageBackupKey)

    assertThat(result.error()).isInstanceOf(ArchiveError.ApplicationError::class)
  }

  // endregion

  // region successOrThrow

  @Test
  fun `successOrThrow rethrows the underlying cause`() {
    val cause = IOException("boom")
    val result: Either<ArchiveError, Unit> = ArchiveError.NetworkError(cause).left()

    val thrown = runCatching { result.successOrThrow() }.exceptionOrNull()

    assertThat(thrown).isSameInstanceAs(cause)
  }

  @Test
  fun `successOrThrow reconstructs the legacy status code for an error with no cause`() {
    // The throwing call sites are jobs that branch on NonSuccessfulResponseCodeException.code to pick a retry policy, so a bare IOException would silently
    // change their behavior. Anything unmapped must still be an IOException, since the job runner treats a runtime exception as a crash.
    val cases = mapOf<ArchiveError, Int>(
      ArchiveError.CredentialError.InvalidRequest() to 400,
      ArchiveError.CredentialError.Unauthorized() to 401,
      ArchiveError.EntitlementError.NotEntitled() to 403,
      ArchiveError.CredentialError.NotFound() to 404,
      ArchiveError.CredentialError.RateLimited(null) to 429
    )

    for ((error, expectedCode) in cases) {
      val result: Either<ArchiveError, Unit> = error.left()

      val thrown = runCatching { result.successOrThrow() }.exceptionOrNull()!!

      assertThat(thrown, name = "$error").isInstanceOf(NonSuccessfulResponseCodeException::class)
      assertThat((thrown as NonSuccessfulResponseCodeException).code, name = "$error").isEqualTo(expectedCode)
    }
  }

  @Test
  fun `successOrThrow throws a plain IOException for an unmapped error with no cause`() {
    val result: Either<ArchiveError, Unit> = ArchiveError.CopyMediaError.SourceNotFound.left()

    val thrown = runCatching { result.successOrThrow() }.exceptionOrNull()

    assertThat(thrown!!).isInstanceOf(IOException::class)
  }

  // endregion

  // region Server-side vs transport failures

  @Test
  fun `a server error is distinguishable from a transport failure`() {
    // ArchiveBackupIdReservationJob backs off much harder for a struggling server than for a flaky connection.
    assertThat(ArchiveError.NetworkError(ServerSideErrorException("Server error: 503")).isServerSide).isTrue()
    assertThat(ArchiveError.NetworkError(IOException("connection reset")).isServerSide).isFalse()
  }

  // endregion

  // region Refresh

  @Test
  fun `refreshing a paid backup refreshes both message and media access`() = runTest {
    val store = storeWithCredentials()
    givenSuccessfulRefresh(backupLevel = BackupLevel.PAID)

    val result = serviceFor(store).refreshBackup()

    assertThat(result.isRight()).isTrue()
    coVerify(exactly = 2) { archiveApi.refreshBackup(any(), any()) }
  }

  @Test
  fun `refreshing a free backup refreshes message access only`() = runTest {
    val store = storeWithCredentials()
    givenSuccessfulRefresh(backupLevel = BackupLevel.FREE)

    val result = serviceFor(store).refreshBackup()

    assertThat(result.isRight()).isTrue()
    coVerify(exactly = 1) { archiveApi.refreshBackup(any(), any()) }
  }

  // endregion

  // region Helpers

  private fun storeWithCredentials(cachedMediaCdnPath: String? = null, isPreRestoreDuringRegistration: Boolean = false): FakeArchiveCacheStore {
    return FakeArchiveCacheStore(cachedMediaCdnPath = cachedMediaCdnPath, isPreRestoreDuringRegistration = isPreRestoreDuringRegistration).apply {
      messageCredentials.add(listOf(credential()))
      mediaCredentials.add(listOf(credential()))
    }
  }

  private fun credential(redemptionTime: Long = 0): ArchiveServiceCredential {
    return ArchiveServiceCredential(ByteArray(16), redemptionTime)
  }

  /** The raised error, failing the test if the operation actually succeeded. */
  private fun <E : ArchiveError, T> Either<E, T>.error(): E {
    return leftOrNull() ?: throw AssertionError("Expected an error, but the operation succeeded with ${getOrNull()}")
  }

  private fun givenSuccessfulRefresh(backupLevel: BackupLevel = BackupLevel.FREE) {
    every { archiveApi.getZkCredential(any(), any()) } returns ArchiveApiV2.ZkCredentialResult.Success(
      mockk<BackupAuthCredential> { every { this@mockk.backupLevel } returns backupLevel }
    )
    coEvery { archiveApi.refreshBackup(any(), any()) } returns RequestResult.Success(Unit)
  }

  // endregion
}
