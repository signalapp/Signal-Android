package org.thoughtcrime.securesms.jobmanager.persistence

data class JobSpec(
  val id: String,
  val factoryKey: String,
  val queueKey: String?,
  val createTime: Long,
  val lastRunAttemptTime: Long,
  val nextBackoffInterval: Long,
  val runAttempt: Int,
  val maxAttempts: Int,
  val lifespan: Long,
  val serializedData: ByteArray?,
  val serializedInputData: ByteArray?,
  val isRunning: Boolean,
  val isMemoryOnly: Boolean,
  val globalPriority: Int,
  val queuePriority: Int
) {

  fun withNextBackoffInterval(updated: Long): JobSpec {
    return copy(nextBackoffInterval = updated)
  }

  fun withData(updatedSerializedData: ByteArray?): JobSpec {
    return copy(serializedData = updatedSerializedData)
  }

  override fun toString(): String {
    return "id: JOB::$id | factoryKey: $factoryKey | queueKey: $queueKey | createTime: $createTime | lastRunAttemptTime: $lastRunAttemptTime | nextBackoffInterval: $nextBackoffInterval | runAttempt: $runAttempt | maxAttempts: $maxAttempts | lifespan: $lifespan | isRunning: $isRunning | memoryOnly: $isMemoryOnly | globalPriority: $globalPriority | queuePriorty: $queuePriority"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as JobSpec

    if (id != other.id) return false
    if (factoryKey != other.factoryKey) return false
    if (queueKey != other.queueKey) return false
    if (createTime != other.createTime) return false
    if (lastRunAttemptTime != other.lastRunAttemptTime) return false
    if (nextBackoffInterval != other.nextBackoffInterval) return false
    if (runAttempt != other.runAttempt) return false
    if (maxAttempts != other.maxAttempts) return false
    if (lifespan != other.lifespan) return false
    if (serializedData != null) {
      if (other.serializedData == null) return false
      if (!serializedData.contentEquals(other.serializedData)) return false
    } else if (other.serializedData != null) {
      return false
    }
    if (serializedInputData != null) {
      if (other.serializedInputData == null) return false
      if (!serializedInputData.contentEquals(other.serializedInputData)) return false
    } else if (other.serializedInputData != null) {
      return false
    }
    if (isRunning != other.isRunning) return false
    if (isMemoryOnly != other.isMemoryOnly) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + factoryKey.hashCode()
    result = 31 * result + (queueKey?.hashCode() ?: 0)
    result = 31 * result + createTime.hashCode()
    result = 31 * result + lastRunAttemptTime.hashCode()
    result = 31 * result + nextBackoffInterval.hashCode()
    result = 31 * result + runAttempt
    result = 31 * result + maxAttempts
    result = 31 * result + lifespan.hashCode()
    result = 31 * result + (serializedData?.contentHashCode() ?: 0)
    result = 31 * result + (serializedInputData?.contentHashCode() ?: 0)
    result = 31 * result + isRunning.hashCode()
    result = 31 * result + isMemoryOnly.hashCode()
    return result
  }
}
