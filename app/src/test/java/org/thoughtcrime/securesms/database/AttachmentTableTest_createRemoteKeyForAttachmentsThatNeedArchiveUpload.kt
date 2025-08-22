/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import android.content.ContentValues
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.update
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_createRemoteKeyForAttachmentsThatNeedArchiveUpload {

  @get:Rule val signalDatabaseRule = SignalDatabaseRule()

  @get:Rule val applicationDependencies = MockAppDependenciesRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun whenNoEligibleAttachments_returnsZero() {
    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(0)
  }

  @Test
  fun whenAttachmentHasArchiveTransferStateInProgress_returnsZero() {
    val attachmentId = insertWithData()
    SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS)

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(0)
  }

  @Test
  fun whenAttachmentMissingDataFile_returnsZero() {
    val attachmentId = insertWithoutData() // No data file set

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(0)
  }

  @Test
  fun whenTransferStateNotDone_returnsZero() {
    val attachmentId = insertWithData()
    SignalDatabase.attachments.setTransferState(1L, attachmentId, 1) // Not TRANSFER_PROGRESS_DONE (0)

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(0)
  }

  @Test
  fun whenAttachmentAlreadyHasRemoteKey_returnsZero() {
    val attachmentId = insertWithData()
    // Set a remote key
    val remoteKey = Base64.encodeWithPadding(byteArrayOf(1, 2, 3, 4))
    setRemoteKey(attachmentId, remoteKey)

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(0)
  }

  @Test
  fun whenOneEligibleAttachment_returnsOneAndCreatesRemoteKey() {
    val attachmentId = insertWithData()

    // Verify attachment has no remote key initially
    val attachmentBefore = SignalDatabase.attachments.getAttachment(attachmentId)
    assertThat(attachmentBefore?.remoteKey).isNull()

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(1)

    // Verify remote key was created
    val attachmentAfter = SignalDatabase.attachments.getAttachment(attachmentId)
    assertThat(attachmentAfter?.remoteKey).isNotNull()
  }

  @Test
  fun whenMultipleEligibleAttachments_returnsCorrectCountAndCreatesKeys() {
    val attachmentId1 = insertWithData()
    val attachmentId2 = insertWithData()
    val attachmentId3 = insertWithData()

    // Verify all attachments have no remote keys initially
    assertThat(SignalDatabase.attachments.getAttachment(attachmentId1)?.remoteKey).isNull()
    assertThat(SignalDatabase.attachments.getAttachment(attachmentId2)?.remoteKey).isNull()
    assertThat(SignalDatabase.attachments.getAttachment(attachmentId3)?.remoteKey).isNull()

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(3)

    // Verify all remote keys were created
    assertThat(SignalDatabase.attachments.getAttachment(attachmentId1)?.remoteKey).isNotNull()
    assertThat(SignalDatabase.attachments.getAttachment(attachmentId2)?.remoteKey).isNotNull()
    assertThat(SignalDatabase.attachments.getAttachment(attachmentId3)?.remoteKey).isNotNull()
  }

  @Test
  fun whenMixedScenarios_returnsCorrectCount() {
    // Eligible attachment - has data file, transfer done, archive state NONE, no remote key
    val eligibleAttachmentId = insertWithData()

    // Ineligible - has remote key already
    val attachmentWithKeyId = insertWithData()
    setRemoteKey(attachmentWithKeyId, Base64.encodeWithPadding(byteArrayOf(1, 2, 3, 4)))

    // Ineligible - archive transfer state is not NONE
    val inProgressAttachmentId = insertWithData()
    SignalDatabase.attachments.setArchiveTransferState(inProgressAttachmentId, AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS)

    // Ineligible - no data file
    val noDataFileAttachmentId = insertWithoutData()

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(1)

    // Verify only the eligible attachment got a remote key
    assertThat(SignalDatabase.attachments.getAttachment(eligibleAttachmentId)?.remoteKey).isNotNull()
    assertThat(SignalDatabase.attachments.getAttachment(attachmentWithKeyId)?.remoteKey).isNotNull() // Already had one
    assertThat(SignalDatabase.attachments.getAttachment(inProgressAttachmentId)?.remoteKey).isNull()
    assertThat(SignalDatabase.attachments.getAttachment(noDataFileAttachmentId)?.remoteKey).isNull()
  }

  @Test
  fun whenCalledTwice_secondCallReturnsZero() {
    val attachmentId = insertWithData()

    // First call should create remote key
    val firstResult = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(firstResult.totalCount).isEqualTo(1)

    // Second call should find no eligible attachments since remote key now exists
    val secondResult = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(secondResult.totalCount).isEqualTo(0)

    // Verify attachment still has remote key
    assertThat(SignalDatabase.attachments.getAttachment(attachmentId)?.remoteKey).isNotNull()
  }

  @Test
  fun whenMatchingDataFileAndHashExists_reusesRemoteKey() {
    val dataFile = "/shared/path/attachment.jpg"
    val dataHashEnd = "shared_hash_end"
    val existingRemoteKey = Base64.encodeWithPadding(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

    // Create source attachment with remote key, location, digest
    val sourceAttachmentId = insertAttachmentDirectly(
      dataFile = dataFile,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      remoteKey = existingRemoteKey,
      dataHashEnd = dataHashEnd,
      remoteLocation = "cdn-location-123",
      remoteDigest = byteArrayOf(9, 10, 11, 12)
    )

    // Create target attachment with same data file and hash but no remote key
    val targetAttachmentId = insertAttachmentDirectly(
      dataFile = dataFile,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE.value,
      remoteKey = null,
      dataHashEnd = dataHashEnd
    )

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(1)

    // Verify target attachment reused the remote key, location, and digest
    val targetAttachment = SignalDatabase.attachments.getAttachment(targetAttachmentId)!!
    assertThat(targetAttachment.remoteKey).isEqualTo(existingRemoteKey)
    assertThat(targetAttachment.remoteLocation).isEqualTo("cdn-location-123")
    assertThat(targetAttachment.remoteDigest.contentEquals(byteArrayOf(9, 10, 11, 12))).isTrue()
  }

  @Test
  fun whenMultipleMatchesExist_reusesFromLatestMatch() {
    val dataFile = "/shared/path/attachment.jpg"
    val firstRemoteKey = Base64.encodeWithPadding(byteArrayOf(1, 2, 3, 4))
    val secondRemoteKey = Base64.encodeWithPadding(byteArrayOf(5, 6, 7, 8))

    // Create first source attachment
    val firstSourceId = insertAttachmentDirectly(
      dataFile = dataFile,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      remoteKey = firstRemoteKey,
      remoteLocation = "first-location",
      remoteDigest = byteArrayOf(9, 10, 11, 12)
    )

    // Create second source attachment (inserted later)
    val secondSourceId = insertAttachmentDirectly(
      dataFile = dataFile,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      remoteKey = secondRemoteKey,
      remoteLocation = "second-location",
      remoteDigest = byteArrayOf(13, 14, 15, 16)
    )

    // Create target attachment
    val targetAttachmentId = insertAttachmentDirectly(
      dataFile = dataFile,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE.value,
      remoteKey = null
    )

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(1)

    // Verify target attachment reused from the first match (by ID order desc)
    val targetAttachment = SignalDatabase.attachments.getAttachment(targetAttachmentId)!!
    assertThat(targetAttachment.remoteKey).isEqualTo(secondRemoteKey)
    assertThat(targetAttachment.remoteLocation).isEqualTo("second-location")
    assertThat(targetAttachment.remoteDigest.contentEquals(byteArrayOf(13, 14, 15, 16))).isTrue()
  }

  @Test
  fun whenSourceHasNoRemoteData_generatesNewKey() {
    val dataFile = "/shared/path/attachment.jpg"

    // Create source attachment without remote key (should not be used for reuse)
    val sourceAttachmentId = insertAttachmentDirectly(
      dataFile = dataFile,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE.value,
      remoteKey = null
    )

    // Create target attachment
    val targetAttachmentId = insertAttachmentDirectly(
      dataFile = dataFile,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE.value,
      remoteKey = null
    )

    val result = SignalDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()
    assertThat(result.totalCount).isEqualTo(2) // Both should get new keys

    // Verify both attachments got new keys
    assertThat(SignalDatabase.attachments.getAttachment(sourceAttachmentId)?.remoteKey).isNotNull()
    assertThat(SignalDatabase.attachments.getAttachment(targetAttachmentId)?.remoteKey).isNotNull()
  }

  /**
   * Creates an attachment that meets all criteria for archive upload:
   * - ARCHIVE_TRANSFER_STATE = NONE (0)
   * - DATA_FILE is not null
   * - TRANSFER_STATE = TRANSFER_PROGRESS_DONE (0)
   * - REMOTE_KEY is null
   */
  fun insertWithData(dataFile: String = "/fake/path/attachment-${UUID.randomUUID()}.jpg"): AttachmentId {
    return insertAttachmentDirectly(
      dataFile = dataFile,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE.value,
      remoteKey = null
    )
  }

  /**
   * Creates an attachment without a data file (ineligible for archive upload)
   */
  fun insertWithoutData(): AttachmentId {
    return insertAttachmentDirectly(
      dataFile = null,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE.value,
      remoteKey = null
    )
  }

  /**
   * Directly inserts an attachment with minimal required columns for testing
   */
  private fun insertAttachmentDirectly(
    dataFile: String?,
    transferState: Int,
    archiveTransferState: Int,
    remoteKey: String?,
    dataHashEnd: String? = null,
    remoteLocation: String? = null,
    remoteDigest: ByteArray? = null
  ): AttachmentId {
    val db = SignalDatabase.attachments.writableDatabase

    val values = ContentValues().apply {
      put(AttachmentTable.DATA_FILE, dataFile)
      put(AttachmentTable.DATA_RANDOM, dataFile?.toByteArray())
      put(AttachmentTable.TRANSFER_STATE, transferState)
      put(AttachmentTable.ARCHIVE_TRANSFER_STATE, archiveTransferState)
      put(AttachmentTable.REMOTE_KEY, remoteKey)
      put(AttachmentTable.DATA_HASH_END, dataHashEnd)
      put(AttachmentTable.REMOTE_LOCATION, remoteLocation)
      put(AttachmentTable.REMOTE_DIGEST, remoteDigest)
    }

    val id = db.insert(AttachmentTable.TABLE_NAME, null, values)
    return AttachmentId(id)
  }

  private fun setRemoteKey(attachmentId: AttachmentId, remoteKey: String) {
    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(AttachmentTable.REMOTE_KEY to remoteKey)
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()
  }
}
