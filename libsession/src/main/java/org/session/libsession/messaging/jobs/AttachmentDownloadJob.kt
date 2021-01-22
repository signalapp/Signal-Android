package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.fileserver.FileServerAPI
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentState
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsignal.service.api.crypto.AttachmentCipherInputStream
import java.io.File
import java.io.FileInputStream

class AttachmentDownloadJob(val attachmentID: Long, val tsIncomingMessageID: Long): Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    private val MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024

    // Error
    internal sealed class Error(val description: String) : Exception() {
        object NoAttachment : Error("No such attachment.")
    }

    // Settings
    override val maxFailureCount: Int = 20
    companion object {
        val collection: String = "AttachmentDownloadJobCollection"

        //keys used for database storage purpose
        private val KEY_ATTACHMENT_ID = "attachment_id"
        private val KEY_TS_INCOMING_MESSAGE_ID = "tsIncoming_message_id"
    }

    override fun execute() {
        val messageDataProvider = MessagingConfiguration.shared.messageDataProvider
        val attachmentStream = messageDataProvider.getAttachmentStream(attachmentID) ?: return handleFailure(Error.NoAttachment)
        messageDataProvider.setAttachmentState(AttachmentState.STARTED, attachmentID, this.tsIncomingMessageID)
        val tempFile = createTempFile()
        val handleFailure: (java.lang.Exception) -> Unit = { exception ->
            tempFile.delete()
            if(exception is Error && exception == Error.NoAttachment) {
                MessagingConfiguration.shared.messageDataProvider.setAttachmentState(AttachmentState.FAILED, attachmentID, tsIncomingMessageID)
                this.handlePermanentFailure(exception)
            } else if (exception is DotNetAPI.Error && exception == DotNetAPI.Error.ParsingFailed) {
                // No need to retry if the response is invalid. Most likely this means we (incorrectly)
                // got a "Cannot GET ..." error from the file server.
                MessagingConfiguration.shared.messageDataProvider.setAttachmentState(AttachmentState.FAILED, attachmentID, tsIncomingMessageID)
                this.handlePermanentFailure(exception)
            } else {
                this.handleFailure(exception)
            }
        }
        try {
            FileServerAPI.shared.downloadFile(tempFile, attachmentStream.url, MAX_ATTACHMENT_SIZE, attachmentStream.listener)
        } catch (e: Exception) {
            return handleFailure(e)
        }

        // DECRYPTION

        // Assume we're retrieving an attachment for an open group server if the digest is not set
        var stream = if (!attachmentStream.digest.isPresent || attachmentStream.key == null) FileInputStream(tempFile)
            else AttachmentCipherInputStream.createForAttachment(tempFile, attachmentStream.length.or(0).toLong(), attachmentStream.key?.toByteArray(), attachmentStream?.digest.get())

        messageDataProvider.insertAttachment(tsIncomingMessageID, attachmentID, stream)

        tempFile.delete()

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

    private fun createTempFile(): File {
        val file = File.createTempFile("push-attachment", "tmp", MessagingConfiguration.shared.context.cacheDir)
        file.deleteOnExit()
        return file
    }

    //database functions

    override fun serialize(): JobData {
        val builder = this.createJobDataBuilder()
        return builder.putLong(KEY_ATTACHMENT_ID, attachmentID)
                .putLong(KEY_TS_INCOMING_MESSAGE_ID, tsIncomingMessageID)
                .build();
    }

    class Factory: Job.Factory<AttachmentDownloadJob> {
        override fun create(data: JobData): AttachmentDownloadJob {
            val job = AttachmentDownloadJob(data.getLong(KEY_ATTACHMENT_ID), data.getLong(KEY_TS_INCOMING_MESSAGE_ID))
            job.initJob(data)
            return job
        }
    }
}