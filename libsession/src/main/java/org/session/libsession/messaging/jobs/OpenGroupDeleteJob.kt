package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.Data
import org.session.libsignal.utilities.Log

class OpenGroupDeleteJob(private val messageServerIds: LongArray, private val threadId: Long, val openGroupId: String): Job {

    companion object {
        private const val TAG = "OpenGroupDeleteJob"
        const val KEY = "OpenGroupDeleteJob"
        private const val MESSAGE_IDS = "messageIds"
        private const val THREAD_ID = "threadId"
        private const val OPEN_GROUP_ID = "openGroupId"
    }

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    override fun execute() {
        val dataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val numberToDelete = messageServerIds.size
        Log.d(TAG, "Deleting $numberToDelete messages")
        messageServerIds.forEach { serverId ->
            val (messageId, isSms) = dataProvider.getMessageID(serverId, threadId) ?: return@forEach
            dataProvider.deleteMessage(messageId, isSms)
        }
        Log.d(TAG, "Deleted $numberToDelete messages successfully")
        delegate?.handleJobSucceeded(this)
    }

    override fun serialize(): Data = Data.Builder()
        .putLongArray(MESSAGE_IDS, messageServerIds)
        .putLong(THREAD_ID, threadId)
        .putString(OPEN_GROUP_ID, openGroupId)
        .build()

    override fun getFactoryKey(): String = KEY

    class Factory: Job.Factory<OpenGroupDeleteJob> {
        override fun create(data: Data): OpenGroupDeleteJob {
            val messageServerIds = data.getLongArray(MESSAGE_IDS)
            val threadId = data.getLong(THREAD_ID)
            val openGroupId = data.getString(OPEN_GROUP_ID)
            return OpenGroupDeleteJob(messageServerIds, threadId, openGroupId)
        }
    }

}