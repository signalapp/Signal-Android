/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.attachment

import kotlinx.coroutines.runBlocking
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.AuthMessagesService
import org.signal.libsignal.net.AuthenticatedChatConnection
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.UploadTooLargeException
import org.signal.libsignal.net.getOrError
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.PushAttachmentData
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import java.io.IOException
import java.io.InputStream
import kotlin.jvm.optionals.getOrNull

/**
 * Class to interact with various attachment-related endpoints.
 */
class AttachmentApi(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val pushServiceSocket: PushServiceSocket
) {

  companion object {
    private val TAG: String = Log.tag(AttachmentApi::class)
  }

  /**
   * Gets a v4 attachment upload form, which provides the necessary information to upload an attachment.
   */
  fun getAttachmentV4UploadForm(uploadSizeBytes: Long): RequestResult<AttachmentUploadForm, UploadTooLargeException> {
    return try {
      runBlocking {
        authWebSocket.runWithChatConnection { chatConnection ->
          AuthMessagesService(chatConnection as AuthenticatedChatConnection).getUploadForm(uploadSizeBytes)
        }
      }.getOrError().map { form ->
        AttachmentUploadForm(
          cdn = form.cdn,
          key = form.key,
          headers = form.headers,
          signedUploadLocation = form.signedUploadUrl.toString()
        )
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Throwable) {
      RequestResult.ApplicationError(e)
    }
  }

  /**
   * Uploads an encrypted attachment, automatically choosing the best upload strategy based on CDN version.
   * For CDN3, uses TUS "Creation With Upload" (single POST). For other CDNs, falls back to the legacy
   * resumable upload flow (POST create + HEAD + PATCH).
   *
   * If [existingSpec] is provided, the upload resumes using the existing resumable upload URL (HEAD+PATCH)
   * and [form] is not required.
   * Otherwise, [form] is required, a new upload is initiated, and [onSpecCreated] is called with the
   * [ResumableUploadSpec] before the upload begins, allowing callers to persist it for crash recovery.
   */
  fun uploadAttachmentV4(
    form: AttachmentUploadForm? = null,
    key: ByteArray,
    iv: ByteArray,
    checksumSha256: String?,
    attachmentStream: SignalServiceAttachmentStream,
    existingSpec: ResumableUploadSpec? = null,
    onSpecCreated: ((ResumableUploadSpec) -> Unit)? = null
  ): NetworkResult<AttachmentUploadResult> {
    return NetworkResult.fromFetch {
      require(existingSpec != null || form != null) { "Either existingSpec or form must be provided" }

      val paddedLength = PaddingInputStream.getPaddedSize(attachmentStream.length)
      val dataStream: InputStream = PaddingInputStream(attachmentStream.inputStream, attachmentStream.length)
      val ciphertextLength = AttachmentCipherStreamUtil.getCiphertextLength(paddedLength)

      val effectiveKey = existingSpec?.attachmentKey ?: key
      val effectiveIv = existingSpec?.attachmentIv ?: iv

      val attachmentData = PushAttachmentData(
        contentType = attachmentStream.contentType,
        data = dataStream,
        dataSize = ciphertextLength,
        incremental = attachmentStream.isFaststart,
        outputStreamFactory = AttachmentCipherOutputStreamFactory(effectiveKey, effectiveIv),
        listener = attachmentStream.listener,
        cancelationSignal = attachmentStream.cancelationSignal,
        resumableUploadSpec = existingSpec
      )

      val digestInfo = if (existingSpec != null) {
        Log.i(TAG, "Resuming upload via HEAD+PATCH")
        pushServiceSocket.uploadAttachment(attachmentData)
      } else if (form!!.cdn == 3) {
        Log.i(TAG, "Fresh upload via creation-with-upload (CDN3)")

        val spec = ResumableUploadSpec(
          attachmentKey = key,
          attachmentIv = iv,
          cdnKey = form.key,
          cdnNumber = form.cdn,
          resumeLocation = form.signedUploadLocation + "/" + form.key,
          expirationTimestamp = System.currentTimeMillis() + PushServiceSocket.CDN2_RESUMABLE_LINK_LIFETIME_MILLIS,
          headers = form.headers
        )
        onSpecCreated?.invoke(spec)

        pushServiceSocket.createAndUploadToCdn3(form, checksumSha256, attachmentData)
      } else {
        Log.i(TAG, "Fresh upload via legacy flow (CDN${form.cdn})")
        val resumeUrl = pushServiceSocket.getResumableUploadUrl(form, checksumSha256)
        val spec = ResumableUploadSpec(
          attachmentKey = key,
          attachmentIv = iv,
          cdnKey = form.key,
          cdnNumber = form.cdn,
          resumeLocation = resumeUrl,
          expirationTimestamp = System.currentTimeMillis() + PushServiceSocket.CDN2_RESUMABLE_LINK_LIFETIME_MILLIS,
          headers = form.headers
        )
        onSpecCreated?.invoke(spec)

        pushServiceSocket.uploadAttachment(attachmentData.copy(resumableUploadSpec = spec))
      }

      val cdnKey = existingSpec?.cdnKey ?: form!!.key
      val cdnNumber = existingSpec?.cdnNumber ?: form!!.cdn

      AttachmentUploadResult(
        remoteId = SignalServiceAttachmentRemoteId.V4(cdnKey),
        cdnNumber = cdnNumber,
        key = key,
        digest = digestInfo.digest,
        incrementalDigest = digestInfo.incrementalDigest,
        incrementalDigestChunkSize = digestInfo.incrementalMacChunkSize,
        uploadTimestamp = attachmentStream.uploadTimestamp,
        dataSize = attachmentStream.length,
        blurHash = attachmentStream.blurHash.getOrNull()
      )
    }
  }
}
