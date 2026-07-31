/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.libsignal.internal.CompletableFuture
import org.signal.libsignal.net.BackupAuth
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.CopyBackupMediaItem
import org.signal.libsignal.net.CopyBackupMediaOutcome
import org.signal.libsignal.net.DeleteBackupMediaItem
import org.signal.libsignal.net.GetUploadFormError
import org.signal.libsignal.net.MediaBackupInfo
import org.signal.libsignal.net.MessageBackupInfo
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.net.UnauthBackupsService
import org.signal.libsignal.net.UnauthenticatedChatConnection
import org.signal.libsignal.net.UploadForm
import org.signal.libsignal.net.UploadTooLargeException
import org.signal.libsignal.net.toRequestResult
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.zkgroup.GenericServerPublicParams
import org.signal.libsignal.zkgroup.backups.BackupAuthCredential
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialRequestContext
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialResponse
import org.signal.network.NetworkResult
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.get
import org.signal.network.websocket.put
import org.whispersystems.signalservice.api.archive.ArchiveCredentialPresentation
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse.StoredMediaObject
import org.whispersystems.signalservice.api.archive.ArchiveKeyRotationLimitResponse
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccess
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredentialsResponse
import org.whispersystems.signalservice.api.archive.ArchiveSetBackupIdRequest
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import org.whispersystems.signalservice.api.fromWebSocketRequest
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.InputStream
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Class to interact with various archive-related endpoints.
 * Why is it called archive instead of backup? Because SVR took the "backup" endpoint namespace first :)
 */
class ArchiveApi(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket,
  private val pushServiceSocket: PushServiceSocket,
  private val backupServerPublicParams: GenericServerPublicParams
) {

  companion object {
    private val TAG = Log.tag(ArchiveApi::class)
  }

  /**
   * Retrieves a set of credentials one can use to authorize other requests.
   *
   * You'll receive a set of credentials spanning 7 days. Cache them and store them for later use.
   * It's important that (at least in the common case) you do not request credentials on-the-fly.
   * Instead, request them in advance on a regular schedule. This is because the purpose of these
   * credentials is to keep the caller anonymous, but that doesn't help if this authenticated request
   * happens right before all of the unauthenticated ones, as that would make it easier to correlate
   * traffic.
   *
   * GET /v1/archives/auth
   *
   * - 200: Success
   * - 400: Bad start/end times
   * - 404: BackupId could not be found
   * - 429: Rate-limited
   */
  fun getServiceCredentials(currentTime: Long): NetworkResult<ArchiveServiceCredentialsResponse> {
    val roundedToNearestDay = currentTime.milliseconds.inWholeDays.days
    val endTime = roundedToNearestDay + 7.days

    val request = WebSocketRequestMessage.get("/v1/archives/auth?redemptionStartSeconds=${roundedToNearestDay.inWholeSeconds}&redemptionEndSeconds=${endTime.inWholeSeconds}")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, ArchiveServiceCredentialsResponse::class)
  }

  /**
   * Gets credentials needed to read from the CDN. Make sure you use the right [archiveServiceAccess] depending on whether you're doing a message or media
   * operation.
   *
   * - 401: Bad presentation, invalid public key signature, no matching backupId on the server, or the credential was of the wrong type (messages/media)
   * - 429: Rate-limited
   */
  fun getCdnReadCredentials(cdnNumber: Int, aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<GetArchiveCdnCredentialsResponse> {
    return runWithBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getCdnCredentials(auth, cdnNumber)
    }.toNetworkResult().map { GetArchiveCdnCredentialsResponse(it.headers) }
  }

  /**
   * Ensures that you reserve backupIds for both messages and media on the service. This must be done before any other
   * backup-related calls. You only need to do it once, but repeated calls are safe.
   *
   * Passing null for either key will skip reserving for that backup and not cost a rate limit permit.
   *
   * PUT /v1/archives/backupid
   *
   * - 204: Success
   * - 400: Invalid credential
   * - 429: Rate-limited
   */
  fun triggerBackupIdReservation(messageBackupKey: MessageBackupKey?, mediaRootBackupKey: MediaRootBackupKey?, aci: ACI): NetworkResult<Unit> {
    val messageBackupRequestContext = messageBackupKey?.let { BackupAuthCredentialRequestContext.create(messageBackupKey.value, aci.rawUuid) }
    val mediaBackupRequestContext = mediaRootBackupKey?.let { BackupAuthCredentialRequestContext.create(mediaRootBackupKey.value, aci.rawUuid) }

    val request = WebSocketRequestMessage.put(
      "/v1/archives/backupid",
      ArchiveSetBackupIdRequest(messageBackupRequestContext?.request, mediaBackupRequestContext?.request)
    )

    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Sets a public key on the service derived from your [MessageBackupKey]. This key is used to prevent
   * unauthorized  users from changing your backup data. You only need to do it once, but repeated
   * calls are safe.
   *
   * - 401: The credential in particular is invalid, since the key is being updated
   * - 429: Rate-limited
   */
  fun setPublicKey(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<Unit> {
    return runWithBackupAuth(aci, archiveServiceAccess) { connection, auth -> UnauthBackupsService(connection).setPublicKey(auth) }.toNetworkResult()
  }

  /**
   * Fetches an upload form you can use to upload your main message backup file to cloud storage.
   *
   * - 401: Authorization failed
   * - 413: The backup is too large
   * - 429: Rate-limited
   */
  fun getMessageBackupUploadForm(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MessageBackupKey>, backupFileSize: Long): NetworkResult<AttachmentUploadForm> {
    return runWithBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getUploadForm(auth, backupFileSize)
    }.toUploadFormNetworkResult().map { it.toAttachmentUploadForm() }
  }

  /**
   * Fetches metadata about the currently-stored message backup.
   *
   * - 401: Authorization failed. Note that the server does not distinguish an invalid credential from a backup-id that was never provisioned, so callers using
   *        this to check whether a backup exists should treat a 401 as "backups not set up" rather than a fatal error.
   * - 429: Rate-limited
   */
  fun getMessageBackupInfo(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MessageBackupKey>): NetworkResult<MessageBackupInfo> {
    return runWithBackupAuth(aci, archiveServiceAccess) { connection, auth -> UnauthBackupsService(connection).getMessageBackupInfo(auth) }.toNetworkResult()
  }

  /**
   * Fetches metadata about the currently-stored media backup, including how much space it uses.
   *
   * - 401: Authorization failed. Note that the server does not distinguish an invalid credential from a backup-id that was never provisioned, so callers using
   *        this to check whether a backup exists should treat a 401 as "backups not set up" rather than a fatal error.
   * - 429: Rate-limited
   */
  fun getMediaBackupInfo(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>): NetworkResult<MediaBackupInfo> {
    return runWithBackupAuth(aci, archiveServiceAccess) { connection, auth -> UnauthBackupsService(connection).getMediaBackupInfo(auth) }.toNetworkResult()
  }

  /**
   * Indicate that this backup is still active. Clients must periodically upload new backups or perform a refresh. If a backup is not refreshed, after 30 days
   * it may be deleted.
   *
   * - 401: Authorization failed
   * - 429: Rate-limited
   */
  fun refreshBackup(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<Unit> {
    return runWithBackupAuth(aci, archiveServiceAccess) { connection, auth -> UnauthBackupsService(connection).refresh(auth) }.toNetworkResult()
  }

  /**
   * Delete all backup metadata, objects, and stored public key. To use backups again, a public key must be resupplied.
   *
   * - 401: Authorization failed
   * - 429: Rate-limited
   */
  fun deleteBackup(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<Unit> {
    return runWithBackupAuth(aci, archiveServiceAccess) { connection, auth -> UnauthBackupsService(connection).deleteAll(auth) }.toNetworkResult()
  }

  /**
   * Uploads a pre-encrypted backup file, automatically choosing the best upload strategy based on CDN version.
   * For CDN3, uses TUS "Creation With Upload" (single POST). For other CDNs, falls back to the legacy
   * resumable upload flow.
   *
   * If [existingResumeUrl] is provided, the upload resumes using the existing URL (HEAD+PATCH).
   * Otherwise, a new upload is initiated and [onResumeUrlCreated] is called with the resumable URL
   * before the upload begins, allowing callers to persist it for crash recovery.
   */
  fun uploadBackupFile(
    uploadForm: AttachmentUploadForm,
    data: InputStream,
    dataLength: Long,
    checksumSha256: String? = null,
    progressListener: SignalServiceAttachment.ProgressListener? = null,
    existingResumeUrl: String? = null,
    onResumeUrlCreated: ((String) -> Unit)? = null
  ): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      if (existingResumeUrl != null) {
        Log.i(TAG, "Resuming backup upload via HEAD+PATCH")
        pushServiceSocket.uploadBackupFile(uploadForm, existingResumeUrl, data, dataLength, progressListener)
      } else if (uploadForm.cdn == 3) {
        Log.i(TAG, "Fresh backup upload via creation-with-upload (CDN3)")
        val resumeUrl = uploadForm.signedUploadLocation + "/" + uploadForm.key
        onResumeUrlCreated?.invoke(resumeUrl)
        pushServiceSocket.uploadBackupFile(uploadForm, checksumSha256, data, dataLength, progressListener, null)
      } else {
        Log.i(TAG, "Fresh backup upload via legacy flow (CDN${uploadForm.cdn})")
        val resumeUrl = pushServiceSocket.getResumableUploadUrl(uploadForm, checksumSha256)
        onResumeUrlCreated?.invoke(resumeUrl)
        pushServiceSocket.uploadBackupFile(uploadForm, resumeUrl, data, dataLength, progressListener)
      }
    }
  }

  /**
   * Retrieves an [AttachmentUploadForm] that can be used to upload pre-existing media to the archive.
   *
   * This is basically the same as [org.signal.network.api.AttachmentApi.getAttachmentV4UploadForm], but with a relaxed rate limit
   * so we can request them more often (which is required for backfilling).
   *
   * After uploading, the media still needs to be copied via [copyMediaToArchive].
   *
   * - 401: Authorization failed
   * - 413: The media is too large
   * - 429: Rate-limited
   */
  fun getMediaUploadForm(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>, uploadLength: Long): NetworkResult<AttachmentUploadForm> {
    return runWithBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getMediaUploadForm(auth, uploadLength)
    }.toUploadFormNetworkResult().map { it.toAttachmentUploadForm() }
  }

  /**
   * Retrieves all media items in the user's archive. Note that this could be a very large number of items, making this only suitable for debugging.
   * Use [getArchiveMediaItemsPage] in production.
   */
  fun debugGetUploadedMediaItemMetadata(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>): NetworkResult<List<StoredMediaObject>> {
    return NetworkResult.fromFetch {
      val mediaObjects: MutableList<StoredMediaObject> = ArrayList()

      var cursor: String? = null
      do {
        val response: ArchiveGetMediaItemsResponse = getArchiveMediaItemsPage(aci, archiveServiceAccess, 10_000, cursor).successOrThrow()
        mediaObjects += response.storedMediaObjects
        cursor = response.cursor
      } while (cursor != null)

      mediaObjects
    }
  }

  /**
   * Retrieves a page of media items in the user's archive.
   *
   * GET /v1/archives/media?limit={limit}&cursor={cursor}
   *
   * - 200: Success
   * - 400: Bad request, or made on authenticated channel
   * - 403: Forbidden
   * - 429: Rate-limited
   *
   * @param limit The maximum number of items to return.
   * @param cursor A token that can be read from your previous response, telling the server where to start the next page.
   */
  fun getArchiveMediaItemsPage(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>, limit: Int, cursor: String?): NetworkResult<ArchiveGetMediaItemsResponse> {
    return getCredentialPresentationHeaders(aci, archiveServiceAccess)
      .then { headers ->
        val request = WebSocketRequestMessage.get("/v1/archives/media?limit=$limit${if (cursor.isNotNullOrBlank()) "&cursor=$cursor" else ""}", headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request, ArchiveGetMediaItemsResponse::class)
      }
  }

  /**
   * Copy and re-encrypt media from the attachments cdn into the backup cdn.
   *
   * The copy operation is not atomic: each item gets its own [CopyBackupMediaOutcome], and there is no need to retry items that produced one. If the stream
   * terminates early, the returned list only contains the outcomes received so far, so a partial success is reported as a failure carrying no outcomes.
   *
   * - 401: Authorization failed. Because large batches span multiple server requests, this can happen partway through.
   * - 429: Rate-limited
   */
  fun copyMediaToArchive(
    aci: ACI,
    archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>,
    items: List<CopyBackupMediaItem>
  ): NetworkResult<List<CopyBackupMediaOutcome>> {
    return collectBackupMediaStream(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).copyMedia(auth, items)
    }.toNetworkResult()
  }

  /**
   * Delete media from the backup cdn.
   *
   * Like [copyMediaToArchive], the operation is not atomic and a stream that terminates early reports failure rather than a partial result.
   *
   * - 401: Authorization failed
   * - 429: Rate-limited
   */
  fun deleteArchivedMedia(
    aci: ACI,
    archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>,
    mediaToDelete: List<DeleteBackupMediaItem>
  ): NetworkResult<List<DeleteBackupMediaItem>> {
    return collectBackupMediaStream(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).deleteMedia(auth, mediaToDelete)
    }.toNetworkResult()
  }

  /**
   * Retrieves auth credentials that can be used to perform SVR-B operations.
   *
   * - 401: Authorization failed
   */
  fun getSvrBAuthorization(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MessageBackupKey>): NetworkResult<AuthCredentials> {
    return runWithBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getSvrBCredentials(auth)
    }.toNetworkResult().map { (username, password) -> AuthCredentials.create(username, password) }
  }

  /**
   * Determine whether the backup-id can currently be rotated
   *
   * GET /v1/archives/backupid/limits
   * - 200: Successfully retrieved backup-id rotation limits
   * - 403: Invalid account authentication
   */
  fun getKeyRotationLimit(): NetworkResult<ArchiveKeyRotationLimitResponse> {
    val request = WebSocketRequestMessage.get("/v1/archives/backupid/limits")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, ArchiveKeyRotationLimitResponse::class)
  }

  /**
   * Issues an anonymous backup request over the unauthenticated chat connection, deriving the [BackupAuth] from [archiveServiceAccess]. Failing to derive it is
   * a local programming error, so it surfaces as [RequestResult.ApplicationError].
   */
  private fun <T, E : BadRequestError> runWithBackupAuth(
    aci: ACI,
    archiveServiceAccess: ArchiveServiceAccess<*>,
    block: (UnauthenticatedChatConnection, BackupAuth) -> CompletableFuture<RequestResult<T, E>>
  ): RequestResult<T, E> {
    val auth = try {
      getBackupAuth(aci, archiveServiceAccess)
    } catch (e: Throwable) {
      return RequestResult.ApplicationError(e)
    }

    return runBlocking {
      unauthWebSocket.runCatchingWithChatConnection { connection -> block(connection, auth) }
    }
  }

  /**
   * Drains a per-item backup media stream into a list. A stream that terminates early throws, which we classify the same way the non-streaming endpoints do.
   */
  private fun <T> collectBackupMediaStream(
    aci: ACI,
    archiveServiceAccess: ArchiveServiceAccess<*>,
    block: (UnauthenticatedChatConnection, BackupAuth) -> Flow<T>
  ): RequestResult<List<T>, RequestUnauthorizedException> {
    val auth = try {
      getBackupAuth(aci, archiveServiceAccess)
    } catch (e: Throwable) {
      return RequestResult.ApplicationError(e)
    }

    return runBlocking {
      try {
        val stream = unauthWebSocket.withChatConnection { connection -> block(connection, auth) }
        RequestResult.Success(stream.toList())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        e.toRequestResult<RequestUnauthorizedException>()
      }
    }
  }

  /**
   * Builds the anonymous-credential headers for the archive endpoints that still go out as hand-rolled websocket requests.
   */
  private fun getCredentialPresentationHeaders(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<Map<String, String>> {
    return NetworkResult.fromLocal {
      val privateKey: ECPrivateKey = archiveServiceAccess.backupKey.deriveAnonymousCredentialPrivateKey(aci)
      val presentation: ByteArray = getZkCredential(aci, archiveServiceAccess).present(backupServerPublicParams).serialize()

      ArchiveCredentialPresentation(
        presentation = presentation,
        signedPresentation = privateKey.calculateSignature(presentation)
      ).toHeaders()
    }
  }

  private fun getBackupAuth(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): BackupAuth {
    return BackupAuth(
      credential = getZkCredential(aci, archiveServiceAccess),
      serverKeys = backupServerPublicParams,
      signingKey = archiveServiceAccess.backupKey.deriveAnonymousCredentialPrivateKey(aci)
    )
  }

  private fun UploadForm.toAttachmentUploadForm(): AttachmentUploadForm {
    return AttachmentUploadForm(
      cdn = cdn,
      key = key,
      headers = headers,
      signedUploadLocation = signedUploadUrl.toString()
    )
  }

  /**
   * Converts a libsignal result into a [NetworkResult] for this class's callers, which still chain [NetworkResult].
   *
   * Note that libsignal reports both HTTP 401 and 403 as [RequestUnauthorizedException] ("incorrect or insufficient" authorization), so a caller that used to
   * distinguish "bad credential" from "insufficient permissions" only ever sees the 401.
   */
  private fun <T> RequestResult<T, RequestUnauthorizedException>.toNetworkResult(): NetworkResult<T> {
    return when (this) {
      is RequestResult.Success -> NetworkResult.Success(result)
      is RequestResult.NonSuccess -> NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(401, "Unauthorized"))
      is RequestResult.RetryableNetworkError -> toNetworkResult()
      is RequestResult.ApplicationError -> NetworkResult.ApplicationError(cause)
    }
  }

  /**
   * [toNetworkResult] for the upload-form endpoints, which can additionally reject the requested size.
   */
  private fun <T> RequestResult<T, GetUploadFormError>.toUploadFormNetworkResult(): NetworkResult<T> {
    return when (this) {
      is RequestResult.Success -> NetworkResult.Success(result)
      is RequestResult.NonSuccess -> when (error) {
        is UploadTooLargeException -> NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(413, "Upload too large"))
        is RequestUnauthorizedException -> NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(401, "Unauthorized"))
      }
      is RequestResult.RetryableNetworkError -> toNetworkResult()
      is RequestResult.ApplicationError -> NetworkResult.ApplicationError(cause)
    }
  }

  /**
   * A rate limit becomes a synthetic 429 carrying a `retry-after` header, since that's where [NetworkResult.StatusCodeError.retryAfter] looks for it.
   */
  private fun <T> RequestResult.RetryableNetworkError.toNetworkResult(): NetworkResult<T> {
    return when (val retryAfter = retryAfter) {
      null -> NetworkResult.NetworkError(networkError)
      else -> NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(429, "Rate limited", null as String?, mapOf("retry-after" to retryAfter.seconds.toString())))
    }
  }

  fun getZkCredential(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): BackupAuthCredential {
    val backupAuthResponse = BackupAuthCredentialResponse(archiveServiceAccess.credential.credential)
    val backupRequestContext = BackupAuthCredentialRequestContext.create(archiveServiceAccess.backupKey.value, aci.rawUuid)

    return backupRequestContext.receiveResponse(
      backupAuthResponse,
      Instant.ofEpochSecond(archiveServiceAccess.credential.redemptionTime),
      backupServerPublicParams
    )
  }
}
