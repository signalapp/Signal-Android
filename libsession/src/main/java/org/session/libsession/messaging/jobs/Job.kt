package org.session.libsession.messaging.jobs

interface Job {
    var delegate: JobDelegate?
    var id: String?
    var failureCount: Int

    val maxFailureCount: Int

    companion object {
        // Keys used for database storage
        private val KEY_ID = "id"
        private val KEY_FAILURE_COUNT = "failure_count"
        internal const val MAX_BUFFER_SIZE = 1_000_000 // bytes
    }

    fun execute()

    fun serialize(): Data

    /**
     * Returns the key that can be used to find the relevant factory needed to create your job.
     */
    fun getFactoryKey(): String

    interface Factory<T : Job> {
        fun create(data: Data): T
    }
}