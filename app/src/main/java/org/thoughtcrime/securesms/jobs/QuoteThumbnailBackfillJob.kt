/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.net.Uri
import org.signal.core.util.logging.Log
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.CONTENT_TYPE
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.DATA_FILE
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.DATA_HASH_END
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.DATA_HASH_START
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.DATA_RANDOM
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.DATA_SIZE
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.ID
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.QUOTE
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.QUOTE_PENDING_TRANSCODE
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.TABLE_NAME
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.mms.DecryptableUri
import org.thoughtcrime.securesms.mms.PartAuthority

/**
 * This job processes quote attachments to generate thumbnails where possible.
 * In order to avoid hammering the device, this job will process a few attachments
 * and then reschedule itself to run again if necessary.
 */
class QuoteThumbnailBackfillJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    val TAG = Log.tag(QuoteThumbnailBackfillJob::class.java)
    const val KEY = "QuoteThumbnailBackfillJob"
  }

  private var activeAttachmentInfo: AttachmentInfo? = null

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .setMaxInstancesForFactory(2)
      .setLifespan(Parameters.IMMORTAL)
      .build()
  )

  override fun serialize() = null

  override fun getFactoryKey() = KEY

  override fun run(): Result {
    for (i in 1..10) {
      val complete = peformSingleBackfill()
      if (complete) {
        return Result.success()
      }
    }

    AppDependencies.jobManager.add(QuoteThumbnailBackfillJob())

    return Result.success()
  }

  /** Returns true if the entire backfill process is complete, otherwise false */
  private fun peformSingleBackfill(): Boolean {
    val attachment = SignalDatabase.attachments.getNextQuoteAttachmentForThumbnailProcessing()

    if (attachment == null) {
      Log.i(TAG, "No more quote attachments to process! Task complete.")
      return true
    }

    activeAttachmentInfo = attachment

    val thumbnail = SignalDatabase.attachments.generateQuoteThumbnail(DecryptableUri(attachment.uri), attachment.contentType, quiet = true)
    if (thumbnail != null) {
      SignalDatabase.attachments.migrationFinalizeQuoteWithData(attachment.dataFile, thumbnail, attachment.contentType)
    } else {
      Log.w(TAG, "Failed to generate thumbnail for attachment: ${attachment.id}. Clearing data.")
      SignalDatabase.attachments.finalizeQuoteWithNoData(attachment.dataFile)
    }

    return false
  }

  override fun onFailure() {
    activeAttachmentInfo?.let { attachment ->
      Log.w(TAG, "Failed during thumbnail generation. Clearing the quote data and continuing.", true)
      SignalDatabase.attachments.finalizeQuoteWithNoData(attachment.dataFile)
    } ?: Log.w(TAG, "Job failed, but no active file is set!")

    AppDependencies.jobManager.add(QuoteThumbnailBackfillJob())
  }

  /** Gets the next quote that has a scheduled thumbnail generation, favoring newer ones. */
  private fun AttachmentTable.getNextQuoteAttachmentForThumbnailProcessing(): AttachmentInfo? {
    return readableDatabase
      .select(ID, DATA_FILE, CONTENT_TYPE)
      .from(TABLE_NAME)
      .where("$QUOTE = $QUOTE_PENDING_TRANSCODE")
      .orderBy("$ID DESC")
      .limit(1)
      .run()
      .readToSingleObject {
        AttachmentInfo(
          id = AttachmentId(it.requireLong(ID)),
          dataFile = it.requireNonNullString(DATA_FILE),
          contentType = it.requireString(CONTENT_TYPE)
        )
      }
  }

  /** Finalizes all quote attachments that share the given [dataFile] with empty data (because we could not generate a thumbnail). */
  private fun AttachmentTable.finalizeQuoteWithNoData(dataFile: String) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        DATA_FILE to null,
        DATA_RANDOM to null,
        DATA_HASH_START to null,
        DATA_HASH_END to null,
        DATA_SIZE to 0,
        QUOTE to 1
      )
      .where("$DATA_FILE = ? AND $QUOTE != 0 ", dataFile)
      .run()
  }

  private data class AttachmentInfo(
    val id: AttachmentId,
    val dataFile: String,
    val contentType: String?
  ) {
    val uri: Uri get() = PartAuthority.getAttachmentDataUri(id)
  }

  class Factory : Job.Factory<QuoteThumbnailBackfillJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): QuoteThumbnailBackfillJob {
      return QuoteThumbnailBackfillJob(parameters)
    }
  }
}
