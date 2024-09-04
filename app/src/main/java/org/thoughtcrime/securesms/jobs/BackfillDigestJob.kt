/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.Base64
import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.readLength
import org.signal.core.util.stream.NullOutputStream
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.BackfillDigestJobData
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import java.io.IOException

/**
 * For attachments that were created before we saved IV's, this will generate an IV and update the corresponding digest.
 * This is important for backupsV2, where we need to know an attachments digest in advance.
 *
 * This job needs to be careful to (1) minimize time in the transaction, and (2) never write partial results to disk, i.e. only write the full (key/iv/digest)
 * tuple together all at once (partial writes could poison the db, preventing us from retrying properly in the event of a crash or transient error).
 */
class BackfillDigestJob private constructor(
  private val attachmentId: AttachmentId,
  params: Parameters
) : Job(params) {

  companion object {
    private val TAG = Log.tag(BackfillDigestJob::class)
    const val KEY = "BackfillDigestJob"
  }

  constructor(attachmentId: AttachmentId) : this(
    attachmentId = attachmentId,
    params = Parameters.Builder()
      .setQueue("BackfillDigestJob")
      .setMaxAttempts(3)
      .setLifespan(Parameters.IMMORTAL)
      .setPriority(Parameters.PRIORITY_LOW)
      .build()
  )

  override fun serialize(): ByteArray {
    return BackfillDigestJobData(attachmentId = attachmentId.id).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val (originalKey, originalIv, decryptingStream) = SignalDatabase.rawDatabase.withinTransaction {
      val attachment = SignalDatabase.attachments.getAttachment(attachmentId)
      if (attachment == null) {
        Log.w(TAG, "$attachmentId no longer exists! Skipping.")
        return Result.success()
      }

      if (!attachment.hasData) {
        Log.w(TAG, "$attachmentId no longer has any data! Skipping.")
        return Result.success()
      }

      if (attachment.remoteKey != null && attachment.remoteIv != null && attachment.remoteDigest != null) {
        Log.w(TAG, "$attachmentId already has all required components! Skipping.")
        return Result.success()
      }

      // There was a bug where we were accidentally saving the padded size for the attachment as the actual size. This corrects that.
      // However, we're in a transaction, and reading a file is expensive in general, so we only do this if the length is unset or set to the padded size.
      // Given that the padding algorithm targets padding <= 5%, and most attachments are a couple hundred kb, this should greatly limit the false positive rate
      // to something like 1 in 10,000ish.
      val fileLength = if (attachment.size == 0L || attachment.size == PaddingInputStream.getPaddedSize(attachment.size)) {
        try {
          SignalDatabase.attachments.getAttachmentStream(attachmentId, offset = 0).use { it.readLength() }
        } catch (e: IOException) {
          Log.w(TAG, "Could not open a stream for $attachmentId while calculating the length. Assuming that the file no longer exists. Skipping.", e)
          return Result.success()
        }
      } else {
        attachment.size
      }

      if (fileLength != attachment.size) {
        Log.w(TAG, "$attachmentId had a saved size of ${attachment.size} but the actual size is $fileLength. Will update.")
        SignalDatabase.attachments.updateSize(attachmentId, fileLength)
      }

      val stream = try {
        SignalDatabase.attachments.getAttachmentStream(attachmentId, offset = 0)
      } catch (e: IOException) {
        Log.w(TAG, "Could not open a stream for $attachmentId. Assuming that the file no longer exists. Skipping.", e)
        return Result.success()
      }

      // In order to match the exact digest calculation, we need to use the same padding that we would use when uploading the attachment.
      Triple(attachment.remoteKey?.let { Base64.decode(it) }, attachment.remoteIv, PaddingInputStream(stream, fileLength))
    }

    val key = originalKey ?: Util.getSecretBytes(64)
    val iv = originalIv ?: Util.getSecretBytes(16)

    val cipherOutputStream = AttachmentCipherOutputStream(key, iv, NullOutputStream)
    StreamUtil.copy(decryptingStream, cipherOutputStream)

    val digest = cipherOutputStream.transmittedDigest

    SignalDatabase.attachments.updateKeyIvDigest(
      attachmentId = attachmentId,
      key = key,
      iv = iv,
      digest = digest
    )

    return Result.success()
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to backfill digest for $attachmentId!")
  }

  class Factory : Job.Factory<BackfillDigestJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackfillDigestJob {
      val attachmentId = AttachmentId(BackfillDigestJobData.ADAPTER.decode(serializedData!!).attachmentId)
      return BackfillDigestJob(attachmentId, parameters)
    }
  }
}
