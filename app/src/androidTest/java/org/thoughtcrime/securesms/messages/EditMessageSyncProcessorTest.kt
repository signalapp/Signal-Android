package org.thoughtcrime.securesms.messages

import android.database.Cursor
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.ThreadUtil
import org.signal.core.util.readToList
import org.signal.core.util.select
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.toBodyRangeList
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.MessageContentFuzzer
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.util.MessageTableTestUtils
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.EditMessage
import org.whispersystems.signalservice.internal.push.SyncMessage
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class EditMessageSyncProcessorTest {

  companion object {
    private val IGNORE_MESSAGE_COLUMNS = listOf(
      MessageTable.DATE_RECEIVED,
      MessageTable.NOTIFIED_TIMESTAMP,
      MessageTable.REACTIONS_LAST_SEEN,
      MessageTable.NOTIFIED
    )

    private val IGNORE_ATTACHMENT_COLUMNS = listOf(
      AttachmentTable.TRANSFER_FILE
    )
  }

  @get:Rule
  val harness = SignalActivityRule()

  private lateinit var processorV2: MessageContentProcessor
  private lateinit var testResult: TestResults
  private var envelopeTimestamp: Long = 0

  @Before
  fun setup() {
    processorV2 = MessageContentProcessor(harness.context)
    envelopeTimestamp = System.currentTimeMillis()
    testResult = TestResults()
  }

  @Test
  fun textMessage() {
    var originalTimestamp = envelopeTimestamp + 200
    for (i in 1..10) {
      originalTimestamp += 400

      val toRecipient = Recipient.resolved(harness.others[0])

      val content = MessageContentFuzzer.fuzzTextMessage()
      val metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, toRecipient.id)
      val syncContent = Content.Builder().syncMessage(
        SyncMessage.Builder().sent(
          SyncMessage.Sent.Builder()
            .destinationServiceId(metadata.destinationServiceId.toString())
            .timestamp(originalTimestamp)
            .expirationStartTimestamp(originalTimestamp)
            .message(content.dataMessage)
            .build()
        ).build()
      ).build()
      SignalDatabase.recipients.setExpireMessages(toRecipient.id, content.dataMessage?.expireTimer ?: 0, content.dataMessage?.expireTimerVersion ?: 1)
      val syncTextMessage = TestMessage(
        envelope = MessageContentFuzzer.envelope(originalTimestamp),
        content = syncContent,
        metadata = metadata,
        serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(originalTimestamp)
      )

      val editTimestamp = originalTimestamp + 200
      val editedContent = MessageContentFuzzer.fuzzTextMessage()
      val editSyncContent = Content.Builder().syncMessage(
        SyncMessage.Builder().sent(
          SyncMessage.Sent.Builder()
            .destinationServiceId(metadata.destinationServiceId.toString())
            .timestamp(editTimestamp)
            .expirationStartTimestamp(editTimestamp)
            .editMessage(
              EditMessage.Builder()
                .dataMessage(editedContent.dataMessage)
                .targetSentTimestamp(originalTimestamp)
                .build()
            )
            .build()
        ).build()
      ).build()

      val syncEditMessage = TestMessage(
        envelope = MessageContentFuzzer.envelope(editTimestamp),
        content = editSyncContent,
        metadata = metadata,
        serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(editTimestamp)
      )

      testResult.runSync(listOf(syncTextMessage, syncEditMessage))

      SignalDatabase.recipients.setExpireMessages(toRecipient.id, (content.dataMessage?.expireTimer ?: 0) / 1000, content.dataMessage?.expireTimerVersion ?: 1)
      val originalTextMessage = OutgoingMessage(
        threadRecipient = toRecipient,
        sentTimeMillis = originalTimestamp,
        body = content.dataMessage?.body ?: "",
        expiresIn = content.dataMessage?.expireTimer?.seconds?.inWholeMilliseconds ?: 0,
        isUrgent = true,
        isSecure = true,
        bodyRanges = content.dataMessage?.bodyRanges.toBodyRangeList()
      )
      val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(toRecipient)
      val originalMessageId = SignalDatabase.messages.insertMessageOutbox(originalTextMessage, threadId, false, null)
      SignalDatabase.messages.markAsSent(originalMessageId, true)
      if ((content.dataMessage?.expireTimer ?: 0) > 0) {
        SignalDatabase.messages.markExpireStarted(originalMessageId, originalTimestamp)
      }

      val editMessage = OutgoingMessage(
        threadRecipient = toRecipient,
        sentTimeMillis = editTimestamp,
        body = editedContent.dataMessage?.body ?: "",
        expiresIn = content.dataMessage?.expireTimer?.seconds?.inWholeMilliseconds ?: 0,
        isUrgent = true,
        isSecure = true,
        bodyRanges = editedContent.dataMessage?.bodyRanges.toBodyRangeList(),
        messageToEdit = originalMessageId
      )

      val editMessageId = SignalDatabase.messages.insertMessageOutbox(editMessage, threadId, false, null)
      SignalDatabase.messages.markAsSent(editMessageId, true)

      if ((content.dataMessage?.expireTimer ?: 0) > 0) {
        SignalDatabase.messages.markExpireStarted(editMessageId, originalTimestamp)
      }
      testResult.collectLocal()
      testResult.assert()
    }
  }

  private inner class TestResults {

    private lateinit var localMessages: List<List<Pair<String, String?>>>
    private lateinit var localAttachments: List<List<Pair<String, String?>>>

    private lateinit var syncMessages: List<List<Pair<String, String?>>>
    private lateinit var syncAttachments: List<List<Pair<String, String?>>>

    fun collectLocal() {
      harness.inMemoryLogger.clear()

      localMessages = dumpMessages()
      localAttachments = dumpAttachments()

      cleanup()
    }

    fun runSync(messages: List<TestMessage>) {
      messages.forEach { (envelope, content, metadata, serverDeliveredTimestamp) ->
        if (content.syncMessage != null) {
          processorV2.process(
            envelope,
            content,
            metadata,
            serverDeliveredTimestamp,
            false
          )
          ThreadUtil.sleep(1)
        }
      }
      harness.inMemoryLogger.clear()

      syncMessages = dumpMessages()
      syncAttachments = dumpAttachments()

      cleanup()
    }

    fun cleanup() {
      SignalDatabase.rawDatabase.withinTransaction { db ->
        SignalDatabase.threads.deleteAllConversations()
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = '${MessageTable.TABLE_NAME}'")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = '${ThreadTable.TABLE_NAME}'")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = '${AttachmentTable.TABLE_NAME}'")
      }
    }

    fun assert() {
      syncMessages.zip(localMessages)
        .forEach { (v2, v1) ->
          v2.assertIs(v1)
        }

      syncAttachments.zip(localAttachments)
        .forEach { (v2, v1) ->
          v2.assertIs(v1)
        }
    }

    private fun dumpMessages(): List<List<Pair<String, String?>>> {
      return dumpTable(MessageTable.TABLE_NAME)
        .map { row ->
          val newRow = row.toMutableList()
          newRow.removeIf { IGNORE_MESSAGE_COLUMNS.contains(it.first) }
          newRow
        }
    }

    private fun dumpAttachments(): List<List<Pair<String, String?>>> {
      return dumpTable(AttachmentTable.TABLE_NAME)
        .map { row ->
          val newRow = row.toMutableList()
          newRow.removeIf { IGNORE_ATTACHMENT_COLUMNS.contains(it.first) }
          newRow
        }
    }

    private fun dumpTable(table: String): List<List<Pair<String, String?>>> {
      return SignalDatabase.rawDatabase
        .select()
        .from(table)
        .run()
        .readToList { cursor ->
          val map: List<Pair<String, String?>> = cursor.columnNames.map { column ->
            val index = cursor.getColumnIndex(column)
            var data: String? = when (cursor.getType(index)) {
              Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(cursor.getBlob(index), 0)
              else -> cursor.getString(index)
            }
            if (table == MessageTable.TABLE_NAME && column == MessageTable.TYPE) {
              data = MessageTableTestUtils.typeColumnToString(cursor.getLong(index))
            }

            column to data
          }
          map
        }
    }
  }
}
