package org.thoughtcrime.securesms.jobs

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint

/**
 * During group state processing we sometimes detect situations where we should auto-leave. For example,
 * being added to a group by someone we've blocked. This job functions similarly to other GV2 related
 * jobs in that it waits for all decryptions to occur and then enqueues the actual [LeaveGroupV2Job] as
 * part of the group's message processing queue.
 */
class LeaveGroupV2Job(parameters: Parameters, private val groupId: GroupId.V2) : BaseJob(parameters) {

  constructor(groupId: GroupId.V2) : this(
    parameters = Parameters.Builder()
      .setQueue("LeaveGroupV2Job")
      .addConstraint(DecryptionsDrainedConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    groupId = groupId
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_GROUP_ID, groupId.toString())
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onRun() {
    AppDependencies.jobManager.add(LeaveGroupV2WorkerJob(groupId))
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  override fun onFailure() = Unit

  class Factory : Job.Factory<LeaveGroupV2Job> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): LeaveGroupV2Job {
      val data = JsonJobData.deserialize(serializedData)
      return LeaveGroupV2Job(parameters, GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV2())
    }
  }

  companion object {
    const val KEY = "LeaveGroupV2Job"

    private const val KEY_GROUP_ID = "group_id"
  }
}
