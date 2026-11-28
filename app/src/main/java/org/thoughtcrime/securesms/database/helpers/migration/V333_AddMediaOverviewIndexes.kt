/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Indexes for the media overview. One lets links be listed without reading every message. The other lets the
 * all-chats view read a page by size instead of joining and sorting every attachment, replacing
 * [V311_AddAttachmentMediaOverviewSizeIndex], which left out offloaded media.
 */
@Suppress("ClassName")
object V333_AddMediaOverviewIndexes : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS message_link_preview_date_index ON message (date_received, thread_id, from_recipient_id) WHERE link_previews IS NOT NULL AND story_type = 0 AND scheduled_date < 0 AND latest_revision_id IS NULL AND view_once = 0")
    db.execSQL("DROP INDEX IF EXISTS attachment_media_overview_size")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_media_overview_size_index ON attachment (data_size, display_order, _id, content_type) WHERE quote = 0 AND sticker_pack_id IS NULL")
  }
}
