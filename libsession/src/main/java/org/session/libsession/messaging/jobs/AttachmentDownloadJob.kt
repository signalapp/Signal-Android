package org.session.libsession.messaging.jobs

import org.greenrobot.eventbus.EventBus
import org.session.libsession.events.AttachmentProgressEvent
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.fileserver.FileServerAPI
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentState
import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachment
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsignal.service.api.crypto.AttachmentCipherInputStream
import org.session.libsignal.service.api.messages.SignalServiceAttachment
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
    }

    override fun execute() {
        val messageDataProvider = MessagingConfiguration.shared.messageDataProvider
        val attachmentPointer = messageDataProvider.getAttachmentPointer(attachmentID) ?: return handleFailure(Error.NoAttachment)
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
            //TODO find how to implement a functional interface in kotlin + use it here & on AttachmentUploadJob (see TODO in DatabaseAttachmentProvider.kt on app side)
            val listener = SessionServiceAttachment.ProgressListener { override fun onAttachmentProgress(total: Long, progress: Long) { EventBus.getDefault().postSticky(AttachmentProgressEvent(attachmentPointer, total, progress)) } }
            FileServerAPI.shared.downloadFile(tempFile, attachmentPointer.url, MAX_ATTACHMENT_SIZE, listener)
        } catch (e: Exception) {
            return handleFailure(e)
        }

        // Assume we're retrieving an attachment for an open group server if the digest is not set
        var stream = if (!attachmentPointer.digest.isPresent) FileInputStream(tempFile)
            else AttachmentCipherInputStream.createForAttachment(tempFile, attachmentPointer.size.or(0).toLong(), attachmentPointer.key?.toByteArray(), attachmentPointer.digest.get())

        messageDataProvider.insertAttachment(tsIncomingMessageID, attachmentID, stream)

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
}