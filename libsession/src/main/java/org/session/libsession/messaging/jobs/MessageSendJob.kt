package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message

class MessageSendJob(val message: Message, val destination: Destination) : Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Settings
    override val maxFailureCount: Int = 10
    companion object {
        val collection: String = "MessageSendJobCollection"
    }

    override fun execute() {
        TODO("Not yet implemented")
    }
}