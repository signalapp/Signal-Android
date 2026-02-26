package org.signal.benchmark

import android.os.Bundle
import android.widget.TextView
import org.signal.benchmark.setup.TestMessages
import org.signal.benchmark.setup.TestUsers
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.TestDbUtils
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences

class BenchmarkSetupActivity : BaseActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    when (intent.extras!!.getString("setup-type")) {
      "cold-start" -> setupColdStart()
      "conversation-open" -> setupConversationOpen()
      "message-send" -> setupMessageSend()
      "group-message-send" -> setupGroupMessageSend()
      "group-delivery-receipt" -> setupGroupReceipt(includeMsl = true)
      "group-read-receipt" -> setupGroupReceipt(enableReadReceipts = true)
    }

    val textView: TextView = TextView(this).apply {
      text = "done"
    }
    setContentView(textView)
  }

  private fun setupColdStart() {
    TestUsers.setupSelf()
    TestUsers.setupTestRecipients(50).forEach {
      val recipient: Recipient = Recipient.resolved(it)

      TestMessages.insertIncomingTextMessage(other = recipient, body = "Cool text message?!?!")
      TestMessages.insertIncomingImageMessage(other = recipient, attachmentCount = 1)
      TestMessages.insertIncomingImageMessage(other = recipient, attachmentCount = 2, body = "Album")
      TestMessages.insertIncomingImageMessage(other = recipient, body = "Test", attachmentCount = 1, failed = true)
      TestMessages.insertIncomingTextMessage(other = recipient, body = "Signal message")
      TestMessages.insertIncomingTextMessage(other = recipient, body = "Test")

      SignalDatabase.messages.setAllMessagesRead()

      SignalDatabase.threads.update(SignalDatabase.threads.getOrCreateThreadIdFor(recipient = recipient), true)
    }
  }

  private fun setupConversationOpen() {
    TestUsers.setupSelf()
    TestUsers.setupTestRecipient().let {
      val recipient: Recipient = Recipient.resolved(it)
      val messagesToAdd = 1000
      val generator: TestMessages.TimestampGenerator = TestMessages.TimestampGenerator(System.currentTimeMillis() - (messagesToAdd * 2000L) - 60_000L)

      for (i in 0 until messagesToAdd) {
        TestMessages.insertIncomingTextMessage(other = recipient, body = "Test message $i", timestamp = generator.nextTimestamp())
        TestMessages.insertOutgoingTextMessage(other = recipient, body = "Test message $i", timestamp = generator.nextTimestamp())
      }

      SignalDatabase.threads.update(SignalDatabase.threads.getOrCreateThreadIdFor(recipient = recipient), true)
    }
  }

  private fun setupMessageSend() {
    TestUsers.setupSelf()
    TestUsers.setupTestClients(1)
  }

  private fun setupGroupMessageSend() {
    TestUsers.setupSelf()
    TestUsers.setupGroup()
  }

  private fun setupGroupReceipt(includeMsl: Boolean = false, enableReadReceipts: Boolean = false) {
    TestUsers.setupSelf()
    val groupId = TestUsers.setupGroup()

    val groupRecipient = Recipient.externalGroupExact(groupId)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(groupRecipient)

    val messageIds = mutableListOf<Long>()
    val timestamps = mutableListOf<Long>()
    val baseTimestamp = 2_000_000L

    for (i in 0 until 100) {
      val timestamp = baseTimestamp + i
      val message = OutgoingMessage(
        recipient = groupRecipient,
        body = "Outgoing message $i",
        timestamp = timestamp,
        isSecure = true
      )
      val insert = SignalDatabase.messages.insertMessageOutbox(message, threadId, false, null)
      SignalDatabase.messages.markAsSent(insert.messageId, true)
      messageIds += insert.messageId
      timestamps += timestamp
    }

    if (includeMsl) {
      val selfId = Recipient.self().id
      val memberRecipientIds = SignalDatabase.groups.getGroup(groupId).get().members.filter { it != selfId }
      TestDbUtils.insertMessageSendLogEntries(messageIds, timestamps, memberRecipientIds)
    }

    if (enableReadReceipts) {
      TextSecurePreferences.setReadReceiptsEnabled(this, true)
    }
  }
}
