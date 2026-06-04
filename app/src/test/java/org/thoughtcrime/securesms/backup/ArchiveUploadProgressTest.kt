/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress.ArchiveBackupProgressListener
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ArchiveCommitAttachmentDeletesJob
import org.thoughtcrime.securesms.jobs.ArchiveThumbnailUploadJob
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.UploadAttachmentToArchiveJob
import org.thoughtcrime.securesms.keyvalue.BackupValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState.BackupPhase
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState.State
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ArchiveUploadProgressTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  companion object {
    private lateinit var backup: BackupValues
    private lateinit var attachments: AttachmentTable

    private var storedState: ArchiveUploadProgressState = ArchiveUploadProgressState(state = State.None)
    private var backsUpMedia: Boolean = true
    private var finishedInitialBackup: Boolean = false
    private var pendingArchiveUploadBytes: Long = 0

    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      backup = mockk(relaxUnitFun = true)
      attachments = mockk(relaxed = true)
      val signalDatabase = mockk<SignalDatabase>(relaxed = true)

      mockkObject(SignalStore)
      every { SignalStore.backup } returns backup
      every { backup.archiveUploadState } answers { storedState }
      every { backup.archiveUploadState = any() } answers { storedState = firstArg() }
      every { backup.backsUpMedia } answers { backsUpMedia }
      every { backup.finishedInitialBackup } answers { finishedInitialBackup }

      mockkObject(SignalDatabase)
      every { SignalDatabase.instance } returns signalDatabase
      every { signalDatabase.attachmentTable } returns attachments
      every { attachments.getPendingArchiveUploadBytes() } answers { pendingArchiveUploadBytes }

      mockkObject(BackupMessagesJob.Companion)
      every { BackupMessagesJob.enqueue() } just Runs
      every { BackupMessagesJob.cancel() } just Runs

      mockkStatic(SignalLocalMetrics.ArchiveAttachmentUpload::class)
      every { SignalLocalMetrics.ArchiveAttachmentUpload.start(any()) } just Runs
      every { SignalLocalMetrics.ArchiveAttachmentUpload.end(any()) } just Runs
    }

    @AfterClass
    @JvmStatic
    fun tearDownClass() {
      unmockkObject(SignalStore)
      unmockkObject(SignalDatabase)
      unmockkObject(BackupMessagesJob.Companion)
      unmockkStatic(SignalLocalMetrics.ArchiveAttachmentUpload::class)
    }
  }

  @Before
  fun setUp() {
    clearMocks(backup, attachments, BackupMessagesJob.Companion, answers = false, recordedCalls = true)

    storedState = ArchiveUploadProgressState(state = State.None)
    backsUpMedia = true
    finishedInitialBackup = false
    pendingArchiveUploadBytes = 0

    every { AppDependencies.jobManager.areQueuesEmpty(any()) } returns true

    setUploadProgress(ArchiveUploadProgressState(state = State.None))
    clearAttachmentProgress()
  }

  @After
  fun tearDown() {
    setUploadProgress(ArchiveUploadProgressState(state = State.None))
    clearAttachmentProgress()
  }

  @Test
  fun `begin - sets export state and shows banner when initial backup not finished`() {
    ArchiveUploadProgress.begin()

    assertThat(uploadProgress().state).isEqualTo(State.Export)
    verify { backup.uploadBannerVisible = true }
  }

  @Test
  fun `begin - does not show banner when initial backup already finished`() {
    finishedInitialBackup = true

    ArchiveUploadProgress.begin()

    assertThat(uploadProgress().state).isEqualTo(State.Export)
    verify(exactly = 0) { backup.uploadBannerVisible = any() }
  }

  @Test
  fun `begin - overrides a prior user cancellation`() {
    setUploadProgress(ArchiveUploadProgressState(state = State.UserCanceled))

    ArchiveUploadProgress.begin()

    assertThat(uploadProgress().state).isEqualTo(State.Export)
  }

  @Test
  fun `cancel - sets user canceled state, hides banner, and cancels jobs`() {
    setUploadProgress(ArchiveUploadProgressState(state = State.Export))

    ArchiveUploadProgress.cancel()

    assertThat(uploadProgress().state).isEqualTo(State.UserCanceled)
    verify { backup.uploadBannerVisible = false }
    verify { BackupMessagesJob.cancel() }
    verify { AppDependencies.jobManager.cancelAllInQueue(ArchiveCommitAttachmentDeletesJob.ARCHIVE_ATTACHMENT_QUEUE) }
    verify { AppDependencies.jobManager.cancelAllInQueues(UploadAttachmentToArchiveJob.QUEUES) }
    verify { AppDependencies.jobManager.cancelAllInQueue(ArchiveThumbnailUploadJob.KEY) }
  }

  @Test
  fun `cancel - is sticky and prevents subsequent state changes`() {
    ArchiveUploadProgress.cancel()
    assertThat(uploadProgress().state).isEqualTo(State.UserCanceled)

    ArchiveUploadProgress.onMessageBackupCreated(backupFileSize = 1234)

    assertThat(uploadProgress().state).isEqualTo(State.UserCanceled)
  }

  @Test
  fun `onMessageBackupCreated - sets upload backup file state with totals`() {
    ArchiveUploadProgress.onMessageBackupCreated(backupFileSize = 5000)

    val state = uploadProgress()
    assertThat(state.state).isEqualTo(State.UploadBackupFile)
    assertThat(state.backupFileTotalBytes).isEqualTo(5000L)
    assertThat(state.backupFileUploadedBytes).isEqualTo(0L)
  }

  @Test
  fun `onMessageBackupUploadProgress - tracks transmitted and total bytes`() {
    ArchiveUploadProgress.onMessageBackupUploadProgress(AttachmentTransferProgress(total = 8000L, transmitted = 3000L))

    val state = uploadProgress()
    assertThat(state.state).isEqualTo(State.UploadBackupFile)
    assertThat(state.backupFileUploadedBytes).isEqualTo(3000L)
    assertThat(state.backupFileTotalBytes).isEqualTo(8000L)
  }

  @Test
  fun `onAttachmentSectionStarted - sets upload media state with total bytes`() {
    pendingArchiveUploadBytes = 7000

    ArchiveUploadProgress.onAttachmentSectionStarted(totalAttachmentBytes = 7000)

    val state = uploadProgress()
    assertThat(state.state).isEqualTo(State.UploadMedia)
    assertThat(state.mediaUploadedBytes).isEqualTo(0L)
    assertThat(state.mediaTotalBytes).isEqualTo(7000L)
  }

  @Test
  fun `onMessageBackupFinishedEarly - without media goes to none and marks initial backup finished`() {
    backsUpMedia = false
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadBackupFile, backupFileTotalBytes = 100))

    ArchiveUploadProgress.onMessageBackupFinishedEarly()

    val state = uploadProgress()
    assertThat(state.state).isEqualTo(State.None)
    assertThat(state.backupPhase).isEqualTo(BackupPhase.BackupPhaseNone)
    verify { backup.finishedInitialBackup = true }
  }

  @Test
  fun `onMessageBackupFinishedEarly - with pending media reverts to upload media`() {
    backsUpMedia = true
    every { AppDependencies.jobManager.areQueuesEmpty(any()) } returns false
    pendingArchiveUploadBytes = 2000
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadBackupFile, backupFileTotalBytes = 100))

    ArchiveUploadProgress.onMessageBackupFinishedEarly()

    val state = uploadProgress()
    assertThat(state.state).isEqualTo(State.UploadMedia)
    assertThat(state.mediaTotalBytes).isEqualTo(2000L)
    verify { backup.finishedInitialBackup = true }
  }

  @Test
  fun `onMainBackupFileUploadFailure - without media goes to none`() {
    backsUpMedia = false
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadBackupFile, backupFileTotalBytes = 100))

    ArchiveUploadProgress.onMainBackupFileUploadFailure()

    val state = uploadProgress()
    assertThat(state.state).isEqualTo(State.None)
    assertThat(state.backupPhase).isEqualTo(BackupPhase.BackupPhaseNone)
  }

  @Test
  fun `onMainBackupFileUploadFailure - with pending media reverts to upload media`() {
    backsUpMedia = true
    every { AppDependencies.jobManager.areQueuesEmpty(any()) } returns false
    pendingArchiveUploadBytes = 3000
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadBackupFile, backupFileTotalBytes = 100))

    ArchiveUploadProgress.onMainBackupFileUploadFailure()

    val state = uploadProgress()
    assertThat(state.state).isEqualTo(State.UploadMedia)
    assertThat(state.mediaTotalBytes).isEqualTo(3000L)
  }

  @Test
  fun `listener - phase callbacks set export state with matching phase`() {
    val cases = listOf<Pair<() -> Unit, BackupPhase>>(
      ({ ArchiveBackupProgressListener.onAccount() }) to BackupPhase.Account,
      ({ ArchiveBackupProgressListener.onRecipient() }) to BackupPhase.Recipient,
      ({ ArchiveBackupProgressListener.onThread() }) to BackupPhase.Thread,
      ({ ArchiveBackupProgressListener.onCall() }) to BackupPhase.Call,
      ({ ArchiveBackupProgressListener.onSticker() }) to BackupPhase.Sticker,
      ({ ArchiveBackupProgressListener.onNotificationProfile() }) to BackupPhase.NotificationProfile,
      ({ ArchiveBackupProgressListener.onChatFolder() }) to BackupPhase.ChatFolder
    )

    cases.forEach { (action, expectedPhase) ->
      action()

      val state = uploadProgress()
      assertThat(state.state).isEqualTo(State.Export)
      assertThat(state.backupPhase).isEqualTo(expectedPhase)
    }
  }

  @Test
  fun `listener - onMessage sets export state with frame counts`() {
    ArchiveBackupProgressListener.onMessage(currentProgress = 25, approximateCount = 100)

    val state = uploadProgress()
    assertThat(state.state).isEqualTo(State.Export)
    assertThat(state.backupPhase).isEqualTo(BackupPhase.Message)
    assertThat(state.frameExportCount).isEqualTo(25L)
    assertThat(state.frameTotalCount).isEqualTo(100L)
  }

  @Test
  fun `listener - onAttachment resets phase to none`() {
    ArchiveBackupProgressListener.onMessage(currentProgress = 25, approximateCount = 100)

    ArchiveBackupProgressListener.onAttachment(currentProgress = 3, totalCount = 7)

    val state = uploadProgress()
    assertThat(state.state).isEqualTo(State.Export)
    assertThat(state.backupPhase).isEqualTo(BackupPhase.BackupPhaseNone)
  }

  @Test
  fun `inProgress - reflects active states`() {
    setUploadProgress(ArchiveUploadProgressState(state = State.Export))
    assertThat(ArchiveUploadProgress.inProgress).isTrue()

    setUploadProgress(ArchiveUploadProgressState(state = State.None))
    assertThat(ArchiveUploadProgress.inProgress).isFalse()

    setUploadProgress(ArchiveUploadProgressState(state = State.UserCanceled))
    assertThat(ArchiveUploadProgress.inProgress).isFalse()
  }

  @Test
  fun `onAttachmentStarted - registers attachment and starts metric`() {
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadMedia, mediaTotalBytes = 1000, mediaUploadedBytes = 0))
    pendingArchiveUploadBytes = 1000

    val attachmentId = AttachmentId(1)
    ArchiveUploadProgress.onAttachmentStarted(attachmentId, sizeBytes = 500)

    verify { SignalLocalMetrics.ArchiveAttachmentUpload.start(attachmentId) }
    assertThat(attachmentProgressMap().containsKey(attachmentId)).isTrue()
  }

  @Test
  fun `onAttachmentProgress - computes media uploaded bytes via flow`() {
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadMedia, mediaTotalBytes = 1000, mediaUploadedBytes = 0))
    pendingArchiveUploadBytes = 1000

    val attachmentId = AttachmentId(1)
    ArchiveUploadProgress.onAttachmentProgress(attachmentId, bytesUploaded = 300)

    val state = awaitUploadProgress { it.state == State.UploadMedia && it.mediaUploadedBytes == 300L }
    assertThat(state.mediaTotalBytes).isEqualTo(1000L)
    assertThat(state.mediaUploadedBytes).isEqualTo(300L)
  }

  @Test
  fun `onAttachmentFinished - removes attachment and ends metric`() {
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadMedia, mediaTotalBytes = 1000, mediaUploadedBytes = 0))
    pendingArchiveUploadBytes = 1000

    val attachmentId = AttachmentId(1)
    ArchiveUploadProgress.onAttachmentStarted(attachmentId, sizeBytes = 500)
    ArchiveUploadProgress.onAttachmentFinished(attachmentId)

    verify { SignalLocalMetrics.ArchiveAttachmentUpload.end(attachmentId) }
    assertThat(attachmentProgressMap().containsKey(attachmentId)).isFalse()
  }

  @Test
  fun `progress flow - expands total when more media becomes pending than expected`() {
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadMedia, mediaTotalBytes = 1000, mediaUploadedBytes = 0))
    backsUpMedia = true
    pendingArchiveUploadBytes = 1500

    ArchiveUploadProgress.triggerUpdate()

    val state = awaitUploadProgress { it.state == State.UploadMedia && it.mediaTotalBytes == 1500L }
    assertThat(state.mediaTotalBytes).isEqualTo(1500L)
    assertThat(state.mediaUploadedBytes).isEqualTo(0L)
  }

  @Test
  fun `progress flow - completes when no pending bytes remain`() {
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadMedia, mediaTotalBytes = 1000, mediaUploadedBytes = 0))
    backsUpMedia = true
    pendingArchiveUploadBytes = 0

    ArchiveUploadProgress.triggerUpdate()

    val state = awaitUploadProgress { it.state == State.None }
    assertThat(state.mediaUploadedBytes).isEqualTo(1000L)
    verify { backup.finishedInitialBackup = true }
    verify { BackupMessagesJob.enqueue() }
  }

  @Test
  fun `progress flow - completes immediately when media is not backed up`() {
    setUploadProgress(ArchiveUploadProgressState(state = State.UploadMedia, mediaTotalBytes = 1000, mediaUploadedBytes = 0))
    backsUpMedia = false

    ArchiveUploadProgress.triggerUpdate()

    val state = awaitUploadProgress { it.state == State.None }
    assertThat(state.backupPhase).isEqualTo(BackupPhase.BackupPhaseNone)
    verify { backup.finishedInitialBackup = true }
  }

  private fun uploadProgress(): ArchiveUploadProgressState {
    val field = ArchiveUploadProgress::class.java.getDeclaredField("uploadProgress")
    field.isAccessible = true
    return field.get(ArchiveUploadProgress) as ArchiveUploadProgressState
  }

  private fun setUploadProgress(value: ArchiveUploadProgressState) {
    val field = ArchiveUploadProgress::class.java.getDeclaredField("uploadProgress")
    field.isAccessible = true
    field.set(ArchiveUploadProgress, value)
    storedState = value
  }

  @Suppress("UNCHECKED_CAST")
  private fun attachmentProgressMap(): MutableMap<AttachmentId, Any> {
    val field = ArchiveUploadProgress::class.java.getDeclaredField("attachmentProgress")
    field.isAccessible = true
    return field.get(ArchiveUploadProgress) as MutableMap<AttachmentId, Any>
  }

  private fun clearAttachmentProgress() {
    attachmentProgressMap().clear()
  }

  private fun awaitUploadProgress(timeoutMs: Long = 5000, predicate: (ArchiveUploadProgressState) -> Boolean): ArchiveUploadProgressState {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val current = uploadProgress()
      if (predicate(current)) {
        return current
      }
      ArchiveUploadProgress.triggerUpdate()
      Thread.sleep(25)
    }

    val current = uploadProgress()
    if (predicate(current)) {
      return current
    }
    throw AssertionError("Timed out waiting for expected progress state. Last state: $current")
  }
}
