/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

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
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.InputStream
import java.time.Instant

/**
 * Class to interact with various archive-related endpoints.
 * Why is it called archive instead of backup? Because SVR took the "backup" endpoint namespace first :)
 */
class ArchiveApi(private val pushServiceSocket: PushServiceSocket) {

  private val backupServerPublicParams: GenericServerPublicParams = GenericServerPublicParams(pushServiceSocket.configuration.backupServerPublicParams)

  companion object {
    @JvmStatic
    fun create(pushServiceSocket: PushServiceSocket): ArchiveApi {
      return ArchiveApi(pushServiceSocket)
    }
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
    return NetworkResult.fromFetch {
      pushServiceSocket.getArchiveCredentials(currentTime)
    }
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
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)

      pushServiceSocket.getArchiveCdnReadCredentials(cdnNumber, presentationData.toArchiveCredentialPresentation())
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
    return NetworkResult.fromFetch {
      val messageBackupRequestContext = BackupAuthCredentialRequestContext.create(messageBackupKey.value, aci.rawUuid)
      val mediaBackupRequestContext = BackupAuthCredentialRequestContext.create(mediaRootBackupKey.value, aci.rawUuid)
      pushServiceSocket.setArchiveBackupId(messageBackupRequestContext.request, mediaBackupRequestContext.request)
    }
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
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)
      pushServiceSocket.setArchivePublicKey(presentationData.publicKey, presentationData.toArchiveCredentialPresentation())
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
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)
      pushServiceSocket.getArchiveMessageBackupUploadForm(presentationData.toArchiveCredentialPresentation())
    }
  }

  /**
   * Fetches metadata about your current backup. This will be different for different key/credential pairs. For example, message credentials will always
   * return 0 for used space since that is stored under the media key/credential.
   *
   * Will return a [NetworkResult.StatusCodeError] with status code 404 if you haven't uploaded a backup yet.
   */
  fun getBackupInfo(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<*>): NetworkResult<ArchiveGetBackupInfoResponse> {
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)
      pushServiceSocket.getArchiveBackupInfo(presentationData.toArchiveCredentialPresentation())
    }
  }

  /**
   * Lists the media objects in the backup
   */
  fun listMediaObjects(aci: ACI, archiveServiceAccess: ArchiveServiceAccess<MediaRootBackupKey>, limit: Int, cursor: String? = null): NetworkResult<ArchiveGetMediaItemsResponse> {
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)
      pushServiceSocket.getArchiveMediaItemsPage(presentationData.toArchiveCredentialPresentation(), limit, cursor)
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
  fun uploadBackupFile(uploadForm: AttachmentUploadForm, resumableUploadUrl: String, data: InputStream, dataLength: Long): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      pushServiceSocket.uploadBackupFile(uploadForm, resumableUploadUrl, data, dataLength)
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
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)
      pushServiceSocket.getArchiveMediaUploadForm(presentationData.toArchiveCredentialPresentation())
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
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)

      pushServiceSocket.getArchiveMediaItemsPage(presentationData.toArchiveCredentialPresentation(), limit, cursor)
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
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)

      pushServiceSocket.archiveAttachmentMedia(presentationData.toArchiveCredentialPresentation(), item)
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
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)

      val request = BatchArchiveMediaRequest(items = items)

      pushServiceSocket.archiveAttachmentMedia(presentationData.toArchiveCredentialPresentation(), request)
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
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(aci, archiveServiceAccess)
      val presentationData = CredentialPresentationData.from(archiveServiceAccess.backupKey, aci, zkCredential, backupServerPublicParams)
      val request = DeleteArchivedMediaRequest(mediaToDelete = mediaToDelete)

      pushServiceSocket.deleteArchivedMedia(presentationData.toArchiveCredentialPresentation(), request)
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
