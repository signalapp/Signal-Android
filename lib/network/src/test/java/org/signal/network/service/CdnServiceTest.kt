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
    private const val CHUNK_SIZE = 256L * 1024
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
    coEvery { cdnApi.createUploadWithData(cdn3Form, "checksum", CIPHERTEXT_LENGTH, CIPHERTEXT_LENGTH, any()) } answers {
      sent = writeBody(arg(4))
      RequestResult.Success(CIPHERTEXT_LENGTH)
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
    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), any(), any(), any()) } answers {
      writeBody(arg(4))
      RequestResult.Success(CIPHERTEXT_LENGTH)
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
    coEvery { cdnApi.uploadFromOffset(2, RESUME_URL, cdn2Form.headers, 0, CIPHERTEXT_LENGTH, CIPHERTEXT_LENGTH, any()) } answers {
      sent = writeBody(arg(6))
      RequestResult.Success(CIPHERTEXT_LENGTH)
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
    coEvery { cdnApi.uploadFromOffset(3, RESUME_URL, spec.headers, offset, CIPHERTEXT_LENGTH - offset, CIPHERTEXT_LENGTH, any()) } answers {
      sent = writeBody(arg(6))
      RequestResult.Success(CIPHERTEXT_LENGTH)
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
    coVerify(exactly = 0) { cdnApi.uploadFromOffset(any(), any(), any(), any(), any(), any(), any()) }
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

    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), any(), any(), any()) } returns RequestResult.NonSuccess(CdnApi.UploadError.ChecksumMismatch)
    assertThat(service.uploadAttachment(cdn3Form, key, iv, null, attachmentStream())).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.ChecksumMismatch))

    val spec = resumableSpec(expirationTimestamp = Long.MAX_VALUE)
    coEvery { cdnApi.getUploadOffset(3, RESUME_URL, any(), any()) } returns RequestResult.NonSuccess(CdnApi.UploadError.InvalidRequest)
    assertThat(service.uploadAttachment(key = key, iv = iv, checksumSha256 = null, attachmentStream = attachmentStream(), existingSpec = spec))
      .isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.InvalidRequest))
  }

  @Test
  fun `network errors are passed through`() = runTest {
    val exception = IOException("reset")
    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), any(), any(), any()) } returns RequestResult.RetryableNetworkError(exception)

    val result = service.uploadAttachment(cdn3Form, key, iv, null, attachmentStream())

    assertThat((result as RequestResult.RetryableNetworkError).networkError).isSameInstanceAs(exception)
  }

  @Test
  fun `fresh CDN3 backup file reports its resume url before uploading`() = runTest {
    val backup = Util.getSecretBytes(1_000)
    var sent: ByteArray? = null
    coEvery { cdnApi.createUploadWithData(cdn3Form, "checksum", 1_000, 1_000, any()) } answers {
      sent = writeBody(arg(4))
      RequestResult.Success(1_000L)
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
    coEvery { cdnApi.uploadFromOffset(3, RESUME_URL, cdn3Form.headers, 400, 600, 1_000, any()) } answers {
      sent = writeBody(arg(6))
      RequestResult.Success(1_000L)
    }

    val result = service.uploadBackupFile(cdn3Form, ByteArrayInputStream(backup), 1_000, existingResumeUrl = RESUME_URL)

    assertThat(result).isEqualTo(RequestResult.Success(Unit))
    assertThat(sent!!.toList()).isEqualTo(backup.copyOfRange(400, 1_000).toList())
    coVerify(exactly = 0) { cdnApi.createUploadWithData(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `uploadAttachmentBlocking returns the digest on success`() {
    val spec = resumableSpec(expirationTimestamp = Long.MAX_VALUE)
    coEvery { cdnApi.getUploadOffset(3, RESUME_URL, spec.headers, CIPHERTEXT_LENGTH) } returns RequestResult.Success(0L)
    coEvery { cdnApi.uploadFromOffset(3, RESUME_URL, spec.headers, 0, CIPHERTEXT_LENGTH, CIPHERTEXT_LENGTH, any()) } answers {
      writeBody(arg(6))
      RequestResult.Success(CIPHERTEXT_LENGTH)
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
    coEvery { cdnApi.uploadFromOffset(2, RESUME_URL, cdn2Form.headers, 0, 1_000, 1_000, any()) } answers {
      sent = writeBody(arg(6))
      RequestResult.Success(1_000L)
    }
    var resumeUrl: String? = null

    val result = service.uploadBackupFile(cdn2Form, ByteArrayInputStream(backup), 1_000, "checksum", onResumeUrlCreated = { resumeUrl = it })

    assertThat(result).isEqualTo(RequestResult.Success(Unit))
    assertThat(resumeUrl).isEqualTo(RESUME_URL)
    assertThat(sent!!.toList()).isEqualTo(backup.toList())
    coVerify(exactly = 0) { cdnApi.createUploadWithData(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `chunking splits a fresh CDN3 upload into a creation request followed by PATCHes`() = runTest {
    val chunked = CdnService(cdnApi, attachmentApi) { CHUNK_SIZE }
    val backup = Util.getSecretBytes((CHUNK_SIZE * 4).toInt())
    val sent = Buffer()
    coEvery { cdnApi.createUploadWithData(cdn3Form, "checksum", CHUNK_SIZE, backup.size.toLong(), any()) } answers {
      sent.write(writeBody(arg(4)))
      RequestResult.Success(CHUNK_SIZE)
    }
    val offsets = mutableListOf<Long>()
    coEvery { cdnApi.uploadFromOffset(3, "https://upload.test/tus/cdn-key", cdn3Form.headers, any(), CHUNK_SIZE, backup.size.toLong(), any()) } answers {
      offsets += arg<Long>(3)
      sent.write(writeBody(arg(6)))
      RequestResult.Success(arg<Long>(3) + CHUNK_SIZE)
    }

    val result = chunked.uploadBackupFile(cdn3Form, ByteArrayInputStream(backup), backup.size.toLong(), "checksum")

    assertThat(result).isEqualTo(RequestResult.Success(Unit))
    assertThat(offsets).isEqualTo(listOf(CHUNK_SIZE, CHUNK_SIZE * 2, CHUNK_SIZE * 3))
    assertThat(sent.readByteArray().toList()).isEqualTo(backup.toList())
  }

  @Test
  fun `chunking a CDN2 upload rounds the chunk size down to what CDN2 accepts`() = runTest {
    val chunked = CdnService(cdnApi, attachmentApi) { CHUNK_SIZE + 1_000 }
    val backup = Util.getSecretBytes(1_000_000)
    val length = backup.size.toLong()
    coEvery { cdnApi.createResumableUpload(cdn2Form, null) } returns RequestResult.Success(RESUME_URL)
    coEvery { cdnApi.getUploadOffset(2, RESUME_URL, cdn2Form.headers, length) } returns RequestResult.Success(0L)
    val chunks = mutableListOf<Pair<Long, Long>>()
    val sent = Buffer()
    coEvery { cdnApi.uploadFromOffset(2, RESUME_URL, cdn2Form.headers, any(), any(), length, any()) } answers {
      chunks += arg<Long>(3) to arg<Long>(4)
      sent.write(writeBody(arg(6)))
      RequestResult.Success(arg<Long>(3) + arg<Long>(4))
    }

    val result = chunked.uploadBackupFile(cdn2Form, ByteArrayInputStream(backup), length)

    assertThat(result).isEqualTo(RequestResult.Success(Unit))
    assertThat(chunks).isEqualTo(listOf(0L to CHUNK_SIZE, CHUNK_SIZE to CHUNK_SIZE, CHUNK_SIZE * 2 to CHUNK_SIZE, CHUNK_SIZE * 3 to length - CHUNK_SIZE * 3))
    assertThat(sent.readByteArray().toList()).isEqualTo(backup.toList())
  }

  @Test
  fun `resuming with chunking sends the rest in chunks starting at the CDN offset`() = runTest {
    val chunked = CdnService(cdnApi, attachmentApi) { CHUNK_SIZE }
    val backup = Util.getSecretBytes((CHUNK_SIZE * 2).toInt())
    val length = backup.size.toLong()
    val resumeOffset = 100_000L
    coEvery { cdnApi.getUploadOffset(3, RESUME_URL, cdn3Form.headers, length) } returns RequestResult.Success(resumeOffset)
    val chunks = mutableListOf<Pair<Long, Long>>()
    val sent = Buffer()
    coEvery { cdnApi.uploadFromOffset(3, RESUME_URL, cdn3Form.headers, any(), any(), length, any()) } answers {
      chunks += arg<Long>(3) to arg<Long>(4)
      sent.write(writeBody(arg(6)))
      RequestResult.Success(arg<Long>(3) + arg<Long>(4))
    }

    val result = chunked.uploadBackupFile(cdn3Form, ByteArrayInputStream(backup), length, existingResumeUrl = RESUME_URL)

    assertThat(result).isEqualTo(RequestResult.Success(Unit))
    assertThat(chunks).isEqualTo(listOf(resumeOffset to CHUNK_SIZE, resumeOffset + CHUNK_SIZE to length - resumeOffset - CHUNK_SIZE))
    assertThat(sent.readByteArray().toList()).isEqualTo(backup.copyOfRange(resumeOffset.toInt(), backup.size).toList())
  }

  @Test
  fun `a chunk the CDN only partly persisted stops the upload as retryable`() = runTest {
    val chunked = CdnService(cdnApi, attachmentApi) { CHUNK_SIZE }
    val backup = Util.getSecretBytes((CHUNK_SIZE * 3).toInt())
    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), any(), any(), any()) } answers {
      writeBody(arg(4))
      RequestResult.Success(CHUNK_SIZE - 10)
    }

    val result = chunked.uploadBackupFile(cdn3Form, ByteArrayInputStream(backup), backup.size.toLong())

    assertThat(result).isInstanceOf<RequestResult.RetryableNetworkError>()
    coVerify(exactly = 0) { cdnApi.uploadFromOffset(any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a failed chunk stops the upload with that chunk's error`() = runTest {
    val chunked = CdnService(cdnApi, attachmentApi) { CHUNK_SIZE }
    val backup = Util.getSecretBytes((CHUNK_SIZE * 3).toInt())
    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), any(), any(), any()) } answers {
      writeBody(arg(4))
      RequestResult.Success(CHUNK_SIZE)
    }
    coEvery { cdnApi.uploadFromOffset(3, any(), any(), CHUNK_SIZE, any(), any(), any()) } returns RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(5.seconds))

    val result = chunked.uploadBackupFile(cdn3Form, ByteArrayInputStream(backup), backup.size.toLong())

    assertThat(result).isEqualTo(RequestResult.NonSuccess(CdnApi.UploadError.RateLimited(5.seconds)))
    coVerify(exactly = 1) { cdnApi.uploadFromOffset(any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a chunked attachment has the same ciphertext and digest as an unchunked one`() = runTest {
    val largePlaintext = Util.getSecretBytes(700_000)
    val largeLength = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(largePlaintext.size.toLong()))
    val largeExpected = encryptFully(largePlaintext)
    val chunked = CdnService(cdnApi, attachmentApi) { CHUNK_SIZE }
    val sent = Buffer()
    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), CHUNK_SIZE, largeLength, any()) } answers {
      sent.write(writeBody(arg(4)))
      RequestResult.Success(CHUNK_SIZE)
    }
    coEvery { cdnApi.uploadFromOffset(3, any(), any(), any(), any(), largeLength, any()) } answers {
      sent.write(writeBody(arg(6)))
      RequestResult.Success(arg<Long>(3) + arg<Long>(4))
    }

    val result = chunked.uploadAttachment(cdn3Form, key, iv, null, attachmentStream(largePlaintext))

    assertThat((result as RequestResult.Success).result.digest.toList()).isEqualTo(largeExpected.digest.toList())
    assertThat(sent.readByteArray().toList()).isEqualTo(largeExpected.ciphertext.toList())
    coVerify(exactly = 2) { cdnApi.uploadFromOffset(any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `chunk sizes below the minimum, including zero and negative, are raised to the minimum`() = runTest {
    listOf(0L, -1L, 1_000L).forEach { configured ->
      val chunked = CdnService(cdnApi, attachmentApi) { configured }
      val backup = Util.getSecretBytes((CHUNK_SIZE * 2).toInt())
      val length = backup.size.toLong()
      val chunkLengths = mutableListOf<Long>()
      coEvery { cdnApi.createUploadWithData(cdn3Form, any(), any(), length, any()) } answers {
        chunkLengths += arg<Long>(2)
        writeBody(arg(4))
        RequestResult.Success(arg<Long>(2))
      }
      coEvery { cdnApi.uploadFromOffset(3, any(), any(), any(), any(), length, any()) } answers {
        chunkLengths += arg<Long>(4)
        writeBody(arg(6))
        RequestResult.Success(arg<Long>(3) + arg<Long>(4))
      }

      val result = chunked.uploadBackupFile(cdn3Form, ByteArrayInputStream(backup), length)

      assertThat(result).isEqualTo(RequestResult.Success(Unit))
      assertThat(chunkLengths).isEqualTo(listOf(CHUNK_SIZE, CHUNK_SIZE))
    }
  }

  @Test
  fun `data no larger than the chunk size is sent in one request`() = runTest {
    val chunked = CdnService(cdnApi, attachmentApi) { CHUNK_SIZE }
    coEvery { cdnApi.createUploadWithData(cdn3Form, any(), CIPHERTEXT_LENGTH, CIPHERTEXT_LENGTH, any()) } answers {
      writeBody(arg(4))
      RequestResult.Success(CIPHERTEXT_LENGTH)
    }

    val result = chunked.uploadAttachment(cdn3Form, key, iv, null, attachmentStream())

    assertThat(result).isInstanceOf<RequestResult.Success<*>>()
    coVerify(exactly = 0) { cdnApi.uploadFromOffset(any(), any(), any(), any(), any(), any(), any()) }
  }

  private fun writeBody(body: RequestBody): ByteArray {
    return Buffer().use { buffer ->
      body.writeTo(buffer)
      buffer.readByteArray()
    }
  }

  private fun attachmentStream(data: ByteArray = plaintext, audioHash: String? = null, blurHash: String? = null): SignalServiceAttachmentStream {
    return SignalServiceAttachment.newStreamBuilder()
      .withStream(ByteArrayInputStream(data))
      .withContentType("application/octet-stream")
      .withLength(data.size.toLong())
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

  private fun encryptFully(data: ByteArray = plaintext): Encrypted {
    val body = DigestingRequestBody(
      PaddingInputStream(ByteArrayInputStream(data), data.size.toLong()),
      AttachmentCipherOutputStreamFactory(key, iv),
      "application/octet-stream",
      AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(data.size.toLong())),
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
