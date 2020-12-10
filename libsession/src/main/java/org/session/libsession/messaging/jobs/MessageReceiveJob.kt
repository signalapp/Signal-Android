package org.session.libsession.messaging.jobs

class MessageReceiveJob(val data: ByteArray, val isBackgroundPoll: Boolean, val openGroupMessageServerID: Long? = null, val openGroupID: String? = null) : Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Settings
    override val maxFailureCount: Int = 10
    companion object {
        val collection: String = "MessageReceiveJobCollection"
    }

    override fun execute() {
        TODO("Not yet implemented")
    }
}