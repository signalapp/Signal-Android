package org.thoughtcrime.securesms.messages.incomingmessageobserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.testing.incomingmessageobserver.IncomingMessageObserverAssertions.assertMessageReceived
import org.thoughtcrime.securesms.testing.incomingmessageobserver.IncomingMessageObserverRule

@RunWith(AndroidJUnit4::class)
class IncomingTextMessageTest {

  @get:Rule
  val rule = IncomingMessageObserverRule(peerCount = 2)

  @Test
  fun deliveredOneToOneText_isPersisted() {
    rule.deliver { text("hello world") from rule.peers[0] }

    assertMessageReceived(from = rule.peers[0], body = "hello world")
  }
}
