package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.utilities.TextSecurePreferences

class TrimThreadJob(val threadId: Long) : Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    override val maxFailureCount: Int = 1

    companion object {
        const val KEY: String = "TrimThreadJob"
        const val THREAD_ID = "thread_id"
    }

    override fun execute() {
        val context = MessagingModuleConfiguration.shared.context
        val trimmingEnabled = TextSecurePreferences.isThreadLengthTrimmingEnabled(context)
        val threadLengthLimit = TextSecurePreferences.getThreadTrimLength(context)
        if (trimmingEnabled) {
            MessagingModuleConfiguration.shared.storage.trimThread(threadId, threadLengthLimit)
        }
        delegate?.handleJobSucceeded(this)
    }

    override fun serialize(): Data {
        return Data.Builder()
            .putLong(THREAD_ID, threadId)
            .build()
    }

    override fun getFactoryKey(): String = "TrimThreadJob"

    class Factory : Job.Factory<TrimThreadJob> {

        override fun create(data: Data): TrimThreadJob {
            return TrimThreadJob(data.getLong(THREAD_ID))
        }
    }

}