/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.internal.http2.StreamResetException
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.UploadTooLargeException
import org.signal.network.api.AttachmentApi
import org.signal.network.api.AttachmentUploadResult
import org.signal.network.api.CdnApi
import org.signal.network.api.CdnApi.UploadError
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.messages.AttachmentUploader
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException
import org.whispersystems.signalservice.internal.crypto.AttachmentDigest
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.PushAttachmentData
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.push.http.CancelationSignal
import org.whispersystems.signalservice.internal.push.http.DigestingUploadStream
import org.whispersystems.signalservice.internal.push.http.NoCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.push.http.OutputStreamFactory
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import org.whispersystems.signalservice.internal.util.Util
import java.io.IOException
import java.io.InputStream
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.days

/**
 * Higher-level CDN upload operations. Each upload is a chain of [CdnApi] calls: creating the upload, asking the CDN how much it already has
 * when resuming, and sending the rest.
 */
class CdnService(
  private val cdnApi: CdnApi,
  private val attachmentApi: AttachmentApi,
  private val uploadChunkSizeBytes: () -> Long? = { null }
) : AttachmentUploader {

  companion object {
    private val TAG = Log.tag(CdnService::class)

    private val RESUMABLE_UPLOAD_LIFETIME = 7.days

    /** CDN2 rejects any chunk except the last that isn't a multiple of 256 KiB. */
    private const val CHUNK_ALIGNMENT_BYTES = 256L * 1024
  }

  /**
   * Fetches a v4 attachment upload form (sized for [uploadSizeBytes]) and turns it into a
   * ready-to-use [ResumableUploadSpec].
   *
   * This is a composite of two requests (fetch form + fetch resumable URL), so it has more possible
   * outcomes than a single request — see [ResumableUploadSpecResult].
   */
  suspend fun getResumableUploadSpec(uploadSizeBytes: Long): ResumableUploadSpecResult {
    val form: AttachmentUploadForm = when (val formResult = attachmentApi.getAttachmentV4UploadForm(uploadSizeBytes)) {
      is RequestResult.Success -> formResult.result
      is RequestResult.NonSuccess -> return ResumableUploadSpecResult.UploadTooLarge(formResult.error)
      is RequestResult.RetryableNetworkError -> return ResumableUploadSpecResult.NetworkError(formResult.networkError)
      is RequestResult.ApplicationError -> return ResumableUploadSpecResult.ApplicationError(formResult.cause)
    }

    return when (val urlResult = cdnApi.createResumableUpload(form, checksumSha256 = null)) {
      is RequestResult.Success -> ResumableUploadSpecResult.Success(
        ResumableUploadSpec(
          attachmentKey = Util.getSecretBytes(64),
          attachmentIv = Util.getSecretBytes(16),
          cdnKey = form.key,
          cdnNumber = form.cdn,
          resumeLocation = urlResult.result,
          expirationTimestamp = System.currentTimeMillis() + RESUMABLE_UPLOAD_LIFETIME.inWholeMilliseconds,
          headers = form.headers
        )
      )
      is RequestResult.NonSuccess -> ResumableUploadSpecResult.UploadUrlStatusError(urlResult.error)
      is RequestResult.RetryableNetworkError -> ResumableUploadSpecResult.NetworkError(urlResult.networkError)
      is RequestResult.ApplicationError -> ResumableUploadSpecResult.ApplicationError(urlResult.cause)
    }
  }

  /**
   * Legacy adapter over [getResumableUploadSpec] that throws on failure. Should only be used
   * by java code.
   */
  @Throws(IOException::class)
  fun getResumableUploadSpecBlocking(uploadSizeBytes: Long): ResumableUploadSpec {
    return when (val result = runBlocking { getResumableUploadSpec(uploadSizeBytes) }) {
      is ResumableUploadSpecResult.Success -> result.spec
      is ResumableUploadSpecResult.UploadTooLarge -> throw result.exception
      is ResumableUploadSpecResult.UploadUrlStatusError -> throw result.error.toException()
      is ResumableUploadSpecResult.NetworkError -> throw PushNetworkException(result.exception)
      is ResumableUploadSpecResult.ApplicationError -> throw result.throwable.asThrowable()
    }
  }

  /**
   * Encrypts and uploads an attachment, automatically choosing the best upload strategy based on CDN version.
   * For CDN3, uses TUS "Creation With Upload" (single POST). For other CDNs, falls back to the legacy
   * resumable upload flow (POST create + HEAD + PATCH).
   *
   * If [existingSpec] is provided, the upload resumes using the existing resumable upload URL (HEAD+PATCH)
   * and [form] is not required.
   * Otherwise, [form] is required, a new upload is initiated, and [onSpecCreated] is called with the
   * [ResumableUploadSpec] before the upload begins, allowing callers to persist it for crash recovery.
   */
  suspend fun uploadAttachment(
    form: AttachmentUploadForm? = null,
    key: ByteArray,
    iv: ByteArray,
    checksumSha256: String?,
    attachmentStream: SignalServiceAttachmentStream,
    existingSpec: ResumableUploadSpec? = null,
    onSpecCreated: ((ResumableUploadSpec) -> Unit)? = null
  ): RequestResult<AttachmentUploadResult, UploadError> {
    val paddedLength = PaddingInputStream.getPaddedSize(attachmentStream.length)
    val data = UploadData(
      inputStream = PaddingInputStream(attachmentStream.inputStream, attachmentStream.length),
      length = AttachmentCipherStreamUtil.getCiphertextLength(paddedLength),
      outputStreamFactory = AttachmentCipherOutputStreamFactory(existingSpec?.attachmentKey ?: key, existingSpec?.attachmentIv ?: iv),
      incremental = attachmentStream.isFaststart,
      progressListener = attachmentStream.listener,
      cancelationSignal = attachmentStream.cancelationSignal
    )

    if (existingSpec != null) {
      Log.i(TAG, "Resuming upload via HEAD+PATCH")
      return resumeUpload(existingSpec, data).map { it.toAttachmentUploadResult(existingSpec.cdnNumber, existingSpec.cdnKey, key, attachmentStream) }
    }

    if (form == null) {
      return RequestResult.ApplicationError(IllegalArgumentException("Either existingSpec or form must be provided"))
    }

    val digestResult = if (form.cdn == 3) {
      Log.i(TAG, "Fresh upload via creation-with-upload (CDN3)")
      onSpecCreated?.invoke(form.toResumableUploadSpec(key, iv, resumeLocation = form.cdn3ResumeUrl))
      createUploadWithData(form, checksumSha256, data)
    } else {
      Log.i(TAG, "Fresh upload via legacy flow (CDN${form.cdn})")
      createResumableUploadThen(form, checksumSha256) { resumeUrl ->
        val spec = form.toResumableUploadSpec(key, iv, resumeLocation = resumeUrl)
        onSpecCreated?.invoke(spec)
        resumeUpload(spec, data)
      }
    }

    return digestResult.map { it.toAttachmentUploadResult(form.cdn, form.key, key, attachmentStream) }
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
  suspend fun uploadBackupFile(
    uploadForm: AttachmentUploadForm,
    data: InputStream,
    dataLength: Long,
    checksumSha256: String? = null,
    progressListener: SignalServiceAttachment.ProgressListener? = null,
    existingResumeUrl: String? = null,
    onResumeUrlCreated: ((String) -> Unit)? = null
  ): RequestResult<Unit, UploadError> {
    val uploadData = UploadData(inputStream = data, length = dataLength, progressListener = progressListener)

    val digestResult = if (existingResumeUrl != null) {
      Log.i(TAG, "Resuming backup upload via HEAD+PATCH")
      resumeUpload(uploadForm.cdn, existingResumeUrl, uploadForm.headers, uploadData)
    } else if (uploadForm.cdn == 3) {
      Log.i(TAG, "Fresh backup upload via creation-with-upload (CDN3)")
      onResumeUrlCreated?.invoke(uploadForm.cdn3ResumeUrl)
      createUploadWithData(uploadForm, checksumSha256, uploadData)
    } else {
      Log.i(TAG, "Fresh backup upload via legacy flow (CDN${uploadForm.cdn})")
      createResumableUploadThen(uploadForm, checksumSha256) { resumeUrl ->
        onResumeUrlCreated?.invoke(resumeUrl)
        resumeUpload(uploadForm.cdn, resumeUrl, uploadForm.headers, uploadData)
      }
    }

    return digestResult.map { }
  }

  /**
   * Legacy adapter that uploads an attachment to its existing [PushAttachmentData.resumableUploadSpec], throwing on failure. Should only be used
   * by java code.
   */
  @Throws(IOException::class)
  override fun uploadAttachmentBlocking(attachmentData: PushAttachmentData): AttachmentDigest {
    val spec = attachmentData.resumableUploadSpec ?: throw IllegalArgumentException("Attachment must have a resumable upload spec.")
    val data = UploadData(
      inputStream = attachmentData.data,
      length = attachmentData.dataSize,
      outputStreamFactory = attachmentData.outputStreamFactory,
      incremental = attachmentData.incremental,
      progressListener = attachmentData.listener,
      cancelationSignal = attachmentData.cancelationSignal
    )

    return when (val result = runBlocking { resumeUpload(spec, data) }) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> throw result.error.toException()
      is RequestResult.RetryableNetworkError -> when (val exception = result.networkError) {
        is StreamResetException, is PushNetworkException -> throw exception
        else -> throw PushNetworkException(exception)
      }
      is RequestResult.ApplicationError -> throw result.cause.asThrowable()
    }
  }

  private suspend fun createResumableUploadThen(
    form: AttachmentUploadForm,
    checksumSha256: String?,
    upload: suspend (resumeUrl: String) -> RequestResult<AttachmentDigest, UploadError>
  ): RequestResult<AttachmentDigest, UploadError> {
    return when (val result = cdnApi.createResumableUpload(form, checksumSha256)) {
      is RequestResult.Success -> upload(result.result)
      is RequestResult.NonSuccess -> result
      is RequestResult.RetryableNetworkError -> result
      is RequestResult.ApplicationError -> result
    }
  }

  private suspend fun createUploadWithData(form: AttachmentUploadForm, checksumSha256: String?, data: UploadData): RequestResult<AttachmentDigest, UploadError> {
    val stream = data.toUploadStream(CdnApi.CDN3_UPLOAD_CONTENT_TYPE, contentStart = 0)

    return sendInChunks(stream, startOffset = 0, data.length) { offset, chunkLength, body ->
      if (offset == 0L) {
        cdnApi.createUploadWithData(form, checksumSha256, chunkLength, data.length, body)
      } else {
        cdnApi.uploadFromOffset(3, form.cdn3ResumeUrl, form.headers, offset, chunkLength, data.length, body)
      }
    }
  }

  private suspend fun resumeUpload(spec: ResumableUploadSpec, data: UploadData): RequestResult<AttachmentDigest, UploadError> {
    if (spec.expirationTimestamp < System.currentTimeMillis()) {
      Log.w(TAG, "Resumable upload has expired.")
      return RequestResult.NonSuccess(UploadError.ResumeLocationInvalid)
    }

    return resumeUpload(spec.cdnNumber, spec.resumeLocation, spec.headers, data)
  }

  private suspend fun resumeUpload(cdnNumber: Int, resumeUrl: String, headers: Map<String, String>, data: UploadData): RequestResult<AttachmentDigest, UploadError> {
    val offset = when (val result = cdnApi.getUploadOffset(cdnNumber, resumeUrl, headers, data.length)) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> return RequestResult.NonSuccess(result.error)
      is RequestResult.RetryableNetworkError -> return RequestResult.RetryableNetworkError(result.networkError)
      is RequestResult.ApplicationError -> return RequestResult.ApplicationError(result.cause)
    }

    val contentType = if (cdnNumber == 2) CdnApi.CDN2_UPLOAD_CONTENT_TYPE else CdnApi.CDN3_UPLOAD_CONTENT_TYPE
    val stream = data.toUploadStream(contentType, contentStart = offset)

    if (offset == data.length) {
      Log.w(TAG, "Resume start point == content length")
      return stream.toDigestResultWithoutSending()
    } else if (offset > 0) {
      Log.i(TAG, "Resuming upload at $offset of ${data.length}")
    }

    return sendInChunks(stream, startOffset = offset, data.length) { chunkOffset, chunkLength, body ->
      cdnApi.uploadFromOffset(cdnNumber, resumeUrl, headers, chunkOffset, chunkLength, data.length, body)
    }
  }

  private suspend fun sendInChunks(
    stream: DigestingUploadStream,
    startOffset: Long,
    length: Long,
    sendChunk: suspend (offset: Long, chunkLength: Long, body: RequestBody) -> RequestResult<Long, UploadError>
  ): RequestResult<AttachmentDigest, UploadError> {
    val chunkSize = chunkSizeFor(length - startOffset)
    var offset = startOffset

    do {
      val chunkLength = min(chunkSize, stream.bytesRemaining)

      when (val result = sendChunk(offset, chunkLength, stream.nextChunk(chunkLength))) {
        is RequestResult.Success -> {
          offset += chunkLength
          if (result.result != offset) {
            Log.w(TAG, "CDN persisted ${result.result} of $length bytes, expected $offset")
            return RequestResult.RetryableNetworkError(IOException("CDN did not persist the full chunk. Expected: $offset, actual: ${result.result}"))
          }
        }
        is RequestResult.NonSuccess -> return RequestResult.NonSuccess(result.error)
        is RequestResult.RetryableNetworkError -> return RequestResult.RetryableNetworkError(result.networkError)
        is RequestResult.ApplicationError -> return RequestResult.ApplicationError(result.cause)
      }
    } while (stream.bytesRemaining > 0)

    return stream.toDigestResult()
  }

  private fun chunkSizeFor(bytesToUpload: Long): Long {
    val configured = uploadChunkSizeBytes() ?: return Long.MAX_VALUE
    val chunkSize = max(configured - configured % CHUNK_ALIGNMENT_BYTES, CHUNK_ALIGNMENT_BYTES)

    if (bytesToUpload > chunkSize) {
      Log.i(TAG, "Uploading $bytesToUpload bytes in chunks of $chunkSize")
    }

    return chunkSize
  }

  private fun DigestingUploadStream.toDigestResultWithoutSending(): RequestResult<AttachmentDigest, UploadError> {
    return try {
      digestWithoutSending()
      toDigestResult()
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    }
  }

  private fun DigestingUploadStream.toDigestResult(): RequestResult<AttachmentDigest, UploadError> {
    val digest = attachmentDigest ?: return RequestResult.ApplicationError(IllegalStateException("Upload finished without computing a digest"))
    return RequestResult.Success(digest)
  }

  private val AttachmentUploadForm.cdn3ResumeUrl: String
    get() = "$signedUploadLocation/$key"

  private fun AttachmentUploadForm.toResumableUploadSpec(key: ByteArray, iv: ByteArray, resumeLocation: String): ResumableUploadSpec {
    return ResumableUploadSpec(
      attachmentKey = key,
      attachmentIv = iv,
      cdnKey = this.key,
      cdnNumber = cdn,
      resumeLocation = resumeLocation,
      expirationTimestamp = System.currentTimeMillis() + RESUMABLE_UPLOAD_LIFETIME.inWholeMilliseconds,
      headers = headers
    )
  }

  private fun AttachmentDigest.toAttachmentUploadResult(cdnNumber: Int, cdnKey: String, key: ByteArray, attachmentStream: SignalServiceAttachmentStream): AttachmentUploadResult {
    return AttachmentUploadResult(
      remoteId = SignalServiceAttachmentRemoteId.V4(cdnKey),
      cdnNumber = cdnNumber,
      key = key,
      digest = digest,
      incrementalDigest = incrementalDigest,
      incrementalDigestChunkSize = incrementalMacChunkSize,
      uploadTimestamp = attachmentStream.uploadTimestamp,
      dataSize = attachmentStream.length,
      blurHash = attachmentStream.blurHash.getOrNull(),
      audioHash = attachmentStream.audioHash.getOrNull()
    )
  }

  private fun UploadError.toException(): IOException {
    return when (this) {
      is UploadError.ResumeLocationInvalid -> ResumeLocationInvalidException()
      is UploadError.InvalidRequest -> NonSuccessfulResponseCodeException(400)
      is UploadError.TooLarge -> NonSuccessfulResponseCodeException(413)
      is UploadError.ChecksumMismatch -> NonSuccessfulResponseCodeException(415)
      is UploadError.RateLimited -> NonSuccessfulResponseCodeException(429)
    }
  }

  private fun Throwable.asThrowable(): Throwable {
    return when (this) {
      is IOException, is RuntimeException -> this
      else -> RuntimeException(this)
    }
  }

  private class UploadData(
    val inputStream: InputStream,
    val length: Long,
    val outputStreamFactory: OutputStreamFactory = NoCipherOutputStreamFactory(),
    val incremental: Boolean = false,
    val progressListener: SignalServiceAttachment.ProgressListener? = null,
    val cancelationSignal: CancelationSignal? = null
  ) {
    fun toUploadStream(contentType: String, contentStart: Long): DigestingUploadStream {
      return DigestingUploadStream(inputStream, outputStreamFactory, contentType, length, incremental, progressListener, cancelationSignal, contentStart)
    }
  }

  /**
   * The possible outcomes of [getResumableUploadSpec]. Because that call composes multiple requests,
   * it can't honestly be represented as a single [RequestResult] — each failure mode here means
   * something different to a caller.
   */
  sealed interface ResumableUploadSpecResult {
    /** Got a usable spec. */
    data class Success(val spec: ResumableUploadSpec) : ResumableUploadSpecResult

    /** The server rejected the upload because it's larger than the maximum supported size. */
    data class UploadTooLarge(val exception: UploadTooLargeException) : ResumableUploadSpecResult

    /** The request for a resumable upload URL was rejected by the CDN. */
    data class UploadUrlStatusError(val error: UploadError) : ResumableUploadSpecResult

    /** A retryable network failure occurred during one of the underlying requests. */
    data class NetworkError(val exception: IOException) : ResumableUploadSpecResult

    /** An unexpected client-side failure (likely a bug). */
    data class ApplicationError(val throwable: Throwable) : ResumableUploadSpecResult
  }
}
