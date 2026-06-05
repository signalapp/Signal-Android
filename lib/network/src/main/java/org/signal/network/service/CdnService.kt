/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import kotlinx.coroutines.runBlocking
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.UploadTooLargeException
import org.signal.network.api.AttachmentApi
import org.signal.network.exceptions.PushNetworkException
import org.signal.network.rest.RequestSpec
import org.signal.network.rest.RequestSpec.Method
import org.signal.network.rest.RestStatusCodeError
import org.signal.network.rest.SignalRestClient
import org.signal.network.rest.toNonSuccessfulResponseCodeException
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import org.whispersystems.signalservice.internal.util.Util
import java.io.IOException

/**
 * Assists in CDN operations.
 */
class CdnService(
  private val signalRestClient: SignalRestClient,
  private val attachmentApi: AttachmentApi
) {

  /**
   * POST to the signed upload location from [uploadForm] to obtain a resumable upload URL. The
   * URL is returned in the response's `Location` header. The exact headers we send depend on the
   * CDN version the upload form targets:
   *
   * - CDN 2: legacy resumable upload — `Content-Type: application/octet-stream`.
   * - CDN 3: TUS protocol — `Upload-Defer-Length: 1`, `Tus-Resumable: 1.0.0`, plus an optional
   *          `x-signal-checksum-sha256` if [checksumSha256] is provided.
   */
  suspend fun getResumableUploadUrl(uploadForm: AttachmentUploadForm, checksumSha256: String? = null): RequestResult<String, RestStatusCodeError> {
    val headers = mutableMapOf<String, String>()
    for ((key, value) in uploadForm.headers) {
      if (!key.equals("host", ignoreCase = true)) {
        headers[key] = value
      }
    }
    headers["Content-Length"] = "0"

    when (uploadForm.cdn) {
      2 -> headers["Content-Type"] = "application/octet-stream"
      3 -> {
        headers["Upload-Defer-Length"] = "1"
        headers["Tus-Resumable"] = "1.0.0"
        if (checksumSha256 != null) {
          headers["x-signal-checksum-sha256"] = checksumSha256
        }
      }
      else -> return RequestResult.ApplicationError(AssertionError("Unknown CDN version: ${uploadForm.cdn}"))
    }

    val spec = RequestSpec(
      method = Method.POST,
      host = RequestSpec.Host.Cdn(uploadForm.cdn),
      path = uploadForm.signedUploadLocation,
      headers = headers
    )

    return when (val result = signalRestClient.request(spec)) {
      is RequestResult.Success -> {
        val location = result.result.headers["location"]
        if (location != null) {
          RequestResult.Success(location)
        } else {
          RequestResult.ApplicationError(IOException("Missing Location header in resumable-upload response"))
        }
      }
      is RequestResult.NonSuccess -> result
      is RequestResult.RetryableNetworkError -> result
      is RequestResult.ApplicationError -> result
    }
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

    return when (val urlResult = getResumableUploadUrl(form)) {
      is RequestResult.Success -> ResumableUploadSpecResult.Success(
        ResumableUploadSpec(
          attachmentKey = Util.getSecretBytes(64),
          attachmentIv = Util.getSecretBytes(16),
          cdnKey = form.key,
          cdnNumber = form.cdn,
          resumeLocation = urlResult.result,
          expirationTimestamp = System.currentTimeMillis() + PushServiceSocket.CDN2_RESUMABLE_LINK_LIFETIME_MILLIS,
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
      is ResumableUploadSpecResult.UploadUrlStatusError -> throw result.error.toNonSuccessfulResponseCodeException()
      is ResumableUploadSpecResult.NetworkError -> throw PushNetworkException(result.exception)
      is ResumableUploadSpecResult.ApplicationError -> when (val cause = result.throwable) {
        is IOException -> throw cause
        is RuntimeException -> throw cause
        else -> throw RuntimeException(cause)
      }
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

    /** The request for a resumable upload URL produced a non-2xx HTTP response. */
    data class UploadUrlStatusError(val error: RestStatusCodeError) : ResumableUploadSpecResult

    /** A retryable network failure occurred during one of the underlying requests. */
    data class NetworkError(val exception: IOException) : ResumableUploadSpecResult

    /** An unexpected client-side failure (likely a bug). */
    data class ApplicationError(val throwable: Throwable) : ResumableUploadSpecResult
  }
}
