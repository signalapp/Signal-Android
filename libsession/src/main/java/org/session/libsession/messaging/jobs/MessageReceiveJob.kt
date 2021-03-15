package org.session.libsession.messaging.jobs

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.sending_receiving.MessageReceiver
import org.session.libsession.messaging.sending_receiving.handle
import org.session.libsignal.utilities.logging.Log

class MessageReceiveJob(val data: ByteArray, val isBackgroundPoll: Boolean, val openGroupMessageServerID: Long? = null, val openGroupID: String? = null) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Settings
    override val maxFailureCount: Int = 10
    companion object {
        val TAG = MessageReceiveJob::class.qualifiedName
        val KEY: String = "MessageReceiveJob"

        //keys used for database storage purpose
        private val KEY_DATA = "data"
        private val KEY_IS_BACKGROUND_POLL = "is_background_poll"
        private val KEY_OPEN_GROUP_MESSAGE_SERVER_ID = "openGroupMessageServerID"
        private val KEY_OPEN_GROUP_ID = "open_group_id"
    }

    override fun execute() {
        executeAsync().get()
    }

    fun executeAsync(): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        try {
            val isRetry: Boolean = failureCount != 0
            val (message, proto) = MessageReceiver.parse(this.data, this.openGroupMessageServerID, isRetry)
            MessageReceiver.handle(message, proto, this.openGroupID)
            this.handleSuccess()
            deferred.resolve(Unit)
        } catch (e: Exception) {
            Log.d(TAG, "Couldn't receive message due to error: $e.")
            val error = e as? MessageReceiver.Error
            if (error != null && !error.isRetryable) {
                Log.d("Loki", "Message receive job permanently failed due to error: $error.")
                this.handlePermanentFailure(error)
            } else {
                Log.d("Loki", "Couldn't receive message due to error: $e.")
                this.handleFailure(e)
            }
            deferred.resolve(Unit) // The promise is just used to keep track of when we're done
        }
        return deferred.promise
    }

    private fun handleSuccess() {
        delegate?.handleJobSucceeded(this)
    }

    private fun handlePermanentFailure(e: Exception) {
        delegate?.handleJobFailedPermanently(this, e)
    }

    private fun handleFailure(e: Exception) {
        delegate?.handleJobFailed(this, e)
    }

    //database functions

    override fun serialize(): Data {
        val builder = Data.Builder().putByteArray(KEY_DATA, data)
                .putBoolean(KEY_IS_BACKGROUND_POLL, isBackgroundPoll)
        openGroupMessageServerID?.let { builder.putLong(KEY_OPEN_GROUP_MESSAGE_SERVER_ID, openGroupMessageServerID) }
        openGroupID?.let { builder.putString(KEY_OPEN_GROUP_ID, openGroupID) }
        return builder.build();
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory: Job.Factory<MessageReceiveJob> {
        override fun create(data: Data): MessageReceiveJob {
            return MessageReceiveJob(data.getByteArray(KEY_DATA), data.getBoolean(KEY_IS_BACKGROUND_POLL), data.getLong(KEY_OPEN_GROUP_MESSAGE_SERVER_ID), data.getString(KEY_OPEN_GROUP_ID))
        }
    }
}