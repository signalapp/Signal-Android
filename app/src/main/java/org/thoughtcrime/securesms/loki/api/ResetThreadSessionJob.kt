package org.thoughtcrime.securesms.loki.api

import org.session.libsession.messaging.jobs.Data
import org.session.libsession.messaging.threads.Address
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.logging.Log
import org.session.libsession.messaging.threads.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.sms.OutgoingEndSessionMessage
import org.thoughtcrime.securesms.sms.OutgoingTextMessage
import java.util.concurrent.TimeUnit

class ResetThreadSessionJob private constructor(
        parameters: Parameters,
        private val address: Address,
        private val threadId: Long)
    : BaseJob(parameters) {

    companion object {
        const val KEY = "ResetThreadSessionJob"
        const val DATA_KEY_ADDRESS = "address"
        const val DATA_KEY_THREAD_ID = "thread_id"
    }

    constructor(address: Address, threadId: Long) : this(Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(KEY)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(Parameters.UNLIMITED)
            .build(),
            address,
            threadId)

    override fun serialize(): Data {
        return Data.Builder()
                .putParcelable(DATA_KEY_ADDRESS, address)
                .putLong(DATA_KEY_THREAD_ID, threadId)
                .build()
    }

    override fun getFactoryKey(): String { return KEY }

    public override fun onRun() {
        val recipient = Recipient.from(context, address, false)

        // Only reset sessions for private chats.
        if (recipient.isGroupRecipient) return

        Log.v(KEY, "Resetting session for thread: \"$threadId\", recipient: \"${address.serialize()}\"")

        val message = OutgoingEndSessionMessage(OutgoingTextMessage(recipient, "TERMINATE", 0, -1))
        MessageSender.send(context, message, threadId, false, null)
    }

    public override fun onShouldRetry(e: Exception): Boolean {
        return false
    }

    override fun onCanceled() { }

    class Factory : Job.Factory<ResetThreadSessionJob> {

        override fun create(parameters: Parameters, data: Data): ResetThreadSessionJob {
            val address = data.getParcelable(DATA_KEY_ADDRESS, Address.CREATOR)!!
            val threadId = data.getLong(DATA_KEY_THREAD_ID)
            return ResetThreadSessionJob(parameters, address, threadId)
        }
    }
}
