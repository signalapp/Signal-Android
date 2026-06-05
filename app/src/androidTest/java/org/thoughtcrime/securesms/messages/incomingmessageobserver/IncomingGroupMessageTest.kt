package org.thoughtcrime.securesms.messages.incomingmessageobserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.testing.incomingmessageobserver.IncomingMessageObserverAssertions.assertGroupMessageReceived
import org.thoughtcrime.securesms.testing.incomingmessageobserver.IncomingMessageObserverRule

@RunWith(AndroidJUnit4::class)
class IncomingGroupMessageTest {

  @get:Rule
  val rule = IncomingMessageObserverRule(peerCount = 5)

  @Test
  fun deliveredGroupText_isPersistedInGroupThread() {
    val group = rule.testGroup

    rule.deliver { groupText("hello group", group = group) from rule.peers[0] }

    assertGroupMessageReceived(from = rule.peers[0], group = group, body = "hello group")
  }

  @Test
  fun multipleGroupMembers_messagesPersistedFromEach() {
    val group = rule.testGroup

    rule.deliver {
      groupText("from peer 0", group = group) from rule.peers[0]
      groupText("from peer 1", group = group) from rule.peers[1]
      groupText("from peer 2", group = group) from rule.peers[2]
    }

    assertGroupMessageReceived(from = rule.peers[0], group = group, body = "from peer 0")
    assertGroupMessageReceived(from = rule.peers[1], group = group, body = "from peer 1")
    assertGroupMessageReceived(from = rule.peers[2], group = group, body = "from peer 2")
  }
}
