/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.linkdevice

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
import org.signal.network.NetworkResult
import org.signal.network.api.ArchiveApi
import org.signal.network.api.AttachmentApi
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LinkDeviceRepositoryTest {

  private val attachments = mockk<AttachmentApi>()
  private val archive = mockk<ArchiveApi>()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())
    mockkObject(SignalNetwork)
    every { SignalNetwork.attachments } returns attachments
    every { SignalNetwork.archive } returns archive
  }

  @After
  fun tearDown() {
    unmockkObject(SignalNetwork)
  }

  @Test
  fun `uploadArchive - invalid resume location fetches a fresh form with a new key`() {
    val firstForm = uploadForm(key = "key-1")
    val secondForm = uploadForm(key = "key-2")

    var fetchCount = 0
    every { attachments.getAttachmentV4UploadForm(any()) } answers {
      fetchCount++
      RequestResult.Success(if (fetchCount == 1) firstForm else secondForm)
    }
    every {
      archive.uploadBackupFile(uploadForm = firstForm, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = any(), onResumeUrlCreated = any())
    } returns NetworkResult.NetworkError(ResumeLocationInvalidException())
    every {
      archive.uploadBackupFile(uploadForm = secondForm, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = any(), onResumeUrlCreated = any())
    } returns NetworkResult.Success(Unit)

    val result = LinkDeviceRepository.uploadArchive(tempBackupFile())

    assertTrue(result is NetworkResult.Success)
    assertEquals(secondForm, (result as NetworkResult.Success).result)
    verify(exactly = 2) { attachments.getAttachmentV4UploadForm(any()) }
    verify(exactly = 1) {
      archive.uploadBackupFile(uploadForm = secondForm, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = null, onResumeUrlCreated = any())
    }
  }

  @Test
  fun `uploadArchive - ordinary network error resumes with the same form and resume url`() {
    val form = uploadForm(key = "key-1")

    every { attachments.getAttachmentV4UploadForm(any()) } returns RequestResult.Success(form)

    every {
      archive.uploadBackupFile(uploadForm = form, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = null, onResumeUrlCreated = any())
    } answers {
      lastArg<((String) -> Unit)?>()?.invoke("resume-1")
      NetworkResult.NetworkError(IOException("flaky connection"))
    }
    every {
      archive.uploadBackupFile(uploadForm = form, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = "resume-1", onResumeUrlCreated = any())
    } returns NetworkResult.Success(Unit)

    val result = LinkDeviceRepository.uploadArchive(tempBackupFile())

    assertTrue(result is NetworkResult.Success)
    assertEquals(form, (result as NetworkResult.Success).result)
    verify(exactly = 1) { attachments.getAttachmentV4UploadForm(any()) }
    verify(exactly = 1) {
      archive.uploadBackupFile(uploadForm = form, data = any(), dataLength = any(), checksumSha256 = any(), progressListener = any(), existingResumeUrl = "resume-1", onResumeUrlCreated = any())
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
