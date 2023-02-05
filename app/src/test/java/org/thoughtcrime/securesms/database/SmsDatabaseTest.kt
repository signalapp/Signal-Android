package org.thoughtcrime.securesms.database

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testing.TestDatabaseUtil
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase
import org.hamcrest.CoreMatchers.`is` as isEqual

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SmsDatabaseTest {

  private lateinit var db: AndroidSQLiteDatabase
  private lateinit var messageTable: MessageTable

  @Before
  fun setup() {
    val sqlCipher = TestDatabaseUtil.inMemoryDatabase {
      execSQL(MessageTable.CREATE_TABLE)
    }

    db = sqlCipher.writableDatabase
    messageTable = MessageTable(ApplicationProvider.getApplicationContext(), sqlCipher)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `getThreadIdForMessage when no message absent for id, return -1`() {
    assertThat(messageTable.getThreadIdForMessage(1), isEqual(-1))
  }

  @Test
  fun `getThreadIdForMessage when message present for id, return thread id`() {
    TestSms.insert(db)
    assertThat(messageTable.getThreadIdForMessage(1), isEqual(1))
  }

  @Test
  fun `hasMeaningfulMessage when no messages, return false`() {
    assertFalse(messageTable.hasMeaningfulMessage(1))
  }

  @Test
  fun `hasMeaningfulMessage when normal message, return true`() {
    TestSms.insert(db)
    assertTrue(messageTable.hasMeaningfulMessage(1))
  }

  @Test
  fun `hasMeaningfulMessage when GV2 create message only, return true`() {
    TestSms.insert(db, type = MessageTypes.BASE_INBOX_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)
    assertTrue(messageTable.hasMeaningfulMessage(1))
  }

  @Test
  fun `hasMeaningfulMessage when empty and then with ignored types, always return false`() {
    assertFalse(messageTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MessageTypes.IGNORABLE_TYPESMASK_WHEN_COUNTING)
    assertFalse(messageTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MessageTypes.PROFILE_CHANGE_TYPE)
    assertFalse(messageTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MessageTypes.CHANGE_NUMBER_TYPE)
    assertFalse(messageTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MessageTypes.BOOST_REQUEST_TYPE)
    assertFalse(messageTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MessageTypes.SMS_EXPORT_TYPE)
    assertFalse(messageTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MessageTypes.BASE_INBOX_TYPE or MessageTypes.GROUP_V2_LEAVE_BITS)
    assertFalse(messageTable.hasMeaningfulMessage(1))
  }
}
