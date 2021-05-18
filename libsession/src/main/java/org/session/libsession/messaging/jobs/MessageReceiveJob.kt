package org.session.libsession.messaging.jobs

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.sending_receiving.MessageReceiver
import org.session.libsession.messaging.sending_receiving.handle
import org.session.libsession.messaging.utilities.Data
import org.session.libsignal.utilities.Log

class MessageReceiveJob(val data: ByteArray, val isBackgroundPoll: Boolean, val openGroupMessageServerID: Long? = null, val openGroupID: String? = null) : Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    override val maxFailureCount: Int = 10
    companion object {
        val TAG = MessageReceiveJob::class.simpleName
        val KEY: String = "MessageReceiveJob"

        private val RECEIVE_LOCK = Object()

        // Keys used for database storage
        private val DATA_KEY = "data"
        // FIXME: We probably shouldn't be using this job when background polling
        private val IS_BACKGROUND_POLL_KEY = "is_background_poll"
        private val OPEN_GROUP_MESSAGE_SERVER_ID_KEY = "openGroupMessageServerID"
        private val OPEN_GROUP_ID_KEY = "open_group_id"
    }

    override fun execute() {
        executeAsync().get()
    }

    fun executeAsync(): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        try {
            val isRetry: Boolean = failureCount != 0
            val (message, proto) = MessageReceiver.parse(this.data, this.openGroupMessageServerID, isRetry)
            synchronized(RECEIVE_LOCK) { // FIXME: Do we need this?
                MessageReceiver.handle(message, proto, this.openGroupID)
            }
            this.handleSuccess()
            deferred.resolve(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't receive message.", e)
            if (e is MessageReceiver.Error && !e.isRetryable) {
                Log.e("Loki", "Message receive job permanently failed.", e)
                this.handlePermanentFailure(e)
            } else {
                Log.e("Loki", "Couldn't receive message.", e)
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

    override fun serialize(): Data {
        val builder = Data.Builder().putByteArray(DATA_KEY, data)
            .putBoolean(IS_BACKGROUND_POLL_KEY, isBackgroundPoll)
        openGroupMessageServerID?.let { builder.putLong(OPEN_GROUP_MESSAGE_SERVER_ID_KEY, it) }
        openGroupID?.let { builder.putString(OPEN_GROUP_ID_KEY, it) }
        return builder.build();
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory: Job.Factory<MessageReceiveJob> {

        override fun create(data: Data): MessageReceiveJob {
            return MessageReceiveJob(
                data.getByteArray(DATA_KEY),
                data.getBoolean(IS_BACKGROUND_POLL_KEY),
                data.getLong(OPEN_GROUP_MESSAGE_SERVER_ID_KEY),
                data.getString(OPEN_GROUP_ID_KEY)
            )
        }
    }
}