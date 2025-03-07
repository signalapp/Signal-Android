/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import org.signal.core.util.isNotNullOrBlank
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.zkgroup.GenericServerPublicParams
import org.signal.libsignal.zkgroup.backups.BackupAuthCredential
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialRequestContext
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialResponse
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse.StoredMediaObject
import org.whispersystems.signalservice.api.backup.BackupKey
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.delete
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.post
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.put
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
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
  private val pushServiceSocket: PushServiceSocket
) {

  private val backupServerPublicParams: GenericServerPublicParams = GenericServerPublicParams(pushServiceSocket.configuration.backupServerPublicParams)

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
   * Gets credentials needed to read from the CDN. Make sure you use the right [backupKey] depending on whether you're doing a message or media operation.
   *
   * GET /v1/archives/auth/read
   *
   * - 200: Success
   * - 400: Bad arguments, or made on an authenticated channel
   * - 401: Bad presentation, invalid public key signature, no matching backupId on teh server, or the credential was of the wrong type (messages/media)
   * - 403: Forbidden
   * - 429: Rate-limited
   */
  fun getCdnReadCredentials(cdnNumber: Int, aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<GetArchiveCdnCredentialsResponse> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.get("/v1/archives/auth/read?cdn=$cdnNumber", headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request, GetArchiveCdnCredentialsResponse::class)
      }
  }

  /**
   * Ensures that you reserve backupIds for both messages and media on the service. This must be done before any other
   * backup-related calls. You only need to do it once, but repeated calls are safe.
   *
   * PUT /v1/archives/backupid
   *
   * - 204: Success
   * - 400: Invalid credential
   * - 429: Rate-limited
   */
  fun triggerBackupIdReservation(messageBackupKey: MessageBackupKey, mediaRootBackupKey: MediaRootBackupKey, aci: ACI): NetworkResult<Unit> {
    val messageBackupRequestContext = BackupAuthCredentialRequestContext.create(messageBackupKey.value, aci.rawUuid)
    val mediaBackupRequestContext = BackupAuthCredentialRequestContext.create(mediaRootBackupKey.value, aci.rawUuid)

    val request = WebSocketRequestMessage.put(
      "/v1/archives/backupid",
      ArchiveSetBackupIdRequest(messageBackupRequestContext.request, mediaBackupRequestContext.request)
    )

    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Sets a public key on the service derived from your [MessageBackupKey]. This key is used to prevent
   * unauthorized  users from changing your backup data. You only need to do it once, but repeated
   * calls are safe.
   *
   * PUT /v1/archives/keys
   *
   * - 204: Success
   * - 400: Bad arguments, or request was made on an authenticated channel
   * - 401: Bad presentation, invalid public key signature, no matching backupId on teh server, or the credential was of the wrong type (messages/media)
   * - 403: Forbidden
   * - 429: Rate-limited
   */
  fun setPublicKey(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<Unit> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .then { presentation ->
        val headers = presentation.toArchiveCredentialPresentation().toHeaders()
        val publicKey = presentation.publicKey

        val request = WebSocketRequestMessage.put("/v1/archives/keys", ArchiveSetPublicKeyRequest(publicKey), headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
      }
  }

  /**
   * Fetches an upload form you can use to upload your main message backup file to cloud storage.
   *
   * GET /v1/archives/upload/form
   * - 200: Success
   * - 400: Bad args, or made on an authenticated channel
   * - 403: Insufficient permissions
   * - 429: Rate-limited
   */
  fun getMessageBackupUploadForm(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MessageBackupKey>): NetworkResult<AttachmentUploadForm> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.get("/v1/archives/upload/form", headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request, AttachmentUploadForm::class)
      }
  }

  /**
   * Fetches metadata about your current backup. This will be different for different key/credential pairs. For example, message credentials will always
   * return 0 for used space since that is stored under the media key/credential.
   *
   * Will return a [NetworkResult.StatusCodeError] with status code 404 if you haven't uploaded a backup yet.
   */
  fun getBackupInfo(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<ArchiveGetBackupInfoResponse> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.get("/v1/archives", headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request, ArchiveGetBackupInfoResponse::class)
      }
  }

  /**
   * Indicate that this backup is still active. Clients must periodically upload new backups or perform a refresh via a POST request. If a backup is not
   * refreshed, after 30 days it may be deleted.
   *
   * POST /v1/archives
   *
   * - 204: The backup was successfully refreshed.
   * - 400: Bad arguments. The request may have been made on an authenticated channel.
   * - 401: The provided backup auth credential presentation could not be verified or The public key signature was invalid or There is no backup associated with
   *        the backup-id in the presentation or The credential was of the wrong type (messages/media)
   * - 403: Forbidden. The request had insufficient permissions to perform the requested action.
   * - 429: Rate limited.
   */
  fun refreshBackup(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MessageBackupKey>): NetworkResult<Unit> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.post(path = "/v1/archives", body = null, headers = headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
      }
  }

  /**
   * Delete all backup metadata, objects, and stored public key. To use backups again, a public key must be resupplied.
   *
   * DELETE /v1/archives
   *
   * - 204: The backup has been successfully deleted
   * - 400: Bad arguments. The request may have been made on an authenticated channel.
   * - 401: The provided backup auth credential presentation could not be verified or The public key signature was invalid or There is no backup associated with
   *        the backup-id in the presentation or The credential was of the wrong type (messages/media)
   * - 403: Forbidden. The request had insufficient permissions to perform the requested action.
   * - 429: Rate limited.
   *
   */
  fun deleteBackup(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MessageBackupKey>): NetworkResult<Unit> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.delete("/v1/archives", headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
      }
  }

  /**
   * Retrieves a resumable upload URL you can use to upload your main message backup file or an arbitrary media file to cloud storage.
   */
  fun getBackupResumableUploadUrl(uploadForm: AttachmentUploadForm): NetworkResult<String> {
    return NetworkResult.fromFetch {
      pushServiceSocket.getResumableUploadUrl(uploadForm)
    }
  }

  /**
   * Uploads your main backup file to cloud storage.
   */
  fun uploadBackupFile(uploadForm: AttachmentUploadForm, resumableUploadUrl: String, data: InputStream, dataLength: Long, progressListener: SignalServiceAttachment.ProgressListener? = null): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      pushServiceSocket.uploadBackupFile(uploadForm, resumableUploadUrl, data, dataLength, progressListener)
    }
  }

  /**
   * Retrieves an [AttachmentUploadForm] that can be used to upload pre-existing media to the archive.
   *
   * This is basically the same as [org.whispersystems.signalservice.api.attachment.AttachmentApi.getAttachmentV4UploadForm], but with a relaxed rate limit
   * so we can request them more often (which is required for backfilling).
   *
   * After uploading, the media still needs to be copied via [copyAttachmentToArchive].
   *
   * GET /v1/archives/media/upload/form
   *
   * - 200: Success
   * - 400: Bad request, or made on authenticated channel
   * - 403: Forbidden
   * - 429: Rate-limited
   */
  fun getMediaUploadForm(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>): NetworkResult<AttachmentUploadForm> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.get("/v1/archives/media/upload/form", headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request, AttachmentUploadForm::class)
      }
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
        val response: ArchiveGetMediaItemsResponse = getArchiveMediaItemsPage(aci, archiveServiceAccess, 512, cursor).successOrThrow()
        mediaObjects += response.storedMediaObjects
        cursor = response.cursor
      } while (cursor != null)

      mediaObjects
    }
  }

  /**
   * Retrieves a page of media items in the user's archive.
   * @param limit The maximum number of items to return.
   * @param cursor A token that can be read from your previous response, telling the server where to start the next page.
   */
  fun getArchiveMediaItemsPage(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>, limit: Int, cursor: String?): NetworkResult<ArchiveGetMediaItemsResponse> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.get("/v1/archives/media?limit=$limit${if (cursor.isNotNullOrBlank()) "&cursor=$cursor" else ""}", headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request, ArchiveGetMediaItemsResponse::class)
      }
  }

  /**
   * Copy and re-encrypt media from the attachments cdn into the backup cdn.
   *
   * Possible errors:
   *   400: Bad arguments, or made on an authenticated channel
   *   401: Invalid presentation or signature
   *   403: Insufficient permissions
   *   410: The source object was not found
   *   413: No media space remaining
   *   429: Rate-limited
   */
  fun copyAttachmentToArchive(
    aci: ACI,
    archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>,
    item: ArchiveMediaRequest
  ): NetworkResult<ArchiveMediaResponse> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.put("/v1/archives/media", item, headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request, ArchiveMediaResponse::class)
      }
  }

  /**
   * Copy and re-encrypt media from the attachments cdn into the backup cdn.
   */
  fun copyAttachmentToArchive(
    aci: ACI,
    archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>,
    items: List<ArchiveMediaRequest>
  ): NetworkResult<BatchArchiveMediaResponse> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.put("/v1/archives/media/batch", BatchArchiveMediaRequest(items = items), headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request, BatchArchiveMediaResponse::class)
      }
  }

  /**
   * Delete media from the backup cdn.
   *
   * POST /v1/archives/media/delete
   *
   * - 400: Bad args or made on an authenticated channel
   * - 401: Bad presentation, invalid public key signature, no matching backupId on teh server, or the credential was of the wrong type (messages/media)
   * - 403: Forbidden
   * - 429: Rate-limited
   */
  fun deleteArchivedMedia(
    aci: ACI,
    archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>,
    mediaToDelete: List<DeleteArchivedMediaRequest.ArchivedMediaObject>
  ): NetworkResult<Unit> {
    return getCredentialPresentation(aci, archiveServiceAccess)
      .map { it.toArchiveCredentialPresentation().toHeaders() }
      .then { headers ->
        val request = WebSocketRequestMessage.post("/v1/archives/media/delete", DeleteArchivedMediaRequest(mediaToDelete = mediaToDelete), headers)
        NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
      }
  }

  private fun getCredentialPresentation(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<CredentialPresentationData> {
    return NetworkResult.fromLocal {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)
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

  private class CredentialPresentationData(
    val privateKey: ECPrivateKey,
    val presentation: ByteArray,
    val signedPresentation: ByteArray
  ) {
    val publicKey: ECPublicKey = privateKey.publicKey()

    companion object {
      fun from(backupKey: BackupKey, aci: ACI, credential: BackupAuthCredential, backupServerPublicParams: GenericServerPublicParams): CredentialPresentationData {
        val privateKey: ECPrivateKey = backupKey.deriveAnonymousCredentialPrivateKey(aci)
        val presentation: ByteArray = credential.present(backupServerPublicParams).serialize()
        val signedPresentation: ByteArray = privateKey.calculateSignature(presentation)

        return CredentialPresentationData(privateKey, presentation, signedPresentation)
      }
    }

    fun toArchiveCredentialPresentation(): ArchiveCredentialPresentation {
      return ArchiveCredentialPresentation(
        presentation = presentation,
        signedPresentation = signedPresentation
      )
    }
  }
}
