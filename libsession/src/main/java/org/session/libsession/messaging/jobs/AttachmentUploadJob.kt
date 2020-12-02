package org.session.libsession.messaging.jobs

class AttachmentUploadJob : Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Settings
    override val maxFailureCount: Int = 20
    companion object {
        val collection: String = "AttachmentUploadJobCollection"
    }

    override fun execute() {
        TODO("Not yet implemented")
    }
}