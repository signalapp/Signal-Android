/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.attachment

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.PushAttachmentData
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import java.io.InputStream
import kotlin.jvm.optionals.getOrNull

/**
 * Class to interact with various attachment-related endpoints.
 */
class AttachmentApi(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val pushServiceSocket: PushServiceSocket
) {
  /**
   * Gets a v4 attachment upload form, which provides the necessary information to upload an attachment.
   *
   * GET /v4/attachments/form/upload
   * - 200: Success
   * - 413: Too many attempts
   * - 429: Too many attempts
   */
  fun getAttachmentV4UploadForm(): NetworkResult<AttachmentUploadForm> {
    val request = WebSocketRequestMessage.get("/v4/attachments/form/upload")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, AttachmentUploadForm::class)
  }

  /**
   * Gets a resumable upload spec, which can be saved and re-used across upload attempts to resume upload progress.
   */
  fun getResumableUploadSpec(key: ByteArray, iv: ByteArray, uploadForm: AttachmentUploadForm): NetworkResult<ResumableUploadSpec> {
    return getResumableUploadUrl(uploadForm)
      .map { url ->
        ResumableUploadSpec(
          attachmentKey = key,
          attachmentIv = iv,
          cdnKey = uploadForm.key,
          cdnNumber = uploadForm.cdn,
          resumeLocation = url,
          expirationTimestamp = System.currentTimeMillis() + PushServiceSocket.CDN2_RESUMABLE_LINK_LIFETIME_MILLIS,
          headers = uploadForm.headers
        )
      }
  }

  /**
   * Uploads an attachment using the v4 upload scheme.
   */
  fun uploadAttachmentV4(attachmentStream: SignalServiceAttachmentStream): NetworkResult<AttachmentUploadResult> {
    if (attachmentStream.resumableUploadSpec.isEmpty) {
      throw IllegalStateException("Attachment must have a resumable upload spec!")
    }

    return NetworkResult.fromFetch {
      val resumableUploadSpec = attachmentStream.resumableUploadSpec.get()

      val paddedLength = PaddingInputStream.getPaddedSize(attachmentStream.length)
      val dataStream: InputStream = PaddingInputStream(attachmentStream.inputStream, attachmentStream.length)
      val ciphertextLength = AttachmentCipherStreamUtil.getCiphertextLength(paddedLength)

      val attachmentData = PushAttachmentData(
        contentType = attachmentStream.contentType,
        data = dataStream,
        dataSize = ciphertextLength,
        incremental = attachmentStream.isFaststart,
        outputStreamFactory = AttachmentCipherOutputStreamFactory(resumableUploadSpec.attachmentKey, resumableUploadSpec.attachmentIv),
        listener = attachmentStream.listener,
        cancelationSignal = attachmentStream.cancelationSignal,
        resumableUploadSpec = attachmentStream.resumableUploadSpec.get()
      )

      val digestInfo = pushServiceSocket.uploadAttachment(attachmentData)

      AttachmentUploadResult(
        remoteId = SignalServiceAttachmentRemoteId.V4(attachmentData.resumableUploadSpec.cdnKey),
        cdnNumber = attachmentData.resumableUploadSpec.cdnNumber,
        key = resumableUploadSpec.attachmentKey,
        iv = resumableUploadSpec.attachmentIv,
        digest = digestInfo.digest,
        incrementalDigest = digestInfo.incrementalDigest,
        incrementalDigestChunkSize = digestInfo.incrementalMacChunkSize,
        uploadTimestamp = attachmentStream.uploadTimestamp,
        dataSize = attachmentStream.length,
        blurHash = attachmentStream.blurHash.getOrNull()
      )
    }
  }

  /**
   * Uploads a raw file using the v4 upload scheme. No additional encryption is supplied! Always prefer [uploadAttachmentV4], unless you are using a separate
   * encryption scheme (i.e. like backup files).
   */
  fun uploadPreEncryptedFileToAttachmentV4(uploadForm: AttachmentUploadForm, resumableUploadUrl: String, inputStream: InputStream, inputStreamLength: Long): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      pushServiceSocket.uploadBackupFile(uploadForm, resumableUploadUrl, inputStream, inputStreamLength)
    }
  }

  fun getResumableUploadUrl(uploadForm: AttachmentUploadForm): NetworkResult<String> {
    return NetworkResult.fromFetch {
      pushServiceSocket.getResumableUploadUrl(uploadForm)
    }
  }
}
