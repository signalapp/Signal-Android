package org.session.libsession.messaging.jobs

import okhttp3.HttpUrl
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentState
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.utilities.DownloadUtilities
import org.session.libsignal.streams.AttachmentCipherInputStream
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import java.io.File
import java.io.FileInputStream

class AttachmentDownloadJob(val attachmentID: Long, val databaseMessageID: Long) : Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Error
    internal sealed class Error(val description: String) : Exception(description) {
        object NoAttachment : Error("No such attachment.")
    }

    // Settings
    override val maxFailureCount: Int = 20

    companion object {
        val KEY: String = "AttachmentDownloadJob"

        // Keys used for database storage
        private val ATTACHMENT_ID_KEY = "attachment_id"
        private val TS_INCOMING_MESSAGE_ID_KEY = "tsIncoming_message_id"
    }

    override fun execute() {
        val storage = MessagingModuleConfiguration.shared.storage
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val handleFailure: (java.lang.Exception) -> Unit = { exception ->
            if (exception == Error.NoAttachment) {
                messageDataProvider.setAttachmentState(AttachmentState.FAILED, attachmentID, databaseMessageID)
                this.handlePermanentFailure(exception)
            } else {
                this.handleFailure(exception)
            }
        }
        try {
            val attachment = messageDataProvider.getDatabaseAttachment(attachmentID)
                ?: return handleFailure(Error.NoAttachment)
            messageDataProvider.setAttachmentState(AttachmentState.STARTED, attachmentID, this.databaseMessageID)
            val tempFile = createTempFile()
            val threadID = storage.getThreadIdForMms(databaseMessageID)
            val openGroupV2 = storage.getV2OpenGroup(threadID)
            val inputStream = if (openGroupV2 == null) {
                DownloadUtilities.downloadFile(tempFile, attachment.url)
                // Assume we're retrieving an attachment for an open group server if the digest is not set
                if (attachment.digest?.size ?: 0 == 0 || attachment.key.isNullOrEmpty()) {
                    FileInputStream(tempFile)
                } else {
                    AttachmentCipherInputStream.createForAttachment(tempFile, attachment.size, Base64.decode(attachment.key), attachment.digest)
                }
            } else {
                val url = HttpUrl.parse(attachment.url)!!
                val fileID = url.pathSegments().last()
                OpenGroupAPIV2.download(fileID.toLong(), openGroupV2.room, openGroupV2.server).get().let {
                    tempFile.writeBytes(it)
                }
                FileInputStream(tempFile)
            }
            messageDataProvider.insertAttachment(databaseMessageID, attachment.attachmentId, inputStream)
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
        val file = File.createTempFile("push-attachment", "tmp", MessagingModuleConfiguration.shared.context.cacheDir)
        file.deleteOnExit()
        return file
    }

    override fun serialize(): Data {
        return Data.Builder()
            .putLong(ATTACHMENT_ID_KEY, attachmentID)
            .putLong(TS_INCOMING_MESSAGE_ID_KEY, databaseMessageID)
            .build();
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory : Job.Factory<AttachmentDownloadJob> {

        override fun create(data: Data): AttachmentDownloadJob {
            return AttachmentDownloadJob(data.getLong(ATTACHMENT_ID_KEY), data.getLong(TS_INCOMING_MESSAGE_ID_KEY))
        }
    }
}