package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.utilities.TextSecurePreferences

class TrimThreadJob(val threadId: Long, val openGroupId: String?) : Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    override val maxFailureCount: Int = 1

    companion object {
        const val KEY: String = "TrimThreadJob"
        const val THREAD_ID = "thread_id"
        const val OPEN_GROUP_ID = "open_group"

        const val TRIM_TIME_LIMIT = 15552000000L // trim messages older than this
        const val THREAD_LENGTH_TRIGGER_SIZE = 2000
    }

    override fun execute() {
        val context = MessagingModuleConfiguration.shared.context
        val trimmingEnabled = TextSecurePreferences.isThreadLengthTrimmingEnabled(context)
        val storage = MessagingModuleConfiguration.shared.storage
        val messageCount = storage.getMessageCount(threadId)
        if (trimmingEnabled && !openGroupId.isNullOrEmpty() && messageCount >= THREAD_LENGTH_TRIGGER_SIZE) {
            val oldestMessageTime = System.currentTimeMillis() - TRIM_TIME_LIMIT
            storage.trimThreadBefore(threadId, oldestMessageTime)
        }
        delegate?.handleJobSucceeded(this)
    }

    override fun serialize(): Data {
        val builder = Data.Builder()
            .putLong(THREAD_ID, threadId)
        if (!openGroupId.isNullOrEmpty()) {
            builder.putString(OPEN_GROUP_ID, openGroupId)
        }
        return builder.build()
    }

    override fun getFactoryKey(): String = "TrimThreadJob"

    class Factory : Job.Factory<TrimThreadJob> {

        override fun create(data: Data): TrimThreadJob {
            return TrimThreadJob(data.getLong(THREAD_ID), data.getStringOrDefault(OPEN_GROUP_ID, null))
        }
    }

}