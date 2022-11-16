package org.signal.smsexporter

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import io.reactivex.rxjava3.processors.BehaviorProcessor
import org.signal.core.util.Result
import org.signal.core.util.Try
import org.signal.core.util.logging.Log
import org.signal.smsexporter.internal.mms.ExportMmsMessagesUseCase
import org.signal.smsexporter.internal.mms.ExportMmsPartsUseCase
import org.signal.smsexporter.internal.mms.ExportMmsRecipientsUseCase
import org.signal.smsexporter.internal.mms.GetOrCreateMmsThreadIdsUseCase
import org.signal.smsexporter.internal.sms.ExportSmsMessagesUseCase
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Exports SMS and MMS messages to the system database.
 */
abstract class SmsExportService : Service() {

  companion object {
    private val TAG = Log.tag(SmsExportService::class.java)
    const val CLEAR_PREVIOUS_EXPORT_STATE_EXTRA = "clear_previous_export_state"

    /**
     * Progress state which can be listened to by interested components, such as fragments.
     */
    val progressState: BehaviorProcessor<SmsExportProgress> = BehaviorProcessor.createDefault(SmsExportProgress.Init)

    fun clearProgressState() {
      progressState.onNext(SmsExportProgress.Init)
    }
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  private val threadCache: MutableMap<Set<String>, Long> = mutableMapOf()
  private var isStarted = false

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "Got start command in SMS Export Service")

    startExport(intent?.getBooleanExtra(CLEAR_PREVIOUS_EXPORT_STATE_EXTRA, false) ?: false)

    return START_NOT_STICKY
  }

  private fun startExport(clearExportState: Boolean) {
    if (isStarted) {
      Log.d(TAG, "Already running exporter.")
      return
    }

    Log.d(TAG, "Running export clearExportState: $clearExportState")

    isStarted = true
    updateNotification(-1, -1)
    progressState.onNext(SmsExportProgress.Starting)

    var progress = 0
    var errorCount = 0
    executor.execute {
      if (clearExportState) {
        clearPreviousExportState()
      }

      prepareForExport()
      val totalCount = getUnexportedMessageCount()
      getUnexportedMessages().forEach { message ->
        val exportState = message.exportState
        if (exportState.progress != SmsExportState.Progress.COMPLETED) {
          val successful = when (message) {
            is ExportableMessage.Sms<*> -> exportSms(exportState, message)
            is ExportableMessage.Mms<*> -> exportMms(exportState, message)
          }

          if (!successful) {
            errorCount++
          }

          progress++
          if (progress == 1 || progress.mod(100) == 0) {
            updateNotification(progress, totalCount)
          }
          progressState.onNext(SmsExportProgress.InProgress(progress, errorCount, totalCount))
        }
      }

      onExportPassCompleted()
      progressState.onNext(SmsExportProgress.Done(errorCount, progress))

      getExportCompleteNotification()?.let { notification ->
        NotificationManagerCompat.from(this).notify(notification.id, notification.notification)
      }

      Log.d(TAG, "Export complete")

      stopForeground(true)
      stopSelf()
      isStarted = false
    }
  }

  /**
   * The executor that this service should do its work on.
   */
  protected open val executor: Executor = Executors.newSingleThreadExecutor()

  /**
   * Produces the notification and notification id to display for this foreground service.
   * The progress and total represent how many messages we've processed, and how many total
   * we have to process. Failures and successes are both aggregated in this progress. You can
   * query for "failure" state *after* we signal completion of a run.
   */
  protected abstract fun getNotification(progress: Int, total: Int): ExportNotification

  /**
   * Produces the notification and notification id to display when the export is complete.
   *
   * Can be null if no notification is needed (e.g., the user is still in the app)
   */
  protected abstract fun getExportCompleteNotification(): ExportNotification?

  /**
   * Called prior to starting export if the user has requested previous export state to be cleared.
   */
  protected open fun clearPreviousExportState() = Unit

  /**
   * Called prior to starting export for any task setup that may need to occur.
   */
  protected open fun prepareForExport() = Unit

  /**
   * Gets the total number of messages to process. This is only used for the notification and
   * progress events.
   */
  protected abstract fun getUnexportedMessageCount(): Int

  /**
   * Gets an iterable of exportable messages.
   */
  protected abstract fun getUnexportedMessages(): Iterable<ExportableMessage>

  /**
   * We've started the export process for a given MMS / SMS message
   */
  protected abstract fun onMessageExportStarted(exportableMessage: ExportableMessage)

  /**
   * We've completely succeeded exporting a given MMS / SMS message. This is only
   * called when all parts of the message (including recipients and attachments) have
   * been completely exported.
   */
  protected abstract fun onMessageExportSucceeded(exportableMessage: ExportableMessage)

  /**
   * We've failed to completely export a given MMS / SMS message
   */
  protected abstract fun onMessageExportFailed(exportableMessage: ExportableMessage)

  /**
   * We've written the message contents to the system database and were handed back an id.
   */
  protected abstract fun onMessageIdCreated(exportableMessage: ExportableMessage, messageId: Long)

  /**
   * We've begun trying to export a part row for an attachment for the given message
   */
  protected abstract fun onAttachmentPartExportStarted(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part)

  /**
   * We've successfully exported the attachment part for a given message and written the
   * attachment file to the local filesystem.
   */
  protected abstract fun onAttachmentPartExportSucceeded(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part)

  /**
   * We failed to export the attachment part for a given message.
   */
  protected abstract fun onAttachmentPartExportFailed(exportableMessage: ExportableMessage, part: ExportableMessage.Mms.Part)

  /**
   * We've begun trying to export a recipient addr for a given message
   */
  protected abstract fun onRecipientExportStarted(exportableMessage: ExportableMessage, recipient: String)

  /**
   * We've successfully exported a recipient addr for a given message
   */
  protected abstract fun onRecipientExportSucceeded(exportableMessage: ExportableMessage, recipient: String)

  /**
   * We've failed to export a recipient addr for a given message
   */
  protected abstract fun onRecipientExportFailed(exportableMessage: ExportableMessage, recipient: String)

  /**
   * Gets the input stream for the given attachment, so that it might be written out to disk.
   */
  protected abstract fun getInputStream(part: ExportableMessage.Mms.Part): InputStream

  /**
   * Called when an export pass completes. It is up to the implementation to determine whether
   * there are still messages to export. This is where the system could initiate a multiple-pass
   * system to ensure all messages are exported, though an approach like this can have data races
   * and other pitfalls.
   */
  protected abstract fun onExportPassCompleted()

  private fun updateNotification(progress: Int, total: Int) {
    val exportNotification = getNotification(progress, total)
    startForeground(exportNotification.id, exportNotification.notification)
  }

  private fun exportSms(smsExportState: SmsExportState, sms: ExportableMessage.Sms<*>): Boolean {
    onMessageExportStarted(sms)
    val mayAlreadyExist = smsExportState.progress == SmsExportState.Progress.STARTED
    return ExportSmsMessagesUseCase.execute(this, sms, mayAlreadyExist).either(onSuccess = {
      onMessageExportSucceeded(sms)
      true
    }, onFailure = {
      onMessageExportFailed(sms)
      false
    })
  }

  private fun exportMms(smsExportState: SmsExportState, mms: ExportableMessage.Mms<*>): Boolean {
    onMessageExportStarted(mms)
    val threadIdOutput: GetOrCreateMmsThreadIdsUseCase.Output? = getThreadId(mms)
    val exportMmsOutput: ExportMmsMessagesUseCase.Output? = threadIdOutput?.let { exportMms(smsExportState, it) }
    val exportMmsPartsOutput: List<ExportMmsPartsUseCase.Output?>? = exportMmsOutput?.let { exportMmsParts(smsExportState, it) }
    val writeMmsPartsOutput: List<Result<Unit, Throwable>>? = exportMmsPartsOutput?.filterNotNull()?.map { writeAttachmentToDisk(smsExportState, it) }
    val exportMmsRecipients: List<Unit?>? = exportMmsOutput?.let { exportMmsRecipients(smsExportState, it) }

    return if (threadIdOutput != null &&
      exportMmsOutput != null &&
      exportMmsPartsOutput != null && !exportMmsPartsOutput.contains(null) &&
      writeMmsPartsOutput != null && writeMmsPartsOutput.all { it is Result.Success || (it is Result.Failure && (it.failure.cause ?: it.failure) is FileNotFoundException) } &&
      exportMmsRecipients != null && !exportMmsRecipients.contains(null)
    ) {
      onMessageExportSucceeded(mms)
      true
    } else {
      onMessageExportFailed(mms)
      false
    }
  }

  private fun getThreadId(mms: ExportableMessage.Mms<*>): GetOrCreateMmsThreadIdsUseCase.Output? {
    return GetOrCreateMmsThreadIdsUseCase.execute(this, mms, threadCache).either(
      onSuccess = { output ->
        output
      },
      onFailure = {
        Log.w(TAG, "Failed to get thread id for export", it)
        null
      }
    )
  }

  private fun exportMms(smsExportState: SmsExportState, threadIdOutput: GetOrCreateMmsThreadIdsUseCase.Output): ExportMmsMessagesUseCase.Output? {
    return ExportMmsMessagesUseCase.execute(this, threadIdOutput, smsExportState.progress == SmsExportState.Progress.STARTED).either(
      onSuccess = {
        onMessageIdCreated(it.mms, it.messageId)
        it
      },
      onFailure = {
        Log.w(TAG, "Failed to export MMS into system database", it)
        null
      }
    )
  }

  private fun exportMmsParts(smsExportState: SmsExportState, exportMmsOutput: ExportMmsMessagesUseCase.Output): List<ExportMmsPartsUseCase.Output?> {
    val attachments = exportMmsOutput.mms.parts
    return if (attachments.isEmpty()) {
      emptyList()
    } else {
      attachments.filterNot { it.contentId in smsExportState.completedAttachments }.map { attachment ->
        onAttachmentPartExportStarted(exportMmsOutput.mms, attachment)
        ExportMmsPartsUseCase.execute(this, attachment, exportMmsOutput, smsExportState.startedAttachments.contains(attachment.contentId)).either(
          onSuccess = {
            it
          },
          onFailure = {
            onAttachmentPartExportFailed(exportMmsOutput.mms, attachment)
            Log.d(TAG, "Could not export MMS Part", it)
            null
          }
        )
      }
    }
  }

  private fun exportMmsRecipients(smsExportState: SmsExportState, exportMmsOutput: ExportMmsMessagesUseCase.Output): List<Unit?> {
    val recipients = exportMmsOutput.mms.addresses.map { it }.toSet()
    return if (recipients.isEmpty()) {
      emptyList()
    } else {
      recipients.filterNot { it in smsExportState.completedRecipients }.map { recipient ->
        onRecipientExportStarted(exportMmsOutput.mms, recipient)
        ExportMmsRecipientsUseCase.execute(this, exportMmsOutput.messageId, recipient, exportMmsOutput.mms.sender.toString(), smsExportState.startedRecipients.contains(recipient)).either(
          onSuccess = {
            onRecipientExportSucceeded(exportMmsOutput.mms, recipient)
          },
          onFailure = {
            onRecipientExportFailed(exportMmsOutput.mms, recipient)
            Log.w(TAG, "Failed to export MMS Recipient", it)
            null
          }
        )
      }
    }
  }

  private fun writeAttachmentToDisk(smsExportState: SmsExportState, output: ExportMmsPartsUseCase.Output): Try<Unit> {
    if (output.part.contentId in smsExportState.completedAttachments) {
      return Try.success(Unit)
    }

    if (output.part is ExportableMessage.Mms.Part.Text) {
      onAttachmentPartExportSucceeded(output.message, output.part)
      return Try.success(Unit)
    }

    return try {
      contentResolver.openOutputStream(output.uri)!!.use { out ->
        getInputStream(output.part).use {
          it.copyTo(out)
        }
      }

      onAttachmentPartExportSucceeded(output.message, output.part)
      Try.success(Unit)
    } catch (e: Exception) {
      Log.d(TAG, "Failed to write attachment to disk.", e)
      Try.failure(e)
    }
  }

  data class ExportNotification(
    val id: Int,
    val notification: Notification
  )
}
