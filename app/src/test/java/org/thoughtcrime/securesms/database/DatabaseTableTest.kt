/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testing.JdbcSqliteDatabase
import org.thoughtcrime.securesms.testing.TestSignalDatabase

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DatabaseTableTest {

  @Before
  fun setUp() {
    DatabaseTable.clearTableReferencesForTests()
  }

  @After
  fun tearDown() {
    DatabaseTable.clearTableReferencesForTests()
  }

  @Test
  fun snapshotDatabasesDoNotRegisterTheirTables() {
    createDatabase(SignalDatabase.DATABASE_NAME)

    val recipientTableCount = DatabaseTable.recipientIdDatabaseTables.size
    val threadTableCount = DatabaseTable.threadIdDatabaseTables.size
    assertTrue(recipientTableCount > 0)
    assertTrue(threadTableCount > 0)

    createDatabase("remote-signal-snapshot.db")

    assertEquals(recipientTableCount, DatabaseTable.recipientIdDatabaseTables.size)
    assertEquals(threadTableCount, DatabaseTable.threadIdDatabaseTables.size)
  }

  private fun createDatabase(name: String): TestSignalDatabase {
    val db = JdbcSqliteDatabase.createInMemory()
    return TestSignalDatabase(ApplicationProvider.getApplicationContext(), db, db, name)
  }
}
