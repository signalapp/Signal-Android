package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Turns off setting for unread reminders for existing users.
 */
internal class DisableUnreadReminderMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {

    const val KEY = "DisableUnreadReminderMigrationJob"

    private val TAG: String = tag(DisableUnreadReminderMigrationJob::class.java)
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    if (!SignalStore.account.isRegistered || SignalStore.account.aci == null || SignalStore.account.pni == null) {
      Log.i(TAG, "Unregistered, skipping.")
      return
    }

    Log.i(TAG, "Disabling unread reminders")
    SignalStore.settings.unreadReminderEnabled = false
    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<DisableUnreadReminderMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): DisableUnreadReminderMigrationJob {
      return DisableUnreadReminderMigrationJob(parameters)
    }
  }
}
