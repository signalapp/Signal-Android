package org.session.libsession.messaging.jobs

import okhttp3.HttpUrl
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.fileserver.FileServerAPI
import org.session.libsession.messaging.opengroups.OpenGroupAPIV2
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentState
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsignal.service.api.crypto.AttachmentCipherInputStream
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.logging.Log
import java.io.File
import java.io.FileInputStream

class AttachmentDownloadJob(val attachmentID: Long, val databaseMessageID: Long) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    private val MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024

    // Error
    internal sealed class Error(val description: String) : Exception(description) {
        object NoAttachment : Error("No such attachment.")
    }

    // Settings
    override val maxFailureCount: Int = 20

    companion object {
        val KEY: String = "AttachmentDownloadJob"

        //keys used for database storage purpose
        private val KEY_ATTACHMENT_ID = "attachment_id"
        private val KEY_TS_INCOMING_MESSAGE_ID = "tsIncoming_message_id"
    }

    override fun execute() {
        val handleFailure: (java.lang.Exception) -> Unit = { exception ->
            if (exception is Error && exception == Error.NoAttachment) {
                MessagingConfiguration.shared.messageDataProvider.setAttachmentState(AttachmentState.FAILED, attachmentID, databaseMessageID)
                this.handlePermanentFailure(exception)
            } else if (exception is DotNetAPI.Error && exception == DotNetAPI.Error.ParsingFailed) {
                // No need to retry if the response is invalid. Most likely this means we (incorrectly)
                // got a "Cannot GET ..." error from the file server.
                MessagingConfiguration.shared.messageDataProvider.setAttachmentState(AttachmentState.FAILED, attachmentID, databaseMessageID)
                this.handlePermanentFailure(exception)
            } else {
                this.handleFailure(exception)
            }
        }
        try {
            val messageDataProvider = MessagingConfiguration.shared.messageDataProvider
            val attachment = messageDataProvider.getDatabaseAttachment(attachmentID)
                    ?: return handleFailure(Error.NoAttachment)
            messageDataProvider.setAttachmentState(AttachmentState.STARTED, attachmentID, this.databaseMessageID)
            val tempFile = createTempFile()

            val threadId = MessagingConfiguration.shared.storage.getThreadIdForMms(databaseMessageID)
            val openGroupV2 = MessagingConfiguration.shared.storage.getV2OpenGroup(threadId.toString())

            val stream = if (openGroupV2 == null) {
                FileServerAPI.shared.downloadFile(tempFile, attachment.url, MAX_ATTACHMENT_SIZE, null)

                // DECRYPTION

                // Assume we're retrieving an attachment for an open group server if the digest is not set
                if (attachment.digest?.size ?: 0 == 0 || attachment.key.isNullOrEmpty()) FileInputStream(tempFile)
                else AttachmentCipherInputStream.createForAttachment(tempFile, attachment.size, Base64.decode(attachment.key), attachment.digest)
            } else {
                val url = HttpUrl.parse(attachment.url)!!
                val fileId = url.pathSegments().last()
                OpenGroupAPIV2.download(fileId.toLong(), openGroupV2.room, openGroupV2.server).get().let {
                    tempFile.writeBytes(it)
                }
                FileInputStream(tempFile)
            }
            messageDataProvider.insertAttachment(databaseMessageID, attachment.attachmentId, stream)
            tempFile.delete()
            handleSuccess()
        } catch (e: Exception) {
            return handleFailure(e)
        }
    }

    private fun handleSuccess() {
        Log.w(AttachmentUploadJob.TAG, "Attachment downloaded successfully.")
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

    override fun serialize(): Data {
        return Data.Builder().putLong(KEY_ATTACHMENT_ID, attachmentID)
                .putLong(KEY_TS_INCOMING_MESSAGE_ID, databaseMessageID)
                .build();
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory : Job.Factory<AttachmentDownloadJob> {
        override fun create(data: Data): AttachmentDownloadJob {
            return AttachmentDownloadJob(data.getLong(KEY_ATTACHMENT_ID), data.getLong(KEY_TS_INCOMING_MESSAGE_ID))
        }
    }
}