package org.thoughtcrime.securesms.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.testing.TestDatabaseUtil

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MmsSmsDatabaseTest {

  private lateinit var mmsSmsTable: MmsSmsTable
  private lateinit var db: SQLiteDatabase

  @Before
  fun setup() {
    val sqlCipher = TestDatabaseUtil.inMemoryDatabase {
      execSQL(MmsTable.CREATE_TABLE)
      execSQL(SmsTable.CREATE_TABLE)
    }

    db = sqlCipher.writableDatabase
    mmsSmsTable = MmsSmsTable(ApplicationProvider.getApplicationContext(), sqlCipher)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `getConversationSnippet when single normal SMS, return SMS message id and transport as false`() {
    TestSms.insert(db)
    mmsSmsTable.getConversationSnippetCursor(1).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, CursorUtil.requireLong(cursor, MmsSmsColumns.ID))
      assertFalse(CursorUtil.requireBoolean(cursor, MmsSmsTable.TRANSPORT))
    }
  }

  @Test
  fun `getConversationSnippet when single normal MMS, return MMS message id and transport as true`() {
    TestMms.insert(db)
    mmsSmsTable.getConversationSnippetCursor(1).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, CursorUtil.requireLong(cursor, MmsSmsColumns.ID))
      assertTrue(CursorUtil.requireBoolean(cursor, MmsSmsTable.TRANSPORT))
    }
  }

  @Test
  fun `getConversationSnippet when single normal MMS then GV2 leave update message, return MMS message id and transport as true both times`() {
    val timestamp = System.currentTimeMillis()

    TestMms.insert(db, receivedTimestampMillis = timestamp + 2)
    mmsSmsTable.getConversationSnippetCursor(1).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, CursorUtil.requireLong(cursor, MmsSmsColumns.ID))
      assertTrue(CursorUtil.requireBoolean(cursor, MmsSmsTable.TRANSPORT))
    }

    TestSms.insert(db, receivedTimestampMillis = timestamp + 3, type = MmsSmsColumns.Types.BASE_SENDING_TYPE or MmsSmsColumns.Types.SECURE_MESSAGE_BIT or MmsSmsColumns.Types.PUSH_MESSAGE_BIT or MmsSmsColumns.Types.GROUP_V2_LEAVE_BITS)
    mmsSmsTable.getConversationSnippetCursor(1).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, CursorUtil.requireLong(cursor, MmsSmsColumns.ID))
      assertTrue(CursorUtil.requireBoolean(cursor, MmsSmsTable.TRANSPORT))
    }
  }
}
