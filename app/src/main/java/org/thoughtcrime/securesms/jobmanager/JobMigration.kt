package org.thoughtcrime.securesms.jobmanager

/**
 * Create a subclass of this to perform a migration on persisted [Job]s. A migration targets
 * a specific end version, and the assumption is that it can migrate jobs to that end version from
 * the previous version. The class will be provided a bundle of job data for each persisted job and
 * give back an updated version (if applicable).
 */
abstract class JobMigration protected constructor(val endVersion: Int) {

  /**
   * Given a bundle of job data, return a bundle of job data that should be used in place of it.
   * You may obviously return the same object if you don't wish to change it.
   */
  abstract fun migrate(jobData: JobData): JobData

  data class JobData(
    val factoryKey: String,
    val queueKey: String?,
    val maxAttempts: Int,
    val lifespan: Long,
    val data: ByteArray?
  ) {
    fun withFactoryKey(newFactoryKey: String): JobData {
      return copy(factoryKey = newFactoryKey)
    }

    fun withQueueKey(newQueueKey: String?): JobData {
      return copy(queueKey = newQueueKey)
    }

    fun withData(newData: ByteArray?): JobData {
      return copy(data = newData)
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as JobData

      if (factoryKey != other.factoryKey) return false
      if (queueKey != other.queueKey) return false
      if (maxAttempts != other.maxAttempts) return false
      if (lifespan != other.lifespan) return false
      if (data != null) {
        if (other.data == null) return false
        if (!data.contentEquals(other.data)) return false
      } else if (other.data != null) return false

      return true
    }

    override fun hashCode(): Int {
      var result = factoryKey.hashCode()
      result = 31 * result + (queueKey?.hashCode() ?: 0)
      result = 31 * result + maxAttempts
      result = 31 * result + lifespan.hashCode()
      result = 31 * result + (data?.contentHashCode() ?: 0)
      return result
    }

    companion object {
      @JvmField
      val FAILING_JOB_DATA = JobData("FailingJob", null, -1, -1, null)
    }
  }
}
