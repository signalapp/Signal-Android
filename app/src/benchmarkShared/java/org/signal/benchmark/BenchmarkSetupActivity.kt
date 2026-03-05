package org.signal.benchmark

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    var setupComplete by mutableStateOf(false)

    setContent {
      if (setupComplete) {
        Text("done")
      } else {
        CircularProgressIndicator()
      }
    }

    lifecycleScope.launch(Dispatchers.IO) {
      when (intent.extras!!.getString("setup-type")) {
        "cold-start" -> setupColdStart()
        "conversation-open" -> setupConversationOpen()
        "message-send" -> setupMessageSend()
        "group-message-send" -> setupGroupMessageSend()
        "group-delivery-receipt" -> setupGroupReceipt(includeMsl = true)
        "group-read-receipt" -> setupGroupReceipt(enableReadReceipts = true)
        "thread-delete" -> setupThreadDelete()
        "thread-delete-group" -> setupThreadDeleteGroup()
      }
      setupComplete = true
    }
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

  private fun setupThreadDelete() {
    TestUsers.setupSelf()
    val recipientIds = TestUsers.setupTestRecipients(2)
    val recipient = Recipient.resolved(recipientIds[0])
    val reactionAuthor = recipientIds[1]
    val messagesToAdd = 20_000
    val generator = TestMessages.TimestampGenerator(System.currentTimeMillis() - (messagesToAdd * 2000L) - 60_000L)

    for (i in 0 until messagesToAdd) {
      val timestamp = generator.nextTimestamp()
      when {
        i % 20 == 0 -> TestMessages.insertIncomingVoiceMessage(other = recipient, timestamp = timestamp)
        i % 4 == 0 -> TestMessages.insertIncomingImageMessage(other = recipient, attachmentCount = 1, timestamp = timestamp)
        else -> TestMessages.insertIncomingTextMessage(other = recipient, body = "Message $i", timestamp = timestamp)
      }
    }

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient = recipient)
    TestDbUtils.insertReactionsForThread(threadId, reactionAuthor, moduloFilter = 5)

    SignalDatabase.threads.update(threadId, true)
  }

  private fun setupThreadDeleteGroup() {
    TestUsers.setupSelf()
    val groupId = TestUsers.setupGroup()
    val groupRecipient = Recipient.externalGroupExact(groupId)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(groupRecipient)

    val selfId = Recipient.self().id
    val memberRecipientIds = SignalDatabase.groups.getGroup(groupId).get().members.filter { it != selfId }

    val messagesToAdd = 20_000
    val generator = TestMessages.TimestampGenerator(System.currentTimeMillis() - (messagesToAdd * 2000L) - 60_000L)

    for (i in 0 until messagesToAdd) {
      val timestamp = generator.nextTimestamp()
      when {
        i % 4 == 0 -> TestMessages.insertOutgoingImageMessage(other = groupRecipient, attachmentCount = 1, timestamp = timestamp)
        else -> {
          val message = OutgoingMessage(
            recipient = groupRecipient,
            body = "Message $i",
            timestamp = timestamp,
            isSecure = true
          )
          val insert = SignalDatabase.messages.insertMessageOutbox(message, threadId, false, null)
          SignalDatabase.messages.markAsSent(insert.messageId, true)
        }
      }
    }

    TestDbUtils.insertGroupReceiptsForThread(threadId, memberRecipientIds)
    TestDbUtils.insertReactionsForThread(threadId, memberRecipientIds[0], moduloFilter = 5)
    TestDbUtils.insertMentionsForThread(threadId, memberRecipientIds[0], moduloFilter = 10)

    SignalDatabase.threads.update(threadId, true)
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
