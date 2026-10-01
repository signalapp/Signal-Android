/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.RequestBody
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import okio.Buffer
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.UploadTooLargeException
import org.signal.network.api.AttachmentApi
import org.signal.network.api.CdnApi
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.PushAttachmentData
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.push.http.DigestingRequestBody
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import org.whispersystems.signalservice.internal.util.Util
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class CdnServiceTest {

  companion object {
    private const val PLAINTEXT_LENGTH = 50_000L
    private val CIPHERTEXT_LENGTH = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(PLAINTEXT_LENGTH))
    private const val RESUME_URL = "https://upload.test/resumable/abc"
  }

  private val cdnApi: CdnApi = mockk()
  private val attachmentApi: AttachmentApi = mockk()
  private val service = CdnService(cdnApi, attachmentApi)

  private val key = Util.getSecretBytes(64)
  private val iv = Util.getSecretBytes(16)
  private val plaintext = Util.getSecretBytes(PLAINTEXT_LENGTH.toInt())
  private val expected = encryptFully()

  private val cdn3Form = AttachmentUploadForm(cdn = 3, key = "cdn-key", headers = mapOf("Authorization" to "Bearer token"), signedUploadLocation = "https://upload.test/tus")
  private val cdn2Form = AttachmentUploadForm(cdn = 2, key = "cdn-key", headers = mapOf("Authorization" to "Bearer token"), signedUploadLocation = "https://upload.test/signed")

  @Test
  fun `fresh CDN3 attachment is created with its data and the resume spec is reported first`() = runTest {
    var sent: ByteArray? = null
    coEvery { cdnApi.createUploadWithData(cdn3Form, "checksum", CIPHERTEXT_LENGTH, any()) } answers {
      sent = writeBody(arg(3))
      RequestResult.Success(Unit)
    }
    var createdSpec: ResumableUploadSpec? = null

    val result = service.uploadAttachment(cdn3Form, key, iv, "checksum", attachmentStream(), onSpecCreated = { createdSpec = it })

    assertThat(result).isInstanceOf<RequestResult.Success<*>>()
    val upload = (result as RequestResult.Success).result
    assertThat(upload.cdnNumber).isEqualTo(3)
    assertThat(upload.remoteId.toString()).isEqualTo("cdn-key")
    assertThat(upload.digest.toList()).isEqualTo(expected.digest.toList())
    assertThat(upload.dataSize).isEqualTo(PLAINTEXT_LENGTH)
    assertThat(sent!!.toList()).isEqualTo(expected.ciphertext.toList())

    assertThat(createdSpec).isNotNull()
    assertThat(createdSpec!!.resumeLocation).isEqualTo("https://upload.test/tus/cdn-key")
    assertThat(createdSpec!!.cdnNumber).isEqualTo(3)
    assertThat(createdSpec!!.headers).isEqualTo(cdn3Form.headers)
  }

  @Test
  fun `upload result carries the attachment's blur and audio hashes`() = runTest {
    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), any(), any()) } answers {
      writeBody(arg(3))
      RequestResult.Success(Unit)
    }

    val result = service.uploadAttachment(cdn3Form, key, iv, null, attachmentStream(audioHash = "audio-hash", blurHash = "blur-hash"))

    val upload = (result as RequestResult.Success).result
    assertThat(upload.audioHash).isEqualTo("audio-hash")
    assertThat(upload.blurHash).isEqualTo("blur-hash")
  }

  @Test
  fun `fresh CDN2 attachment creates a resumable upload and then sends the data`() = runTest {
    coEvery { cdnApi.createResumableUpload(cdn2Form, "checksum") } returns RequestResult.Success(RESUME_URL)
    coEvery { cdnApi.getUploadOffset(2, RESUME_URL, cdn2Form.headers, CIPHERTEXT_LENGTH) } returns RequestResult.Success(0L)
    var sent: ByteArray? = null
    coEvery { cdnApi.uploadFromOffset(2, RESUME_URL, cdn2Form.headers, 0, CIPHERTEXT_LENGTH, any()) } answers {
      sent = writeBody(arg(5))
      RequestResult.Success(Unit)
    }
    var createdSpec: ResumableUploadSpec? = null

    val result = service.uploadAttachment(cdn2Form, key, iv, "checksum", attachmentStream(), onSpecCreated = { createdSpec = it })

    assertThat((result as RequestResult.Success).result.digest.toList()).isEqualTo(expected.digest.toList())
    assertThat(sent!!.toList()).isEqualTo(expected.ciphertext.toList())
    assertThat(createdSpec!!.resumeLocation).isEqualTo(RESUME_URL)
  }

  @Test
  fun `resuming an attachment sends only the bytes after the CDN offset`() = runTest {
    val offset = 12_345L
    val spec = resumableSpec(expirationTimestamp = Long.MAX_VALUE)
    coEvery { cdnApi.getUploadOffset(3, RESUME_URL, spec.headers, CIPHERTEXT_LENGTH) } returns RequestResult.Success(offset)
    var sent: ByteArray? = null
    coEvery { cdnApi.uploadFromOffset(3, RESUME_URL, spec.headers, offset, CIPHERTEXT_LENGTH, any()) } answers {
      sent = writeBody(arg(5))
      RequestResult.Success(Unit)
    }

    val result = service.uploadAttachment(key = key, iv = iv, checksumSha256 = null, attachmentStream = attachmentStream(), existingSpec = spec)

    assertThat((result as RequestResult.Success).result.digest.toList()).isEqualTo(expected.digest.toList())
    assertThat(sent!!.toList()).isEqualTo(expected.ciphertext.copyOfRange(offset.toInt(), expected.ciphertext.size).toList())
  }

  @Test
  fun `resuming an attachment the CDN already has skips the upload and still returns the digest`() = runTest {
    val spec = resumableSpec(expirationTimestamp = Long.MAX_VALUE)
    coEvery { cdnApi.getUploadOffset(3, RESUME_URL, spec.headers, CIPHERTEXT_LENGTH) } returns RequestResult.Success(CIPHERTEXT_LENGTH)

    val result = service.uploadAttachment(key = key, iv = iv, checksumSha256 = null, attachmentStream = attachmentStream(), existingSpec = spec)

    assertThat((result as RequestResult.Success).result.digest.toList()).isEqualTo(expected.digest.toList())
    coVerify(exactly = 0) { cdnApi.uploadFromOffset(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `resuming an expired spec reports an invalid resume location without making a request`() = runTest {
    val result = service.uploadAttachment(key = key, iv = iv, checksumSha256 = null, attachmentStream = attachmentStream(), existingSpec = resumableSpec(expirationTimestamp = 0))

    assertThat(result).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid))
    coVerify(exactly = 0) { cdnApi.getUploadOffset(any(), any(), any(), any()) }
  }

  @Test
  fun `uploading without a form or spec is an ApplicationError`() = runTest {
    val result = service.uploadAttachment(key = key, iv = iv, checksumSha256 = null, attachmentStream = attachmentStream())

    assertThat(result).isInstanceOf<RequestResult.ApplicationError>()
  }

  @Test
  fun `api errors are mapped to upload errors`() = runTest {
    coEvery { cdnApi.createResumableUpload(cdn2Form, any()) } returns RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(5.seconds))
    assertThat(service.uploadAttachment(cdn2Form, key, iv, null, attachmentStream())).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(5.seconds)))

    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), any(), any()) } returns RequestResult.NonSuccess(CdnApi.UploadError.ChecksumMismatch)
    assertThat(service.uploadAttachment(cdn3Form, key, iv, null, attachmentStream())).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ChecksumMismatch))

    val spec = resumableSpec(expirationTimestamp = Long.MAX_VALUE)
    coEvery { cdnApi.getUploadOffset(3, RESUME_URL, any(), any()) } returns RequestResult.NonSuccess(CdnApi.UploadError.InvalidRequest)
    assertThat(service.uploadAttachment(key = key, iv = iv, checksumSha256 = null, attachmentStream = attachmentStream(), existingSpec = spec))
      .isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.InvalidRequest))
  }

  @Test
  fun `network errors are passed through`() = runTest {
    val exception = IOException("reset")
    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), any(), any()) } returns RequestResult.RetryableNetworkError(exception)

    val result = service.uploadAttachment(cdn3Form, key, iv, null, attachmentStream())

    assertThat((result as RequestResult.RetryableNetworkError).networkError).isSameInstanceAs(exception)
  }

  @Test
  fun `fresh CDN3 backup file reports its resume url before uploading`() = runTest {
    val backup = Util.getSecretBytes(1_000)
    var sent: ByteArray? = null
    coEvery { cdnApi.createUploadWithData(cdn3Form, "checksum", 1_000, any()) } answers {
      sent = writeBody(arg(3))
      RequestResult.Success(Unit)
    }
    var resumeUrl: String? = null

    val result = service.uploadBackupFile(cdn3Form, ByteArrayInputStream(backup), 1_000, "checksum", onResumeUrlCreated = { resumeUrl = it })

    assertThat(result).isEqualTo(RequestResult.Success(Unit))
    assertThat(resumeUrl).isEqualTo("https://upload.test/tus/cdn-key")
    assertThat(sent!!.toList()).isEqualTo(backup.toList())
  }

  @Test
  fun `resumed backup file uses the existing url and form headers`() = runTest {
    val backup = Util.getSecretBytes(1_000)
    coEvery { cdnApi.getUploadOffset(3, RESUME_URL, cdn3Form.headers, 1_000) } returns RequestResult.Success(400L)
    var sent: ByteArray? = null
    coEvery { cdnApi.uploadFromOffset(3, RESUME_URL, cdn3Form.headers, 400, 1_000, any()) } answers {
      sent = writeBody(arg(5))
      RequestResult.Success(Unit)
    }

    val result = service.uploadBackupFile(cdn3Form, ByteArrayInputStream(backup), 1_000, existingResumeUrl = RESUME_URL)

    assertThat(result).isEqualTo(RequestResult.Success(Unit))
    assertThat(sent!!.toList()).isEqualTo(backup.copyOfRange(400, 1_000).toList())
    coVerify(exactly = 0) { cdnApi.createUploadWithData(any(), any(), any(), any()) }
  }

  @Test
  fun `uploadAttachmentBlocking returns the digest on success`() {
    val spec = resumableSpec(expirationTimestamp = Long.MAX_VALUE)
    coEvery { cdnApi.getUploadOffset(3, RESUME_URL, spec.headers, CIPHERTEXT_LENGTH) } returns RequestResult.Success(0L)
    coEvery { cdnApi.uploadFromOffset(3, RESUME_URL, spec.headers, 0, CIPHERTEXT_LENGTH, any()) } answers {
      writeBody(arg(5))
      RequestResult.Success(Unit)
    }

    val digest = service.uploadAttachmentBlocking(pushAttachmentData(spec))

    assertThat(digest.digest.toList()).isEqualTo(expected.digest.toList())
  }

  @Test
  fun `uploadAttachmentBlocking throws the exceptions sync jobs expect`() {
    val spec = resumableSpec(expirationTimestamp = Long.MAX_VALUE)

    coEvery { cdnApi.getUploadOffset(any(), any(), any(), any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))
    assertFailure { service.uploadAttachmentBlocking(pushAttachmentData(spec)) }.isInstanceOf<PushNetworkException>()

    val reset = StreamResetException(ErrorCode.REFUSED_STREAM)
    coEvery { cdnApi.getUploadOffset(any(), any(), any(), any()) } returns RequestResult.RetryableNetworkError(reset)
    assertFailure { service.uploadAttachmentBlocking(pushAttachmentData(spec)) }.isSameInstanceAs(reset)

    coEvery { cdnApi.getUploadOffset(any(), any(), any(), any()) } returns RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid)
    assertFailure { service.uploadAttachmentBlocking(pushAttachmentData(spec)) }.isInstanceOf<ResumeLocationInvalidException>()

    coEvery { cdnApi.getUploadOffset(any(), any(), any(), any()) } returns RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(null))
    assertFailure { service.uploadAttachmentBlocking(pushAttachmentData(spec)) }
      .isInstanceOf<NonSuccessfulResponseCodeException>()
      .prop(NonSuccessfulResponseCodeException::code)
      .isEqualTo(429)
  }

  @Test
  fun `getResumableUploadSpec builds a spec from the form and resumable url`() = runTest {
    every { attachmentApi.getAttachmentV4UploadForm(100) } returns RequestResult.Success(cdn2Form)
    coEvery { cdnApi.createResumableUpload(cdn2Form, null) } returns RequestResult.Success(RESUME_URL)

    val result = service.getResumableUploadSpec(100)

    val spec = (result as CdnService.ResumableUploadSpecResult.Success).spec
    assertThat(spec.resumeLocation).isEqualTo(RESUME_URL)
    assertThat(spec.cdnNumber).isEqualTo(2)
    assertThat(spec.cdnKey).isEqualTo("cdn-key")
  }

  @Test
  fun `getResumableUploadSpec reports a rejected resumable url request`() = runTest {
    every { attachmentApi.getAttachmentV4UploadForm(100) } returns RequestResult.Success(cdn2Form)
    coEvery { cdnApi.createResumableUpload(cdn2Form, null) } returns RequestResult.NonSuccess(CdnApi.UploadError.InvalidRequest)

    val result = service.getResumableUploadSpec(100)

    assertThat(result).isEqualTo(CdnService.ResumableUploadSpecResult.UploadUrlStatusError(CdnApi.UploadError.InvalidRequest))
  }

  @Test
  fun `getResumableUploadSpecBlocking throws the exceptions sync jobs expect`() {
    val tooLarge = UploadTooLargeException("too large")
    every { attachmentApi.getAttachmentV4UploadForm(100) } returns RequestResult.NonSuccess(tooLarge)
    assertFailure { service.getResumableUploadSpecBlocking(100) }.isSameInstanceAs(tooLarge)

    every { attachmentApi.getAttachmentV4UploadForm(100) } returns RequestResult.Success(cdn2Form)
    coEvery { cdnApi.createResumableUpload(cdn2Form, null) } returns RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(5.seconds))
    assertFailure { service.getResumableUploadSpecBlocking(100) }
      .isInstanceOf<NonSuccessfulResponseCodeException>()
      .prop(NonSuccessfulResponseCodeException::code)
      .isEqualTo(429)

    coEvery { cdnApi.createResumableUpload(cdn2Form, null) } returns RequestResult.RetryableNetworkError(IOException("offline"))
    assertFailure { service.getResumableUploadSpecBlocking(100) }.isInstanceOf<PushNetworkException>()

    coEvery { cdnApi.createResumableUpload(cdn2Form, null) } returns RequestResult.Success(RESUME_URL)
    assertThat(service.getResumableUploadSpecBlocking(100).resumeLocation).isEqualTo(RESUME_URL)
  }

  @Test
  fun `fresh CDN2 backup file creates a resumable upload, reports its url, and then sends the data`() = runTest {
    val backup = Util.getSecretBytes(1_000)
    coEvery { cdnApi.createResumableUpload(cdn2Form, "checksum") } returns RequestResult.Success(RESUME_URL)
    coEvery { cdnApi.getUploadOffset(2, RESUME_URL, cdn2Form.headers, 1_000) } returns RequestResult.Success(0L)
    var sent: ByteArray? = null
    coEvery { cdnApi.uploadFromOffset(2, RESUME_URL, cdn2Form.headers, 0, 1_000, any()) } answers {
      sent = writeBody(arg(5))
      RequestResult.Success(Unit)
    }
    var resumeUrl: String? = null

    val result = service.uploadBackupFile(cdn2Form, ByteArrayInputStream(backup), 1_000, "checksum", onResumeUrlCreated = { resumeUrl = it })

    assertThat(result).isEqualTo(RequestResult.Success(Unit))
    assertThat(resumeUrl).isEqualTo(RESUME_URL)
    assertThat(sent!!.toList()).isEqualTo(backup.toList())
    coVerify(exactly = 0) { cdnApi.createUploadWithData(any(), any(), any(), any()) }
  }

  private fun writeBody(body: RequestBody): ByteArray {
    return Buffer().use { buffer ->
      body.writeTo(buffer)
      buffer.readByteArray()
    }
  }

  private fun attachmentStream(audioHash: String? = null, blurHash: String? = null): SignalServiceAttachmentStream {
    return SignalServiceAttachment.newStreamBuilder()
      .withStream(ByteArrayInputStream(plaintext))
      .withContentType("application/octet-stream")
      .withLength(PLAINTEXT_LENGTH)
      .withAudioHash(audioHash)
      .withBlurHash(blurHash)
      .build()
  }

  private fun pushAttachmentData(spec: ResumableUploadSpec): PushAttachmentData {
    return PushAttachmentData(
      contentType = "application/octet-stream",
      data = PaddingInputStream(ByteArrayInputStream(plaintext), PLAINTEXT_LENGTH),
      dataSize = CIPHERTEXT_LENGTH,
      incremental = false,
      outputStreamFactory = AttachmentCipherOutputStreamFactory(key, iv),
      listener = null,
      cancelationSignal = null,
      resumableUploadSpec = spec
    )
  }

  private fun resumableSpec(expirationTimestamp: Long): ResumableUploadSpec {
    return ResumableUploadSpec(
      attachmentKey = key,
      attachmentIv = iv,
      cdnKey = "cdn-key",
      cdnNumber = 3,
      resumeLocation = RESUME_URL,
      expirationTimestamp = expirationTimestamp,
      headers = mapOf("Authorization" to "Bearer token")
    )
  }

  private fun encryptFully(): Encrypted {
    val body = DigestingRequestBody(
      PaddingInputStream(ByteArrayInputStream(plaintext), PLAINTEXT_LENGTH),
      AttachmentCipherOutputStreamFactory(key, iv),
      "application/octet-stream",
      CIPHERTEXT_LENGTH,
      false,
      null,
      null,
      0
    )
    val ciphertext = writeBody(body)
    return Encrypted(ciphertext, body.attachmentDigest!!.digest)
  }

  private class Encrypted(val ciphertext: ByteArray, val digest: ByteArray)
}
