package org.thoughtcrime.securesms.exporter

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.cash.exhaustive.Exhaustive
import org.signal.core.util.PendingIntentFlags
import org.signal.smsexporter.ExportableMessage
import org.signal.smsexporter.SmsExportService
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.exporter.flow.SmsExportActivity
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.notifications.v2.NotificationPendingIntentHelper
import org.thoughtcrime.securesms.util.JsonUtils
import java.io.IOException
import java.io.InputStream

/**
 * Service which integrates the SMS exporter functionality.
 */
class SignalSmsExportService : SmsExportService() {

  companion object {
    /**
     * Launches the export service and immediately begins exporting messages.
     */
    fun start(context: Context, clearPreviousExportState: Boolean) {
      val intent = Intent(context, SignalSmsExportService::class.java)
        .apply { putExtra(CLEAR_PREVIOUS_EXPORT_STATE_EXTRA, clearPreviousExportState) }
      ForegroundServiceUtil.startOrThrow(context, intent)
    }
  }

  private var reader: SignalSmsExportReader? = null

  override fun getNotification(progress: Int, total: Int): ExportNotification {
    val pendingIntent = NotificationPendingIntentHelper.getActivity(
      this,
      0,
      SmsExportActivity.createIntent(this),
      PendingIntentFlags.mutable()
    )

    return ExportNotification(
      NotificationIds.SMS_EXPORT_SERVICE,
      NotificationCompat.Builder(this, NotificationChannels.getInstance().BACKUPS)
        .setSmallIcon(R.drawable.ic_signal_backup)
        .setContentTitle(getString(R.string.SignalSmsExportService__exporting_messages))
        .setContentIntent(pendingIntent)
        .setProgress(total, progress, false)
        .build()
    )
  }

  override fun getExportCompleteNotification(): ExportNotification? {
    if (ApplicationDependencies.getAppForegroundObserver().isForegrounded) {
      return null
    }

    val pendingIntent = NotificationPendingIntentHelper.getActivity(
      this,
      0,
      SmsExportActivity.createIntent(this),
      PendingIntentFlags.mutable()
    )

    return ExportNotification(
      NotificationIds.SMS_EXPORT_COMPLETE,
      NotificationCompat.Builder(this, NotificationChannels.getInstance().APP_ALERTS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.SignalSmsExportService__signal_sms_export_complete))
        .setContentText(getString(R.string.SignalSmsExportService__tap_to_return_to_signal))
        .setContentIntent(pendingIntent)
        .build()
    )
  }

  override fun clearPreviousExportState() {
    SignalDatabase.messages.clearExportState()
  }

  override fun prepareForExport() {
    SignalDatabase.messages.clearInsecureMessageExportedErrorStatus()
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
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().setProgress(MessageExportState.Progress.STARTED).build()
    }
  }

  override fun onMessageExportSucceeded(exportableMessage: ExportableMessage) {
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().setProgress(MessageExportState.Progress.COMPLETED).build()
    }

    SignalDatabase.messages.markMessageExported(exportableMessage.getMessageId())
  }

  override fun onMessageExportFailed(exportableMessage: ExportableMessage) {
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().setProgress(MessageExportState.Progress.INIT).build()
    }

    SignalDatabase.messages.markMessageExportFailed(exportableMessage.getMessageId())
  }

  override fun onMessageIdCreated(exportableMessage: ExportableMessage, messageId: Long) {
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().setMessageId(messageId).build()
    }
  }

  override fun onAttachmentPartExportStarted(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part) {
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().addStartedAttachments(part.contentId).build()
    }
  }

  override fun onAttachmentPartExportSucceeded(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part) {
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().addCompletedAttachments(part.contentId).build()
    }
  }

  override fun onAttachmentPartExportFailed(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part) {
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      val startedAttachments = it.startedAttachmentsList - part.contentId
      it.toBuilder().clearStartedAttachments().addAllStartedAttachments(startedAttachments).build()
    }
  }

  override fun onRecipientExportStarted(exportableMessage: ExportableMessage, recipient: String) {
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().addStartedRecipients(recipient).build()
    }
  }

  override fun onRecipientExportSucceeded(exportableMessage: ExportableMessage, recipient: String) {
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      it.toBuilder().addCompletedRecipients(recipient).build()
    }
  }

  override fun onRecipientExportFailed(exportableMessage: ExportableMessage, recipient: String) {
    SignalDatabase.messages.updateMessageExportState(exportableMessage.getMessageId()) {
      val startedAttachments = it.startedRecipientsList - recipient
      it.toBuilder().clearStartedRecipients().addAllStartedRecipients(startedAttachments).build()
    }
  }

  @Throws(IOException::class)
  override fun getInputStream(part: ExportableMessage.Mms.Part): InputStream {
    return SignalDatabase.attachments.getAttachmentStream(JsonUtils.fromJson(part.contentId, AttachmentId::class.java), 0)
  }

  override fun onExportPassCompleted() {
    reader?.close()
  }

  private fun ExportableMessage.getMessageId(): MessageId {
    @Exhaustive
    val messageId: Any = when (this) {
      is ExportableMessage.Mms<*> -> id
      is ExportableMessage.Sms<*> -> id
    }

    if (messageId is MessageId) {
      return messageId
    } else {
      throw AssertionError("Exportable message id must be type MessageId. Type: ${messageId.javaClass}")
    }
  }

  private fun ensureReader() {
    if (reader == null) {
      reader = SignalSmsExportReader()
    }
  }
}
