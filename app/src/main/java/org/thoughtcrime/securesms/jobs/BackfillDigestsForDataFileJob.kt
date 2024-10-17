/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.Base64
import org.signal.core.util.copyTo
import org.signal.core.util.logging.Log
import org.signal.core.util.stream.NullOutputStream
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.BackfillDigestsForDataFileJobData
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import java.io.IOException

/**
 * This goes through all attachments that share a data file and recalcuates their digests, ensuring that all instances share the same (key/iv/digest).
 *
 * This job needs to be careful to (1) minimize time in the transaction, and (2) never write partial results to disk, i.e. only write the full (key/iv/digest)
 * tuple together all at once (partial writes could poison the db, preventing us from retrying properly in the event of a crash or transient error).
 */
class BackfillDigestsForDataFileJob private constructor(
  private val dataFile: String,
  params: Parameters
) : Job(params) {

  companion object {
    private val TAG = Log.tag(BackfillDigestsForDataFileJob::class)
    const val KEY = "BackfillDigestsForDataFileJob"
  }

  constructor(dataFile: String) : this(
    dataFile = dataFile,
    params = Parameters.Builder()
      .setQueue(BackfillDigestJob.QUEUE)
      .setMaxAttempts(3)
      .setLifespan(Parameters.IMMORTAL)
      .build()
  )

  override fun serialize(): ByteArray {
    return BackfillDigestsForDataFileJobData(dataFile = dataFile).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val (originalKey, originalIv, decryptingStream) = SignalDatabase.rawDatabase.withinTransaction {
      val attachment = SignalDatabase.attachments.getMostRecentValidAttachmentUsingDataFile(dataFile)
      if (attachment == null) {
        Log.w(TAG, "No attachments using file $dataFile exist anymore! Skipping.")
        return Result.failure()
      }

      val stream = try {
        SignalDatabase.attachments.getAttachmentStream(attachment.attachmentId, offset = 0)
      } catch (e: IOException) {
        Log.w(TAG, "Could not open a stream for ${attachment.attachmentId}. Assuming that the file no longer exists. Skipping.", e)
        return Result.failure()
      }

      // In order to match the exact digest calculation, we need to use the same padding that we would use when uploading the attachment.
      Triple(attachment.remoteKey?.let { Base64.decode(it) }, attachment.remoteIv, PaddingInputStream(stream, attachment.size))
    }

    val key = originalKey ?: Util.getSecretBytes(64)
    val iv = originalIv ?: Util.getSecretBytes(16)

    val cipherOutputStream = AttachmentCipherOutputStream(key, iv, NullOutputStream)
    decryptingStream.copyTo(cipherOutputStream)

    val digest = cipherOutputStream.transmittedDigest

    SignalDatabase.attachments.updateKeyIvDigestByDataFile(
      dataFile = dataFile,
      key = key,
      iv = iv,
      digest = digest
    )

    return Result.success()
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to backfill digest for file $dataFile!")
  }

  class Factory : Job.Factory<BackfillDigestsForDataFileJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackfillDigestsForDataFileJob {
      val dataFile = (BackfillDigestsForDataFileJobData.ADAPTER.decode(serializedData!!).dataFile)
      return BackfillDigestsForDataFileJob(dataFile, parameters)
    }
  }
}
