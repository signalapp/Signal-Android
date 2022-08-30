package org.thoughtcrime.securesms.exporter

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.signal.smsexporter.ExportableMessage
import org.signal.smsexporter.SmsExportService
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.JsonUtils
import java.io.InputStream

/**
 * Service which integrates the SMS exporter functionality.
 */
class SignalSmsExportService : SmsExportService() {

  companion object {
    /**
     * Launches the export service and immediately begins exporting messages.
     */
    fun start(context: Context) {
      ContextCompat.startForegroundService(context, Intent(context, SignalSmsExportService::class.java))
    }
  }

  private var reader: SignalSmsExportReader? = null

  override fun getNotification(progress: Int, total: Int): ExportNotification {
    return ExportNotification(
      NotificationIds.SMS_EXPORT_SERVICE,
      NotificationCompat.Builder(this, NotificationChannels.BACKUPS)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.SignalSmsExportService__exporting_messages))
        .setProgress(total, progress, false)
        .build()
    )
  }

  override fun getUnexportedMessageCount(): Int {
    ensureReader()
    return reader!!.getCount()
  }

  override fun getUnexportedMessages(): Iterable<ExportableMessage> {
    ensureReader()
    return reader!!
  }

  override fun onMessageExportStarted(exportableMessage: ExportableMessage) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().setProgress(MessageExportState.Progress.STARTED).build()
    }
  }

  override fun onMessageExportSucceeded(exportableMessage: ExportableMessage) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().setProgress(MessageExportState.Progress.COMPLETED).build()
    }

    SignalDatabase.mmsSms.markMessageExported(exportableMessage.getMessageId())
  }

  override fun onMessageExportFailed(exportableMessage: ExportableMessage) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().setProgress(MessageExportState.Progress.INIT).build()
    }
  }

  override fun onMessageIdCreated(exportableMessage: ExportableMessage, messageId: Long) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().setMessageId(messageId).build()
    }
  }

  override fun onAttachmentPartExportStarted(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().addStartedAttachments(part.contentId).build()
    }
  }

  override fun onAttachmentPartExportSucceeded(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().addCompletedAttachments(part.contentId).build()
    }
  }

  override fun onAttachmentPartExportFailed(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      val startedAttachments = it.startedAttachmentsList - part.contentId
      it.toBuilder().clearStartedAttachments().addAllStartedAttachments(startedAttachments).build()
    }
  }

  override fun onRecipientExportStarted(exportableMessage: ExportableMessage, recipient: String) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().addStartedRecipients(recipient).build()
    }
  }

  override fun onRecipientExportSucceeded(exportableMessage: ExportableMessage, recipient: String) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().addCompletedRecipients(recipient).build()
    }
  }

  override fun onRecipientExportFailed(exportableMessage: ExportableMessage, recipient: String) {
    SignalDatabase.mmsSms.updateMessageExportState(exportableMessage.getMessageId()) {
      val startedAttachments = it.startedRecipientsList - recipient
      it.toBuilder().clearStartedRecipients().addAllStartedRecipients(startedAttachments).build()
    }
  }

  override fun getInputStream(part: ExportableMessage.Mms.Part): InputStream {
    return SignalDatabase.attachments.getAttachmentStream(JsonUtils.fromJson(part.contentId, AttachmentId::class.java), 0)
  }

  override fun onExportPassCompleted() {
    reader?.close()
  }

  private fun ExportableMessage.getMessageId(): MessageId {
    return when (this) {
      is ExportableMessage.Mms -> MessageId(id.toLong(), true)
      is ExportableMessage.Sms -> MessageId(id.toLong(), false)
    }
  }

  private fun ensureReader() {
    if (reader == null) {
      reader = SignalSmsExportReader(
        smsCursor = SignalDatabase.sms.unexportedInsecureMessages,
        mmsCursor = SignalDatabase.mms.unexportedInsecureMessages
      )
    }
  }
}
