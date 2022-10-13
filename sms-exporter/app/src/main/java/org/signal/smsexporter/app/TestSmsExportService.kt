package org.signal.smsexporter.app

import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.signal.core.util.logging.Log
import org.signal.smsexporter.ExportableMessage
import org.signal.smsexporter.SmsExportService
import org.signal.smsexporter.SmsExportState
import java.io.InputStream
import kotlin.time.Duration.Companion.seconds

class TestSmsExportService : SmsExportService() {

  companion object {
    private val TAG = Log.tag(TestSmsExportService::class.java)

    private const val NOTIFICATION_ID = 1234
    private const val NOTIFICATION_CHANNEL_ID = "sms_export"

    private const val startTime = 1659377120L
  }

  override fun getNotification(progress: Int, total: Int): ExportNotification {
    ensureNotificationChannel()
    return ExportNotification(
      id = NOTIFICATION_ID,
      NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Test Exporter")
        .setProgress(total, progress, false)
        .build()
    )
  }

  override fun getExportCompleteNotification(): ExportNotification? {
    return null
  }

  override fun getUnexportedMessageCount(): Int {
    return 50
  }

  override fun getUnexportedMessages(): Iterable<ExportableMessage> {
    return object : Iterable<ExportableMessage> {
      override fun iterator(): Iterator<ExportableMessage> {
        return ExportableMessageIterator(getUnexportedMessageCount())
      }
    }
  }

  override fun onMessageExportStarted(exportableMessage: ExportableMessage) {
    Log.d(TAG, "onMessageExportStarted() called with: exportableMessage = $exportableMessage")
  }

  override fun onMessageExportSucceeded(exportableMessage: ExportableMessage) {
    Log.d(TAG, "onMessageExportSucceeded() called with: exportableMessage = $exportableMessage")
  }

  override fun onMessageExportFailed(exportableMessage: ExportableMessage) {
    Log.d(TAG, "onMessageExportFailed() called with: exportableMessage = $exportableMessage")
  }

  override fun onMessageIdCreated(exportableMessage: ExportableMessage, messageId: Long) {
    Log.d(TAG, "onMessageIdCreated() called with: exportableMessage = $exportableMessage, messageId = $messageId")
  }

  override fun onAttachmentPartExportStarted(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part) {
    Log.d(TAG, "onAttachmentPartExportStarted() called with: exportableMessage = $exportableMessage, attachment = $part")
  }

  override fun onAttachmentPartExportSucceeded(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part) {
    Log.d(TAG, "onAttachmentPartExportSucceeded() called with: exportableMessage = $exportableMessage, attachment = $part")
  }

  override fun onAttachmentPartExportFailed(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part) {
    Log.d(TAG, "onAttachmentPartExportFailed() called with: exportableMessage = $exportableMessage, attachment = $part")
  }

  override fun onRecipientExportStarted(exportableMessage: ExportableMessage, recipient: String) {
    Log.d(TAG, "onRecipientExportStarted() called with: exportableMessage = $exportableMessage, recipient = $recipient")
  }

  override fun onRecipientExportSucceeded(exportableMessage: ExportableMessage, recipient: String) {
    Log.d(TAG, "onRecipientExportSucceeded() called with: exportableMessage = $exportableMessage, recipient = $recipient")
  }

  override fun onRecipientExportFailed(exportableMessage: ExportableMessage, recipient: String) {
    Log.d(TAG, "onRecipientExportFailed() called with: exportableMessage = $exportableMessage, recipient = $recipient")
  }

  override fun getInputStream(part: ExportableMessage.Mms.Part): InputStream {
    return BitmapGenerator.getStream()
  }

  override fun onExportPassCompleted() {
    Log.d(TAG, "onExportPassCompleted() called")
  }

  private fun ensureNotificationChannel() {
    val notificationManager = NotificationManagerCompat.from(this)
    val channel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
    if (channel == null) {
      val newChannel = NotificationChannelCompat
        .Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        .setName("misc")
        .build()

      notificationManager.createNotificationChannel(newChannel)
    }
  }

  private class ExportableMessageIterator(private val size: Int) : Iterator<ExportableMessage> {
    private var emitted: Int = 0

    override fun hasNext(): Boolean {
      return emitted < size
    }

    override fun next(): ExportableMessage {
      val message = if (emitted % 2 == 0) {
        getSmsMessage(emitted)
      } else {
        getMmsMessage(emitted)
      }

      emitted++
      return message
    }

    private fun getMmsMessage(it: Int): ExportableMessage.Mms<*> {
      val me = "+15065550101"
      val addresses = setOf(me, "+15065550102", "+15065550121")
      val address = addresses.random()
      return ExportableMessage.Mms(
        id = "$it",
        exportState = SmsExportState(),
        addresses = addresses,
        dateSent = (startTime + it - 1).seconds,
        dateReceived = (startTime + it).seconds,
        isRead = true,
        isOutgoing = address == me,
        sender = address,
        parts = listOf(
          ExportableMessage.Mms.Part.Text("Hello, $it from $address"),
          ExportableMessage.Mms.Part.Stream("$it", "image/jpeg")
        )
      )
    }

    private fun getSmsMessage(it: Int): ExportableMessage.Sms<*> {
      return ExportableMessage.Sms(
        id = it.toString(),
        exportState = SmsExportState(),
        address = "+15065550102",
        body = "Hello, World! $it",
        dateSent = (startTime + it - 1).seconds,
        dateReceived = (startTime + it).seconds,
        isRead = true,
        isOutgoing = it % 4 == 0
      )
    }
  }
}
