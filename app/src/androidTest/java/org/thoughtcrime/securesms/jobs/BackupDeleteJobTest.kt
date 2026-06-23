/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import okio.IOException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.util.Base64
import org.signal.core.util.Util
import org.signal.network.NetworkResult
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobs.protos.BackupDeleteJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.RemoteConfig
import java.util.UUID

class BackupDeleteJobTest {

  @get:Rule
  val harness = SignalActivityRule()

  @Before
  fun setUp() {
    mockkObject(RemoteConfig)
    every { RemoteConfig.internalUser } returns true
    every { RemoteConfig.defaultMaxBackoff } returns 1000L

    mockkObject(BackupRepository)
    every { BackupRepository.getBackupTier() } returns NetworkResult.Success(MessageBackupTier.PAID)
    every { BackupRepository.deleteBackup() } returns NetworkResult.Success(Unit)
    every { BackupRepository.deleteMediaBackup() } returns NetworkResult.Success(Unit)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun givenUserNotRegistered_whenIRun_thenIExpectFailure() {
    mockkObject(SignalStore) {
      every { SignalStore.account.isRegistered } returns false

      val job = BackupDeleteJob()

      val result = job.run()

      assertThat(result.isFailure).isTrue()
    }
  }

  @Test
  fun givenLinkedDevice_whenIRun_thenIExpectFailure() {
    mockkObject(SignalStore) {
      every { SignalStore.account.isRegistered } returns true
      every { SignalStore.account.isLinkedDevice } returns true

      val job = BackupDeleteJob()

      val result = job.run()

      assertThat(result.isFailure).isTrue()
    }
  }

  @Test
  fun givenDeletionStateNone_whenIRun_thenIExpectFailure() {
    SignalStore.backup.deletionState = DeletionState.NONE

    val job = BackupDeleteJob()

    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun givenDeletionStateFailed_whenIRun_thenIExpectFailure() {
    SignalStore.backup.deletionState = DeletionState.FAILED

    val job = BackupDeleteJob()

    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun givenDeletionStateComplete_whenIRun_thenIExpectFailure() {
    SignalStore.backup.deletionState = DeletionState.NONE

    val job = BackupDeleteJob()

    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun givenDeletionStateAwaitingMediaDownload_whenIRun_thenIExpectRetry() {
    SignalStore.backup.deletionState = DeletionState.AWAITING_MEDIA_DOWNLOAD

    val job = BackupDeleteJob()

    val result = job.run()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun givenDeletionStateClearLocalState_whenIRun_thenIDeleteLocalState() {
    SignalStore.backup.deletionState = DeletionState.CLEAR_LOCAL_STATE

    val job = BackupDeleteJob()

    job.run()

    val jobData = BackupDeleteJobData.ADAPTER.decode(job.serialize())

    assertThat(SignalStore.backup.backupTier).isNull()
    assertThat(jobData.tier).isEqualTo(BackupDeleteJobData.Tier.PAID)
    assertThat(jobData.completed).contains(BackupDeleteJobData.Stage.CLEAR_LOCAL_STATE)
  }

  @Test
  fun givenDeletionStateClearLocalState_whenIRun_thenIUnsubscribe() {
    SignalStore.backup.deletionState = DeletionState.CLEAR_LOCAL_STATE

    val job = BackupDeleteJob()

    job.run()

    val jobData = BackupDeleteJobData.ADAPTER.decode(job.serialize())

    assertThat(SignalStore.backup.backupTier).isNull()
    assertThat(jobData.tier).isEqualTo(BackupDeleteJobData.Tier.PAID)
    assertThat(jobData.completed).contains(BackupDeleteJobData.Stage.CANCEL_SUBSCRIBER)
  }

  @Test
  fun givenMediaOffloaded_whenIRun_thenIExpectAwaitingMediaDownload() {
    insertOffloadedAttachment()

    SignalStore.backup.deletionState = DeletionState.CLEAR_LOCAL_STATE

    val job = BackupDeleteJob()
    val result = job.run()
    val jobData = BackupDeleteJobData.ADAPTER.decode(job.serialize())

    assertThat(SignalStore.backup.backupTier).isNull()
    assertThat(jobData.tier).isEqualTo(BackupDeleteJobData.Tier.PAID)
    assertThat(jobData.completed).contains(BackupDeleteJobData.Stage.CLEAR_LOCAL_STATE)
    assertThat(jobData.completed).contains(BackupDeleteJobData.Stage.CANCEL_SUBSCRIBER)

    assertThat(SignalStore.backup.deletionState).isEqualTo(DeletionState.AWAITING_MEDIA_DOWNLOAD)
    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun givenMediaDownloadFinished_whenIRun_thenIExpectDeletion() {
    SignalStore.backup.deletionState = DeletionState.MEDIA_DOWNLOAD_FINISHED

    val job = BackupDeleteJob(
      backupDeleteJobData = BackupDeleteJobData(
        tier = BackupDeleteJobData.Tier.PAID,
        completed = listOf(
          BackupDeleteJobData.Stage.CLEAR_LOCAL_STATE,
          BackupDeleteJobData.Stage.CANCEL_SUBSCRIBER
        )
      )
    )

    val result = job.run()

    verify {
      BackupRepository.deleteBackup()
      BackupRepository.deleteMediaBackup()
      BackupRepository.resetInitializedStateAndAuthCredentials()
    }

    assertThat(result.isSuccess).isTrue()
    assertThat(SignalStore.backup.deletionState).isEqualTo(DeletionState.COMPLETE)
  }

  @Test
  fun givenNetworkErrorDuringMessageBackupDeletion_whenIRun_thenIExpectRetry() {
    every { BackupRepository.deleteBackup() } returns NetworkResult.NetworkError(IOException())

    SignalStore.backup.deletionState = DeletionState.CLEAR_LOCAL_STATE

    val job = BackupDeleteJob()

    val result = job.run()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun givenNetworkErrorDuringMediaBackupDeletion_whenIRun_thenIExpectRetry() {
    every { BackupRepository.deleteMediaBackup() } returns NetworkResult.NetworkError(IOException())

    SignalStore.backup.deletionState = DeletionState.CLEAR_LOCAL_STATE

    val job = BackupDeleteJob()

    val result = job.run()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun givenRateLimitedDuringMessageBackupDeletion_whenIRun_thenIExpectRetry() {
    every { BackupRepository.deleteBackup() } returns NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(429))

    SignalStore.backup.deletionState = DeletionState.CLEAR_LOCAL_STATE

    val job = BackupDeleteJob()

    val result = job.run()

    assertThat(result.isRetry).isTrue()
  }

  @Test
  fun givenRateLimitedDuringMediaBackupDeletion_whenIRun_thenIExpectRetry() {
    every { BackupRepository.deleteMediaBackup() } returns NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(429))

    SignalStore.backup.deletionState = DeletionState.CLEAR_LOCAL_STATE

    val job = BackupDeleteJob()

    val result = job.run()

    assertThat(result.isRetry).isTrue()
  }

  private fun insertOffloadedAttachment(size: Long = 100) {
    SignalDatabase.attachments.insertAttachmentsForMessage(
      mmsId = 1,
      attachments = listOf(
        PointerAttachment(
          contentType = "image/jpeg",
          transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED,
          size = size,
          fileName = null,
          cdn = Cdn.CDN_3,
          location = "somelocation",
          key = Base64.encodeWithPadding(Util.getSecretBytes(64)),
          iv = null,
          digest = Util.getSecretBytes(64),
          incrementalDigest = null,
          incrementalMacChunkSize = 0,
          fastPreflightId = null,
          voiceNote = false,
          borderless = false,
          videoGif = false,
          width = 100,
          height = 100,
          uploadTimestamp = System.currentTimeMillis(),
          caption = null,
          stickerLocator = null,
          blurHash = null,
          uuid = UUID.randomUUID(),
          quote = false,
          quoteTargetContentType = null
        )
      ),
      quoteAttachment = emptyList()
    )
  }
}
