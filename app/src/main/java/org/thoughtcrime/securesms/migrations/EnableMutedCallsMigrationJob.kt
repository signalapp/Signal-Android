package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Turns on the setting to allow calls to break through muted chats for existing users.
 */
internal class EnableMutedCallsMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {

    const val KEY = "EnableMutedCallsMigrationJob"

    private val TAG: String = tag(EnableMutedCallsMigrationJob::class.java)
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    if (!SignalStore.account.isRegistered || SignalStore.account.aci == null || SignalStore.account.pni == null) {
      Log.i(TAG, "Unregistered, skipping.")
      return
    }

    Log.i(TAG, "Enabling calls to break through muted chats")
    SignalStore.settings.allowCallsWhileMuted = true
    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<EnableMutedCallsMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): EnableMutedCallsMigrationJob {
      return EnableMutedCallsMigrationJob(parameters)
    }
  }
}
