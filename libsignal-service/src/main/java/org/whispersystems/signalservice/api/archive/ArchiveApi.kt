/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.zkgroup.GenericServerPublicParams
import org.signal.libsignal.zkgroup.backups.BackupAuthCredential
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialRequestContext
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialResponse
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse.StoredMediaObject
import org.whispersystems.signalservice.api.backup.BackupKey
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.InputStream

/**
 * Class to interact with various archive-related endpoints.
 * Why is it called archive instead of backup? Because SVR took the "backup" endpoint namespace first :)
 */
class ArchiveApi(
  private val pushServiceSocket: PushServiceSocket,
  private val backupServerPublicParams: GenericServerPublicParams,
  private val aci: ACI
) {
  companion object {
    @JvmStatic
    fun create(pushServiceSocket: PushServiceSocket, backupServerPublicParams: ByteArray, aci: ACI): ArchiveApi {
      return ArchiveApi(
        pushServiceSocket,
        GenericServerPublicParams(backupServerPublicParams),
        aci
      )
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
   */
  fun getServiceCredentials(currentTime: Long): NetworkResult<ArchiveServiceCredentialsResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.getArchiveCredentials(currentTime)
    }
  }

  /**
   * Ensures that you reserve a backupId on the service. This must be done before any other
   * backup-related calls. You only need to do it once, but repeated calls are safe.
   */
  fun triggerBackupIdReservation(backupKey: BackupKey): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      val backupRequestContext = BackupAuthCredentialRequestContext.create(backupKey.value, aci.rawUuid)
      pushServiceSocket.setArchiveBackupId(backupRequestContext.request)
    }
  }

  /**
   * Sets a public key on the service derived from your [BackupKey]. This key is used to prevent
   * unauthorized  users from changing your backup data. You only need to do it once, but repeated
   * calls are safe.
   */
  fun setPublicKey(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(backupKey, serviceCredential)
      val presentationData = CredentialPresentationData.from(backupKey, zkCredential, backupServerPublicParams)
      pushServiceSocket.setArchivePublicKey(presentationData.publicKey, presentationData.toArchiveCredentialPresentation())
    }
  }

  /**
   * Fetches an upload form you can use to upload your main message backup file to cloud storage.
   */
  fun getMessageBackupUploadForm(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<ArchiveMessageBackupUploadFormResponse> {
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(backupKey, serviceCredential)
      val presentationData = CredentialPresentationData.from(backupKey, zkCredential, backupServerPublicParams)
      pushServiceSocket.getArchiveMessageBackupUploadForm(presentationData.toArchiveCredentialPresentation())
    }
  }

  /**
   * Fetches metadata about your current backup.
   * Will return a [NetworkResult.StatusCodeError] with status code 404 if you haven't uploaded a
   * backup yet.
   */
  fun getBackupInfo(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<ArchiveGetBackupInfoResponse> {
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(backupKey, serviceCredential)
      val presentationData = CredentialPresentationData.from(backupKey, zkCredential, backupServerPublicParams)
      pushServiceSocket.getArchiveBackupInfo(presentationData.toArchiveCredentialPresentation())
    }
  }

  /**
   * Retrieves a resumable upload URL you can use to upload your main message backup file to cloud storage.
   */
  fun getBackupResumableUploadUrl(archiveFormResponse: ArchiveMessageBackupUploadFormResponse): NetworkResult<String> {
    return NetworkResult.fromFetch {
      pushServiceSocket.getResumableUploadUrl(archiveFormResponse)
    }
  }

  /**
   * Uploads your main backup file to cloud storage.
   */
  fun uploadBackupFile(archiveFormResponse: ArchiveMessageBackupUploadFormResponse, resumableUploadUrl: String, data: InputStream, dataLength: Long): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      pushServiceSocket.uploadBackupFile(archiveFormResponse, resumableUploadUrl, data, dataLength)
    }
  }

  /**
   * Retrieves all media items in the user's archive. Note that this could be a very large number of items, making this only suitable for debugging.
   * Use [getArchiveMediaItemsPage] in production.
   */
  fun debugGetUploadedMediaItemMetadata(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): NetworkResult<List<ArchiveGetMediaItemsResponse.StoredMediaObject>> {
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(backupKey, serviceCredential)
      val presentationData = CredentialPresentationData.from(backupKey, zkCredential, backupServerPublicParams)
      val credentialPresentation = presentationData.toArchiveCredentialPresentation()

      val mediaObjects: MutableList<StoredMediaObject> = ArrayList()

      var cursor: String? = null
      do {
        val response: ArchiveGetMediaItemsResponse = pushServiceSocket.getArchiveMediaItemsPage(credentialPresentation, 512, cursor)
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
  fun getArchiveMediaItemsPage(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential, limit: Int, cursor: String): NetworkResult<ArchiveGetMediaItemsResponse> {
    return NetworkResult.fromFetch {
      val zkCredential = getZkCredential(backupKey, serviceCredential)
      val presentationData = CredentialPresentationData.from(backupKey, zkCredential, backupServerPublicParams)

      pushServiceSocket.getArchiveMediaItemsPage(presentationData.toArchiveCredentialPresentation(), 512, cursor)
    }
  }

  private fun getZkCredential(backupKey: BackupKey, serviceCredential: ArchiveServiceCredential): BackupAuthCredential {
    val backupAuthResponse = BackupAuthCredentialResponse(serviceCredential.credential)
    val backupRequestContext = BackupAuthCredentialRequestContext.create(backupKey.value, aci.rawUuid)

    return backupRequestContext.receiveResponse(
      backupAuthResponse,
      backupServerPublicParams,
      20
    )
  }

  private class CredentialPresentationData(
    val privateKey: ECPrivateKey,
    val presentation: ByteArray,
    val signedPresentation: ByteArray
  ) {
    val publicKey: ECPublicKey = privateKey.publicKey()

    companion object {
      fun from(backupKey: BackupKey, credential: BackupAuthCredential, backupServerPublicParams: GenericServerPublicParams): CredentialPresentationData {
        val privateKey: ECPrivateKey = Curve.decodePrivatePoint(backupKey.value)
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
