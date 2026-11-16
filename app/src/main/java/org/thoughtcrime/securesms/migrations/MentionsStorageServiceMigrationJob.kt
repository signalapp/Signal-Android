package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Rotates the storage service id for every GV2 group to support writing the new mentions setting
 */
internal class MentionsStorageServiceMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {

    const val KEY = "MentionsStorageServiceMigrationJob"

    private val TAG: String = tag(MentionsStorageServiceMigrationJob::class.java)
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    if (!SignalStore.account.isRegistered || SignalStore.account.aci == null) {
      Log.i(TAG, "Unregistered, skipping.")
      return
    }

    SignalDatabase.recipients.markAllGV2NeedsSync()
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<MentionsStorageServiceMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MentionsStorageServiceMigrationJob {
      return MentionsStorageServiceMigrationJob(parameters)
    }
  }
}
