package org.thoughtcrime.securesms.database

import android.database.MatrixCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.contacts.ContactsCursorRows
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord
import org.thoughtcrime.securesms.groups.GroupId
import java.security.SecureRandom

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
  fun queryInsertedGroupTest() {
    // GIVEN
    clearDB()
    createGroup("Title")

    // WHEN
    val groupContacts = groupCursor("T")

    // THEN
    assertTrue(groupContacts.count > 0)
  }

  private fun groupCursor(constraint: String): MatrixCursor {
    val groupReader = groupDatabase.getGroupsFilteredByTitle(constraint, true, false, false)
    val groupContacts = ContactsCursorRows.createMatrixCursor()
    var groupRecord: GroupRecord?
    while (groupReader.next.also { groupRecord = it } != null) {
      groupContacts.addRow(ContactsCursorRows.forGroup(groupRecord!!))
    }
    return groupContacts
  }

  private fun createGroup(title: String) {
    groupDatabase.create(
      GroupId.Mms.createMms(SecureRandom()),
      title,
      emptyList()
    )
  }

  private fun clearDB() {
    SignalDatabase.rawDatabase.delete(GroupDatabase.TABLE_NAME, null, null)
  }

  private fun ensureDbEmpty() {
    SignalDatabase.rawDatabase.rawQuery("SELECT COUNT(*) FROM ${GroupDatabase.TABLE_NAME}", null)
      .use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getLong(0))
      }
  }

  @After
  fun cleanup() {
    clearDB()
    ensureDbEmpty()
  }
}