package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.fileserver.FileServerAPI
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.push.PushAttachmentData
import org.session.libsignal.service.internal.push.http.AttachmentCipherOutputStreamFactory
import org.session.libsignal.service.internal.util.Util
import org.session.libsignal.service.loki.utilities.PlaintextOutputStreamFactory

class AttachmentUploadJob(val attachmentID: Long, val threadID: String, val message: Message, val messageSendJobID: String) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Error
    internal sealed class Error(val description: String) : Exception() {
        object NoAttachment : Error("No such attachment.")
    }

    // Settings
    override val maxFailureCount: Int = 20
    companion object {
        val TAG = AttachmentUploadJob::class.qualifiedName

        val collection: String = "AttachmentUploadJobCollection"
        val maxFailureCount: Int = 20
    }

    override fun execute() {
        try {
            val attachmentStream = MessagingConfiguration.shared.messageDataProvider.getAttachmentStream(attachmentID)
                    ?: return handleFailure(Error.NoAttachment)

            val openGroup = MessagingConfiguration.shared.storage.getOpenGroup(threadID)
            val server = openGroup?.server ?: FileServerAPI.server

            //TODO add some encryption stuff here
            val isEncryptionRequired = false
            //val isEncryptionRequired = (server == FileServerAPI.server)

            val attachmentKey = Util.getSecretBytes(64)
            val outputStreamFactory = if (isEncryptionRequired) AttachmentCipherOutputStreamFactory(attachmentKey) else PlaintextOutputStreamFactory()
            val ciphertextLength = attachmentStream.length

            val attachmentData = PushAttachmentData(attachmentStream.contentType, attachmentStream.inputStream, ciphertextLength, outputStreamFactory, attachmentStream.listener)

            FileServerAPI.shared.uploadAttachment(server, attachmentData)

        } catch (e: java.lang.Exception) {
            if (e is Error && e == Error.NoAttachment) {
                this.handlePermanentFailure(e)
            } else if (e is DotNetAPI.Error && !e.isRetryable) {
                this.handlePermanentFailure(e)
            } else {
                this.handleFailure(e)
            }
        }
    }

    private fun handleSuccess() {
        Log.w(TAG, "Attachment uploaded successfully.")
        delegate?.handleJobSucceeded(this)
        MessagingConfiguration.shared.storage.resumeMessageSendJobIfNeeded(messageSendJobID)
        //TODO interaction stuff, not sure how to deal with that
    }

    private fun handlePermanentFailure(e: Exception) {
        Log.w(TAG, "Attachment upload failed permanently due to error: $this.")
        delegate?.handleJobFailedPermanently(this, e)
        failAssociatedMessageSendJob(e)
    }

    private fun handleFailure(e: Exception) {
        Log.w(TAG, "Attachment upload failed due to error: $this.")
        delegate?.handleJobFailed(this, e)
        if (failureCount + 1 == AttachmentUploadJob.maxFailureCount) {
            failAssociatedMessageSendJob(e)
        }
    }

    private fun failAssociatedMessageSendJob(e: Exception) {
        val storage = MessagingConfiguration.shared.storage
        val messageSendJob = storage.getMessageSendJob(messageSendJobID)
        MessageSender.handleFailedMessageSend(this.message!!, e)
        if (messageSendJob != null) {
            storage.markJobAsFailed(messageSendJob)
        }
    }
}