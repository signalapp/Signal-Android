/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaviewer

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import arrow.core.Either
import kotlinx.coroutines.withContext
import org.signal.core.models.database.AttachmentId
import org.signal.core.models.media.Media
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logTime
import org.signal.core.util.logging.Log
import org.signal.mediaviewer.MediaViewerItem
import org.signal.uicomponents.styledtext.StyledString
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.toStyledString
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.withAttachments
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.longmessage.resolveBody
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mms.PartUriParser
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.AttachmentUtil
import org.thoughtcrime.securesms.util.MessageConstraintsUtil
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Data access for [MediaViewerViewModel].
 */
object MediaViewerRepository {

  private val TAG = Log.tag(MediaViewerRepository::class)

  private val NOT_DELETABLE = DeleteValidity(canDeleteForEveryone = false, isNoteToSelf = false)

  /**
   * Primary-key read of a single attachment so the tapped media renders without waiting on the
   * paged gallery query. Null when the attachment is missing or not displayable.
   */
  suspend fun getInitialItem(context: Context, attachmentId: AttachmentId, args: MediaIntentFactory.MediaPreviewArgs): MediaViewerItem? {
    return withContext(SignalDispatchers.Default) {
      val attachment = logTime(TAG, "getInitialItem($attachmentId)", decimalPlaces = 2) {
        SignalDatabase.attachments.getAttachment(attachmentId)?.takeIf { it.isDisplayable() }
      } ?: return@withContext null
      MediaTable.MediaRecord(
        attachment = attachment,
        recipientId = args.fromRecipientId,
        threadRecipientId = args.threadRecipientId,
        threadId = args.threadId,
        messageId = args.messageId,
        date = args.date,
        isOutgoing = args.outgoing
      ).toMediaViewerItem(context)
    }
  }

  suspend fun resolveMessageBodies(context: Context, messageIds: Set<Long>): Map<Long, StyledString> {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.messages.getMessages(messageIds).use { reader ->
        reader.toList().withAttachments()
          .filterIsInstance<MmsMessageRecord>()
          .associate { it.id to it.resolveBody(context).getDisplayBody(context).toStyledString() }
      }
    }
  }

  suspend fun localDelete(attachmentId: AttachmentId): Either<Throwable, Unit> {
    return withContext(SignalDispatchers.Default) {
      Either.catch {
        val attachment = SignalDatabase.attachments.getAttachment(attachmentId) ?: return@catch
        val deletedMessageRecord = AttachmentUtil.deleteAttachment(attachment)
        if (deletedMessageRecord != null) {
          MultiDeviceDeleteSyncJob.enqueueMessageDeletes(setOf(deletedMessageRecord))
        }
      }
    }
  }

  suspend fun remoteDelete(attachmentId: AttachmentId): Either<Throwable, Unit> {
    return withContext(SignalDispatchers.Default) {
      Either.catch {
        val attachment = SignalDatabase.attachments.getAttachment(attachmentId) ?: return@catch
        MessageSender.sendRemoteDelete(attachment.mmsId)
      }
    }
  }

  /** Whether the delete dialog may offer "Delete for everyone", plus the note-to-self flag for its labels. */
  suspend fun getDeleteValidity(attachmentId: AttachmentId): DeleteValidity {
    return withContext(SignalDispatchers.Default) {
      val attachment = SignalDatabase.attachments.getAttachment(attachmentId) ?: return@withContext NOT_DELETABLE
      val messageRecord: MessageRecord = SignalDatabase.messages.getMessageRecordOrNull(attachment.mmsId) ?: return@withContext NOT_DELETABLE
      val isNoteToSelf = messageRecord.isOutgoing && messageRecord.toRecipient.isSelf
      val attachmentCount = SignalDatabase.attachments.getAttachmentsForMessage(attachment.mmsId).size
      val canRemotelyDelete = attachmentCount <= 1 && MessageConstraintsUtil.isValidRemoteDeleteSend(listOf(messageRecord), System.currentTimeMillis())

      DeleteValidity(
        canDeleteForEveryone = canRemotelyDelete && !isNoteToSelf,
        isNoteToSelf = isNoteToSelf
      )
    }
  }

  /** An intent opening the conversation at the message's position, or null when the message or its thread recipient no longer exists. */
  suspend fun getMessagePositionIntent(context: Context, messageId: Long): Intent? {
    return withContext(SignalDispatchers.Default) {
      val messageRecord: MessageRecord = SignalDatabase.messages.getMessageRecordOrNull(messageId) ?: return@withContext null
      val threadId: Long = messageRecord.threadId
      val messagePosition: Int = SignalDatabase.messages.getMessagePositionInConversation(threadId, messageRecord.dateReceived)
      val recipientId: RecipientId = SignalDatabase.threads.getRecipientForThreadId(threadId)?.id ?: return@withContext null

      ConversationIntents.createBuilderSync(context, recipientId, threadId)
        .withStartingPosition(messagePosition)
        .build()
    }
  }

  /** Builds the editor payload for the attachment. Null when the attachment has no data to edit. */
  suspend fun getMediaForEdit(attachmentId: AttachmentId, date: Long): Media? {
    return withContext(SignalDispatchers.Default) {
      val attachment = SignalDatabase.attachments.getAttachment(attachmentId) ?: return@withContext null
      val uri = attachment.uri ?: return@withContext null
      Media(
        uri = uri,
        contentType = attachment.contentType,
        date = date,
        width = attachment.width,
        height = attachment.height,
        size = attachment.size,
        duration = 0,
        isBorderless = attachment.borderless,
        isVideoGif = attachment.videoGif,
        bucketId = null,
        caption = attachment.caption,
        transformProperties = null,
        fileName = null
      )
    }
  }

  /** Whether backup storage optimization is enabled, which allows media to be offloaded from the device. */
  fun isOptimizedStorageEnabled(): Boolean {
    return SignalStore.backup.optimizeStorage
  }

  suspend fun attachmentExists(attachmentId: AttachmentId): Boolean {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.attachments.getAttachment(attachmentId) != null
    }
  }

  suspend fun downloadAttachmentIfNeeded(attachmentId: AttachmentId) {
    withContext(SignalDispatchers.Default) {
      val attachment = SignalDatabase.attachments.getAttachment(attachmentId) ?: return@withContext
      AttachmentDownloadJob.downloadAttachmentIfNeeded(attachment)
    }
  }

  suspend fun handleIncrementalMacError(uri: Uri) {
    withContext(SignalDispatchers.Default) {
      maybePostInvalidMacErrorNotification()

      val attachmentId = try {
        PartUriParser(uri).partId
      } catch (e: Exception) {
        Log.w(TAG, "Got an incremental mac error, but could not parse the attachment data from the URI!", e)
        return@withContext
      }

      Log.w(TAG, "Got an incremental mac error for attachment $attachmentId, clearing the incremental mac data.")
      val attachment = SignalDatabase.attachments.getAttachment(attachmentId) ?: return@withContext
      SignalDatabase.attachments.clearIncrementalMacsForAttachmentAndAnyDuplicates(attachmentId, attachment.remoteKey, attachment.dataHash)
    }
  }

  /** Matches the viewability filter the gallery query applies, so both agree on what can be paged to. */
  private fun DatabaseAttachment.isDisplayable(): Boolean {
    return transferState == AttachmentTable.TRANSFER_PROGRESS_DONE ||
      transferState == AttachmentTable.TRANSFER_PROGRESS_STARTED ||
      thumbnailUri != null
  }

  private fun maybePostInvalidMacErrorNotification() {
    if (!RemoteConfig.internalUser) {
      return
    }

    val context = AppDependencies.application
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "maybePostInvalidMacErrorNotification: Notification permission is not granted.")
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Bad incrementalMac!")
      .setContentText("Tap to send a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)
  }

  data class DeleteValidity(val canDeleteForEveryone: Boolean, val isNoteToSelf: Boolean)
}
