package org.thoughtcrime.securesms.exporter

import android.database.Cursor
import org.signal.smsexporter.ExportableMessage
import org.signal.smsexporter.SmsExportState
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.JsonUtils
import java.io.Closeable
import kotlin.time.Duration.Companion.milliseconds

class SignalSmsExportReader(
  smsCursor: Cursor,
  mmsCursor: Cursor
) : Iterable<ExportableMessage>, Closeable {

  private val smsReader = SmsDatabase.readerFor(smsCursor)
  private val mmsReader = MmsDatabase.readerFor(mmsCursor)

  override fun iterator(): Iterator<ExportableMessage> {
    return ExportableMessageIterator()
  }

  fun getCount(): Int {
    return smsReader.count + mmsReader.count
  }

  override fun close() {
    smsReader.close()
    mmsReader.close()
  }

  private inner class ExportableMessageIterator : Iterator<ExportableMessage> {

    private val smsIterator = smsReader.iterator()
    private val mmsIterator = mmsReader.iterator()

    override fun hasNext(): Boolean {
      return smsIterator.hasNext() || mmsIterator.hasNext()
    }

    override fun next(): ExportableMessage {
      return if (smsIterator.hasNext()) {
        readExportableSmsMessageFromRecord(smsIterator.next())
      } else if (mmsIterator.hasNext()) {
        readExportableMmsMessageFromRecord(mmsIterator.next())
      } else {
        throw NoSuchElementException()
      }
    }
  }

  private fun readExportableMmsMessageFromRecord(record: MessageRecord): ExportableMessage {
    val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(record.threadId)!!
    val addresses = if (threadRecipient.isMmsGroup) {
      Recipient.resolvedList(threadRecipient.participantIds).map { it.requireSmsAddress() }.toSet()
    } else {
      setOf(threadRecipient.requireSmsAddress())
    }

    val parts: MutableList<ExportableMessage.Mms.Part> = mutableListOf()
    if (record.body.isNotBlank()) {
      parts.add(ExportableMessage.Mms.Part.Text(record.body))
    }

    if (record is MmsMessageRecord) {
      val slideDeck = record.slideDeck
      slideDeck.slides.forEach {
        parts.add(
          ExportableMessage.Mms.Part.Stream(
            id = JsonUtils.toJson((it.asAttachment() as DatabaseAttachment).attachmentId),
            contentType = it.contentType
          )
        )
      }
    }

    val sender = if (record.isOutgoing) Recipient.self().requireSmsAddress() else record.individualRecipient.requireSmsAddress()

    return ExportableMessage.Mms(
      id = record.id.toString(),
      exportState = mapExportState(mmsReader.messageExportStateForCurrentRecord),
      addresses = addresses,
      dateReceived = record.dateReceived.milliseconds,
      dateSent = record.dateSent.milliseconds,
      isRead = true,
      isOutgoing = record.isOutgoing,
      parts = parts,
      sender = sender
    )
  }

  private fun readExportableSmsMessageFromRecord(record: MessageRecord): ExportableMessage {
    val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(record.threadId)!!

    return if (threadRecipient.isMmsGroup) {
      readExportableMmsMessageFromRecord(record)
    } else {
      ExportableMessage.Sms(
        id = record.id.toString(),
        exportState = mapExportState(smsReader.messageExportStateForCurrentRecord),
        address = record.recipient.requireSmsAddress(),
        dateReceived = record.dateReceived.milliseconds,
        dateSent = record.dateSent.milliseconds,
        isRead = true,
        isOutgoing = record.isOutgoing,
        body = record.body
      )
    }
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
}
