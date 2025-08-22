/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V287_FixInvalidArchiveStateTest {

  @get:Rule val signalDatabaseRule = SignalDatabaseMigrationRule(286)

  @Test
  fun migrate_whenArchiveTransferStateIsFinishedAndRemoteKeyIsNull_clearsArchiveCdnAndSetsStateToNone() {
    val attachmentId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 2,
      remoteKey = null
    )

    val db = signalDatabaseRule.database
    V287_FixInvalidArchiveState.migrate(ApplicationProvider.getApplicationContext(), db, 286, 287)

    val cursor = db.query(
      AttachmentTable.TABLE_NAME,
      arrayOf(AttachmentTable.ARCHIVE_CDN, AttachmentTable.ARCHIVE_TRANSFER_STATE),
      "${AttachmentTable.ID} = ?",
      arrayOf(attachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.isNull(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_CDN))).isEqualTo(true)
      assertThat(it.getInt(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_TRANSFER_STATE)))
        .isEqualTo(AttachmentTable.ArchiveTransferState.NONE.value)
    }
  }

  @Test
  fun migrate_whenArchiveTransferStateIsFinishedButHasRemoteKey_noChanges() {
    val attachmentId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 2,
      remoteKey = "some-remote-key"
    )

    val db = signalDatabaseRule.database
    V287_FixInvalidArchiveState.migrate(ApplicationProvider.getApplicationContext(), db, 286, 287)

    val cursor = db.query(
      AttachmentTable.TABLE_NAME,
      arrayOf(AttachmentTable.ARCHIVE_CDN, AttachmentTable.ARCHIVE_TRANSFER_STATE),
      "${AttachmentTable.ID} = ?",
      arrayOf(attachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getInt(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_CDN))).isEqualTo(2)
      assertThat(it.getInt(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_TRANSFER_STATE)))
        .isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED.value)
    }
  }

  @Test
  fun migrate_whenArchiveTransferStateIsNoneAndRemoteKeyIsNull_noChanges() {
    val attachmentId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE.value,
      archiveCdn = 2,
      remoteKey = null
    )

    val db = signalDatabaseRule.database
    V287_FixInvalidArchiveState.migrate(ApplicationProvider.getApplicationContext(), db, 286, 287)

    val cursor = db.query(
      AttachmentTable.TABLE_NAME,
      arrayOf(AttachmentTable.ARCHIVE_CDN, AttachmentTable.ARCHIVE_TRANSFER_STATE),
      "${AttachmentTable.ID} = ?",
      arrayOf(attachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getInt(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_CDN))).isEqualTo(2)
      assertThat(it.getInt(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_TRANSFER_STATE)))
        .isEqualTo(AttachmentTable.ArchiveTransferState.NONE.value)
    }
  }

  @Test
  fun migrate_whenArchiveTransferStateIsUploadInProgressAndRemoteKeyIsNull_noChanges() {
    val attachmentId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS.value,
      archiveCdn = 2,
      remoteKey = null
    )

    val db = signalDatabaseRule.database
    V287_FixInvalidArchiveState.migrate(ApplicationProvider.getApplicationContext(), db, 286, 287)

    val cursor = db.query(
      AttachmentTable.TABLE_NAME,
      arrayOf(AttachmentTable.ARCHIVE_CDN, AttachmentTable.ARCHIVE_TRANSFER_STATE),
      "${AttachmentTable.ID} = ?",
      arrayOf(attachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getInt(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_CDN))).isEqualTo(2)
      assertThat(it.getInt(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_TRANSFER_STATE)))
        .isEqualTo(AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS.value)
    }
  }

  @Test
  fun migrate_whenMultipleAttachmentsWithMixedStates_onlyUpdatesFinishedStateWithNullRemoteKey() {
    // These should be updated (FINISHED state + null remote_key)
    val finishedNoKeyId1 = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 1,
      remoteKey = null
    )
    val finishedNoKeyId2 = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 3,
      remoteKey = null
    )

    // These should NOT be updated
    val finishedWithKeyId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 2,
      remoteKey = "some-key"
    )
    val noneId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE.value,
      archiveCdn = 2,
      remoteKey = null
    )
    val inProgressId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS.value,
      archiveCdn = 1,
      remoteKey = null
    )

    val db = signalDatabaseRule.database
    V287_FixInvalidArchiveState.migrate(ApplicationProvider.getApplicationContext(), db, 286, 287)

    // Check finished attachments with null remote_key were updated
    assertArchiveState(finishedNoKeyId1, expectedCdn = null, expectedState = AttachmentTable.ArchiveTransferState.NONE.value)
    assertArchiveState(finishedNoKeyId2, expectedCdn = null, expectedState = AttachmentTable.ArchiveTransferState.NONE.value)

    // Check other states were not changed
    assertArchiveState(finishedWithKeyId, expectedCdn = 2, expectedState = AttachmentTable.ArchiveTransferState.FINISHED.value)
    assertArchiveState(noneId, expectedCdn = 2, expectedState = AttachmentTable.ArchiveTransferState.NONE.value)
    assertArchiveState(inProgressId, expectedCdn = 1, expectedState = AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS.value)
  }

  @Test
  fun migrate_whenNoAttachmentsMatchCriteria_noChanges() {
    val noneId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE.value,
      archiveCdn = 2,
      remoteKey = null
    )
    val inProgressId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS.value,
      archiveCdn = 1,
      remoteKey = null
    )
    val finishedWithKeyId = insertAttachmentWithArchiveState(
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED.value,
      archiveCdn = 3,
      remoteKey = "has-key"
    )

    val db = signalDatabaseRule.database
    V287_FixInvalidArchiveState.migrate(ApplicationProvider.getApplicationContext(), db, 286, 287)

    // Check no changes were made
    assertArchiveState(noneId, expectedCdn = 2, expectedState = AttachmentTable.ArchiveTransferState.NONE.value)
    assertArchiveState(inProgressId, expectedCdn = 1, expectedState = AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS.value)
    assertArchiveState(finishedWithKeyId, expectedCdn = 3, expectedState = AttachmentTable.ArchiveTransferState.FINISHED.value)
  }

  private fun insertAttachmentWithArchiveState(archiveTransferState: Int, archiveCdn: Int?, remoteKey: String?): Long {
    val db = signalDatabaseRule.database

    val values = ContentValues().apply {
      put(AttachmentTable.DATA_FILE, "/fake/path/attachment.jpg")
      put(AttachmentTable.DATA_RANDOM, "/fake/path/attachment.jpg".toByteArray())
      put(AttachmentTable.TRANSFER_STATE, AttachmentTable.TRANSFER_PROGRESS_DONE)
      put(AttachmentTable.ARCHIVE_TRANSFER_STATE, archiveTransferState)
      if (archiveCdn != null) {
        put(AttachmentTable.ARCHIVE_CDN, archiveCdn)
      }
      put(AttachmentTable.REMOTE_KEY, remoteKey)
    }

    return db.insert(AttachmentTable.TABLE_NAME, null, values)
  }

  private fun assertArchiveState(attachmentId: Long, expectedCdn: Int?, expectedState: Int) {
    val db = signalDatabaseRule.database
    val cursor = db.query(
      AttachmentTable.TABLE_NAME,
      arrayOf(AttachmentTable.ARCHIVE_CDN, AttachmentTable.ARCHIVE_TRANSFER_STATE),
      "${AttachmentTable.ID} = ?",
      arrayOf(attachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)

      if (expectedCdn == null) {
        assertThat(it.isNull(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_CDN))).isEqualTo(true)
      } else {
        assertThat(it.getInt(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_CDN))).isEqualTo(expectedCdn)
      }

      assertThat(it.getInt(it.getColumnIndexOrThrow(AttachmentTable.ARCHIVE_TRANSFER_STATE))).isEqualTo(expectedState)
    }
  }
}
