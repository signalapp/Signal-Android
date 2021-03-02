package org.session.libsession.messaging.jobs

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.fileserver.FileServerAPI
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsignal.utilities.logging.Log
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
        val TAG = AttachmentUploadJob::class.simpleName
        val KEY: String = "AttachmentUploadJob"

        val maxFailureCount: Int = 20

        //keys used for database storage purpose
        private val KEY_ATTACHMENT_ID = "attachment_id"
        private val KEY_THREAD_ID = "thread_id"
        private val KEY_MESSAGE = "message"
        private val KEY_MESSAGE_SEND_JOB_ID = "message_send_job_id"
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

            val uploadResult = FileServerAPI.shared.uploadAttachment(server, attachmentData)
            handleSuccess(uploadResult)

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

    private fun handleSuccess(uploadResult: DotNetAPI.UploadResult) {
        Log.w(TAG, "Attachment uploaded successfully.")
        delegate?.handleJobSucceeded(this)
        //TODO: handle success in database
        MessagingConfiguration.shared.storage.resumeMessageSendJobIfNeeded(messageSendJobID)
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

    //database functions

    override fun serialize(): Data {
        //serialize Message property
        val kryo = Kryo()
        kryo.isRegistrationRequired = false
        val serializedMessage = ByteArray(4096)
        val output = Output(serializedMessage)
        kryo.writeObject(output, message)
        output.close()
        return Data.Builder().putLong(KEY_ATTACHMENT_ID, attachmentID)
                .putString(KEY_THREAD_ID, threadID)
                .putByteArray(KEY_MESSAGE, serializedMessage)
                .putString(KEY_MESSAGE_SEND_JOB_ID, messageSendJobID)
                .build();
    }

    override fun getFactoryKey(): String {
        return AttachmentDownloadJob.KEY
    }

    class Factory: Job.Factory<AttachmentUploadJob> {
        override fun create(data: Data): AttachmentUploadJob {
            val serializedMessage = data.getByteArray(KEY_MESSAGE)
            //deserialize Message property
            val kryo = Kryo()
            val input = Input(serializedMessage)
            val message: Message = kryo.readObject(input, Message::class.java)
            input.close()
            return AttachmentUploadJob(data.getLong(KEY_ATTACHMENT_ID), data.getString(KEY_THREAD_ID)!!, message, data.getString(KEY_MESSAGE_SEND_JOB_ID)!!)
        }
    }
}