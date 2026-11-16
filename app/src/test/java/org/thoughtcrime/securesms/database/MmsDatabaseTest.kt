package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MmsDatabaseTest {
  companion object {
    private const val SENT_SECURE = MessageTypes.BASE_SENT_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT
  }

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @Test
  fun `getLatestGroupQuitTimestamp when only normal message, return -1`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, sentTimeMillis = 1, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT)
    assertEquals(-1, SignalDatabase.messages.getLatestGroupQuitTimestamp(1, 4))
  }

  @Test
  fun `getLatestGroupQuitTimestamp when legacy quit, return message timestamp`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, sentTimeMillis = 2, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_LEAVE_BIT)
    assertEquals(2, SignalDatabase.messages.getLatestGroupQuitTimestamp(1, 4))
  }

  @Test
  fun `getLatestGroupQuitTimestamp when GV2 leave update message, return -1`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, sentTimeMillis = 3, type = MessageTypes.BASE_SENDING_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_LEAVE_BIT or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)
    assertEquals(-1, SignalDatabase.messages.getLatestGroupQuitTimestamp(1, 4))
  }

  @Test
  fun `getOutgoingSecureMessageCount when only normal sent message, return 1`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, type = SENT_SECURE)
    assertEquals(1, SignalDatabase.messages.getOutgoingSecureMessageCount(1))
  }

  @Test
  fun `getOutgoingSecureMessageCount when only GV2 leave update, return 0`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, type = SENT_SECURE or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT or MessageTypes.GROUP_LEAVE_BIT)
    assertEquals(0, SignalDatabase.messages.getOutgoingSecureMessageCount(1))
  }

  @Test
  fun `getOutgoingSecureMessageCount when only GV2 non-leave update, return 0`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, type = SENT_SECURE or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)
    assertEquals(0, SignalDatabase.messages.getOutgoingSecureMessageCount(1))
  }

  @Test
  fun `getOutgoingSecureMessageCount when only legacy quit, return 0`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, type = SENT_SECURE or MessageTypes.GROUP_LEAVE_BIT)
    assertEquals(0, SignalDatabase.messages.getOutgoingSecureMessageCount(1))
  }

  @Test
  fun `getOutgoingSecureMessageCount when group updates and normal sent message, return 1`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, type = SENT_SECURE or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, type = SENT_SECURE or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT or MessageTypes.GROUP_LEAVE_BIT)
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, type = SENT_SECURE)
    assertEquals(1, SignalDatabase.messages.getOutgoingSecureMessageCount(1))
  }

  @Test
  fun `getOutgoingSecureMessageCount when normal sent message in another thread, return 0`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 2, type = SENT_SECURE)
    assertEquals(0, SignalDatabase.messages.getOutgoingSecureMessageCount(1))
  }

  @Test
  fun `Given no stories in database, when I getStoryViewState, then I expect NONE`() {
    assertEquals(StoryViewState.NONE, SignalDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given stories in database not in thread 1, when I getStoryViewState for thread 1, then I expect NONE`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 2, storyType = StoryType.STORY_WITH_REPLIES)
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 2, storyType = StoryType.STORY_WITH_REPLIES)
    assertEquals(StoryViewState.NONE, SignalDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given viewed incoming stories in database, when I getStoryViewState, then I expect VIEWED`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = true)
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = true)
    assertEquals(StoryViewState.VIEWED, SignalDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given unviewed incoming stories in database, when I getStoryViewState, then I expect UNVIEWED`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = false)
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = false)
    assertEquals(StoryViewState.UNVIEWED, SignalDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given mix of viewed and unviewed incoming stories in database, when I getStoryViewState, then I expect UNVIEWED`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = true)
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, viewed = false)
    assertEquals(StoryViewState.UNVIEWED, SignalDatabase.messages.getStoryViewState(1))
  }

  @Test
  fun `Given only outgoing story in database, when I getStoryViewState, then I expect VIEWED`() {
    TestMms.insert(signalDatabaseRule.writeableDatabase, threadId = 1, storyType = StoryType.STORY_WITH_REPLIES, type = MessageTypes.BASE_OUTBOX_TYPE)
    assertEquals(StoryViewState.VIEWED, SignalDatabase.messages.getStoryViewState(1))
  }
}
