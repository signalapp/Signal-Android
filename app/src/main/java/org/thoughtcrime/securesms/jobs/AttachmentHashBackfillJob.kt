/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.ThreadUtil
import org.signal.core.util.drain
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * This job backfills hashes for attachments that were sent before we started hashing them.
 * In order to avoid hammering the device with hash calculations and disk I/O, this job will
 * calculate the hash for a single attachment and then reschedule itself to run again if necessary.
 */
class AttachmentHashBackfillJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    val TAG = Log.tag(AttachmentHashBackfillJob::class.java)

    const val KEY = "AttachmentHashBackfillJob"
  }

  private var activeFile: File? = null

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .setMaxInstancesForFactory(2)
      .setLifespan(Parameters.IMMORTAL)
      .setMaxAttempts(10)
      .build()
  )

  override fun serialize() = null

  override fun getFactoryKey() = KEY

  override fun run(): Result {
    val (file: File?, attachmentId: AttachmentId?) = SignalDatabase.attachments.getUnhashedDataFile() ?: (null to null)
    if (file == null || attachmentId == null) {
      Log.i(TAG, "No more unhashed files! Task complete.")
      return Result.success()
    }

    activeFile = file

    if (!file.exists()) {
      Log.w(TAG, "File does not exist! Clearing all usages.", true)
      SignalDatabase.attachments.clearUsagesOfDataFile(file)
      AppDependencies.jobManager.add(AttachmentHashBackfillJob())
      return Result.success()
    }

    try {
      val inputStream = SignalDatabase.attachments.getAttachmentStream(attachmentId, 0)
      val messageDigest = MessageDigest.getInstance("SHA-256")

      DigestInputStream(inputStream, messageDigest).use {
        it.drain()
      }

      val hash = messageDigest.digest()

      SignalDatabase.attachments.setHashForDataFile(file, hash)
    } catch (e: FileNotFoundException) {
      Log.w(TAG, "File could not be found! Clearing all usages.", true)
      SignalDatabase.attachments.clearUsagesOfDataFile(file)
    } catch (e: IOException) {
      Log.e(TAG, "Error hashing attachment. Retrying.", e)

      if (e.cause is FileNotFoundException) {
        Log.w(TAG, "Underlying cause was a FileNotFoundException. Clearing all usages.", true)
        SignalDatabase.attachments.clearUsagesOfDataFile(file)
      } else {
        return Result.retry(defaultBackoff())
      }
    }

    // Sleep just so we don't hammer the device with hash calculations and disk I/O
    ThreadUtil.sleep(1000)

    AppDependencies.jobManager.add(AttachmentHashBackfillJob())
    return Result.success()
  }

  override fun onFailure() {
    activeFile?.let { file ->
      Log.w(TAG, "Failed to calculate hash, marking as unhashable: $file", true)
      SignalDatabase.attachments.markDataFileAsUnhashable(file)
    } ?: Log.w(TAG, "Job failed, but no active file is set!")

    AppDependencies.jobManager.add(AttachmentHashBackfillJob())
  }

  class Factory : Job.Factory<AttachmentHashBackfillJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AttachmentHashBackfillJob {
      return AttachmentHashBackfillJob(parameters)
    }
  }
}
