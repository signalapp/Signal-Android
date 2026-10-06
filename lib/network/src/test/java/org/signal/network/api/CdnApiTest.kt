/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.ServerSideErrorException
import org.signal.network.config.SignalCdnUrl
import org.signal.network.config.SignalCdsiUrl
import org.signal.network.config.SignalServiceConfiguration
import org.signal.network.config.SignalServiceUrl
import org.signal.network.config.SignalStorageUrl
import org.signal.network.config.SignalSvr2Url
import org.signal.network.config.TrustStore
import org.signal.network.rest.SignalRestClient
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Optional
import java.util.Random
import kotlin.time.Duration.Companion.seconds

class CdnApiTest {

  companion object {
    private const val LENGTH = 1_000L

    private val DUMMY_TRUST_STORE = object : TrustStore {
      override fun getKeyStoreInputStream() = ByteArrayInputStream(ByteArray(0))
      override fun getKeyStorePassword() = ""
    }
  }

  private val requests = mutableListOf<RecordedRequest>()
  private var responder: (RecordedRequest) -> Response = { respond(it, 200) }

  private val cdnApi = CdnApi(restClient())

  private val cdn3Form = AttachmentUploadForm(
    cdn = 3,
    key = "cdn-key",
    headers = mapOf("Authorization" to "Bearer token", "Host" to "ignored.test"),
    signedUploadLocation = "https://upload.test/tus"
  )

  private val cdn2Form = AttachmentUploadForm(
    cdn = 2,
    key = "cdn-key",
    headers = mapOf("Authorization" to "Bearer token"),
    signedUploadLocation = "https://upload.test/signed"
  )

  @Test
  fun `createResumableUpload uses TUS deferred length on CDN3 and returns the location`() = runTest {
    responder = { respond(it, 201, "Location" to "https://upload.test/tus/abc") }

    val result = cdnApi.createResumableUpload(cdn3Form, "checksum")

    assertThat(result).isEqualTo(RequestResult.Success("https://upload.test/tus/abc"))
    val request = requests.single()
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.url).isEqualTo("https://cdn3.test/tus")
    assertThat(request.header("Upload-Defer-Length")).isEqualTo("1")
    assertThat(request.header("Tus-Resumable")).isEqualTo("1.0.0")
    assertThat(request.header("x-signal-checksum-sha256")).isEqualTo("checksum")
    assertThat(request.header("Authorization")).isEqualTo("Bearer token")
  }

  @Test
  fun `createResumableUpload uses octet-stream on CDN2`() = runTest {
    responder = { respond(it, 201, "Location" to "https://upload.test/resumable?id=1") }

    val result = cdnApi.createResumableUpload(cdn2Form, null)

    assertThat(result).isEqualTo(RequestResult.Success("https://upload.test/resumable?id=1"))
    assertThat(requests.single().header("Content-Type")).isEqualTo("application/octet-stream")
    assertThat(requests.single().header("Upload-Defer-Length")).isNull()
  }

  @Test
  fun `createResumableUpload without a location is an ApplicationError`() = runTest {
    responder = { respond(it, 201) }

    val result = cdnApi.createResumableUpload(cdn3Form, null)

    assertThat(result).isInstanceOf<RequestResult.ApplicationError>()
  }

  @Test
  fun `createResumableUpload maps documented errors`() = runTest {
    responder = { respond(it, 400) }
    assertThat(cdnApi.createResumableUpload(cdn3Form, null)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.InvalidRequest))

    responder = { respond(it, 413) }
    assertThat(cdnApi.createResumableUpload(cdn3Form, null)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.TooLarge))

    responder = { respond(it, 429, "Retry-After" to "5") }
    assertThat(cdnApi.createResumableUpload(cdn3Form, null)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(5.seconds)))
  }

  @Test
  fun `server errors and unmapped statuses are retryable`() = runTest {
    listOf(500, 503, 418).forEach { code ->
      responder = { respond(it, code) }

      val result = cdnApi.createResumableUpload(cdn3Form, null)

      assertThat(result).isInstanceOf<RequestResult.RetryableNetworkError>()
      assertThat((result as RequestResult.RetryableNetworkError).networkError).isInstanceOf<ServerSideErrorException>()
    }
  }

  @Test
  fun `transport failures are retryable`() = runTest {
    responder = { throw IOException("connection reset") }

    val result = cdnApi.createUploadWithData(cdn3Form, null, LENGTH, LENGTH, body())

    assertThat(result).isInstanceOf<RequestResult.RetryableNetworkError>()
  }

  @Test
  fun `createUploadWithData sends the body in a TUS creation request`() = runTest {
    val result = cdnApi.createUploadWithData(cdn3Form, "checksum", LENGTH, LENGTH, body())

    assertThat(result).isEqualTo(RequestResult.Success(LENGTH))
    val request = requests.single()
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.url).isEqualTo("https://cdn3.test/tus")
    assertThat(request.header("Upload-Length")).isEqualTo(LENGTH.toString())
    assertThat(request.header("Tus-Resumable")).isEqualTo("1.0.0")
    assertThat(request.header("x-signal-checksum-sha256")).isEqualTo("checksum")
    assertThat(request.header("Authorization")).isEqualTo("Bearer token")
    assertThat(request.headers.filter { it.first.equals("host", ignoreCase = true) }).isEmpty()
    assertThat(request.body.toList()).isEqualTo(bodyBytes().toList())
  }

  @Test
  fun `createUploadWithData rejects non-CDN3 forms without making a request`() = runTest {
    val result = cdnApi.createUploadWithData(cdn2Form, null, LENGTH, LENGTH, body())

    assertThat(result).isInstanceOf<RequestResult.ApplicationError>()
    assertThat(requests).isEmpty()
  }

  @Test
  fun `createUploadWithData maps a checksum mismatch`() = runTest {
    responder = { respond(it, 415) }

    val result = cdnApi.createUploadWithData(cdn3Form, "checksum", LENGTH, LENGTH, body())

    assertThat(result).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ChecksumMismatch))
  }

  @Test
  fun `CDN3 getUploadOffset reads Upload-Offset from a HEAD request`() = runTest {
    responder = { respond(it, 200, "Upload-Offset" to "123") }

    val result = cdnApi.getUploadOffset(3, "https://upload.test/tus/cdn-key", cdn3Form.headers, LENGTH)

    assertThat(result).isEqualTo(RequestResult.Success(123L))
    val request = requests.single()
    assertThat(request.method).isEqualTo("HEAD")
    assertThat(request.url).isEqualTo("https://cdn3.test/tus/cdn-key")
    assertThat(request.header("Tus-Resumable")).isEqualTo("1.0.0")
    assertThat(request.header("Authorization")).isEqualTo("Bearer token")
  }

  @Test
  fun `CDN3 getUploadOffset treats a missing upload or unusable offset as an invalid resume location`() = runTest {
    listOf(403, 404, 410).forEach { code ->
      responder = { respond(it, code) }
      assertThat(cdnApi.getUploadOffset(3, "https://upload.test/tus/cdn-key", emptyMap(), LENGTH)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid))
    }

    listOf(null, "not-a-number", "-1", (LENGTH + 1).toString()).forEach { offset ->
      responder = { request -> if (offset == null) respond(request, 200) else respond(request, 200, "Upload-Offset" to offset) }
      assertThat(cdnApi.getUploadOffset(3, "https://upload.test/tus/cdn-key", emptyMap(), LENGTH)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid))
    }
  }

  @Test
  fun `CDN3 getUploadOffset keeps server errors and rate limits retryable`() = runTest {
    responder = { respond(it, 503) }
    assertThat(cdnApi.getUploadOffset(3, "https://upload.test/tus/cdn-key", emptyMap(), LENGTH)).isInstanceOf<RequestResult.RetryableNetworkError>()

    responder = { respond(it, 429, "Retry-After" to "7") }
    assertThat(cdnApi.getUploadOffset(3, "https://upload.test/tus/cdn-key", emptyMap(), LENGTH)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(7.seconds)))
  }

  @Test
  fun `CDN2 getUploadOffset reads the persisted range from a 308`() = runTest {
    responder = { respond(it, 308, "Range" to "bytes=0-99") }

    val result = cdnApi.getUploadOffset(2, "https://upload.test/resumable?id=1", emptyMap(), LENGTH)

    assertThat(result).isEqualTo(RequestResult.Success(100L))
    val request = requests.single()
    assertThat(request.method).isEqualTo("PUT")
    assertThat(request.url).isEqualTo("https://cdn2.test/resumable?id=1")
    assertThat(request.header("Content-Range")).isEqualTo("bytes */$LENGTH")
    assertThat(request.body.size).isEqualTo(0)
  }

  @Test
  fun `CDN2 getUploadOffset handles nothing stored and a completed upload`() = runTest {
    responder = { respond(it, 308) }
    assertThat(cdnApi.getUploadOffset(2, "https://upload.test/resumable?id=1", emptyMap(), LENGTH)).isEqualTo(RequestResult.Success(0L))

    responder = { respond(it, 200) }
    assertThat(cdnApi.getUploadOffset(2, "https://upload.test/resumable?id=1", emptyMap(), LENGTH)).isEqualTo(RequestResult.Success(LENGTH))
  }

  @Test
  fun `CDN2 getUploadOffset maps failures`() = runTest {
    responder = { respond(it, 404) }
    assertThat(cdnApi.getUploadOffset(2, "https://upload.test/resumable?id=1", emptyMap(), LENGTH)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid))

    responder = { respond(it, 410) }
    assertThat(cdnApi.getUploadOffset(2, "https://upload.test/resumable?id=1", emptyMap(), LENGTH)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid))

    responder = { respond(it, 400) }
    assertThat(cdnApi.getUploadOffset(2, "https://upload.test/resumable?id=1", emptyMap(), LENGTH)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.InvalidRequest))

    responder = { respond(it, 308, "Range" to "garbage") }
    assertThat(cdnApi.getUploadOffset(2, "https://upload.test/resumable?id=1", emptyMap(), LENGTH)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid))

    responder = { respond(it, 429, "Retry-After" to "3") }
    assertThat(cdnApi.getUploadOffset(2, "https://upload.test/resumable?id=1", emptyMap(), LENGTH)).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(3.seconds)))

    responder = { respond(it, 403) }
    assertThat(cdnApi.getUploadOffset(2, "https://upload.test/resumable?id=1", emptyMap(), LENGTH)).isInstanceOf<RequestResult.RetryableNetworkError>()
  }

  @Test
  fun `CDN3 uploadFromOffset PATCHes from the offset`() = runTest {
    responder = { respond(it, 204) }

    val result = cdnApi.uploadFromOffset(3, "https://upload.test/tus/cdn-key", cdn3Form.headers, offset = 12, chunkLength = LENGTH - 12, length = LENGTH, body = body())

    assertThat(result).isEqualTo(RequestResult.Success(LENGTH))
    val request = requests.single()
    assertThat(request.method).isEqualTo("PATCH")
    assertThat(request.url).isEqualTo("https://cdn3.test/tus/cdn-key")
    assertThat(request.header("Upload-Offset")).isEqualTo("12")
    assertThat(request.header("Upload-Length")).isEqualTo(LENGTH.toString())
    assertThat(request.header("Tus-Resumable")).isEqualTo("1.0.0")
    assertThat(request.header("Authorization")).isEqualTo("Bearer token")
    assertThat(request.body.toList()).isEqualTo(bodyBytes().toList())
  }

  @Test
  fun `CDN2 uploadFromOffset PUTs with a content range`() = runTest {
    val result = cdnApi.uploadFromOffset(2, "https://upload.test/resumable?id=1", emptyMap(), offset = 100, chunkLength = LENGTH - 100, length = LENGTH, body = body())

    assertThat(result).isEqualTo(RequestResult.Success(LENGTH))
    val request = requests.single()
    assertThat(request.method).isEqualTo("PUT")
    assertThat(request.header("Content-Range")).isEqualTo("bytes 100-${LENGTH - 1}/$LENGTH")
  }

  @Test
  fun `createUploadWithData returns the offset the CDN persisted`() = runTest {
    responder = { respond(it, 201, "Upload-Offset" to "256") }

    val result = cdnApi.createUploadWithData(cdn3Form, null, chunkLength = 256, length = LENGTH, body = body())

    assertThat(result).isEqualTo(RequestResult.Success(256L))
    assertThat(requests.single().header("Upload-Length")).isEqualTo(LENGTH.toString())

    responder = { respond(it, 201, "Upload-Offset" to "garbage") }
    assertThat(cdnApi.createUploadWithData(cdn3Form, null, chunkLength = 256, length = LENGTH, body = body())).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid))
  }

  @Test
  fun `CDN3 uploadFromOffset returns the offset the CDN persisted`() = runTest {
    responder = { respond(it, 204, "Upload-Offset" to "300") }
    assertThat(cdnApi.uploadFromOffset(3, "https://upload.test/tus/cdn-key", emptyMap(), 100, 256, LENGTH, body())).isEqualTo(RequestResult.Success(300L))

    responder = { respond(it, 204) }
    assertThat(cdnApi.uploadFromOffset(3, "https://upload.test/tus/cdn-key", emptyMap(), 100, 256, LENGTH, body())).isEqualTo(RequestResult.Success(356L))

    responder = { respond(it, 204, "Upload-Offset" to "garbage") }
    assertThat(cdnApi.uploadFromOffset(3, "https://upload.test/tus/cdn-key", emptyMap(), 100, 256, LENGTH, body())).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid))
  }

  @Test
  fun `CDN2 uploadFromOffset sends a partial content range and reads the persisted range from a 308`() = runTest {
    responder = { respond(it, 308, "Range" to "bytes=0-511") }

    val result = cdnApi.uploadFromOffset(2, "https://upload.test/resumable?id=1", emptyMap(), offset = 256, chunkLength = 256, length = LENGTH, body = body())

    assertThat(result).isEqualTo(RequestResult.Success(512L))
    assertThat(requests.single().header("Content-Range")).isEqualTo("bytes 256-511/$LENGTH")
  }

  @Test
  fun `CDN2 uploadFromOffset maps failures`() = runTest {
    responder = { respond(it, 308) }
    assertThat(cdnApi.uploadFromOffset(2, "https://upload.test/resumable?id=1", emptyMap(), 0, 256, LENGTH, body())).isEqualTo(RequestResult.Success(0L))

    responder = { respond(it, 404) }
    assertThat(cdnApi.uploadFromOffset(2, "https://upload.test/resumable?id=1", emptyMap(), 0, 256, LENGTH, body())).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid))

    responder = { respond(it, 429, "Retry-After" to "2") }
    assertThat(cdnApi.uploadFromOffset(2, "https://upload.test/resumable?id=1", emptyMap(), 0, 256, LENGTH, body())).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(2.seconds)))

    listOf(503, 409).forEach { code ->
      responder = { respond(it, code) }
      assertThat(cdnApi.uploadFromOffset(2, "https://upload.test/resumable?id=1", emptyMap(), 0, 256, LENGTH, body())).isInstanceOf<RequestResult.RetryableNetworkError>()
    }
  }

  @Test
  fun `uploadFromOffset maps documented errors`() = runTest {
    mapOf(
      400 to CdnApi.UploadError.InvalidRequest,
      404 to CdnApi.UploadError.ResumeLocationInvalid,
      410 to CdnApi.UploadError.ResumeLocationInvalid,
      413 to CdnApi.UploadError.TooLarge,
      415 to CdnApi.UploadError.ChecksumMismatch
    ).forEach { (code, error) ->
      responder = { respond(it, code) }
      assertThat(cdnApi.uploadFromOffset(3, "https://upload.test/tus/cdn-key", emptyMap(), 0, LENGTH, LENGTH, body())).isEqualTo(RequestResult.NonSuccess(error))
    }

    responder = { respond(it, 409) }
    assertThat(cdnApi.uploadFromOffset(3, "https://upload.test/tus/cdn-key", emptyMap(), 0, LENGTH, LENGTH, body())).isInstanceOf<RequestResult.RetryableNetworkError>()
  }

  @Test
  fun `unknown CDN numbers are an ApplicationError without making a request`() = runTest {
    assertThat(cdnApi.getUploadOffset(7, "https://upload.test/x", emptyMap(), LENGTH)).isInstanceOf<RequestResult.ApplicationError>()
    assertThat(cdnApi.uploadFromOffset(7, "https://upload.test/x", emptyMap(), 0, LENGTH, LENGTH, body())).isInstanceOf<RequestResult.ApplicationError>()
    assertThat(requests).isEmpty()
  }

  private fun bodyBytes(): ByteArray = ByteArray(LENGTH.toInt()) { it.toByte() }

  private fun body() = bodyBytes().toRequestBody("application/offset+octet-stream".toMediaType())

  private fun respond(request: RecordedRequest, code: Int, vararg headers: Pair<String, String>): Response {
    val builder = Response.Builder()
      .request(request.original)
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message("Test")
      .body(ByteArray(0).toResponseBody("application/octet-stream".toMediaType()))

    for ((name, value) in headers) {
      builder.header(name, value)
    }

    return builder.build()
  }

  private fun restClient(): SignalRestClient {
    val recordingClient = OkHttpClient.Builder()
      .addInterceptor(
        Interceptor { chain ->
          val recorded = RecordedRequest.from(chain.request())
          requests += recorded
          responder(recorded)
        }
      )
      .build()

    return SignalRestClient(
      configuration = testConfiguration(),
      signalAgent = "test-agent",
      automaticNetworkRetry = false,
      socketTimeoutMillis = 1_000,
      random = Random(0),
      clientOverride = recordingClient
    )
  }

  private fun testConfiguration(): SignalServiceConfiguration {
    return SignalServiceConfiguration(
      signalServiceUrls = arrayOf(SignalServiceUrl("https://service.test", DUMMY_TRUST_STORE)),
      signalCdnUrlMap = mapOf(
        2 to arrayOf(SignalCdnUrl("https://cdn2.test", DUMMY_TRUST_STORE)),
        3 to arrayOf(SignalCdnUrl("https://cdn3.test", DUMMY_TRUST_STORE))
      ),
      signalStorageUrls = arrayOf(SignalStorageUrl("https://storage.test", DUMMY_TRUST_STORE)),
      signalCdsiUrls = emptyArray<SignalCdsiUrl>(),
      signalSvr2Urls = emptyArray<SignalSvr2Url>(),
      networkInterceptors = emptyList(),
      dns = Optional.empty(),
      signalProxy = Optional.empty(),
      zkGroupServerPublicParams = ByteArray(0),
      genericServerPublicParams = ByteArray(0),
      backupServerPublicParams = ByteArray(0),
      censored = false
    )
  }

  private class RecordedRequest(
    val original: Request,
    val method: String,
    val url: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray
  ) {
    fun header(name: String): String? = original.header(name)

    companion object {
      fun from(request: Request): RecordedRequest {
        val body = Buffer().use { buffer ->
          request.body?.writeTo(buffer)
          buffer.readByteArray()
        }
        return RecordedRequest(request, request.method, request.url.toString(), request.headers.toList(), body)
      }
    }
  }
}
