/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.models.media.Media
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.preupload.PreUploadManager
import org.signal.mediasend.preupload.PreUploadResult
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mediasend.MediaUploadRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.MessageSender

class MediaSendV3PreUploadCallback : PreUploadManager.Callback {

  @WorkerThread
  override fun preUpload(context: Context, media: Media, recipientId: MediaRecipientId?): PreUploadResult? {
    val attachment = MediaUploadRepository.asAttachment(context, media)
    val recipient = recipientId?.let { Recipient.resolved(RecipientId.from(it.id)) }
    val legacyResult = MessageSender.preUploadPushAttachment(context, attachment, recipient, media) ?: return null
    return PreUploadResult(
      legacyResult.media,
      legacyResult.attachmentId.id,
      legacyResult.jobIds.toMutableList()
    )
  }

  @WorkerThread
  override fun cancelJobs(context: Context, jobIds: List<String>) {
    val jobManager = AppDependencies.jobManager
    jobIds.forEach(jobManager::cancel)
  }

  @WorkerThread
  override fun deleteAttachment(context: Context, attachmentId: Long) {
    SignalDatabase.attachments.deleteAttachment(AttachmentId(attachmentId))
  }

  @WorkerThread
  override fun updateAttachmentCaption(context: Context, attachmentId: Long, caption: String?) {
    SignalDatabase.attachments.updateAttachmentCaption(AttachmentId(attachmentId), caption)
  }

  @WorkerThread
  override fun updateDisplayOrder(context: Context, orderMap: Map<Long, Int>) {
    val mapped = orderMap.mapKeys { AttachmentId(it.key) }
    SignalDatabase.attachments.updateDisplayOrder(mapped)
  }

  @WorkerThread
  override fun deleteAbandonedPreuploadedAttachments(context: Context): Int {
    return SignalDatabase.attachments.deleteAbandonedPreuploadedAttachments()
  }
}
