/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.Base64
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.serialization.ByteArrayToBase64Serializer
import org.signal.core.util.serialization.SignalJson
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
import org.signal.libsignal.net.ServerSideErrorException
import org.signal.libsignal.net.UnauthBackupsService
import org.signal.libsignal.net.UnauthenticatedChatConnection
import org.signal.libsignal.net.UploadForm
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.zkgroup.GenericServerPublicParams
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.backups.BackupAuthCredential
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialRequestContext
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialResponse
import org.signal.network.toRequestResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebsocketResponse
import org.signal.network.websocket.get
import org.signal.network.websocket.put
import org.whispersystems.signalservice.api.archive.ArchiveCredentialPresentation
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccess
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.AuthCredentials
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Collection of archive-related endpoints.
 */
class ArchiveApiV2(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket,
  private val backupServerPublicParams: GenericServerPublicParams
) {

  companion object {
    private const val KEY_MESSAGES = "messages"
    private const val KEY_MEDIA = "media"
  }

  /**
   * Retrieves a set of credentials one can use to authorize other requests.
   *
   * You'll receive a set of credentials spanning 7 days. Cache them and store them for later use. It's important that (at least in the common case) you do not
   * request credentials on-the-fly. Instead, request them in advance on a regular schedule. This is because the purpose of these credentials is to keep the
   * caller anonymous, but that doesn't help if this authenticated request happens right before all of the unauthenticated ones, as that would make it easier to
   * correlate traffic.
   *
   * GET /v1/archives/auth
   */
  suspend fun getServiceCredentials(currentTime: Long): RequestResult<ArchiveCredentials, GetServiceCredentialsError> {
    val roundedToNearestDay = currentTime.milliseconds.inWholeDays.days
    val endTime = roundedToNearestDay + 7.days

    val request = WebSocketRequestMessage.get("/v1/archives/auth?redemptionStartSeconds=${roundedToNearestDay.inWholeSeconds}&redemptionEndSeconds=${endTime.inWholeSeconds}")

    return authWebSocket.requestResult(
      request = request,
      parseSuccess = { response ->
        val body = SignalJson.decode(ArchiveCredentialsBody.serializer(), response.body).getOrNull()
          ?: throw IOException("Unparseable archive credentials response")

        ArchiveCredentials(
          messageCredentials = body.credentials[KEY_MESSAGES]?.map { it.toArchiveServiceCredential() } ?: throw IOException("Missing key '$KEY_MESSAGES'"),
          mediaCredentials = body.credentials[KEY_MEDIA]?.map { it.toArchiveServiceCredential() } ?: throw IOException("Missing key '$KEY_MEDIA'")
        )
      },
      mapError = { response ->
        when (response.status) {
          400 -> GetServiceCredentialsError.InvalidRedemptionTimes
          401 -> GetServiceCredentialsError.Unauthorized
          404 -> GetServiceCredentialsError.BackupIdNotFound
          429 -> GetServiceCredentialsError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Ensures that you reserve backupIds for both messages and media on the service. This must be done before any other backup-related calls. You only need to do
   * it once, but repeated calls are safe.
   *
   * Passing null for either key will skip reserving for that backup and not cost a rate limit permit.
   *
   * PUT /v1/archives/backupid
   */
  suspend fun triggerBackupIdReservation(messageBackupKey: MessageBackupKey?, mediaRootBackupKey: MediaRootBackupKey?, aci: ACI): RequestResult<Unit, SetBackupIdError> {
    val body = try {
      SetBackupIdBody(
        messagesBackupAuthCredentialRequest = messageBackupKey?.let { BackupAuthCredentialRequestContext.create(it.value, aci.rawUuid).request.serialize().toBase64() },
        mediaBackupAuthCredentialRequest = mediaRootBackupKey?.let { BackupAuthCredentialRequestContext.create(it.value, aci.rawUuid).request.serialize().toBase64() }
      ).encode()
    } catch (e: Throwable) {
      return RequestResult.ApplicationError(e)
    }

    return authWebSocket.requestResult(
      request = WebSocketRequestMessage.put("/v1/archives/backupid", body),
      parseSuccess = { },
      mapError = { response ->
        when (response.status) {
          400 -> SetBackupIdError.InvalidCredential
          401 -> SetBackupIdError.Unauthorized
          429 -> SetBackupIdError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Determine whether the backup-id can currently be rotated.
   *
   * GET /v1/archives/backupid/limits
   */
  suspend fun getKeyRotationLimit(): RequestResult<KeyRotationLimit, GetKeyRotationLimitError> {
    return authWebSocket.requestResult(
      request = WebSocketRequestMessage.get("/v1/archives/backupid/limits"),
      parseSuccess = { response ->
        SignalJson.decode(KeyRotationLimit.serializer(), response.body).getOrNull()
          ?: throw IOException("Unparseable key rotation limit response")
      },
      mapError = { response ->
        when (response.status) {
          403 -> GetKeyRotationLimitError.Forbidden
          else -> null
        }
      }
    )
  }

  /**
   * Retrieves a page of media items in the user's archive.
   *
   * GET /v1/archives/media?limit={limit}&cursor={cursor}
   *
   * @param limit The maximum number of items to return.
   * @param cursor A token that can be read from your previous response, telling the server where to start the next page.
   */
  suspend fun getArchiveMediaItemsPage(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>, limit: Int, cursor: String?): RequestResult<MediaItemsPage, GetMediaItemsError> {
    val headers = when (val result = getCredentialPresentationHeaders(aci, archiveServiceAccess)) {
      is ZkCredentialResult.Success -> result.value
      is ZkCredentialResult.Failure -> return RequestResult.ApplicationError(result.exception)
    }

    val request = WebSocketRequestMessage.get("/v1/archives/media?limit=$limit${if (cursor.isNotNullOrBlank()) "&cursor=$cursor" else ""}", headers)

    return unauthWebSocket.requestResult(
      request = request,
      parseSuccess = { response ->
        SignalJson.decode(MediaItemsPage.serializer(), response.body).getOrNull()
          ?: throw IOException("Unparseable media items response")
      },
      mapError = { response ->
        when (response.status) {
          400 -> GetMediaItemsError.InvalidRequest
          401 -> GetMediaItemsError.Unauthorized
          403 -> GetMediaItemsError.Forbidden
          429 -> GetMediaItemsError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Gets credentials needed to read from the CDN. Make sure you use the right [archiveServiceAccess] depending on whether you're doing a message or media
   * operation.
   */
  suspend fun getCdnReadCredentials(cdnNumber: Int, aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): RequestResult<GetArchiveCdnCredentialsResponse, RequestUnauthorizedException> {
    return withBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getCdnCredentials(auth, cdnNumber)
    }.map { GetArchiveCdnCredentialsResponse(it.headers) }
  }

  /**
   * Sets a public key on the service derived from your [MessageBackupKey]. This key is used to prevent unauthorized users from changing your backup data. You
   * only need to do it once, but repeated calls are safe.
   */
  suspend fun setPublicKey(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): RequestResult<Unit, RequestUnauthorizedException> {
    return withBackupAuth(aci, archiveServiceAccess) { connection, auth -> UnauthBackupsService(connection).setPublicKey(auth) }
  }

  /**
   * Fetches an upload form you can use to upload your main message backup file to cloud storage.
   */
  suspend fun getMessageBackupUploadForm(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MessageBackupKey>, backupFileSize: Long): RequestResult<AttachmentUploadForm, GetUploadFormError> {
    return withBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getUploadForm(auth, backupFileSize)
    }.map { it.toAttachmentUploadForm() }
  }

  /**
   * Retrieves an upload form that can be used to upload pre-existing media to the archive.
   *
   * This is basically the same as [AttachmentApi.getAttachmentV4UploadForm], but with a relaxed rate limit so we can request them more often (which is required
   * for backfilling). After uploading, the media still needs to be copied via [copyMediaToArchive].
   */
  suspend fun getMediaUploadForm(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>, uploadLength: Long): RequestResult<AttachmentUploadForm, GetUploadFormError> {
    return withBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getMediaUploadForm(auth, uploadLength)
    }.map { it.toAttachmentUploadForm() }
  }

  /**
   * Fetches metadata about the currently-stored message backup.
   *
   * Note that the server does not distinguish an invalid credential from a backup-id that was never provisioned, so callers using this to check whether a backup
   * exists should treat [RequestUnauthorizedException] as "backups not set up" rather than a fatal error.
   */
  suspend fun getMessageBackupInfo(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MessageBackupKey>): RequestResult<MessageBackupInfo, RequestUnauthorizedException> {
    return withBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getMessageBackupInfo(auth)
    }
  }

  /**
   * Fetches metadata about the currently-stored media backup, including how much space it uses. Carries the same 401 caveat as [getMessageBackupInfo].
   */
  suspend fun getMediaBackupInfo(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>): RequestResult<MediaBackupInfo, RequestUnauthorizedException> {
    return withBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getMediaBackupInfo(auth)
    }
  }

  /**
   * Indicate that this backup is still active. Clients must periodically upload new backups or perform a refresh. If a backup is not refreshed, after 30 days
   * it may be deleted.
   */
  suspend fun refreshBackup(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): RequestResult<Unit, RequestUnauthorizedException> {
    return withBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).refresh(auth)
    }
  }

  /**
   * Delete all backup metadata, objects, and stored public key. To use backups again, a public key must be resupplied.
   */
  suspend fun deleteBackup(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): RequestResult<Unit, RequestUnauthorizedException> {
    return withBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).deleteAll(auth)
    }
  }

  /**
   * Retrieves auth credentials that can be used to perform SVR-B operations.
   */
  suspend fun getSvrBAuthorization(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MessageBackupKey>): RequestResult<AuthCredentials, RequestUnauthorizedException> {
    return withBackupAuth(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).getSvrBCredentials(auth)
    }.map { (username, password) -> AuthCredentials.create(username, password) }
  }

  /**
   * Copy and re-encrypt media from the attachments cdn into the backup cdn.
   *
   * The copy operation is not atomic: each item gets its own [CopyBackupMediaOutcome], and there is no need to retry items that produced one. If the stream
   * terminates early, the returned list only contains the outcomes received so far, so a partial success is reported as a failure carrying no outcomes.
   */
  suspend fun copyMediaToArchive(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>, items: List<CopyBackupMediaItem>): RequestResult<List<CopyBackupMediaOutcome>, RequestUnauthorizedException> {
    return collectBackupMediaStream(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).copyMedia(auth, items)
    }
  }

  /**
   * Delete media from the backup cdn. Like [copyMediaToArchive], the operation is not atomic and a stream that terminates early reports failure rather than a
   * partial result.
   */
  suspend fun deleteArchivedMedia(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>, mediaToDelete: List<DeleteBackupMediaItem>): RequestResult<List<DeleteBackupMediaItem>, RequestUnauthorizedException> {
    return collectBackupMediaStream(aci, archiveServiceAccess) { connection, auth ->
      UnauthBackupsService(connection).deleteMedia(auth, mediaToDelete)
    }
  }

  /**
   * Derives the zkgroup credential backing [archiveServiceAccess]. Purely local, so it can only fail over the inputs it was handed.
   */
  fun getZkCredential(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): ZkCredentialResult<BackupAuthCredential> {
    val backupAuthResponse = try {
      BackupAuthCredentialResponse(archiveServiceAccess.credential.credential)
    } catch (e: InvalidInputException) {
      return ZkCredentialResult.Failure.MalformedCredential(e)
    }

    val backupRequestContext = BackupAuthCredentialRequestContext.create(archiveServiceAccess.backupKey.value, aci.rawUuid)

    return try {
      ZkCredentialResult.Success(
        backupRequestContext.receiveResponse(
          backupAuthResponse,
          Instant.ofEpochSecond(archiveServiceAccess.credential.redemptionTime),
          backupServerPublicParams
        )
      )
    } catch (e: VerificationFailedException) {
      ZkCredentialResult.Failure.VerificationFailed(e)
    }
  }

  /**
   * Issues a hand-rolled REST-over-websocket request, classifying the outcome the same way libsignal classifies its own.
   *
   * A 5xx is reported as [RequestResult.RetryableNetworkError] rather than being offered to [mapError], because a server-side failure is transient no matter
   * which endpoint produced it, and callers uniformly want to back off rather than treat it as a decision point.
   *
   * A null from [mapError] on any other code means the status isn't one this endpoint documents. That's also a [RequestResult.RetryableNetworkError], not an
   * [RequestResult.ApplicationError]: the server can start returning a code we don't model yet without us shipping, so callers should back off rather than treat
   * it as a local bug. Several callers escalate an application error to a crash, which is the wrong response to an unexpected status code.
   *
   * Both of those carry a [ServerSideErrorException], which is what libsignal uses for a server-side failure on the endpoints it owns. That lets a caller that
   * wants a longer backoff for "the server is unhappy" than for "we couldn't reach the server" tell them apart, and get the same answer either way.
   */
  private suspend fun <T, E : BadRequestError> SignalWebSocket.requestResult(
    request: WebSocketRequestMessage,
    parseSuccess: (WebsocketResponse) -> T,
    mapError: (WebsocketResponse) -> E?
  ): RequestResult<T, E> {
    return try {
      val response = requestSuspend(request)

      when {
        response.status in 200..299 -> RequestResult.Success(parseSuccess(response))
        response.status in 500..599 -> RequestResult.RetryableNetworkError(ServerSideErrorException("Server error: ${response.status}"))
        else -> when (val error = mapError(response)) {
          null -> RequestResult.RetryableNetworkError(ServerSideErrorException("Unexpected response code: ${response.status}"))
          else -> RequestResult.NonSuccess(error)
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Throwable) {
      RequestResult.ApplicationError(e)
    }
  }

  /**
   * Issues an anonymous backup request over the unauthenticated chat connection, deriving the [BackupAuth] from [archiveServiceAccess]. Failing to derive it is
   * a local programming error, so it surfaces as [RequestResult.ApplicationError].
   */
  private suspend fun <T, E : BadRequestError> withBackupAuth(
    aci: ACI,
    archiveServiceAccess: ArchiveServiceAccess<*>,
    block: (UnauthenticatedChatConnection, BackupAuth) -> org.signal.libsignal.internal.CompletableFuture<RequestResult<T, E>>
  ): RequestResult<T, E> {
    val auth = when (val result = getBackupAuth(aci, archiveServiceAccess)) {
      is ZkCredentialResult.Success -> result.value
      is ZkCredentialResult.Failure -> return RequestResult.ApplicationError(result.exception)
    }

    return unauthWebSocket.runCatchingWithChatConnection { connection -> block(connection, auth) }
  }

  /**
   * Drains a per-item backup media stream into a list. A stream that terminates early throws, which we classify the same way the non-streaming endpoints do.
   */
  private suspend fun <T> collectBackupMediaStream(
    aci: ACI,
    archiveServiceAccess: ArchiveServiceAccess<*>,
    block: (UnauthenticatedChatConnection, BackupAuth) -> Flow<T>
  ): RequestResult<List<T>, RequestUnauthorizedException> {
    val auth = when (val result = getBackupAuth(aci, archiveServiceAccess)) {
      is ZkCredentialResult.Success -> result.value
      is ZkCredentialResult.Failure -> return RequestResult.ApplicationError(result.exception)
    }

    return try {
      val stream = unauthWebSocket.withChatConnection { connection -> block(connection, auth) }
      RequestResult.Success(stream.toList())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      e.toRequestResult<RequestUnauthorizedException>()
    }
  }

  /**
   * Builds the anonymous-credential headers for the archive endpoints that still go out as hand-rolled websocket requests.
   */
  private fun getCredentialPresentationHeaders(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): ZkCredentialResult<Map<String, String>> {
    val credential = when (val result = getZkCredential(aci, archiveServiceAccess)) {
      is ZkCredentialResult.Success -> result.value
      is ZkCredentialResult.Failure -> return result
    }

    val privateKey: ECPrivateKey = archiveServiceAccess.backupKey.deriveAnonymousCredentialPrivateKey(aci)
    val presentation: ByteArray = credential.present(backupServerPublicParams).serialize()

    return ZkCredentialResult.Success(
      ArchiveCredentialPresentation(
        presentation = presentation,
        signedPresentation = privateKey.calculateSignature(presentation)
      ).toHeaders()
    )
  }

  private fun getBackupAuth(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): ZkCredentialResult<BackupAuth> {
    val credential = when (val result = getZkCredential(aci, archiveServiceAccess)) {
      is ZkCredentialResult.Success -> result.value
      is ZkCredentialResult.Failure -> return result
    }

    return ZkCredentialResult.Success(
      BackupAuth(
        credential = credential,
        serverKeys = backupServerPublicParams,
        signingKey = archiveServiceAccess.backupKey.deriveAnonymousCredentialPrivateKey(aci)
      )
    )
  }

  private fun <T, E : BadRequestError, R> RequestResult<T, E>.map(transform: (T) -> R): RequestResult<R, E> {
    return when (this) {
      is RequestResult.Success -> RequestResult.Success(transform(result))
      is RequestResult.NonSuccess -> this
      is RequestResult.RetryableNetworkError -> this
      is RequestResult.ApplicationError -> this
    }
  }

  private fun UploadForm.toAttachmentUploadForm(): AttachmentUploadForm {
    return AttachmentUploadForm(
      cdn = cdn,
      key = key,
      headers = headers,
      signedUploadLocation = signedUploadUrl.toString()
    )
  }

  private fun ByteArray.toBase64(): String = Base64.encodeWithPadding(this)

  /** The credentials for both archives, spanning the next several days. */
  data class ArchiveCredentials(
    val messageCredentials: List<ArchiveServiceCredential>,
    val mediaCredentials: List<ArchiveServiceCredential>
  )

  /**
   * A page of the media stored in the user's archive.
   *
   * The server also returns `backupDir`/`mediaDir` here, but [getMediaBackupInfo] is the authoritative source for those, so they're omitted.
   */
  @Serializable
  data class MediaItemsPage(
    val storedMediaObjects: List<StoredMediaObject> = emptyList(),
    val cursor: String? = null
  )

  @Serializable
  data class StoredMediaObject(
    val cdn: Int,
    val mediaId: String,
    val objectLength: Long
  )

  @Serializable
  data class KeyRotationLimit(
    val hasPermitsRemaining: Boolean? = null,
    val retryAfterSeconds: Long? = null
  )

  @Serializable
  private class ArchiveCredentialsBody(
    val credentials: Map<String, List<WireCredential>> = emptyMap()
  )

  @Serializable
  private class WireCredential(
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val credential: ByteArray,
    val redemptionTime: Long
  ) {
    fun toArchiveServiceCredential(): ArchiveServiceCredential = ArchiveServiceCredential(credential, redemptionTime)
  }

  @Serializable
  private class SetBackupIdBody(
    val messagesBackupAuthCredentialRequest: String?,
    val mediaBackupAuthCredentialRequest: String?
  ) {
    fun encode(): String = SignalJson.encode(serializer(), this).getOrNull() ?: throw IllegalStateException("Unable to encode backupId request")
  }

  sealed interface GetServiceCredentialsError : BadRequestError {
    /** The requested redemption window was rejected. */
    data object InvalidRedemptionTimes : GetServiceCredentialsError

    /** The account credentials this request was made with were rejected. Unlike the anonymous endpoints, this is about the account, not the archive credential. */
    data object Unauthorized : GetServiceCredentialsError

    /** No backupId has been reserved for this account yet. See [triggerBackupIdReservation]. */
    data object BackupIdNotFound : GetServiceCredentialsError

    data class RateLimited(val retryAfter: Duration?) : GetServiceCredentialsError
  }

  sealed interface SetBackupIdError : BadRequestError {
    /** The zkgroup credential request was rejected. */
    data object InvalidCredential : SetBackupIdError

    /** See [GetServiceCredentialsError.Unauthorized]. */
    data object Unauthorized : SetBackupIdError

    data class RateLimited(val retryAfter: Duration?) : SetBackupIdError
  }

  sealed interface GetMediaItemsError : BadRequestError {
    /** Malformed request, or made on the authenticated channel when it must be anonymous. */
    data object InvalidRequest : GetMediaItemsError

    /** The anonymous credential presentation was rejected. Unlike the libsignal-backed endpoints, this endpoint reports 401 and 403 separately. */
    data object Unauthorized : GetMediaItemsError

    data object Forbidden : GetMediaItemsError

    data class RateLimited(val retryAfter: Duration?) : GetMediaItemsError
  }

  sealed interface GetKeyRotationLimitError : BadRequestError {
    data object Forbidden : GetKeyRotationLimitError
  }

  /**
   * The outcome of deriving a zkgroup credential. Not a [RequestResult] because nothing goes over the network -- a derivation can only fail over the inputs it
   * was given, and either failure means a retry with the same inputs fails the same way.
   */
  sealed interface ZkCredentialResult<out T> {
    data class Success<T>(val value: T) : ZkCredentialResult<T>

    sealed interface Failure : ZkCredentialResult<Nothing> {
      val exception: Exception

      /** The credential didn't verify, which most often means the backup key doesn't belong to the aci it was presented with. */
      data class VerificationFailed(override val exception: VerificationFailedException) : Failure

      /** The stored credential couldn't be parsed at all, so there's nothing to verify. */
      data class MalformedCredential(override val exception: InvalidInputException) : Failure
    }
  }
}
