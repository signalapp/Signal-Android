/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.rules.ExternalResource
import org.signal.core.util.PartAuthorityUris
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.database.DatabaseTable
import org.thoughtcrime.securesms.database.RemappedRecordsTestHelper
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SearchTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.JdbcSqliteDatabase
import org.thoughtcrime.securesms.testing.TestSignalDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherSQLiteDatabase

class SignalDatabaseRule : ExternalResource() {

  lateinit var signalDatabase: TestSignalDatabase

  val readableDatabase: SQLiteDatabase
    get() = signalDatabase.signalReadableDatabase

  val writeableDatabase: SQLiteDatabase
    get() = signalDatabase.signalWritableDatabase

  override fun before() {
    PartAuthorityUris.init(BuildConfig.APPLICATION_ID)

    RecipientId.clearCache()
    RemappedRecordsTestHelper.resetInstance()
    DatabaseTable.clearTableReferencesForTests()

    signalDatabase = inMemorySignalDatabase()

    mockkObject(SignalDatabase)
    every { SignalDatabase.instance } returns signalDatabase
    every { SignalDatabase.inTransaction } answers { signalDatabase.signalWritableDatabase.inTransaction() }
    every { SignalDatabase.rawDatabase } returns rawDatabaseDelegatingTransactionsToTestDatabase()
  }

  /**
   * The test database is backed by sqlite-jdbc rather than SQLCipher, so there's no real
   * [SQLCipherSQLiteDatabase] to hand out. Callers that grab [SignalDatabase.rawDatabase] do so to control
   * transactions, so that's what we wire up here. Everything else is relaxed and does nothing.
   */
  private fun rawDatabaseDelegatingTransactionsToTestDatabase(): SQLCipherSQLiteDatabase {
    return mockk(relaxed = true) {
      every { beginTransaction() } answers { writeableDatabase.beginTransaction() }
      every { beginTransactionNonExclusive() } answers { writeableDatabase.beginTransactionNonExclusive() }
      every { setTransactionSuccessful() } answers { writeableDatabase.setTransactionSuccessful() }
      every { endTransaction() } answers { writeableDatabase.endTransaction() }
      every { inTransaction() } answers { writeableDatabase.inTransaction() }
    }
  }

  override fun after() {
    unmockkObject(SignalDatabase)
    signalDatabase.close()
    RecipientId.clearCache()
    RemappedRecordsTestHelper.resetInstance()
    DatabaseTable.clearTableReferencesForTests()
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
