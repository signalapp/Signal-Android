package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MmsSmsDatabaseTest {

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @Test
  fun `getConversationSnippet when single normal SMS, return SMS message id and transport as false`() {
    TestSms.insert(signalDatabaseRule.writeableDatabase)
    SignalDatabase.messages.getConversationSnippetCursor(1).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, CursorUtil.requireLong(cursor, MessageTable.ID))
    }
  }

  @Test
  fun `getConversationSnippet when single normal MMS, return MMS message id and transport as true`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase)
    SignalDatabase.messages.getConversationSnippetCursor(1).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, CursorUtil.requireLong(cursor, MessageTable.ID))
    }
  }

  @Test
  fun `getConversationSnippet when single normal MMS then GV2 leave update message, return MMS message id and transport as true both times`() {
    val timestamp = System.currentTimeMillis()

    TestMms.insert(signalDatabaseRule.writeableDatabase, receivedTimestampMillis = timestamp + 2)
    SignalDatabase.messages.getConversationSnippetCursor(1).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, CursorUtil.requireLong(cursor, MessageTable.ID))
    }

    TestSms.insert(signalDatabaseRule.writeableDatabase, receivedTimestampMillis = timestamp + 3, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_V2_LEAVE_BITS)
    SignalDatabase.messages.getConversationSnippetCursor(1).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, CursorUtil.requireLong(cursor, MessageTable.ID))
    }
  }
}
