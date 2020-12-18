package org.session.libsession.messaging.jobs

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.sending_receiving.MessageReceiver
import org.session.libsession.messaging.sending_receiving.handle
import org.session.libsignal.libsignal.logging.Log

class MessageReceiveJob(val data: ByteArray, val isBackgroundPoll: Boolean, val openGroupMessageServerID: Long? = null, val openGroupID: String? = null) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Settings
    override val maxFailureCount: Int = 10
    companion object {
        val TAG = MessageReceiveJob::class.qualifiedName

        val collection: String = "MessageReceiveJobCollection"
    }

    override fun execute() {
        executeAsync().get()
    }

    fun executeAsync(): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        try {
            val (message, proto) = MessageReceiver.parse(this.data, this.openGroupMessageServerID)
            MessageReceiver.handle(message, proto, this.openGroupID)
            this.handleSuccess()
            deferred.resolve(Unit)
        } catch (e: Exception) {
            Log.d(TAG, "Couldn't receive message due to error: $e.")
            val error = e as? MessageReceiver.Error
            error?.let {
                if (!error.isRetryable) this.handlePermanentFailure(error)
            }
            this.handleFailure(e)
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
}