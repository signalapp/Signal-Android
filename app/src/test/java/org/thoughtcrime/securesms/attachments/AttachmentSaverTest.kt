/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import android.app.Application
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.attachments.AttachmentSaver.Host
import org.thoughtcrime.securesms.attachments.AttachmentSaver.RequestPermissionResult
import org.thoughtcrime.securesms.attachments.AttachmentSaver.SaveToStorageWarningResult
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.UiHintValues
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.util.SaveAttachmentUtil
import org.thoughtcrime.securesms.util.SaveAttachmentUtil.SaveAttachmentsResult
import org.thoughtcrime.securesms.util.StorageUtil

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentSaverTest {
  private val testDispatcher = StandardTestDispatcher()

  @get:Rule
  val coroutineDispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val testAttachments: Set<SaveAttachmentUtil.SaveAttachment> = setOf(
    SaveAttachmentUtil.SaveAttachment(
      uri = Uri.parse("content://org.thoughtcrime.securesms/part/111"),
      contentType = "image/jpeg",
      date = 1742234803832,
      fileName = null
    ),

    SaveAttachmentUtil.SaveAttachment(
      uri = Uri.parse("content://org.thoughtcrime.securesms/part/222"),
      contentType = "image/jpeg",
      date = 1742234384758,
      fileName = null
    )
  )

  private fun setUpTestEnvironment(
    hasDismissedSaveStorageWarning: Boolean,
    canWriteToMediaStore: Boolean,
    saveWarningResult: SaveToStorageWarningResult = SaveToStorageWarningResult.ACCEPTED,
    writeExternalStoragePermissionResult: RequestPermissionResult = RequestPermissionResult.GRANTED,
    saveAttachmentsResult: SaveAttachmentsResult = SaveAttachmentsResult.Success(successesCount = 2)
  ): TestEnvironment {
    val host = mockk<Host>(relaxUnitFun = true) {
      coEvery { showSaveToStorageWarning(attachmentCount = any()) } returns saveWarningResult
      coEvery { requestWriteExternalStoragePermission() } returns writeExternalStoragePermissionResult
    }

    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns hasDismissedSaveStorageWarning
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkStatic(StorageUtil::class)
    every { StorageUtil.canWriteToMediaStore() } returns canWriteToMediaStore

    mockkObject(SaveAttachmentUtil)
    coEvery { SaveAttachmentUtil.saveAttachments(any()) } returns saveAttachmentsResult

    return TestEnvironment(
      host = host,
      uiHints = uiHints
    )
  }

  @After
  fun tearDown() {
    unmockkObject(SignalStore)
    unmockkObject(SaveAttachmentUtil)
    unmockkStatic(StorageUtil::class)
  }

  @Test
  fun `saveAttachments shows save to storage warning when it has not been dismissed`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = false,
      canWriteToMediaStore = true
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerify { testEnv.host.showSaveToStorageWarning(attachmentCount = 2) }
  }

  @Test
  fun `saveAttachments does not show save to storage warning when it has been dismissed`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = true
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerify(exactly = 0) { testEnv.host.showSaveToStorageWarning(attachmentCount = any()) }
  }

  @Test
  fun `saveAttachments requests WRITE_EXTERNAL_STORAGE permission when not yet granted and save to storage warning has been dismissed`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = false
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerify { testEnv.host.requestWriteExternalStoragePermission() }
  }

  @Test
  fun `saveAttachments requests WRITE_EXTERNAL_STORAGE permission when not yet granted and save to storage warning is accepted`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = false,
      canWriteToMediaStore = false,
      saveWarningResult = SaveToStorageWarningResult.ACCEPTED
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerify { testEnv.host.requestWriteExternalStoragePermission() }
  }

  @Test
  fun `saveAttachments does not request WRITE_EXTERNAL_STORAGE permission when not yet granted and save to storage warning is denied`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = false,
      canWriteToMediaStore = false,
      saveWarningResult = SaveToStorageWarningResult.DENIED
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerify(exactly = 0) { testEnv.host.requestWriteExternalStoragePermission() }
  }

  @Test
  fun `saveAttachments does not request WRITE_EXTERNAL_STORAGE permission when canWriteToMediaStore = true`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = true
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerify(exactly = 0) { testEnv.host.requestWriteExternalStoragePermission() }
  }

  @Test
  fun `saveAttachments does not perform save when save to storage warning is denied`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = false,
      canWriteToMediaStore = true,
      saveWarningResult = SaveToStorageWarningResult.DENIED
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerify(exactly = 0) {
      SaveAttachmentUtil.saveAttachments(attachments = any())
      testEnv.host.showSaveProgress(any())
      testEnv.host.showSaveResult(SaveAttachmentsResult.Success(successesCount = 2))
      testEnv.host.dismissSaveProgress()
    }
  }

  @Test
  fun `saveAttachments does not perform save when WRITE_EXTERNAL_STORAGE permission is denied`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = false,
      canWriteToMediaStore = false,
      writeExternalStoragePermissionResult = RequestPermissionResult.DENIED
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerify(exactly = 0) {
      SaveAttachmentUtil.saveAttachments(attachments = any())
      testEnv.host.showSaveProgress(any())
      testEnv.host.dismissSaveProgress()
    }
    verify { testEnv.host.showSaveResult(SaveAttachmentsResult.WriteStoragePermissionDenied) }
  }

  @Test
  fun `saveAttachments performs save when save storage warning is accepted and WRITE_EXTERNAL_STORAGE permission is granted`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = false,
      canWriteToMediaStore = false,
      saveWarningResult = SaveToStorageWarningResult.ACCEPTED,
      writeExternalStoragePermissionResult = RequestPermissionResult.GRANTED
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerifyOrder {
      testEnv.host.showSaveProgress(attachmentCount = 2)
      SaveAttachmentUtil.saveAttachments(attachments = testAttachments)
      testEnv.host.showSaveResult(SaveAttachmentsResult.Success(successesCount = 2))
      testEnv.host.dismissSaveProgress()
    }
  }

  @Test
  fun `saveAttachments performs save when save storage warning is dismissed and WRITE_EXTERNAL_STORAGE permission is granted`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = false,
      writeExternalStoragePermissionResult = RequestPermissionResult.GRANTED,
      saveAttachmentsResult = SaveAttachmentsResult.Success(successesCount = 2)
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerifyOrder {
      testEnv.host.showSaveProgress(attachmentCount = 2)
      SaveAttachmentUtil.saveAttachments(attachments = testAttachments)
      testEnv.host.showSaveResult(SaveAttachmentsResult.Success(successesCount = 2))
      testEnv.host.dismissSaveProgress()
    }
  }

  @Test
  fun `saveAttachments performs save when save storage warning is accepted and canWriteToMediaStore = true`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = false,
      canWriteToMediaStore = true,
      saveWarningResult = SaveToStorageWarningResult.ACCEPTED,
      saveAttachmentsResult = SaveAttachmentsResult.Success(successesCount = 2)
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerifyOrder {
      testEnv.host.showSaveProgress(attachmentCount = 2)
      SaveAttachmentUtil.saveAttachments(attachments = testAttachments)
      testEnv.host.showSaveResult(SaveAttachmentsResult.Success(successesCount = 2))
      testEnv.host.dismissSaveProgress()
    }
  }

  @Test
  fun `saveAttachments performs save when save storage warning is dismissed and canWriteToMediaStore=true`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = true,
      saveAttachmentsResult = SaveAttachmentsResult.Success(successesCount = 2)
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    coVerifyOrder {
      testEnv.host.showSaveProgress(attachmentCount = 2)
      SaveAttachmentUtil.saveAttachments(attachments = testAttachments)
      testEnv.host.showSaveResult(SaveAttachmentsResult.Success(successesCount = 2))
      testEnv.host.dismissSaveProgress()
    }
  }

  @Test
  fun `saveAttachments shows success result when save result is Success`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = true,
      saveAttachmentsResult = SaveAttachmentsResult.Success(successesCount = 2)
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    verify { testEnv.host.showSaveResult(SaveAttachmentsResult.Success(successesCount = 2)) }
  }

  @Test
  fun `saveAttachments shows partial success result when save result is PartialSuccess`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = true,
      saveAttachmentsResult = SaveAttachmentsResult.PartialSuccess(successesCount = 1, failuresCount = 1)
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    verify { testEnv.host.showSaveResult(SaveAttachmentsResult.PartialSuccess(successesCount = 1, failuresCount = 1)) }
  }

  @Test
  fun `saveAttachments shows failure result when save result is Failure`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = true,
      saveAttachmentsResult = SaveAttachmentsResult.Failure(failuresCount = 2)
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    verify { testEnv.host.showSaveResult(SaveAttachmentsResult.Failure(failuresCount = 2)) }
  }

  @Test
  fun `saveAttachments shows no write access result when save result is ErrorNoWriteAccess`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = false,
      saveAttachmentsResult = SaveAttachmentsResult.ErrorNoWriteAccess
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    verify { testEnv.host.showSaveResult(SaveAttachmentsResult.ErrorNoWriteAccess) }
  }

  @Test
  fun `saveAttachments shows permission denied result when save result is WriteStoragePermissionDenied`() = runTest(testDispatcher) {
    val testEnv = setUpTestEnvironment(
      hasDismissedSaveStorageWarning = true,
      canWriteToMediaStore = false,
      saveAttachmentsResult = SaveAttachmentsResult.WriteStoragePermissionDenied
    )

    AttachmentSaver(host = testEnv.host).saveAttachments(testAttachments)

    verify { testEnv.host.showSaveResult(SaveAttachmentsResult.WriteStoragePermissionDenied) }
  }
}

private data class TestEnvironment(
  val host: Host,
  val uiHints: UiHintValues
)
