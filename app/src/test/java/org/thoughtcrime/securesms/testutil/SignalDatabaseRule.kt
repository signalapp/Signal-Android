/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SearchTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testing.JdbcSqliteDatabase
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
     * Create an in-memory only database mimicking one created fresh for Signal. Uses sqlite-jdbc
     * (org.xerial) to provide a modern SQLite with FTS5 and JSON1 support, bypassing Robolectric's
     * limited native SQLite.
     */
    private fun inMemorySignalDatabase(): TestSignalDatabase {
      val db = JdbcSqliteDatabase.createInMemory()
      val signalDatabase = TestSignalDatabase(ApplicationProvider.getApplicationContext(), db, db)
      signalDatabase.onCreateTablesIndexesAndTriggers(signalDatabase.signalWritableDatabase)
      SearchTable.CREATE_TABLE.forEach { signalDatabase.signalWritableDatabase.execSQL(it) }
      SearchTable.CREATE_TRIGGERS.forEach { signalDatabase.signalWritableDatabase.execSQL(it) }

      return signalDatabase
    }
  }
}
