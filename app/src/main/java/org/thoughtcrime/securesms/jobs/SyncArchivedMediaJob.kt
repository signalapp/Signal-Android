/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import java.io.IOException
import java.lang.Exception

/**
 * Job responsible for keeping remote archive media objects in sync. That is
 * we make sure our CDN number aligns on all media ids, as well as deleting any
 * extra media ids that we don't know about.
 */
class SyncArchivedMediaJob private constructor(
  parameters: Parameters,
  private var jobCursor: String?
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(BackupRestoreMediaJob::class.java)

    private const val KEY_CURSOR = "cursor"

    const val KEY = "SyncArchivedMediaJob"
  }

  constructor(cursor: String? = null) : this(
    Parameters.Builder()
      .setQueue("SyncArchivedMedia")
      .setMaxAttempts(Parameters.UNLIMITED)
      .setMaxInstancesForQueue(2)
      .build(),
    cursor
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_CURSOR, jobCursor)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    val batchSize = 100
    val attachmentsToDelete = HashSet<ArchivedMediaObject>()
    var cursor: String? = jobCursor
    do {
      val archivedItemPage = BackupRepository.listRemoteMediaObjects(batchSize, cursor).successOrThrow()
      attachmentsToDelete += syncPage(archivedItemPage)
      cursor = archivedItemPage.cursor
      if (attachmentsToDelete.size >= batchSize) {
        when (val result = BackupRepository.deleteAbandonedMediaObjects(attachmentsToDelete)) {
          is NetworkResult.Success -> Log.i(TAG, "Deleted ${attachmentsToDelete.size} attachments off CDN")
          else -> Log.w(TAG, "Failed to delete attachments from CDN", result.getCause())
        }
        attachmentsToDelete.clear()
      }
      if (attachmentsToDelete.isEmpty()) {
        jobCursor = archivedItemPage.cursor
      }
    } while (cursor != null)

    if (attachmentsToDelete.isNotEmpty()) {
      BackupRepository.deleteAbandonedMediaObjects(attachmentsToDelete)
      Log.i(TAG, "Deleted ${attachmentsToDelete.size} attachments off CDN")
    }
    SignalStore.backup.lastMediaSyncTime = System.currentTimeMillis()
  }

  /**
   * Update CDNs of archived media items. Returns set of objects that don't match
   * to a local attachment DB row.
   */
  private fun syncPage(archivedItemPage: ArchiveGetMediaItemsResponse): Set<ArchivedMediaObject> {
    val abandonedObjects = HashSet<ArchivedMediaObject>()
    SignalDatabase.rawDatabase.withinTransaction {
      archivedItemPage.storedMediaObjects.forEach { storedMediaObject ->
        val rows = SignalDatabase.attachments.updateArchiveCdnByMediaId(archiveMediaId = storedMediaObject.mediaId, archiveCdn = storedMediaObject.cdn)
        if (rows == 0) {
          abandonedObjects.add(ArchivedMediaObject(storedMediaObject.mediaId, storedMediaObject.cdn))
        }
      }
    }
    return abandonedObjects
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is IOException && e !is NonSuccessfulResponseCodeException
  }

  class Factory : Job.Factory<SyncArchivedMediaJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SyncArchivedMediaJob {
      val data = JsonJobData.deserialize(serializedData)
      return SyncArchivedMediaJob(parameters, if (data.hasString(KEY_CURSOR)) data.getString(KEY_CURSOR) else null)
    }
  }
}
