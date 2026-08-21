/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.recover
import arrow.core.right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.backup.MediaName
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.logging.Log
import org.signal.core.util.urlEncode
import org.signal.libsignal.net.CopyBackupMediaItem
import org.signal.libsignal.net.CopyBackupMediaOutcome
import org.signal.libsignal.net.DeleteBackupMediaItem
import org.signal.libsignal.net.GetUploadFormError
import org.signal.libsignal.net.MediaBackupInfo
import org.signal.libsignal.net.MessageBackupInfo
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.net.ServerSideErrorException
import org.signal.libsignal.net.UploadTooLargeException
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.backups.BackupLevel
import org.signal.network.NetworkResult
import org.signal.network.api.ArchiveApiV2
import org.signal.network.api.ArchiveApiV2.ZkCredentialResult
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.service.ArchiveError.CopyMediaError
import org.signal.network.service.ArchiveError.CredentialError
import org.signal.network.service.ArchiveError.EntitlementError
import org.signal.network.service.ArchiveError.UploadFormError
import org.signal.network.service.ArchiveService.CredentialType
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccess
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccessPair
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.AuthCredentials
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration

/**
 * Collection of higher-level archive operations.
 *
 * Nearly every one of these is a chain of several [ArchiveApiV2] calls, because the archive endpoints sit behind an anonymous auth credential system that is a
 * precursor to almost every request. These credentials must be cached and re-used, and this class attempts to encapsulate all of that to provide a clean
 * interface.
 */
class ArchiveService(
  private val archiveApi: ArchiveApiV2,
  private val store: ArchiveCacheStore
) {

  companion object {
    private val TAG = Log.tag(ArchiveService::class)

    /** The server caps how many media items may be deleted in a single request. */
    private const val MAX_DELETE_ITEMS_PER_REQUEST = 1000

    /** Only suitable for debugging -- an account can have far more media than this in a single page. */
    private const val DEBUG_MEDIA_PAGE_SIZE = 10_000
  }

  /**
   * During normal operation, ensures that the backupId has been reserved and that your public key has been set, while also returning archive access data.
   * Should be the basis of all backup operations.
   *
   * When called during registration before backups are initialized, will only fetch access data and not initialize backups. This prevents early initialization
   * with incorrect keys before we have restored them.
   */
  suspend fun getArchiveServiceAccess(): Either<CredentialError, ArchiveServiceAccessPair> = request {
    initBackupAndFetchAuth()
  }

  /**
   * Ensures that backupIds are reserved on the service. As documented on [ArchiveApiV2.triggerBackupIdReservation], this is safe to perform multiple times.
   *
   * Pass false for [includeMedia] to skip reserving the media backupId, which also avoids spending a rate limit permit on it. That's what you want during
   * registration, where only the message backup is relevant.
   */
  suspend fun triggerBackupIdReservation(includeMedia: Boolean = true): Either<CredentialError, Unit> = request {
    reserveBackupId(
      messageBackupKey = store.messageBackupKey,
      mediaRootBackupKey = if (includeMedia) store.mediaRootBackupKey else null
    )

    store.messageCredentials.clearAll()
    if (includeMedia) {
      store.mediaCredentials.clearAll()
    }
  }

  /**
   * Indicates that this backup is still active. Clients must periodically upload new backups or perform a refresh, or the backup may be deleted after 30 days.
   *
   * Media is only refreshed for paid-tier accounts, since free-tier accounts have no media backup to keep alive.
   */
  suspend fun refreshBackup(): Either<CredentialError, Unit> = request {
    Log.d(TAG, "Refreshing backup...")
    val access = initBackupAndFetchAuth()

    val backupLevel = backupLevelOf(access)
    Log.d(TAG, "Fetched backup level. Refreshing message backup access.")

    withRejectedCredentialActions {
      refreshBackupAccess(access.messageBackupAccess)
      Log.d(TAG, "Refreshed message backup access.")

      if (backupLevel == BackupLevel.PAID) {
        Log.d(TAG, "Refreshing media backup access.")
        refreshBackupAccess(access.mediaBackupAccess)
        Log.d(TAG, "Refreshed media backup access.")
      }
    }
  }

  /**
   * The backup level the service says this account is on, derived locally from the archive credential.
   *
   * Reserving the backupId and re-establishing a rejected credential are side effects of this call. Use [getBackupLevelWithoutDowngrade] to check without them.
   */
  suspend fun getBackupLevel(): Either<CredentialError, BackupLevel> = request {
    backupLevelOf(initBackupAndFetchAuth())
  }

  /**
   * [getBackupLevel] without any of the error handling that clears local state, and without initializing backups as a side effect. Lets a periodic check run
   * without risking rolling the user back.
   */
  suspend fun getBackupLevelWithoutDowngrade(): Either<CredentialError, BackupLevel> = request(selfHeal = false) {
    backupLevelOf(fetchArchiveServiceAccessPair())
  }

  /**
   * Everything needed to read the main message backup file off the cdn: which cdn it's on, the credentials to read from it, and the path to the file.
   */
  suspend fun getMessageBackupFileLocation(): Either<CredentialError, BackupFileLocation> = request {
    val access = initBackupAndFetchAuth()
    val info = fetchMessageBackupInfo(access.messageBackupAccess)
    val credentials = store.messageCredentials.cdnReadCredentials ?: fetchAndCacheCdnReadCredentials(CredentialType.MESSAGE, access, info.cdn)

    BackupFileLocation(info, credentials.headers)
  }

  /**
   * [getMessageBackupFileLocation] using a freshly-fetched credential derived from [messageBackupKey] rather than anything in the cache.
   *
   * This is how you check whether an AEP the user typed in actually belongs to [aci]: a key that isn't associated with the account fails zk verification while
   * deriving the credential, surfacing as [ArchiveError.CredentialError.ZkVerificationFailed].
   */
  suspend fun getMessageBackupFileLocationForKey(aci: ACI, messageBackupKey: MessageBackupKey): Either<CredentialError, BackupFileLocation> = request(selfHeal = false) {
    val access = freshMessageBackupAccess(messageBackupKey)
    val info = fetchMessageBackupInfo(access, aci)
    val credentials = fetchCdnReadCredentials(access, info.cdn, aci)

    BackupFileLocation(info, credentials.headers)
  }

  /**
   * Metadata about the message backup, fetched with a freshly-derived credential for [messageBackupKey]. Carries the same zk verification semantics as
   * [getMessageBackupFileLocationForKey], and the same 401 caveat as [ArchiveApiV2.getMessageBackupInfo].
   */
  suspend fun getMessageBackupInfoForKey(aci: ACI, messageBackupKey: MessageBackupKey): Either<CredentialError, MessageBackupInfo> = request(selfHeal = false) {
    fetchMessageBackupInfo(freshMessageBackupAccess(messageBackupKey), aci)
  }

  /**
   * An upload form for the main message backup file.
   */
  suspend fun getMessageBackupUploadForm(backupFileSize: Long): Either<UploadFormError, AttachmentUploadForm> = request {
    val access = initBackupAndFetchAuth()
    withRejectedCredentialActions {
      fetchMessageBackupUploadForm(access.messageBackupAccess, backupFileSize)
    }
  }

  /**
   * An upload form for a single piece of media, to be uploaded to the transit cdn.
   *
   * Getting it onto the archive cdn still requires a follow-up [copyToArchive].
   */
  suspend fun getMediaUploadForm(uploadLength: Long): Either<UploadFormError, AttachmentUploadForm> = request {
    val access = initBackupAndFetchAuth()
    withRejectedCredentialActions {
      fetchMediaUploadForm(access.mediaBackupAccess, uploadLength)
    }
  }

  /**
   * Credentials for reading from the backup cdn, preferring the cached value if it hasn't expired.
   */
  suspend fun getCdnReadCredentials(credentialType: CredentialType, cdnNumber: Int): Either<CredentialError, GetArchiveCdnCredentialsResponse> = request {
    store.cacheFor(credentialType).cdnReadCredentials?.let { return@request it }

    val access = initBackupAndFetchAuth()
    withRejectedCredentialActions {
      fetchAndCacheCdnReadCredentials(credentialType, access, cdnNumber)
    }
  }

  /**
   * The `{backupDir}/{mediaDir}` path that archived media lives under, preferring the cached value.
   *
   * This changes if the backup expires, a new backupId is set, or the delete-all endpoint is called -- all of which clear the cache.
   */
  suspend fun getArchivedMediaCdnPath(): Either<CredentialError, String> = request {
    store.cachedMediaCdnPath?.let { return@request it }

    val access = initBackupAndFetchAuth()
    val info = withRejectedCredentialActions { fetchMediaBackupInfo(access.mediaBackupAccess) }
    val path = "${info.backupDir.urlEncode()}/${info.mediaDir.urlEncode()}"

    store.cachedMediaCdnPath = path
    path
  }

  /**
   * A page of the media items in the user's archive.
   *
   * @param cursor A token read from the previous response, telling the server where to start the next page.
   */
  suspend fun listRemoteMediaObjects(limit: Int, cursor: String? = null): Either<EntitlementError, ArchiveApiV2.MediaItemsPage> = request {
    val access = initBackupAndFetchAuth()

    withRejectedCredentialActions {
      recover({ fetchMediaItemsPage(access.mediaBackupAccess, limit, cursor) }) { error ->
        if (error is EntitlementError.NotEntitled) {
          store.onNotEntitled()
        }

        raise(error)
      }
    }
  }

  /**
   * Copies media that has already been uploaded to the transit cdn over to the archive cdn.
   *
   * @return The archive cdn number the media landed on.
   */
  suspend fun copyToArchive(cdnNumber: Int, remoteLocation: String, plaintextSize: Long, mediaName: MediaName): Either<CopyMediaError, Int> = request {
    val access = initBackupAndFetchAuth()
    val mediaSecrets = access.mediaBackupAccess.backupKey.deriveMediaSecrets(mediaName)
    val item = CopyBackupMediaItem(
      sourceAttachmentCdn = cdnNumber,
      sourceKey = remoteLocation,
      objectLength = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(plaintextSize)),
      mediaId = mediaSecrets.id.value,
      encryptionKey = mediaSecrets.macKey + mediaSecrets.aesKey
    )
    val outcomes: List<CopyBackupMediaOutcome> = withRejectedCredentialActions {
      bindSimpleRequest(archiveApi.copyMediaToArchive(store.aci, access.mediaBackupAccess, listOf(item)), access.mediaBackupAccess.credentialType)
    }

    when (val outcome = outcomes.firstOrNull()) {
      is CopyBackupMediaOutcome.Success -> outcome.cdn
      is CopyBackupMediaOutcome.SourceNotFound -> raise(CopyMediaError.SourceNotFound)
      is CopyBackupMediaOutcome.WrongSourceLength -> raise(CopyMediaError.WrongSourceLength)
      is CopyBackupMediaOutcome.OutOfSpace -> raise(CopyMediaError.OutOfRemoteSpace)
      null -> raise(ArchiveError.ApplicationError(IllegalStateException("Copy stream ended without an outcome for the item!")))
    }
  }

  /**
   * Deletes media from the backup cdn, in server-sized chunks. An empty list is a no-op success.
   */
  suspend fun deleteArchivedMedia(mediaToDelete: List<DeleteBackupMediaItem>): Either<CredentialError, Unit> = request {
    if (mediaToDelete.isEmpty()) {
      Log.i(TAG, "No media to delete, quick success")
      return@request
    }

    val access = initBackupAndFetchAuth()

    withRejectedCredentialActions {
      mediaToDelete.chunked(MAX_DELETE_ITEMS_PER_REQUEST).forEachIndexed { index, chunk ->
        recover({ deleteArchivedMediaChunk(access.mediaBackupAccess, chunk) }) { error ->
          Log.w(TAG, "Error occurred while deleting archived media chunk #$index: $error")
          raise(error)
        }
      }
    }
  }

  /**
   * Deletes all message backup metadata, objects, and the stored public key. To use backups again, a public key must be resupplied.
   */
  suspend fun deleteMessageBackup(): Either<CredentialError, Unit> = request {
    val access = initBackupAndFetchAuth()
    deleteBackup(access.messageBackupAccess)
  }

  /**
   * The media-backup counterpart to [deleteMessageBackup].
   */
  suspend fun deleteMediaBackup(): Either<CredentialError, Unit> = request {
    val access = initBackupAndFetchAuth()
    deleteBackup(access.mediaBackupAccess)
  }

  /**
   * Auth credentials that can be used to perform SVR-B operations.
   */
  suspend fun getSvrBAuth(): Either<CredentialError, AuthCredentials> = request {
    val access = initBackupAndFetchAuth()
    withRejectedCredentialActions {
      bindSimpleRequest(archiveApi.getSvrBAuthorization(store.aci, access.messageBackupAccess), access.messageBackupAccess.credentialType)
    }
  }

  /**
   * Whether the backupId can currently be rotated. Authenticated, so it needs no archive credential of its own.
   */
  suspend fun getKeyRotationLimit(): Either<EntitlementError, ArchiveApiV2.KeyRotationLimit> = request {
    val result = archiveApi.getKeyRotationLimit()
    when (result) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> when (result.error) {
        ArchiveApiV2.GetKeyRotationLimitError.Forbidden -> raise(EntitlementError.NotEntitled())
      }

      is RequestResult.RetryableNetworkError -> raise(result.toArchiveError())
      is RequestResult.ApplicationError -> raise(result.toArchiveError(credentialType = null))
    }
  }

  /**
   * Every media item in the user's archive. Potentially a very large number of items, making this only suitable for debugging.
   * Use [listRemoteMediaObjects] in production.
   */
  suspend fun debugGetArchivedMediaState(): Either<EntitlementError, List<ArchiveApiV2.StoredMediaObject>> = request {
    debugFetchAllMediaObjects(initBackupAndFetchAuth())
  }

  /**
   * A summary of the remote backup state, for debugging.
   */
  suspend fun debugGetRemoteBackupState(): Either<EntitlementError, DebugBackupMetadata> = request {
    val access = initBackupAndFetchAuth()
    val mediaBackupInfo = fetchMediaBackupInfo(access.mediaBackupAccess)
    val mediaObjects = debugFetchAllMediaObjects(access)

    DebugBackupMetadata(
      usedSpace = mediaBackupInfo.usedSpace,
      mediaCount = mediaObjects.size.toLong(),
      mediaSize = mediaObjects.sumOf { it.objectLength }
    )
  }

  private suspend fun Raise<EntitlementError>.debugFetchAllMediaObjects(access: ArchiveServiceAccessPair): List<ArchiveApiV2.StoredMediaObject> {
    val mediaObjects = mutableListOf<ArchiveApiV2.StoredMediaObject>()

    var cursor: String? = null
    do {
      val page = fetchMediaItemsPage(access.mediaBackupAccess, DEBUG_MEDIA_PAGE_SIZE, cursor)
      mediaObjects += page.storedMediaObjects
      cursor = page.cursor
    } while (cursor != null)

    return mediaObjects
  }

  /**
   * See [getArchiveServiceAccess].
   */
  private suspend fun Raise<CredentialError>.initBackupAndFetchAuth(): ArchiveServiceAccessPair {
    val needsMessageInit = !store.messageBackupInitialized
    val needsMediaInit = !store.mediaBackupInitialized

    return if (store.isLinkedDevice || (store.messageBackupInitialized && store.mediaBackupInitialized)) {
      withAuthErrorActions {
        fetchArchiveServiceAccessPair()
      }
    } else if (store.isPreRestoreDuringRegistration) {
      Log.w(TAG, "Requesting/using auth credentials in pre-restore state", Throwable())
      withAuthErrorActions(includeUnauthorizedActions = false) {
        fetchArchiveServiceAccessPair()
      }
    } else {
      withAuthErrorActions {
        Log.i(TAG, "Initializing backups. message: $needsMessageInit, media: $needsMediaInit")

        // Only the sides that actually need it, since the service rate-limits media reservations far more aggressively than message ones.
        reserveBackupId(
          messageBackupKey = if (needsMessageInit) store.messageBackupKey else null,
          mediaRootBackupKey = if (needsMediaInit) store.mediaRootBackupKey else null
        )

        if (needsMessageInit) {
          store.messageCredentials.clearAll()
        }
        if (needsMediaInit) {
          store.mediaCredentials.clearAll()
        }

        val access = fetchArchiveServiceAccessPair()

        // Marked one at a time so that a failure on the second side doesn't cost us the reservation we just spent on the first.
        if (needsMessageInit) {
          setPublicKey(access.messageBackupAccess)
          store.messageBackupInitialized = true
        }
        if (needsMediaInit) {
          setPublicKey(access.mediaBackupAccess)
          store.mediaBackupInitialized = true
        }

        access
      }
    }
  }

  /**
   * Retrieves an archive credential pair, preferring the cached values. Falling through to the network here means the routine background fetch isn't running
   * properly, since fetching on-demand undermines the anonymity the credentials exist to provide.
   */
  private suspend fun Raise<CredentialError>.fetchArchiveServiceAccessPair(): ArchiveServiceAccessPair {
    val currentTime = System.currentTimeMillis()

    val messageCredential = store.messageCredentials.getForTime(currentTime.milliseconds)
    val mediaCredential = store.mediaCredentials.getForTime(currentTime.milliseconds)

    if (messageCredential != null && mediaCredential != null) {
      return ArchiveServiceAccessPair(
        messageBackupAccess = ArchiveServiceAccess(messageCredential, store.messageBackupKey),
        mediaBackupAccess = ArchiveServiceAccess(mediaCredential, store.mediaRootBackupKey)
      )
    }

    Log.w(TAG, "No credentials found for today, need to fetch new ones! This shouldn't happen under normal circumstances. We should ensure the routine fetch is running properly.")

    val response = fetchServiceCredentials(currentTime)

    store.messageCredentials.add(response.messageCredentials)
    store.messageCredentials.clearOlderThan(currentTime)

    store.mediaCredentials.add(response.mediaCredentials)
    store.mediaCredentials.clearOlderThan(currentTime)

    return ArchiveServiceAccessPair(
      messageBackupAccess = ArchiveServiceAccess(store.messageCredentials.getForTime(currentTime.milliseconds)!!, store.messageBackupKey),
      mediaBackupAccess = ArchiveServiceAccess(store.mediaCredentials.getForTime(currentTime.milliseconds)!!, store.mediaRootBackupKey)
    )
  }

  /**
   * Applies the local state cleanup that a rejected credential implies. Scoped to the credential-fetching portion of an operation, because a 401 from, say,
   * fetching backup metadata says something different than a 401 while establishing the credential itself.
   *
   * A failed zk derivation gets the same cleanup, but is handled in [request] instead, since it isn't endpoint-dependent the way a 401 is.
   *
   * Pass false for [includeUnauthorizedActions] to log a rejected credential without acting on it, which is what registration wants before keys are restored.
   */
  private inline fun <T> Raise<CredentialError>.withAuthErrorActions(includeUnauthorizedActions: Boolean = true, block: Raise<CredentialError>.() -> T): T {
    return recover({ block() }) { error ->
      if (error is CredentialError.Unauthorized && includeUnauthorizedActions) {
        Log.w(TAG, "Credential rejected (${error.credentialType.describe()}). Resetting initialized state + auth credentials.", error.cause)
        resetInitializedState(error.credentialType)
      }

      raise(error)
    }
  }

  /**
   * Applies the local state cleanup that a rejected *anonymous* credential implies. The counterpart to [withAuthErrorActions]: that one covers establishing a
   * credential, this one covers using an established one. Without it a rejected credential is never cleared, so callers retry against the same bad credential
   * until it expires on its own.
   *
   * Only intended to wrap the portion of an operation that runs *after* [initBackupAndFetchAuth], so it can't undo that method's pre-restore exemption.
   */
  private inline fun <E : ArchiveError, T> Raise<E>.withRejectedCredentialActions(block: Raise<E>.() -> T): T {
    return recover({ block() }) { error ->
      if (error is CredentialError.Unauthorized) {
        Log.w(TAG, "Anonymous credential rejected (${error.credentialType.describe()}). Resetting initialized state + auth credentials.", error.cause)
        resetInitializedState(error.credentialType)
      }

      raise(error)
    }
  }

  /**
   * Fetches fresh cdn read credentials and caches them. Callers that can tolerate a cached value should check [ArchiveCacheStore.CredentialCache.cdnReadCredentials]
   * first -- these are cheap to re-fetch but not free.
   */
  private suspend fun Raise<CredentialError>.fetchAndCacheCdnReadCredentials(credentialType: CredentialType, access: ArchiveServiceAccessPair, cdnNumber: Int): GetArchiveCdnCredentialsResponse {
    val archiveServiceAccess = when (credentialType) {
      CredentialType.MESSAGE -> access.messageBackupAccess
      CredentialType.MEDIA -> access.mediaBackupAccess
    }

    val credentials = fetchCdnReadCredentials(archiveServiceAccess, cdnNumber)
    store.cacheFor(credentialType).cdnReadCredentials = credentials

    return credentials
  }

  private fun Raise<CredentialError>.backupLevelOf(access: ArchiveServiceAccessPair): BackupLevel {
    return when (val result = archiveApi.getZkCredential(store.aci, access.messageBackupAccess)) {
      is ZkCredentialResult.Success -> result.value.backupLevel
      is ZkCredentialResult.Failure.VerificationFailed -> raise(CredentialError.ZkVerificationFailed(result.exception, access.messageBackupAccess.credentialType))
      is ZkCredentialResult.Failure.MalformedCredential -> raise(ArchiveError.ApplicationError(result.exception))
    }
  }

  /**
   * Which archive an access belongs to, so that an error raised while using it can be attributed to one side. [BackupKey] isn't sealed, so an unrecognized key
   * reports null and takes the both-sides cleanup rather than guessing.
   */
  private val ArchiveServiceAccess<*>.credentialType: CredentialType?
    get() = when (backupKey) {
      is MessageBackupKey -> CredentialType.MESSAGE
      is MediaRootBackupKey -> CredentialType.MEDIA
      else -> null
    }

  private fun ArchiveCacheStore.cacheFor(credentialType: CredentialType): ArchiveCacheStore.CredentialCache {
    return when (credentialType) {
      CredentialType.MESSAGE -> messageCredentials
      CredentialType.MEDIA -> mediaCredentials
    }
  }

  private fun startOfDay(currentTime: Long): Long {
    return currentTime.milliseconds.inWholeDays.days.inWholeSeconds
  }

  /**
   * The general cruft we need to do for every request -- run on IO, map to an Either, and handle common verification errors.
   *
   * Pass false for [selfHeal] to keep a zk failure from touching the initialized state: either the credential came from a key the caller handed us rather than
   * the account's own, or the operation is one that must never change local state. See [onZkVerificationFailed].
   */
  private suspend fun <E : ArchiveError, T> request(selfHeal: Boolean = true, block: suspend Raise<E>.() -> T): Either<E, T> {
    return withContext(Dispatchers.IO) {
      either {
        recover({ block() }) { error ->
          if (error is CredentialError.ZkVerificationFailed) {
            onZkVerificationFailed(error, selfHeal)
          }

          raise(error)
        }
      }
    }
  }

  /**
   * Handles a ZK verification failure by resetting initialization state when appropriate.
   */
  private fun onZkVerificationFailed(error: CredentialError.ZkVerificationFailed, selfHeal: Boolean) {
    if (!selfHeal || store.isPreRestoreDuringRegistration) {
      Log.w(TAG, "Unable to verify/receive credentials (${error.credentialType.describe()}). Leaving local state alone.", error.exception)
      return
    }

    Log.w(TAG, "Unable to verify/receive credentials for our own key (${error.credentialType.describe()}). Our backupId is likely stale. Resetting initialized state + auth credentials.", error.exception)
    resetInitializedState(error.credentialType)
  }

  private fun resetInitializedState(credentialType: CredentialType?) {
    when (credentialType) {
      CredentialType.MESSAGE -> {
        store.messageBackupInitialized = false
      }
      CredentialType.MEDIA -> {
        store.mediaBackupInitialized = false
        store.cachedMediaCdnPath = null
      }
      null -> {
        store.messageBackupInitialized = false
        store.mediaBackupInitialized = false
        store.cachedMediaCdnPath = null
      }
    }

    clearCredentials(credentialType)
  }

  private fun clearCredentials(credentialType: CredentialType?) {
    when (credentialType) {
      null -> {
        store.messageCredentials.clearAll()
        store.mediaCredentials.clearAll()
      }
      else -> {
        store.cacheFor(credentialType).clearAll()
      }
    }
  }

  private fun CredentialType?.describe(): String = this?.name ?: "unattributed"

  /**
   * Reserves the backupIds for the keys provided. Pass null for either to skip reserving it.
   */
  private suspend fun Raise<CredentialError>.reserveBackupId(messageBackupKey: MessageBackupKey?, mediaRootBackupKey: MediaRootBackupKey?) {
    val result = archiveApi.triggerBackupIdReservation(messageBackupKey, mediaRootBackupKey, store.aci)

    when (result) {
      is RequestResult.Success -> Unit
      is RequestResult.NonSuccess -> when (val error = result.error) {
        ArchiveApiV2.SetBackupIdError.InvalidCredential -> raise(CredentialError.InvalidRequest())
        ArchiveApiV2.SetBackupIdError.Unauthorized -> raise(CredentialError.Unauthorized())
        is ArchiveApiV2.SetBackupIdError.RateLimited -> raise(CredentialError.RateLimited(error.retryAfter))
      }
      is RequestResult.RetryableNetworkError -> raise(result.toArchiveError())
      is RequestResult.ApplicationError -> raise(result.toArchiveError(credentialType = null))
    }
  }

  private suspend fun Raise<CredentialError>.fetchServiceCredentials(currentTime: Long): ArchiveApiV2.ArchiveCredentials {
    val result = archiveApi.getServiceCredentials(currentTime)

    return when (result) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> when (val error = result.error) {
        ArchiveApiV2.GetServiceCredentialsError.InvalidRedemptionTimes -> raise(CredentialError.InvalidRequest())
        ArchiveApiV2.GetServiceCredentialsError.Unauthorized -> raise(CredentialError.Unauthorized())
        ArchiveApiV2.GetServiceCredentialsError.BackupIdNotFound -> raise(CredentialError.NotFound())
        is ArchiveApiV2.GetServiceCredentialsError.RateLimited -> raise(CredentialError.RateLimited(error.retryAfter))
      }
      is RequestResult.RetryableNetworkError -> raise(result.toArchiveError())
      is RequestResult.ApplicationError -> raise(result.toArchiveError(credentialType = null))
    }
  }

  private suspend fun Raise<CredentialError>.setPublicKey(access: ArchiveServiceAccess<*>) {
    bindSimpleRequest(archiveApi.setPublicKey(store.aci, access), access.credentialType)
  }

  /**
   * Tells the service that the backup behind [access] is still in use, so it doesn't get cleaned up.
   */
  private suspend fun Raise<CredentialError>.refreshBackupAccess(access: ArchiveServiceAccess<*>) {
    bindSimpleRequest(archiveApi.refreshBackup(store.aci, access), access.credentialType)
  }

  /**
   * Access built from a credential fetched right now for [messageBackupKey], bypassing the cache entirely. The cache is keyed to the account's stored key, so a
   * caller checking some other key (like one the user typed) must neither read from it nor write to it.
   */
  private suspend fun Raise<CredentialError>.freshMessageBackupAccess(messageBackupKey: MessageBackupKey): ArchiveServiceAccess<MessageBackupKey> {
    val currentTime = System.currentTimeMillis()
    val response = fetchServiceCredentials(currentTime)

    val credential: ArchiveServiceCredential = response.messageCredentials
      .associateBy { it.redemptionTime }[startOfDay(currentTime)]
      ?: raise(ArchiveError.ApplicationError(IllegalStateException("No credential available for the current time.")))

    return ArchiveServiceAccess(credential, messageBackupKey)
  }

  private suspend fun Raise<CredentialError>.fetchMessageBackupInfo(access: ArchiveServiceAccess<MessageBackupKey>, aci: ACI = store.aci): MessageBackupInfo {
    return bindSimpleRequest(archiveApi.getMessageBackupInfo(aci, access), access.credentialType)
  }

  private suspend fun Raise<CredentialError>.fetchMediaBackupInfo(access: ArchiveServiceAccess<MediaRootBackupKey>): MediaBackupInfo {
    return bindSimpleRequest(archiveApi.getMediaBackupInfo(store.aci, access), access.credentialType)
  }

  private suspend fun Raise<CredentialError>.fetchCdnReadCredentials(access: ArchiveServiceAccess<*>, cdnNumber: Int, aci: ACI = store.aci): GetArchiveCdnCredentialsResponse {
    return bindSimpleRequest(archiveApi.getCdnReadCredentials(cdnNumber, aci, access), access.credentialType)
  }

  private suspend fun Raise<UploadFormError>.fetchMessageBackupUploadForm(access: ArchiveServiceAccess<MessageBackupKey>, backupFileSize: Long): AttachmentUploadForm {
    return bindUploadForm(archiveApi.getMessageBackupUploadForm(store.aci, access, backupFileSize), access.credentialType)
  }

  private suspend fun Raise<UploadFormError>.fetchMediaUploadForm(access: ArchiveServiceAccess<MediaRootBackupKey>, uploadLength: Long): AttachmentUploadForm {
    return bindUploadForm(archiveApi.getMediaUploadForm(store.aci, access, uploadLength), access.credentialType)
  }

  private suspend fun Raise<EntitlementError>.fetchMediaItemsPage(access: ArchiveServiceAccess<MediaRootBackupKey>, limit: Int, cursor: String?): ArchiveApiV2.MediaItemsPage {
    val result = archiveApi.getArchiveMediaItemsPage(store.aci, access, limit, cursor)

    return when (result) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> when (val error = result.error) {
        ArchiveApiV2.GetMediaItemsError.InvalidRequest -> raise(CredentialError.InvalidRequest())
        ArchiveApiV2.GetMediaItemsError.Unauthorized -> raise(CredentialError.Unauthorized(credentialType = access.credentialType))
        ArchiveApiV2.GetMediaItemsError.Forbidden -> raise(EntitlementError.NotEntitled())
        is ArchiveApiV2.GetMediaItemsError.RateLimited -> raise(CredentialError.RateLimited(error.retryAfter))
      }
      is RequestResult.RetryableNetworkError -> raise(result.toArchiveError())
      is RequestResult.ApplicationError -> raise(result.toArchiveError(access.credentialType))
    }
  }

  private suspend fun Raise<CredentialError>.deleteArchivedMediaChunk(access: ArchiveServiceAccess<MediaRootBackupKey>, chunk: List<DeleteBackupMediaItem>): List<DeleteBackupMediaItem> {
    return bindSimpleRequest(archiveApi.deleteArchivedMedia(store.aci, access, chunk), access.credentialType)
  }

  private suspend fun Raise<CredentialError>.deleteBackup(access: ArchiveServiceAccess<*>) {
    bindSimpleRequest(archiveApi.deleteBackup(store.aci, access), access.credentialType)
  }

  /**
   * The majority of archive endpoints, whose only modeled non-success is a rejected credential.
   */
  private fun <T> Raise<CredentialError>.bindSimpleRequest(result: RequestResult<T, RequestUnauthorizedException>, credentialType: CredentialType?): T {
    return when (result) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> raise(CredentialError.Unauthorized(result.error, credentialType))
      is RequestResult.RetryableNetworkError -> raise(result.toArchiveError())
      is RequestResult.ApplicationError -> raise(result.toArchiveError(credentialType))
    }
  }

  /**
   * [bindSimpleRequest] for the upload-form endpoints, which can additionally reject the requested size.
   */
  private fun <T> Raise<UploadFormError>.bindUploadForm(result: RequestResult<T, GetUploadFormError>, credentialType: CredentialType?): T {
    return when (result) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> when (val error = result.error) {
        is UploadTooLargeException -> raise(UploadFormError.TooLarge(error))
        is RequestUnauthorizedException -> raise(CredentialError.Unauthorized(error, credentialType))
      }
      is RequestResult.RetryableNetworkError -> raise(result.toArchiveError())
      is RequestResult.ApplicationError -> raise(result.toArchiveError(credentialType))
    }
  }

  /**
   * A failed request means the same thing on every endpoint, so each wrapper above hands this branch straight to `raise`.
   *
   * Note that libsignal folds a rate limit in here with a retry-after attached rather than giving it its own error type.
   */
  private fun RequestResult.RetryableNetworkError.toArchiveError(): CredentialError {
    return when (val retryAfter = retryAfter) {
      null -> ArchiveError.NetworkError(networkError)
      else -> CredentialError.RateLimited(retryAfter.toKotlinDuration(), networkError)
    }
  }

  /**
   * A local failure means the same thing on every endpoint too. Failing to derive a zk credential arrives here, but means something far more specific than
   * "unexpected": the backup key doesn't belong to this account.
   */
  private fun RequestResult.ApplicationError.toArchiveError(credentialType: CredentialType?): CredentialError {
    return when (val failure = cause) {
      is VerificationFailedException -> CredentialError.ZkVerificationFailed(failure, credentialType)
      else -> ArchiveError.ApplicationError(failure)
    }
  }

  /**
   * Where the main message backup file lives on the cdn, and what's needed to read it.
   */
  data class BackupFileLocation(
    val cdn: Int,
    val cdnCredentials: Map<String, String>,
    val path: String
  ) {
    constructor(info: MessageBackupInfo, cdnCredentials: Map<String, String>) : this(
      cdn = info.cdn,
      cdnCredentials = cdnCredentials,
      path = "backups/${info.backupDir}/${info.backupName}"
    )
  }

  /**
   * A summary of the remote backup state. Debug-only.
   */
  data class DebugBackupMetadata(
    val usedSpace: Long,
    val mediaCount: Long,
    val mediaSize: Long
  )

  /**
   * Which of the two archives an operation applies to. The service issues separate credentials for each, keyed to separate backupIds, so nearly everything in
   * this file is scoped to one of them.
   */
  enum class CredentialType {
    MESSAGE, MEDIA
  }
}

/**
 * Returns the value, or throws.
 *
 * Throws the underlying cause where there is one, so callers that already catch things like [IOException] keep working. Intended for the legacy throwing call
 * sites; prefer handling the [Either] directly.
 */
fun <T> Either<ArchiveError, T>.successOrThrow(): T {
  return getOrElse { error -> throw error.cause ?: error.toLegacyException() }
}

/**
 * The exception the pre-[ArchiveService] code would have thrown for an error that carries no cause of its own.
 *
 * This is the one place a status code is reconstructed rather than interpreted, and it exists purely for [successOrThrow]: its callers are jobs that branch on
 * [NonSuccessfulResponseCodeException.code] to decide whether to retry, so handing them a bare [IOException] silently changes their retry behavior. Anything
 * unmapped stays an [IOException] rather than something unchecked, because the job runner treats a runtime exception as a crash.
 */
private fun ArchiveError.toLegacyException(): IOException {
  return when (this) {
    is ArchiveError.CredentialError.InvalidRequest -> NonSuccessfulResponseCodeException(400)
    is ArchiveError.CredentialError.Unauthorized -> NonSuccessfulResponseCodeException(401)
    is ArchiveError.EntitlementError.NotEntitled -> NonSuccessfulResponseCodeException(403)
    is ArchiveError.CredentialError.NotFound -> NonSuccessfulResponseCodeException(404)
    is ArchiveError.CredentialError.RateLimited -> NonSuccessfulResponseCodeException(429)
    else -> IOException("Archive operation failed: $this")
  }
}

/**
 * Translates a [NetworkResult] into the [ArchiveError] vocabulary. Needed for the handful of archive-adjacent requests that still go out through the CDN
 * download path rather than [org.signal.network.api.ArchiveApiV2], so their callers can branch on the same errors [ArchiveService] returns.
 *
 * This is the only place a cdn status code is interpreted -- callers get a named error instead, the same way they do for the requests that go through
 * [org.signal.network.api.ArchiveApiV2]. A 5xx is transient no matter what we asked for, so it lands on [ArchiveError.NetworkError] to be retried.
 */
fun <T> NetworkResult<T>.toArchiveResult(): Either<ArchiveError.BackupFileError, T> {
  return when (this) {
    is NetworkResult.Success -> result.right()
    is NetworkResult.NetworkError -> ArchiveError.NetworkError(exception).left()
    is NetworkResult.ApplicationError -> ArchiveError.ApplicationError(throwable).left()
    is NetworkResult.StatusCodeError -> when (code) {
      401 -> ArchiveError.CredentialError.Unauthorized(exception)
      403 -> ArchiveError.EntitlementError.NotEntitled(exception)
      404 -> ArchiveError.CredentialError.NotFound(exception)
      429 -> ArchiveError.CredentialError.RateLimited(retryAfter(), exception)
      in 500..599 -> ArchiveError.NetworkError(exception)
      else -> ArchiveError.BackupFileError.UnexpectedResponse(exception)
    }.left()
  }
}

/**
 * Everything that can go wrong during an [ArchiveService] operation.
 */
sealed interface ArchiveError {

  /** The failure the underlying layer handed us, where there was one. */
  val cause: Throwable?
    get() = null

  /**
   * Whether the server rejected the request, as opposed to the request failing locally or never arriving.
   *
   * [CredentialError.RateLimited] counts, even though libsignal can also produce it out of a retryable network failure that carried a retry-after.
   */
  val isServerRejection: Boolean
    get() = when (this) {
      is CredentialError.Unauthorized,
      is CredentialError.NotFound,
      is CredentialError.InvalidRequest,
      is CredentialError.RateLimited,
      is EntitlementError.NotEntitled,
      is UploadFormError.TooLarge,
      is BackupFileError.UnexpectedResponse -> {
        true
      }
      is CredentialError.ZkVerificationFailed,
      is NetworkError,
      is ApplicationError,
      is CopyMediaError.SourceNotFound,
      is CopyMediaError.WrongSourceLength,
      is CopyMediaError.OutOfRemoteSpace -> {
        false
      }
    }

  /** A generic, retryable network error. Extends [CredentialError] because that's how it lands in every collection. */
  data class NetworkError(val exception: IOException) : CredentialError {
    override val cause: Throwable get() = exception

    val isServerSide: Boolean
      get() = exception is ServerSideErrorException
  }

  /** An unexpected error. You should likely crash. Extends [CredentialError] because that's how it lands in every collection. */
  data class ApplicationError(val exception: Throwable) : CredentialError {
    override val cause: Throwable get() = exception
  }

  /**
   * Errors that can happen when fetching a credential.
   * By implementing all of these other error collection interfaces, we're saying that all of those collections include [CredentialError].
   */
  sealed interface CredentialError : ArchiveError, UploadFormError, CopyMediaError, EntitlementError, BackupFileError {
    /**
     * The server rejected our credential.
     *
     * [credentialType] is which archive's credential was rejected, or null when the rejection wasn't attributable to one -- our account auth, or a cdn file
     * download mapped by [toArchiveResult]. Local cleanup is scoped to it, since the two archives have independent backupIds.
     */
    data class Unauthorized(override val cause: Throwable? = null, val credentialType: CredentialType? = null) : CredentialError

    /** Nothing exists at the requested location. */
    data class NotFound(override val cause: Throwable? = null) : CredentialError

    /** The server rejected the request as malformed. Retrying the same request won't help. */
    data class InvalidRequest(override val cause: Throwable? = null) : CredentialError

    /** You're rate-limited. Use [retryAfter] for your backoff. */
    data class RateLimited(val retryAfter: Duration?, override val cause: Throwable? = null) : CredentialError

    /**
     * The zkgroup credential could not be derived or verified. It could mean the backup key is incorrect, or some other state-tracking error.
     *
     * [credentialType] carries the same meaning as it does on [Unauthorized].
     */
    data class ZkVerificationFailed(val exception: VerificationFailedException, val credentialType: CredentialType? = null) : CredentialError {
      override val cause: Throwable get() = exception
    }
  }

  /** Errors that can happen when you upload a form. */
  sealed interface UploadFormError : ArchiveError {
    /** The thing you're trying to upload exceeds the server's size limit. Retrying with the same content won't help. */
    data class TooLarge(override val cause: Throwable? = null) : UploadFormError
  }

  /** Errors that can happen when copying media. */
  sealed interface CopyMediaError : ArchiveError {
    /** The attachment being copied no longer exists on the transit cdn. It needs to be re-uploaded. */
    data object SourceNotFound : CopyMediaError

    /** The attachment being copied wasn't the length we told the server it would be. */
    data object WrongSourceLength : CopyMediaError

    /** The account is out of remote storage space. */
    data object OutOfRemoteSpace : CopyMediaError
  }

  /** Errors that can happen when an operation requires paid-tier permissions. */
  sealed interface EntitlementError : ArchiveError {
    /** The account isn't entitled to this operation, e.g. a free-tier user performing a media operation. Also a [BackupFileError]. */
    data class NotEntitled(override val cause: Throwable? = null) : EntitlementError, BackupFileError
  }

  /** Errors that can happen when reading a file off the CDN. */
  sealed interface BackupFileError : ArchiveError {
    /** The cdn refused the request for a reason we don't model. Retrying the same request won't help. */
    data class UnexpectedResponse(override val cause: Throwable? = null) : BackupFileError
  }
}
