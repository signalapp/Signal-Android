package org.session.libsession.messaging.jobs

interface Job {
    var delegate: JobDelegate?
    var id: String?
    var failureCount: Int

    val maxFailureCount: Int

    companion object {

        // Keys used for database storage
        private val ID_KEY = "id"
        private val FAILURE_COUNT_KEY = "failure_count"
    }

    fun execute()

    fun serialize(): Data

    fun getFactoryKey(): String

    interface Factory<T : Job> {
        
        fun create(data: Data): T
    }
}