/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V288_AddQuoteTargetContentTypeColumnTest {

  @get:Rule val signalDatabaseRule = SignalDatabaseMigrationRule(287)

  private val db get() = signalDatabaseRule.database

  @Test
  fun migrate_addsQuoteTargetContentTypeColumn() {
    V289_AddQuoteTargetContentTypeColumn.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    assertThat(SqlUtil.columnExists(db, AttachmentTable.TABLE_NAME, AttachmentTable.QUOTE_TARGET_CONTENT_TYPE)).isEqualTo(true)
  }

  @Test
  fun migrate_whenQuoteAttachmentExists_populatesQuoteTargetContentType() {
    val quoteAttachmentId = insertAttachment(
      contentType = "video/mp4",
      quote = 1
    )

    V289_AddQuoteTargetContentTypeColumn.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    val cursor = db
      .select(AttachmentTable.CONTENT_TYPE, AttachmentTable.QUOTE_TARGET_CONTENT_TYPE)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.ID} = ?", quoteAttachmentId.toString())
      .run()

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getString(it.getColumnIndexOrThrow(AttachmentTable.CONTENT_TYPE))).isEqualTo("video/mp4")
      assertThat(it.getString(it.getColumnIndexOrThrow(AttachmentTable.QUOTE_TARGET_CONTENT_TYPE))).isEqualTo("video/mp4")
    }
  }

  @Test
  fun migrate_whenNonQuoteAttachmentExists_doesNotPopulateQuoteTargetContentType() {
    val nonQuoteAttachmentId = insertAttachment(
      contentType = "image/jpeg",
      quote = 0
    )

    V289_AddQuoteTargetContentTypeColumn.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    val cursor = db
      .select(AttachmentTable.CONTENT_TYPE, AttachmentTable.QUOTE_TARGET_CONTENT_TYPE)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.ID} = ?", nonQuoteAttachmentId.toString())
      .run()

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getString(it.getColumnIndexOrThrow(AttachmentTable.CONTENT_TYPE))).isEqualTo("image/jpeg")
      assertThat(it.getString(it.getColumnIndexOrThrow(AttachmentTable.QUOTE_TARGET_CONTENT_TYPE))).isNull()
    }
  }

  @Test
  fun migrate_whenMixedQuoteAndNonQuoteAttachments_onlyPopulatesQuoteAttachments() {
    val quoteVideoId = insertAttachment(
      contentType = "video/mp4",
      quote = 1
    )
    val quoteImageId = insertAttachment(
      contentType = "image/png",
      quote = 1
    )
    val nonQuoteImageId = insertAttachment(
      contentType = "image/jpeg",
      quote = 0
    )
    val nonQuoteVideoId = insertAttachment(
      contentType = "video/webm",
      quote = 0
    )

    V289_AddQuoteTargetContentTypeColumn.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    assertQuoteTargetContentType(quoteVideoId, expectedContentType = "video/mp4", expectedQuoteTargetContentType = "video/mp4")
    assertQuoteTargetContentType(quoteImageId, expectedContentType = "image/png", expectedQuoteTargetContentType = "image/png")
    assertQuoteTargetContentType(nonQuoteImageId, expectedContentType = "image/jpeg", expectedQuoteTargetContentType = null)
    assertQuoteTargetContentType(nonQuoteVideoId, expectedContentType = "video/webm", expectedQuoteTargetContentType = null)
  }

  @Test
  fun migrate_whenQuoteAttachmentWithNullContentType_setsQuoteTargetContentTypeToNull() {
    val quoteAttachmentId = insertAttachment(
      contentType = null,
      quote = 1
    )

    V289_AddQuoteTargetContentTypeColumn.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    val cursor = db
      .select(AttachmentTable.CONTENT_TYPE, AttachmentTable.QUOTE_TARGET_CONTENT_TYPE)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.ID} = ?", quoteAttachmentId.toString())
      .run()

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getString(it.getColumnIndexOrThrow(AttachmentTable.CONTENT_TYPE))).isNull()
      assertThat(it.getString(it.getColumnIndexOrThrow(AttachmentTable.QUOTE_TARGET_CONTENT_TYPE))).isNull()
    }
  }

  private fun insertAttachment(contentType: String?, quote: Int): Long {
    return db
      .insertInto(AttachmentTable.TABLE_NAME)
      .values(
        AttachmentTable.MESSAGE_ID to 1L,
        AttachmentTable.CONTENT_TYPE to contentType,
        AttachmentTable.DATA_FILE to "/fake/path/attachment.jpg",
        AttachmentTable.DATA_RANDOM to "/fake/path/attachment.jpg".toByteArray(),
        AttachmentTable.TRANSFER_STATE to AttachmentTable.TRANSFER_PROGRESS_DONE,
        AttachmentTable.QUOTE to quote,
        AttachmentTable.DATA_SIZE to 1024L,
        AttachmentTable.WIDTH to 100,
        AttachmentTable.HEIGHT to 100
      )
      .run()
  }

  private fun assertQuoteTargetContentType(attachmentId: Long, expectedContentType: String?, expectedQuoteTargetContentType: String?) {
    val cursor = db
      .select(AttachmentTable.CONTENT_TYPE, AttachmentTable.QUOTE_TARGET_CONTENT_TYPE)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.ID} = ?", attachmentId.toString())
      .run()

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)

      val actualContentType = it.getString(it.getColumnIndexOrThrow(AttachmentTable.CONTENT_TYPE))
      val actualQuoteTargetContentType = it.getString(it.getColumnIndexOrThrow(AttachmentTable.QUOTE_TARGET_CONTENT_TYPE))

      if (expectedContentType == null) {
        assertThat(actualContentType).isNull()
      } else {
        assertThat(actualContentType).isEqualTo(expectedContentType)
      }

      if (expectedQuoteTargetContentType == null) {
        assertThat(actualQuoteTargetContentType).isNull()
      } else {
        assertThat(actualQuoteTargetContentType).isEqualTo(expectedQuoteTargetContentType)
      }
    }
  }
}
