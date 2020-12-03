package org.session.libsession.messaging.jobs

class AttachmentDownloadJob: Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Settings
    override val maxFailureCount: Int = 100
    companion object {
        val collection: String = "AttachmentDownloadJobCollection"
    }

    override fun execute() {
        TODO("Not yet implemented")
    }
}