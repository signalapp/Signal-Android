package org.signal.benchmark

import android.os.Bundle
import android.widget.TextView
import org.signal.benchmark.setup.TestMessages
import org.signal.benchmark.setup.TestUsers
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient

class BenchmarkSetupActivity : BaseActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    when (intent.extras!!.getString("setup-type")) {
      "cold-start" -> setupColdStart()
      "conversation-open" -> setupConversationOpen()
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

      val voiceMessageId = TestMessages.insertIncomingVoiceMessage(other = recipient, timestamp = generator.nextTimestamp())
      val mmsRecord = SignalDatabase.messages.getMessageRecord(voiceMessageId) as MediaMmsMessageRecord
      TestMessages.insertOutgoingImageMessage(other = recipient, body = "test", 2, generator.nextTimestamp())
      TestMessages.insertIncomingTextMessage(other = recipient, "reply to the test message", generator.nextTimestamp())
      TestMessages.insertIncomingQuoteTextMessage(other = recipient, quote = QuoteModel(mmsRecord.timestamp, recipient.id, "Fake voice message text", false, mmsRecord.slideDeck.asAttachments(), null, QuoteModel.Type.NORMAL, null), body = "Here is a cool quote", timestamp = generator.nextTimestamp())
      TestMessages.insertOutgoingTextMessage(other = recipient, body = "longaweorijoaijwerijoiajwer", timestamp = generator.nextTimestamp())

      SignalDatabase.threads.update(SignalDatabase.threads.getOrCreateThreadIdFor(recipient = recipient), true)
    }
  }
}
