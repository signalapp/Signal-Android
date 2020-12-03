package org.session.libsession.messaging.jobs

class MessageSendJob : Job {
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