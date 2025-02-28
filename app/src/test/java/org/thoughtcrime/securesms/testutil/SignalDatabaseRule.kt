/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testing.TestSignalDatabase

class SignalDatabaseRule : ExternalResource() {

  lateinit var signalDatabase: TestSignalDatabase

  val readableDatabase: SQLiteDatabase
    get() = signalDatabase.signalReadableDatabase

  val writeableDatabase: SQLiteDatabase
    get() = signalDatabase.signalWritableDatabase

  override fun before() {
    signalDatabase = inMemorySignalDatabase()

    mockkObject(SignalDatabase)
    every { SignalDatabase.instance } returns signalDatabase
  }

  override fun after() {
    unmockkObject(SignalDatabase)
    signalDatabase.close()
  }

  companion object {
    /**
     * Create an in-memory only database mimicking one created fresh for Signal. This includes
     * all non-FTS tables, indexes, and triggers.
     */
    private fun inMemorySignalDatabase(): TestSignalDatabase {
      val configuration = SupportSQLiteOpenHelper.Configuration(
        context = ApplicationProvider.getApplicationContext(),
        name = "test",
        callback = object : SupportSQLiteOpenHelper.Callback(1) {
          override fun onCreate(db: SupportSQLiteDatabase) = Unit
          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        },
        useNoBackupDirectory = false,
        allowDataLossOnRecovery = true
      )

      val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
      val signalDatabase = TestSignalDatabase(ApplicationProvider.getApplicationContext(), helper)
      signalDatabase.onCreateTablesIndexesAndTriggers(signalDatabase.signalWritableDatabase)

      return signalDatabase
    }
  }
}
