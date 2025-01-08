package org.thoughtcrime.securesms.database

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testing.TestDatabaseUtil
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SmsDatabaseTest {
  private lateinit var db: AndroidSQLiteDatabase
  private lateinit var messageTable: MessageTable

  @Before
  fun setup() {
    val sqlCipher = TestDatabaseUtil.inMemoryDatabase {
      execSQL(MessageTable.CREATE_TABLE)
      MessageTable.CREATE_INDEXS.forEach {
        execSQL(it)
      }
    }

    db = sqlCipher.myWritableDatabase
    messageTable = MessageTable(ApplicationProvider.getApplicationContext(), sqlCipher)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `getThreadIdForMessage when no message absent for id, return -1`() {
    assertThat(messageTable.getThreadIdForMessage(1)).isEqualTo(-1)
  }

  @Test
  fun `getThreadIdForMessage when message present for id, return thread id`() {
    TestSms.insert(db)
    assertThat(messageTable.getThreadIdForMessage(1)).isEqualTo(1)
  }

  @Test
  fun `hasMeaningfulMessage when no messages, return false`() {
    assertThat(messageTable.hasMeaningfulMessage(1)).isFalse()
  }

  @Test
  fun `hasMeaningfulMessage when normal message, return true`() {
    TestSms.insert(db)
    assertThat(messageTable.hasMeaningfulMessage(1)).isTrue()
  }

  @Test
  fun `hasMeaningfulMessage when GV2 create message only, return true`() {
    TestSms.insert(db, type = MessageTypes.BASE_INBOX_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)
    assertThat(messageTable.hasMeaningfulMessage(1)).isTrue()
  }

  @Test
  fun `hasMeaningfulMessage when empty and then with ignored types, always return false`() {
    assertThat(messageTable.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(db, type = MessageTypes.IGNORABLE_TYPESMASK_WHEN_COUNTING)
    assertThat(messageTable.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(db, type = MessageTypes.PROFILE_CHANGE_TYPE)
    assertThat(messageTable.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(db, type = MessageTypes.CHANGE_NUMBER_TYPE)
    assertThat(messageTable.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(db, type = MessageTypes.RELEASE_CHANNEL_DONATION_REQUEST_TYPE)
    assertThat(messageTable.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(db, type = MessageTypes.SMS_EXPORT_TYPE)
    assertThat(messageTable.hasMeaningfulMessage(1)).isFalse()

    TestSms.insert(db, type = MessageTypes.BASE_INBOX_TYPE or MessageTypes.GROUP_V2_LEAVE_BITS)
    assertThat(messageTable.hasMeaningfulMessage(1)).isFalse()
  }
}
