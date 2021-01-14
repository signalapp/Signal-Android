package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos

class MessageSendJob(val message: Message, val destination: Destination) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Settings
    override val maxFailureCount: Int = 10
    companion object {
        val TAG = MessageSendJob::class.qualifiedName

        val collection: String = "MessageSendJobCollection"
    }

    override fun execute() {
        val messageDataProvider = MessagingConfiguration.shared.messageDataProvider
        val message = message as? VisibleMessage
        message?.let {
            if(!messageDataProvider.isOutgoingMessage(message.sentTimestamp!!)) return // The message has been deleted
            val attachments = message.attachmentIDs.map { messageDataProvider.getAttachmentStream(it) }.filterNotNull()
            val attachmentsToUpload = attachments.filter { !it.isUploaded }
            attachmentsToUpload.forEach {
                if(MessagingConfiguration.shared.storage.getAttachmentUploadJob(it.attachmentId) != null) {
                    // Wait for it to finish
                } else {
                    val job = AttachmentUploadJob(it.attachmentId, message.threadID!!.toString(), message, id!!)
                    JobQueue.shared.add(job)
                }
            }
            if (attachmentsToUpload.isNotEmpty()) return // Wait for all attachments to upload before continuing
        }
        MessageSender.send(this.message, this.destination).success {
            this.handleSuccess()
        }.fail { exception ->
            Log.e(TAG, "Couldn't send message due to error: $exception.")
            val e = exception as? MessageSender.Error
            e?.let {
                if (!e.isRetryable) this.handlePermanentFailure(e)
            }
            this.handleFailure(exception)
        }
    }

    private fun handleSuccess() {
        delegate?.handleJobSucceeded(this)
    }

    private fun handlePermanentFailure(error: Exception) {
        delegate?.handleJobFailedPermanently(this, error)
    }

    private fun handleFailure(error: Exception) {
        Log.w(TAG, "Failed to send $message::class.simpleName.")
        val message = message as? VisibleMessage
        message?.let {
            if(!MessagingConfiguration.shared.messageDataProvider.isOutgoingMessage(message.sentTimestamp!!)) return // The message has been deleted
        }
        delegate?.handleJobFailed(this, error)
    }
}