package org.thoughtcrime.securesms.testing

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.UUID

/**
 * Sets up bare-minimum to allow writing unit tests against the database,
 * including setting up the local ACI and PNI pair.
 *
 * @param deleteAllThreadsOnEachRun Run deleteAllThreads between each unit test
 */
class SignalDatabaseRule(
  private val deleteAllThreadsOnEachRun: Boolean = true
) : TestWatcher() {

  val localAci: ACI = ACI.from(UUID.randomUUID())
  val localPni: PNI = PNI.from(UUID.randomUUID())

  override fun starting(description: Description?) {
    deleteAllThreads()

    SignalStore.account.setAci(localAci)
    SignalStore.account.setPni(localPni)
  }

  override fun finished(description: Description?) {
    deleteAllThreads()
  }

  private fun deleteAllThreads() {
    if (deleteAllThreadsOnEachRun) {
      SignalDatabase.threads.deleteAllConversations()
      SignalDatabase.rawDatabase.deleteAll(ThreadTable.TABLE_NAME)
    }
  }
}
