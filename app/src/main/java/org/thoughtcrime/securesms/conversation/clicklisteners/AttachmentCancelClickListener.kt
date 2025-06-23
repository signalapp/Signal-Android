/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.conversation.clicklisteners

import android.view.View
import kotlinx.collections.immutable.toPersistentList
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.AttachmentCompressionJob
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlidesClickedListener

/**
 * Cancels all attachments passed through to the callback.
 *
 * Creates a persistent copy of the handed list of slides to prevent off-thread
 * manipulation.
 */
internal class AttachmentCancelClickListener : SlidesClickedListener {
  override fun onClick(unused: View, slides: List<Slide>) {
    val toCancel = slides.toPersistentList()

    Log.i(TAG, "Canceling compression/upload/download jobs for ${toCancel.size} items")

    SignalExecutors.BOUNDED_IO.execute {
      var cancelCount = 0
      for (slide in toCancel) {
        val attachmentId = (slide.asAttachment() as DatabaseAttachment).attachmentId
        val jobsToCancel = AppDependencies.jobManager.find {
          when (it.factoryKey) {
            AttachmentDownloadJob.KEY -> AttachmentDownloadJob.jobSpecMatchesAttachmentId(it, attachmentId)
            AttachmentCompressionJob.KEY -> AttachmentCompressionJob.jobSpecMatchesAttachmentId(it, attachmentId)
            AttachmentUploadJob.KEY -> AttachmentUploadJob.jobSpecMatchesAttachmentId(it, attachmentId)
            else -> false
          }
        }
        jobsToCancel.forEach {
          AppDependencies.jobManager.cancel(it.id)
          cancelCount++
        }
      }
      Log.i(TAG, "Canceled $cancelCount jobs.")
    }
  }

  companion object {
    private val TAG = Log.tag(AttachmentCancelClickListener::class.java)
  }
}
