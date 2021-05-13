package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.utilities.Data

interface Job {
    var delegate: JobDelegate?
    var id: String?
    var failureCount: Int

    val maxFailureCount: Int

    companion object {

        // Keys used for database storage
        private val ID_KEY = "id"
        private val FAILURE_COUNT_KEY = "failure_count"
        internal const val MAX_BUFFER_SIZE = 1_000_000 // bytes
    }

    fun execute()

    fun serialize(): Data

    fun getFactoryKey(): String

    interface Factory<T : Job> {

        fun create(data: Data): T?
    }
}