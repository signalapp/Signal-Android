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
  private lateinit var smsTable: SmsTable

  @Before
  fun setup() {
    val sqlCipher = TestDatabaseUtil.inMemoryDatabase {
      execSQL(SmsTable.CREATE_TABLE)
    }

    db = sqlCipher.writableDatabase
    smsTable = SmsTable(ApplicationProvider.getApplicationContext(), sqlCipher)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `getThreadIdForMessage when no message absent for id, return -1`() {
    assertThat(smsTable.getThreadIdForMessage(1), isEqual(-1))
  }

  @Test
  fun `getThreadIdForMessage when message present for id, return thread id`() {
    TestSms.insert(db)
    assertThat(smsTable.getThreadIdForMessage(1), isEqual(1))
  }

  @Test
  fun `hasMeaningfulMessage when no messages, return false`() {
    assertFalse(smsTable.hasMeaningfulMessage(1))
  }

  @Test
  fun `hasMeaningfulMessage when normal message, return true`() {
    TestSms.insert(db)
    assertTrue(smsTable.hasMeaningfulMessage(1))
  }

  @Test
  fun `hasMeaningfulMessage when GV2 create message only, return true`() {
    TestSms.insert(db, type = MmsSmsColumns.Types.BASE_INBOX_TYPE or MmsSmsColumns.Types.SECURE_MESSAGE_BIT or MmsSmsColumns.Types.GROUP_V2_BIT or MmsSmsColumns.Types.GROUP_UPDATE_BIT)
    assertTrue(smsTable.hasMeaningfulMessage(1))
  }

  @Test
  fun `hasMeaningfulMessage when empty and then with ignored types, always return false`() {
    assertFalse(smsTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = SmsTable.IGNORABLE_TYPESMASK_WHEN_COUNTING)
    assertFalse(smsTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MmsSmsColumns.Types.PROFILE_CHANGE_TYPE)
    assertFalse(smsTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MmsSmsColumns.Types.CHANGE_NUMBER_TYPE)
    assertFalse(smsTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MmsSmsColumns.Types.BOOST_REQUEST_TYPE)
    assertFalse(smsTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MmsSmsColumns.Types.SMS_EXPORT_TYPE)
    assertFalse(smsTable.hasMeaningfulMessage(1))

    TestSms.insert(db, type = MmsSmsColumns.Types.BASE_INBOX_TYPE or MmsSmsColumns.Types.GROUP_V2_LEAVE_BITS)
    assertFalse(smsTable.hasMeaningfulMessage(1))
  }
}
