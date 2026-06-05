/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import android.content.ContentValues
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.signal.core.util.select
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_deleteAttachmentsForMessage {

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun givenAttachmentWithMetadata_whenMessageDeleted_thenMetadataRowIsCleanedUp() {
    // GIVEN
    val metadataId = insertMetadata("hash_unique_1")
    val messageId = insertMessage()
    insertAttachment(messageId = messageId, metadataId = metadataId)

    // WHEN
    SignalDatabase.attachments.deleteAttachmentsForMessage(messageId)

    // THEN
    assertThat(metadataExists(metadataId)).isFalse()
  }

  @Test
  fun givenAttachmentWithNoMetadata_whenMessageDeleted_thenNothingExplodes() {
    // GIVEN
    val messageId = insertMessage()
    insertAttachment(messageId = messageId, metadataId = null)

    // WHEN
    SignalDatabase.attachments.deleteAttachmentsForMessage(messageId)

    // THEN — no assertion needed, just verifying no crash
  }

  @Test
  fun givenTwoAttachmentsReferencingSameMetadata_whenFirstMessageDeleted_thenMetadataRowIsPreserved() {
    // GIVEN — two attachments sharing the same deduped metadata row
    val metadataId = insertMetadata("hash_shared")
    val messageId1 = insertMessage()
    val messageId2 = insertMessage()
    insertAttachment(messageId = messageId1, metadataId = metadataId)
    insertAttachment(messageId = messageId2, metadataId = metadataId)

    // WHEN
    SignalDatabase.attachments.deleteAttachmentsForMessage(messageId1)

    // THEN
    assertThat(metadataExists(metadataId)).isTrue()
  }

  @Test
  fun givenTwoAttachmentsReferencingSameMetadata_whenBothMessagesDeleted_thenMetadataRowIsCleanedUp() {
    // GIVEN
    val metadataId = insertMetadata("hash_shared_both")
    val messageId1 = insertMessage()
    val messageId2 = insertMessage()
    insertAttachment(messageId = messageId1, metadataId = metadataId)
    insertAttachment(messageId = messageId2, metadataId = metadataId)

    // WHEN
    SignalDatabase.attachments.deleteAttachmentsForMessage(messageId1)
    SignalDatabase.attachments.deleteAttachmentsForMessage(messageId2)

    // THEN
    assertThat(metadataExists(metadataId)).isFalse()
  }

  // region helpers

  private fun insertMetadata(plaintextHash: String): Long {
    return SignalDatabase.attachmentMetadata.insert(plaintextHash, ByteArray(64) { it.toByte() })
  }

  private fun insertMessage(): Long {
    return TestSms.insert(signalDatabaseRule.writeableDatabase)
  }

  private fun insertAttachment(messageId: Long, metadataId: Long?): Long {
    return SignalDatabase.writableDatabase.insert(
      AttachmentTable.TABLE_NAME,
      null,
      ContentValues().apply {
        put(AttachmentTable.MESSAGE_ID, messageId)
        put(AttachmentTable.TRANSFER_STATE, AttachmentTable.TRANSFER_PROGRESS_DONE)
        put(AttachmentTable.CONTENT_TYPE, "image/jpeg")
        if (metadataId != null) put(AttachmentTable.METADATA_ID, metadataId)
      }
    )
  }

  private fun metadataExists(metadataId: Long): Boolean {
    return SignalDatabase.writableDatabase.select(AttachmentMetadataTable.ID)
      .from(AttachmentMetadataTable.TABLE_NAME)
      .where("${AttachmentMetadataTable.ID} = ?", metadataId)
      .run()
      .use { it.moveToFirst() }
  }

  // endregion
}
