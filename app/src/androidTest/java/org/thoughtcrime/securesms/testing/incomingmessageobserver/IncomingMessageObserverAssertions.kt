package org.thoughtcrime.securesms.testing.incomingmessageobserver

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.signal.benchmark.setup.OtherClient
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.SignalServiceAddress

/**
 * Reads database state produced by [IncomingMessageObserverRule]-driven tests. Import members
 * individually (e.g. `import …IncomingMessageObserverAssertions.assertMessageReceived`) so test
 * bodies stay terse.
 */
object IncomingMessageObserverAssertions {

  fun OtherClient.recipientId(): RecipientId = Recipient.externalPush(SignalServiceAddress(serviceId, e164)).id

  fun findIncomingMessage(from: OtherClient, body: String): MessageRecord? {
    val threadId = SignalDatabase.threads.getThreadIdFor(from.recipientId()) ?: return null
    return SignalDatabase.messages.getConversation(threadId).use { cursor ->
      MessageTable.MmsReader(cursor).use { reader -> reader.firstOrNull { it.body == body } }
    }
  }

  fun findIncomingGroupMessage(from: OtherClient, group: GroupHandle, body: String): MessageRecord? {
    val threadId = SignalDatabase.threads.getThreadIdFor(group.recipientId) ?: return null
    return SignalDatabase.messages.getConversation(threadId).use { cursor ->
      MessageTable.MmsReader(cursor).use { reader ->
        reader.firstOrNull { it.body == body && it.fromRecipient.id == from.recipientId() }
      }
    }
  }

  fun assertMessageReceived(from: OtherClient, body: String) {
    val record = findIncomingMessage(from, body)
    assertThat(record, "incoming message with body \"$body\" from ${from.serviceId} not found").isNotNull()
    assertThat(record!!.fromRecipient.id, "incoming message sender mismatch for body \"$body\"").isEqualTo(from.recipientId())
  }

  fun assertGroupMessageReceived(from: OtherClient, group: GroupHandle, body: String) {
    val record = findIncomingGroupMessage(from, group, body)
    assertThat(record, "group message \"$body\" from ${from.serviceId} in ${group.groupId} not found").isNotNull()
  }

  fun assertNoMessageReceived(from: OtherClient, body: String) {
    val record = findIncomingMessage(from, body)
    assertThat(record == null, "expected no message with body \"$body\" from ${from.serviceId}, but found one").isTrue()
  }

  fun assertNoMessagesInThread(recipientId: RecipientId) {
    val threadId = SignalDatabase.threads.getThreadIdFor(recipientId) ?: return
    val count = SignalDatabase.messages.getConversation(threadId).use { cursor -> cursor.count }
    assertThat(count, "expected thread for $recipientId to be empty, but message count was").isEqualTo(0)
  }

  fun assertDeliveryReceipt(outgoingMessageId: Long) {
    val record = SignalDatabase.messages.getMessageRecord(outgoingMessageId)
    assertThat(record.hasDeliveryReceipt(), "expected delivery receipt on outgoing message $outgoingMessageId, but none recorded").isTrue()
  }

  fun assertReadReceipt(outgoingMessageId: Long) {
    val record = SignalDatabase.messages.getMessageRecord(outgoingMessageId)
    assertThat(record.hasReadReceipt(), "expected read receipt on outgoing message $outgoingMessageId, but none recorded").isTrue()
  }
}
