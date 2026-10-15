/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider

/**
 * Helper to create an in-memory database used for testing SQLite stuff.
 */
object InMemorySqliteOpenHelper {
  fun create(
    onCreate: (db: SupportSQLiteDatabase) -> Unit,
    onUpgrade: (db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) -> Unit = { _, _, _ -> }
  ): SupportSQLiteOpenHelper {
    val configuration = SupportSQLiteOpenHelper.Configuration(
      context = ApplicationProvider.getApplicationContext(),
      name = "test",
      callback = object : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = onUpgrade(db, oldVersion, newVersion)
      },
      useNoBackupDirectory = false,
      allowDataLossOnRecovery = true
    )

    return FrameworkSQLiteOpenHelperFactory().create(configuration)
  }
}
