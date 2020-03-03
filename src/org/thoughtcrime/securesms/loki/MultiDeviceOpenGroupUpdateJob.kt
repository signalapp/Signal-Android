package org.thoughtcrime.securesms.loki

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.dependencies.InjectableType
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.loki.api.LokiPublicChat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MultiDeviceOpenGroupUpdateJob private constructor(parameters: Parameters) : BaseJob(parameters), InjectableType {

  companion object {
    const val KEY = "MultiDeviceOpenGroupUpdateJob"
  }

  @Inject
  lateinit var messageSender: SignalServiceMessageSender

  constructor() : this(Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue("MultiDeviceOpenGroupUpdateJob")
          .setLifespan(TimeUnit.DAYS.toMillis(1))
          .setMaxAttempts(Parameters.UNLIMITED)
          .build())

  override fun getFactoryKey(): String { return KEY }

  override fun serialize(): Data { return Data.EMPTY }

  @Throws(Exception::class)
  public override fun onRun() {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.d("Loki", "Not multi device; aborting...")
      return
    }

    val openGroups = mutableListOf<LokiPublicChat>()
    DatabaseFactory.getGroupDatabase(context).groups.use { reader ->
      while (true) {
        val record = reader.next ?: return@use
        if (!record.isPublicChat) { continue; }

        val threadID = GroupManager.getThreadIdFromGroupId(record.encodedId, context)
        val openGroup = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
        if (openGroup != null) {
          openGroups.add(openGroup)
        }
      }
    }

    if (openGroups.size > 0) {
      messageSender.sendMessage(0, SignalServiceSyncMessage.forOpenGroups(openGroups),
              UnidentifiedAccessUtil.getAccessForSync(context))
    } else {
      Log.d("Loki", "No open groups to sync.")
    }
  }

  public override fun onShouldRetry(exception: Exception): Boolean {
    return false
  }

  override fun onCanceled() { }

  class Factory : Job.Factory<MultiDeviceOpenGroupUpdateJob> {

    override fun create(parameters: Parameters, data: Data): MultiDeviceOpenGroupUpdateJob {
      return MultiDeviceOpenGroupUpdateJob(parameters)
    }
  }
}
