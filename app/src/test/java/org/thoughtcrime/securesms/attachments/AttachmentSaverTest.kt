/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import android.app.Application
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
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

  @After
  fun tearDown() {
    unmockkObject(SignalStore)
    unmockkObject(SaveAttachmentUtil)
    unmockkStatic(StorageUtil::class)
  }

  @Test
  fun `saveAttachments shows save to storage warning when it has not been dismissed`() = runTest(testDispatcher) {
    val host = mockk<Host> {
      coEvery { showSaveToStorageWarning(attachmentCount = any()) } returns mockk()
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns false
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify { host.showSaveToStorageWarning(attachmentCount = 2) }
  }

  @Test
  fun `saveAttachments does not show save to storage warning when it has been dismissed`() = runTest(testDispatcher) {
    val host = mockk<Host>(relaxUnitFun = true) {
      coEvery { requestWriteExternalStoragePermission() } returns mockk()
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns true
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify(exactly = 0) { host.showSaveToStorageWarning(attachmentCount = any()) }
  }

  @Test
  fun `saveAttachments requests WRITE_EXTERNAL_STORAGE permission when not yet granted and save to storage warning has been dismissed`() = runTest(testDispatcher) {
    val host = mockk<Host> {
      coEvery { requestWriteExternalStoragePermission() } returns mockk()
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns true
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkStatic(StorageUtil::class)
    every { StorageUtil.canWriteToMediaStore() } returns false

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify { host.requestWriteExternalStoragePermission() }
  }

  @Test
  fun `saveAttachments requests WRITE_EXTERNAL_STORAGE permission when not yet granted and save to storage warning is accepted`() = runTest(testDispatcher) {
    val host = mockk<Host>(relaxUnitFun = true) {
      coEvery { showSaveToStorageWarning(attachmentCount = any()) } returns SaveToStorageWarningResult.ACCEPTED
      coEvery { requestWriteExternalStoragePermission() } returns mockk()
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns false
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkStatic(StorageUtil::class)
    every { StorageUtil.canWriteToMediaStore() } returns false

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify { host.requestWriteExternalStoragePermission() }
  }

  @Test
  fun `saveAttachments does not request WRITE_EXTERNAL_STORAGE permission when not yet granted and save to storage warning is denied`() = runTest(testDispatcher) {
    val host = mockk<Host> {
      coEvery { showSaveToStorageWarning(attachmentCount = any()) } returns SaveToStorageWarningResult.DENIED
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns false
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify(exactly = 0) { host.requestWriteExternalStoragePermission() }
  }

  @Test
  fun `saveAttachments does not request WRITE_EXTERNAL_STORAGE permission when already granted`() = runTest(testDispatcher) {
    val host = mockk<Host>(relaxUnitFun = true)
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns true
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkStatic(StorageUtil::class)
    every { StorageUtil.canWriteToMediaStore() } returns true

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify(exactly = 0) { host.requestWriteExternalStoragePermission() }
  }

  @Test
  fun `saveAttachments does not perform save when save to storage warning is denied`() = runTest(testDispatcher) {
    val host = mockk<Host> {
      coEvery { showSaveToStorageWarning(attachmentCount = any()) } returns SaveToStorageWarningResult.DENIED
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns false
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkStatic(StorageUtil::class)
    every { StorageUtil.canWriteToMediaStore() } returns true

    mockkObject(SaveAttachmentUtil)

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify(exactly = 0) { SaveAttachmentUtil.saveAttachments(attachments = any()) }
    verify(exactly = 0) { host.showSaveProgress(any()) }
    verify(exactly = 0) { host.dismissSaveProgress() }
  }

  @Test
  fun `saveAttachments does not perform save when WRITE_EXTERNAL_STORAGE permission is denied`() = runTest(testDispatcher) {
    val host = mockk<Host> {
      coEvery { requestWriteExternalStoragePermission() } returns RequestPermissionResult.DENIED
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns true
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkStatic(StorageUtil::class)
    every { StorageUtil.canWriteToMediaStore() } returns false

    mockkObject(SaveAttachmentUtil)

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify(exactly = 0) { SaveAttachmentUtil.saveAttachments(attachments = any()) }
    verify(exactly = 0) { host.showSaveProgress(any()) }
    verify(exactly = 0) { host.dismissSaveProgress() }
  }

  @Test
  fun `saveAttachments performs save when save storage warning is accepted and WRITE_EXTERNAL_STORAGE permission is granted`() = runTest(testDispatcher) {
    val host = mockk<Host>(relaxUnitFun = true) {
      coEvery { showSaveToStorageWarning(attachmentCount = 2) } returns SaveToStorageWarningResult.ACCEPTED
      coEvery { requestWriteExternalStoragePermission() } returns RequestPermissionResult.GRANTED
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns false
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkStatic(StorageUtil::class)
    every { StorageUtil.canWriteToMediaStore() } returns false

    mockkObject(SaveAttachmentUtil)

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify { SaveAttachmentUtil.saveAttachments(attachments = any()) }
    verify { host.showSaveProgress(attachmentCount = 2) }
    verify { host.dismissSaveProgress() }
  }

  @Test
  fun `saveAttachments performs save when save storage warning is dismissed and WRITE_EXTERNAL_STORAGE permission is granted`() = runTest(testDispatcher) {
    val host = mockk<Host>(relaxUnitFun = true) {
      coEvery { requestWriteExternalStoragePermission() } returns RequestPermissionResult.GRANTED
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns true
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkObject(SaveAttachmentUtil)
    coEvery { SaveAttachmentUtil.saveAttachments(testAttachments) } returns SaveAttachmentsResult.Completed(successCount = 2, errorCount = 0)

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify { SaveAttachmentUtil.saveAttachments(attachments = any()) }
    verify { host.showSaveProgress(attachmentCount = 2) }
    verify { host.dismissSaveProgress() }
  }

  @Test
  fun `saveAttachments performs save when save storage warning is accepted and hasWriteExternalStoragePermission=true`() = runTest(testDispatcher) {
    val host = mockk<Host>(relaxUnitFun = true) {
      coEvery { showSaveToStorageWarning(attachmentCount = 2) } returns SaveToStorageWarningResult.ACCEPTED
    }
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns false
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkStatic(StorageUtil::class)
    every { StorageUtil.canWriteToMediaStore() } returns true

    mockkObject(SaveAttachmentUtil)
    coEvery { SaveAttachmentUtil.saveAttachments(testAttachments) } returns SaveAttachmentsResult.Completed(successCount = 2, errorCount = 0)

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify { SaveAttachmentUtil.saveAttachments(attachments = any()) }
    verify { host.showSaveProgress(attachmentCount = 2) }
    verify { host.dismissSaveProgress() }
  }

  @Test
  fun `saveAttachments performs save when save storage warning is dismissed and hasWriteExternalStoragePermission=true`() = runTest(testDispatcher) {
    val host = mockk<Host>(relaxUnitFun = true)
    val uiHints = mockk<UiHintValues> {
      every { hasDismissedSaveStorageWarning() } returns true
    }

    mockkObject(SignalStore)
    every { SignalStore.uiHints } returns uiHints

    mockkStatic(StorageUtil::class)
    every { StorageUtil.canWriteToMediaStore() } returns true

    mockkObject(SaveAttachmentUtil)

    AttachmentSaver(host = host).saveAttachments(testAttachments)

    coVerify { SaveAttachmentUtil.saveAttachments(attachments = any()) }
    verify { host.showSaveProgress(attachmentCount = 2) }
    verify { host.dismissSaveProgress() }
  }
}
