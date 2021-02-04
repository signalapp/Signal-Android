package org.session.libsession.messaging.jobs

interface Job {
    var delegate: JobDelegate?
    var id: String?
    var failureCount: Int

    val maxFailureCount: Int

    companion object {
        //keys used for database storage purpose
        private val KEY_ID = "id"
        private val KEY_FAILURE_COUNT = "failure_count"
    }

    fun execute()

    //database functions

    fun serialize(): Data

    /**
     * Returns the key that can be used to find the relevant factory needed to create your job.
     */
    fun getFactoryKey(): String

    interface Factory<T : Job> {
        fun create(data: Data): T
    }
}