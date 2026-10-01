/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import okhttp3.RequestBody
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.ServerSideErrorException
import org.signal.network.rest.RequestSpec
import org.signal.network.rest.RestResponse
import org.signal.network.rest.RestStatusCodeError
import org.signal.network.rest.SignalRestClient
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * CDN upload endpoints. CDN2 uses GCS resumable uploads and CDN3 uses TUS.
 *
 * These requests go straight to the CDN rather than over the chat connection, so they are made with [SignalRestClient]. A 5xx or an unmapped
 * status is reported as a [RequestResult.RetryableNetworkError] carrying a [ServerSideErrorException].
 */
class CdnApi(private val restClient: SignalRestClient) {

  companion object {
    private val TAG = Log.tag(CdnApi::class)

    const val CDN2_UPLOAD_CONTENT_TYPE = "application/octet-stream"
    const val CDN3_UPLOAD_CONTENT_TYPE = "application/offset+octet-stream"

    private const val TUS_VERSION = "1.0.0"
    private const val CHECKSUM_HEADER = "x-signal-checksum-sha256"
  }

  /**
   * Creates a resumable upload at the form's signed upload location, returning the URL the data should be sent to.
   *
   * `POST {signedUploadLocation}`
   * - 2xx: Success, `Location` holds the resumable upload URL
   * - 400: Request is invalid
   * - 413: Upload is too large
   * - 429: Rate limited
   */
  suspend fun createResumableUpload(uploadForm: AttachmentUploadForm, checksumSha256: String?): RequestResult<String, UploadError> {
    val headers = uploadForm.headers.withoutHost().toMutableMap()
    headers["Content-Length"] = "0"

    when (uploadForm.cdn) {
      2 -> headers["Content-Type"] = CDN2_UPLOAD_CONTENT_TYPE
      3 -> {
        headers["Upload-Defer-Length"] = "1"
        headers["Tus-Resumable"] = TUS_VERSION
        if (checksumSha256 != null) {
          headers[CHECKSUM_HEADER] = checksumSha256
        }
      }
      else -> return RequestResult.ApplicationError(IllegalArgumentException("Unknown CDN version: ${uploadForm.cdn}"))
    }

    val spec = RequestSpec(
      method = RequestSpec.Method.POST,
      host = RequestSpec.Host.Cdn(uploadForm.cdn),
      path = uploadForm.signedUploadLocation,
      headers = headers
    )

    return request(
      spec = spec,
      parseSuccess = { response -> response.headers["location"] ?: throw IllegalStateException("Missing Location header in resumable-upload response") },
      mapError = { error ->
        when (error.statusCode) {
          400 -> UploadError.InvalidRequest
          413 -> UploadError.TooLarge
          429 -> UploadError.RateLimited(error.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * CDN3 only. Creates the upload and sends [body] in the same request (TUS creation-with-upload). The upload can be resumed afterwards at
   * `{signedUploadLocation}/{key}`.
   *
   * `POST {signedUploadLocation}`
   * - 2xx: Success
   * - 400: Request is invalid
   * - 413: Upload is too large
   * - 415: The data did not match [checksumSha256]
   * - 429: Rate limited
   */
  suspend fun createUploadWithData(uploadForm: AttachmentUploadForm, checksumSha256: String?, length: Long, body: RequestBody): RequestResult<Unit, UploadError> {
    if (uploadForm.cdn != 3) {
      return RequestResult.ApplicationError(IllegalArgumentException("Creation with upload is only supported on CDN3, not CDN${uploadForm.cdn}"))
    }

    val headers = uploadForm.headers.withoutHost().toMutableMap()
    headers["Upload-Length"] = length.toString()
    headers["Tus-Resumable"] = TUS_VERSION
    if (checksumSha256 != null) {
      headers[CHECKSUM_HEADER] = checksumSha256
    }

    val spec = RequestSpec(
      method = RequestSpec.Method.POST,
      host = RequestSpec.Host.Cdn(3),
      path = uploadForm.signedUploadLocation,
      body = body,
      headers = headers
    )

    return request(spec, parseSuccess = { }, mapError = { it.toUploadError() })
  }

  /**
   * Asks the CDN how many bytes of the upload at [resumeUrl] it has stored.
   *
   * CDN2: `PUT {resumeUrl}` with a `Content-Range` of `bytes` followed by `*` and `/{length}`
   * - 2xx: The upload is already complete
   * - 308: Upload is incomplete, `Range` holds the persisted bytes
   * - 400: Request is invalid
   * - 404, 410: Upload not found
   * - 429: Rate limited
   *
   * CDN3: `HEAD {resumeUrl}`
   * - 2xx: Success, `Upload-Offset` holds the persisted bytes
   * - 429: Rate limited
   * - Other 4xx: Upload not found or no longer usable
   */
  suspend fun getUploadOffset(cdnNumber: Int, resumeUrl: String, headers: Map<String, String>, length: Long): RequestResult<Long, UploadError> {
    return when (cdnNumber) {
      2 -> getCdn2UploadOffset(resumeUrl, length)
      3 -> getCdn3UploadOffset(resumeUrl, headers, length)
      else -> RequestResult.ApplicationError(IllegalArgumentException("Unknown CDN version: $cdnNumber"))
    }
  }

  /**
   * Sends [body], which holds the upload's data starting at [offset], to the upload at [resumeUrl].
   *
   * CDN2: `PUT {resumeUrl}` with `Content-Range: bytes {offset}-{length - 1}/{length}`
   *
   * CDN3: `PATCH {resumeUrl}` with `Upload-Offset: {offset}`
   *
   * - 2xx: Success
   * - 400: Request is invalid
   * - 404, 410: Upload not found
   * - 413: Upload is too large
   * - 415: The data did not match the checksum provided when the upload was created
   * - 429: Rate limited
   */
  suspend fun uploadFromOffset(cdnNumber: Int, resumeUrl: String, headers: Map<String, String>, offset: Long, length: Long, body: RequestBody): RequestResult<Unit, UploadError> {
    val spec = when (cdnNumber) {
      2 -> RequestSpec(
        method = RequestSpec.Method.PUT,
        host = RequestSpec.Host.Cdn(2),
        path = resumeUrl,
        body = body,
        headers = mapOf("Content-Range" to "bytes $offset-${length - 1}/$length")
      )
      3 -> RequestSpec(
        method = RequestSpec.Method.PATCH,
        host = RequestSpec.Host.Cdn(3),
        path = resumeUrl,
        body = body,
        headers = headers.withoutHost() + mapOf(
          "Upload-Offset" to offset.toString(),
          "Upload-Length" to length.toString(),
          "Tus-Resumable" to TUS_VERSION
        )
      )
      else -> return RequestResult.ApplicationError(IllegalArgumentException("Unknown CDN version: $cdnNumber"))
    }

    return request(spec, parseSuccess = { }, mapError = { it.toUploadError() })
  }

  private suspend fun getCdn2UploadOffset(resumeUrl: String, length: Long): RequestResult<Long, UploadError> {
    val spec = RequestSpec(
      method = RequestSpec.Method.PUT,
      host = RequestSpec.Host.Cdn(2),
      path = resumeUrl,
      headers = mapOf("Content-Range" to "bytes */$length")
    )

    return when (val result = restClient.request(spec)) {
      is RequestResult.Success -> RequestResult.Success(length)
      is RequestResult.NonSuccess -> when (result.error.statusCode) {
        308 -> parseCdn2Range(result.error.headers["range"], length)
        400 -> RequestResult.NonSuccess(UploadError.InvalidRequest)
        404, 410 -> RequestResult.NonSuccess(UploadError.ResumeLocationInvalid)
        429 -> RequestResult.NonSuccess(UploadError.RateLimited(result.error.retryAfter()))
        else -> RequestResult.RetryableNetworkError(ServerSideErrorException("Unexpected response code: ${result.error.statusCode}"))
      }
      is RequestResult.RetryableNetworkError -> result
      is RequestResult.ApplicationError -> result
    }
  }

  private suspend fun getCdn3UploadOffset(resumeUrl: String, headers: Map<String, String>, length: Long): RequestResult<Long, UploadError> {
    val spec = RequestSpec(
      method = RequestSpec.Method.HEAD,
      host = RequestSpec.Host.Cdn(3),
      path = resumeUrl,
      headers = headers.withoutHost() + ("Tus-Resumable" to TUS_VERSION)
    )

    return when (val result = restClient.request(spec)) {
      is RequestResult.Success -> {
        val offset = result.result.headers["upload-offset"]?.toLongOrNull()
        if (offset == null || offset !in 0..length) {
          Log.w(TAG, "Unusable Upload-Offset from CDN3: ${result.result.headers["upload-offset"]}")
          RequestResult.NonSuccess(UploadError.ResumeLocationInvalid)
        } else {
          RequestResult.Success(offset)
        }
      }
      is RequestResult.NonSuccess -> when (result.error.statusCode) {
        in 500..599 -> RequestResult.RetryableNetworkError(ServerSideErrorException("Server error: ${result.error.statusCode}"))
        429 -> RequestResult.NonSuccess(UploadError.RateLimited(result.error.retryAfter()))
        else -> RequestResult.NonSuccess(UploadError.ResumeLocationInvalid)
      }
      is RequestResult.RetryableNetworkError -> result
      is RequestResult.ApplicationError -> result
    }
  }

  /**
   * CDN2 reports the persisted bytes as an inclusive `Range: bytes=0-N`, and leaves it out when nothing has been stored.
   */
  private fun parseCdn2Range(range: String?, length: Long): RequestResult<Long, UploadError> {
    if (range == null) {
      return RequestResult.Success(0)
    }

    val offset = range.substringAfterLast('-').toLongOrNull()?.plus(1)
    return if (offset == null || offset !in 0..length) {
      Log.w(TAG, "Unusable Range from CDN2: $range")
      RequestResult.NonSuccess(UploadError.ResumeLocationInvalid)
    } else {
      RequestResult.Success(offset)
    }
  }

  private suspend fun <T, E : BadRequestError> request(
    spec: RequestSpec,
    parseSuccess: (RestResponse) -> T,
    mapError: (RestStatusCodeError) -> E?
  ): RequestResult<T, E> {
    return when (val result = restClient.request(spec)) {
      is RequestResult.Success -> try {
        RequestResult.Success(parseSuccess(result.result))
      } catch (e: Exception) {
        RequestResult.ApplicationError(e)
      }
      is RequestResult.NonSuccess -> when {
        result.error.statusCode in 500..599 -> RequestResult.RetryableNetworkError(ServerSideErrorException("Server error: ${result.error.statusCode}"))
        else -> when (val error = mapError(result.error)) {
          null -> RequestResult.RetryableNetworkError(ServerSideErrorException("Unexpected response code: ${result.error.statusCode}"))
          else -> RequestResult.NonSuccess(error)
        }
      }
      is RequestResult.RetryableNetworkError -> result
      is RequestResult.ApplicationError -> result
    }
  }

  private fun RestStatusCodeError.toUploadError(): UploadError? {
    return when (statusCode) {
      400 -> UploadError.InvalidRequest
      404, 410 -> UploadError.ResumeLocationInvalid
      413 -> UploadError.TooLarge
      415 -> UploadError.ChecksumMismatch
      429 -> UploadError.RateLimited(retryAfter())
      else -> null
    }
  }

  private fun RestStatusCodeError.retryAfter(): Duration? {
    return headers["retry-after"]?.toLongOrNull()?.seconds
  }

  private fun Map<String, String>.withoutHost(): Map<String, String> {
    return filterKeys { !it.equals("host", ignoreCase = true) }
  }

  sealed interface UploadError : BadRequestError {
    /** The CDN rejected the upload's resumable state (HTTP 400). The upload has to start over with a new upload form. */
    data object InvalidRequest : UploadError

    /** The upload has expired, the CDN no longer has it, or it reported progress we can't use. The upload has to start over with a new upload form. */
    data object ResumeLocationInvalid : UploadError

    /** The CDN will not accept an upload this large (HTTP 413). Retrying will not help. */
    data object TooLarge : UploadError

    /** The uploaded data did not match the checksum given when the upload was created (HTTP 415). */
    data object ChecksumMismatch : UploadError

    data class RateLimited(val retryAfter: Duration?) : UploadError
  }
}
