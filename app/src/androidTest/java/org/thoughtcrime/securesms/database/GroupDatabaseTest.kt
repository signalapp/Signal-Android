package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the GroupDatabase class
 */
@RunWith(AndroidJUnit4::class)
class GroupDatabaseTest {

  private lateinit var groupDatabase: GroupDatabase

  @Before
  fun setup() {
    groupDatabase = SignalDatabase.groups
    ensureDbEmpty()
  }

  @Test
  fun testTest() {
    assertTrue(true)
  }

  private fun ensureDbEmpty() {
    SignalDatabase.rawDatabase.rawQuery("SELECT COUNT(*) FROM ${GroupDatabase.TABLE_NAME}", null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(0, cursor.getLong(0))
    }
  }
}