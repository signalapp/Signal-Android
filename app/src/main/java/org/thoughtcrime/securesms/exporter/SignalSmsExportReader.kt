package org.thoughtcrime.securesms.exporter

import org.signal.core.util.logging.Log
import org.signal.smsexporter.ExportableMessage
import org.signal.smsexporter.SmsExportState
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.JsonUtils
import java.io.Closeable
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reads through the SMS and MMS databases for insecure messages that haven't been exported. Due to cursor size limitations
 * we "page" through the unexported messages to reduce chances of exceeding that limit.
 */
class SignalSmsExportReader(
  private val messageTable: MessageTable = SignalDatabase.messages
) : Iterable<ExportableMessage>, Closeable {

  companion object {
    private val TAG = Log.tag(SignalSmsExportReader::class.java)
    private const val CURSOR_LIMIT = 1000
  }

  private var messageReader: MessageTable.MmsReader? = null
  private var done: Boolean = false

  override fun iterator(): Iterator<ExportableMessage> {
    return ExportableMessageIterator()
  }

  fun getCount(): Int {
    return messageTable.unexportedInsecureMessagesCount
  }

  override fun close() {
    messageReader?.close()
  }

  private fun refreshReaders() {
    if (!done) {
      messageReader?.close()
      messageReader = null

      val refreshedMmsReader = MessageTable.mmsReaderFor(messageTable.getUnexportedInsecureMessages(CURSOR_LIMIT))
      if (refreshedMmsReader.count > 0) {
        messageReader = refreshedMmsReader
        return
      } else {
        refreshedMmsReader.close()
        done = true
      }
    }
  }

  private inner class ExportableMessageIterator : Iterator<ExportableMessage> {

    private var messageIterator: Iterator<MessageRecord>? = null

    private fun refreshIterators() {
      refreshReaders()
      messageIterator = messageReader?.iterator()
    }

    override fun hasNext(): Boolean {
      if (messageIterator?.hasNext() == true) {
        return true
      } else if (!done) {
        refreshIterators()
        if (messageIterator?.hasNext() == true) {
          return true
        }
      }

      return false
    }

    override fun next(): ExportableMessage {
      var record: MessageRecord? = null
      try {
        return if (messageIterator?.hasNext() == true) {
          record = messageIterator!!.next()
          readExportableMmsMessageFromRecord(record, messageReader!!.messageExportStateForCurrentRecord)
        } else {
          throw NoSuchElementException()
        }
      } catch (e: Throwable) {
        Log.w(TAG, "Error processing message: isMms: ${record?.isMms} type: ${record?.type}")
        throw e
      }
    }

    private fun readExportableMmsMessageFromRecord(record: MessageRecord, exportState: MessageExportState): ExportableMessage {
      val self = Recipient.self()
      val threadRecipient: Recipient? = SignalDatabase.threads.getRecipientForThreadId(record.threadId)
      val addresses: Set<String> = if (threadRecipient?.isMmsGroup == true) {
        Recipient
          .resolvedList(threadRecipient.participantIds)
          .filter { it != self }
          .map { r -> r.smsExportAddress() }
          .toSet()
      } else if (threadRecipient != null) {
        setOf(threadRecipient.smsExportAddress())
      } else {
        setOf(record.individualRecipient.smsExportAddress())
      }

      val parts: MutableList<ExportableMessage.Mms.Part> = mutableListOf()
      if (record.body.isNotBlank()) {
        parts.add(ExportableMessage.Mms.Part.Text(record.body))
      }

      if (record is MmsMessageRecord) {
        val slideDeck = record.slideDeck
        slideDeck
          .slides
          .filter { it.asAttachment() is DatabaseAttachment }
          .forEach {
            parts.add(
              ExportableMessage.Mms.Part.Stream(
                id = JsonUtils.toJson((it.asAttachment() as DatabaseAttachment).attachmentId),
                contentType = it.contentType
              )
            )
          }
      }

      val sender: String = if (record.isOutgoing) Recipient.self().smsExportAddress() else record.individualRecipient.smsExportAddress()

      return ExportableMessage.Mms(
        id = MessageId(record.id),
        exportState = mapExportState(exportState),
        addresses = addresses,
        dateReceived = record.dateReceived.milliseconds,
        dateSent = record.dateSent.milliseconds,
        isRead = true,
        isOutgoing = record.isOutgoing,
        parts = parts,
        sender = sender
      )
    }

    private fun mapExportState(messageExportState: MessageExportState): SmsExportState {
      return SmsExportState(
        messageId = messageExportState.messageId,
        startedRecipients = messageExportState.startedRecipientsList.toSet(),
        completedRecipients = messageExportState.completedRecipientsList.toSet(),
        startedAttachments = messageExportState.startedAttachmentsList.toSet(),
        completedAttachments = messageExportState.completedAttachmentsList.toSet(),
        progress = messageExportState.progress.let {
          when (it) {
            MessageExportState.Progress.INIT -> SmsExportState.Progress.INIT
            MessageExportState.Progress.STARTED -> SmsExportState.Progress.STARTED
            MessageExportState.Progress.COMPLETED -> SmsExportState.Progress.COMPLETED
            MessageExportState.Progress.UNRECOGNIZED -> SmsExportState.Progress.INIT
            null -> SmsExportState.Progress.INIT
          }
        }
      )
    }

    private fun Recipient.smsExportAddress(): String {
      return smsAddress.orElseGet { getDisplayName(ApplicationDependencies.getApplication()) }
    }
  }
}
