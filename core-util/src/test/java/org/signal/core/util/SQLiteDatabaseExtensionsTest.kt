/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.Application
import androidx.sqlite.db.SupportSQLiteOpenHelper
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SQLiteDatabaseExtensionsTest {

  lateinit var db: SupportSQLiteOpenHelper

  companion object {
    const val TABLE_NAME = "test"
    const val ID = "_id"
    const val STRING_COLUMN = "string_column"
    const val LONG_COLUMN = "long_column"
    const val DOUBLE_COLUMN = "double_column"
    const val BLOB_COLUMN = "blob_column"
  }

  @Before
  fun setup() {
    db = InMemorySqliteOpenHelper.create(
      onCreate = { db ->
        db.execSQL("CREATE TABLE $TABLE_NAME ($ID INTEGER PRIMARY KEY AUTOINCREMENT, $STRING_COLUMN TEXT, $LONG_COLUMN INTEGER, $DOUBLE_COLUMN DOUBLE, $BLOB_COLUMN BLOB)")
      }
    )

    db.writableDatabase.insertInto(TABLE_NAME)
      .values(
        STRING_COLUMN to "asdf",
        LONG_COLUMN to 1,
        DOUBLE_COLUMN to 0.5f,
        BLOB_COLUMN to byteArrayOf(1, 2, 3)
      )
      .run()
  }

  @Test
  fun `update - content values work`() {
    val updateCount: Int = db.writableDatabase
      .update("test")
      .values(
        STRING_COLUMN to "asdf2",
        LONG_COLUMN to 2,
        DOUBLE_COLUMN to 1.5f,
        BLOB_COLUMN to byteArrayOf(4, 5, 6)
      )
      .where("$ID = ?", 1)
      .run()

    val record = readRecord(1)

    assertThat(updateCount).isEqualTo(1)
    assertThat(record).isNotNull()
    assertThat(record!!.id).isEqualTo(1)
    assertThat(record.stringColumn).isEqualTo("asdf2")
    assertThat(record.longColumn).isEqualTo(2)
    assertThat(record.doubleColumn).isEqualTo(1.5f)
    assertArrayEquals(record.blobColumn, byteArrayOf(4, 5, 6))
  }

  @Test
  fun `update - querying by blob works`() {
    val updateCount: Int = db.writableDatabase
      .update("test")
      .values(
        STRING_COLUMN to "asdf2"
      )
      .where("$BLOB_COLUMN = ?", byteArrayOf(1, 2, 3))
      .run()

    val record = readRecord(1)

    assertThat(updateCount).isEqualTo(1)
    assertThat(record).isNotNull()
    assertThat(record!!.stringColumn).isEqualTo("asdf2")
  }

  private fun readRecord(id: Long): TestRecord? {
    return db.readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$ID = ?", id)
      .run()
      .readToSingleObject {
        TestRecord(
          id = it.requireLong(ID),
          stringColumn = it.requireString(STRING_COLUMN),
          longColumn = it.requireLong(LONG_COLUMN),
          doubleColumn = it.requireFloat(DOUBLE_COLUMN),
          blobColumn = it.requireBlob(BLOB_COLUMN)
        )
      }
  }

  class TestRecord(
    val id: Long,
    val stringColumn: String?,
    val longColumn: Long,
    val doubleColumn: Float,
    val blobColumn: ByteArray?
  )
}
