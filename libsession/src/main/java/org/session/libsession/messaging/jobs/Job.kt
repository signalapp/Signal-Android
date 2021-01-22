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

    fun initJob(data: Data) {
        id = data.getString(KEY_ID)
        failureCount = data.getInt(KEY_FAILURE_COUNT)
    }

    fun createJobDataBuilder(): Data.Builder {
        return Data.Builder().putString(KEY_ID, id)
                .putInt(KEY_FAILURE_COUNT, failureCount)
    }

    interface Factory<T : Job> {
        fun create(data: Data): T
    }
}