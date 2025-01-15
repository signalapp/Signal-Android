package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceIds

/**
 * Migration to fix groups that we locally marked as inactive because of the server
 * but may not actually be left.
 */
internal class InactiveGroupCheckMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(InactiveGroupCheckMigrationJob::class.java)
    const val KEY = "InactiveGroupCheckMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (SignalStore.account.aci == null) {
      Log.w(TAG, "ACI missing, abort")
      return
    }

    val serviceIds = SignalStore.account.getServiceIds()

    SignalDatabase
      .groups
      .getInactiveGroups()
      .use { reader ->
        reader
          .asSequence()
          .filter { it.isV2Group }
          .filter { it.requireV2GroupProperties().decryptedGroup.isMember(serviceIds) }
          .forEach {
            AppDependencies.jobManager.add(RequestGroupV2InfoJob(it.id.requireV2()))
          }
      }
  }

  private fun DecryptedGroup.isMember(serviceIds: ServiceIds): Boolean {
    return this
      .members
      .asSequence()
      .mapNotNull { ServiceId.ACI.Companion.parseOrNull(it.aciBytes) }
      .any { serviceIds.matches(it) }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<InactiveGroupCheckMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InactiveGroupCheckMigrationJob {
      return InactiveGroupCheckMigrationJob(parameters)
    }
  }
}
