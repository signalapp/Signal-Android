/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.linkdevice

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.AttachmentApi
import org.signal.network.api.CdnApi
import org.signal.network.service.CdnService
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LinkDeviceRepositoryTest {

  private val attachments = mockk<AttachmentApi>()
  private val cdn = mockk<CdnService>()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    val signalNetwork = mockk<SignalNetwork>()
    every { signalNetwork.attachmentApi } returns attachments
    every { signalNetwork.cdnService } returns cdn

    SignalNetwork.init(signalNetwork)
  }

  @After
  fun tearDown() {
    SignalNetwork.init(SignalNetwork())
  }

  @Test
  fun `uploadArchive - invalid resume location fetches a fresh form with a new key`() = runTest {
    val firstForm = uploadForm(key = "key-1")
    val secondForm = uploadForm(key = "key-2")

    var fetchCount = 0
    every { attachments.getAttachmentV4UploadForm(any()) } answers {
      fetchCount++
      RequestResult.Success(if (fetchCount == 1) firstForm else secondForm)
    }
    coEvery {
      cdn.uploadBackupFile(uploadForm = firstForm, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = any(), onResumeUrlCreated = any())
    } returns RequestResult.NonSuccess(CdnApi.UploadError.ResumeLocationInvalid)
    coEvery {
      cdn.uploadBackupFile(uploadForm = secondForm, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = any(), onResumeUrlCreated = any())
    } returns RequestResult.Success(Unit)

    val result = LinkDeviceRepository.uploadArchive(tempBackupFile())

    assertTrue(result is RequestResult.Success)
    assertEquals(secondForm, (result as RequestResult.Success).result)
    verify(exactly = 2) { attachments.getAttachmentV4UploadForm(any()) }
    coVerify(exactly = 1) {
      cdn.uploadBackupFile(uploadForm = secondForm, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = null, onResumeUrlCreated = any())
    }
  }

  @Test
  fun `uploadArchive - ordinary network error resumes with the same form and resume url`() = runTest {
    val form = uploadForm(key = "key-1")

    every { attachments.getAttachmentV4UploadForm(any()) } returns RequestResult.Success(form)

    coEvery {
      cdn.uploadBackupFile(uploadForm = form, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = null, onResumeUrlCreated = any())
    } answers {
      arg<((String) -> Unit)?>(6)?.invoke("resume-1")
      RequestResult.RetryableNetworkError(IOException("flaky connection"))
    }
    coEvery {
      cdn.uploadBackupFile(uploadForm = form, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = "resume-1", onResumeUrlCreated = any())
    } returns RequestResult.Success(Unit)

    val result = LinkDeviceRepository.uploadArchive(tempBackupFile())

    assertTrue(result is RequestResult.Success)
    assertEquals(form, (result as RequestResult.Success).result)
    verify(exactly = 1) { attachments.getAttachmentV4UploadForm(any()) }
    coVerify(exactly = 1) {
      cdn.uploadBackupFile(uploadForm = form, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = "resume-1", onResumeUrlCreated = any())
    }
  }

  @Test
  fun `uploadArchive - rejected upload is returned without retrying`() = runTest {
    val form = uploadForm(key = "key-1")

    every { attachments.getAttachmentV4UploadForm(any()) } returns RequestResult.Success(form)
    coEvery {
      cdn.uploadBackupFile(uploadForm = form, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = any(), onResumeUrlCreated = any())
    } returns RequestResult.NonSuccess(CdnApi.UploadError.TooLarge)

    val result = LinkDeviceRepository.uploadArchive(tempBackupFile())

    assertEquals(RequestResult.NonSuccess(CdnApi.UploadError.TooLarge), result)
    coVerify(exactly = 1) {
      cdn.uploadBackupFile(uploadForm = form, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = any(), onResumeUrlCreated = any())
    }
  }

  private fun uploadForm(key: String): AttachmentUploadForm {
    return AttachmentUploadForm(cdn = 3, key = key, headers = emptyMap(), signedUploadLocation = "https://example.com/$key")
  }

  private fun tempBackupFile(): File {
    return File.createTempFile("link-archive-test", ".bin").apply {
      writeBytes(ByteArray(64) { it.toByte() })
      deleteOnExit()
    }
  }
}
