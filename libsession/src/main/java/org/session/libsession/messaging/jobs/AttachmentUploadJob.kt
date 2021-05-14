package org.session.libsession.messaging.jobs

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.file_server.FileServerAPI
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsignal.service.api.crypto.AttachmentCipherOutputStream
import org.session.libsignal.service.api.messages.SignalServiceAttachmentStream
import org.session.libsignal.service.internal.crypto.PaddingInputStream
import org.session.libsignal.service.internal.push.PushAttachmentData
import org.session.libsignal.service.internal.push.http.AttachmentCipherOutputStreamFactory
import org.session.libsignal.service.internal.util.Util
import org.session.libsignal.service.loki.PlaintextOutputStreamFactory
import org.session.libsignal.utilities.logging.Log

class AttachmentUploadJob(val attachmentID: Long, val threadID: String, val message: Message, val messageSendJobID: String) : Job {
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
        val TAG = AttachmentUploadJob::class.simpleName
        val KEY: String = "AttachmentUploadJob"

        // Keys used for database storage
        private val ATTACHMENT_ID_KEY = "attachment_id"
        private val THREAD_ID_KEY = "thread_id"
        private val MESSAGE_KEY = "message"
        private val MESSAGE_SEND_JOB_ID_KEY = "message_send_job_id"
    }

    override fun execute() {
        try {
            val attachment = MessagingModuleConfiguration.shared.messageDataProvider.getScaledSignalAttachmentStream(attachmentID)
                ?: return handleFailure(Error.NoAttachment)
            val usePadding = false
            val openGroupV2 = MessagingModuleConfiguration.shared.storage.getV2OpenGroup(threadID)
            val openGroup = MessagingModuleConfiguration.shared.storage.getOpenGroup(threadID)
            val server = openGroupV2?.server ?: openGroup?.server ?: FileServerAPI.shared.server
            val shouldEncrypt = (openGroup == null && openGroupV2 == null) // Encrypt if this isn't an open group
            val attachmentKey = Util.getSecretBytes(64)
            val paddedLength = if (usePadding) PaddingInputStream.getPaddedSize(attachment.length) else attachment.length
            val dataStream = if (usePadding) PaddingInputStream(attachment.inputStream, attachment.length) else attachment.inputStream
            val ciphertextLength = if (shouldEncrypt) AttachmentCipherOutputStream.getCiphertextLength(paddedLength) else attachment.length
            val outputStreamFactory = if (shouldEncrypt) AttachmentCipherOutputStreamFactory(attachmentKey) else PlaintextOutputStreamFactory()
            val attachmentData = PushAttachmentData(attachment.contentType, dataStream, ciphertextLength, outputStreamFactory, attachment.listener)
            val uploadResult = if (openGroupV2 != null) {
                val dataBytes = attachmentData.data.readBytes()
                val result = OpenGroupAPIV2.upload(dataBytes, openGroupV2.room, openGroupV2.server).get()
                DotNetAPI.UploadResult(result, "${openGroupV2.server}/files/$result", byteArrayOf())
            } else {
                FileServerAPI.shared.uploadAttachment(server, attachmentData)
            }
            handleSuccess(attachment, attachmentKey, uploadResult)
        } catch (e: java.lang.Exception) {
            if (e == Error.NoAttachment) {
                this.handlePermanentFailure(e)
            } else if (e is DotNetAPI.Error && !e.isRetryable) {
                this.handlePermanentFailure(e)
            } else {
                this.handleFailure(e)
            }
        }
    }

    private fun handleSuccess(attachment: SignalServiceAttachmentStream, attachmentKey: ByteArray, uploadResult: DotNetAPI.UploadResult) {
        Log.d(TAG, "Attachment uploaded successfully.")
        delegate?.handleJobSucceeded(this)
        MessagingModuleConfiguration.shared.messageDataProvider.updateAttachmentAfterUploadSucceeded(attachmentID, attachment, attachmentKey, uploadResult)
        MessagingModuleConfiguration.shared.storage.resumeMessageSendJobIfNeeded(messageSendJobID)
    }

    private fun handlePermanentFailure(e: Exception) {
        Log.w(TAG, "Attachment upload failed permanently due to error: $this.")
        delegate?.handleJobFailedPermanently(this, e)
        MessagingModuleConfiguration.shared.messageDataProvider.updateAttachmentAfterUploadFailed(attachmentID)
        failAssociatedMessageSendJob(e)
    }

    private fun handleFailure(e: Exception) {
        Log.w(TAG, "Attachment upload failed due to error: $this.")
        delegate?.handleJobFailed(this, e)
        if (failureCount + 1 == maxFailureCount) {
            failAssociatedMessageSendJob(e)
        }
    }

    private fun failAssociatedMessageSendJob(e: Exception) {
        val storage = MessagingModuleConfiguration.shared.storage
        val messageSendJob = storage.getMessageSendJob(messageSendJobID)
        MessageSender.handleFailedMessageSend(this.message, e)
        if (messageSendJob != null) {
            storage.markJobAsFailedPermanently(messageSendJobID)
        }
    }

    override fun serialize(): Data {
        val kryo = Kryo()
        kryo.isRegistrationRequired = false
        val serializedMessage = ByteArray(4096)
        val output = Output(serializedMessage)
        kryo.writeObject(output, message)
        output.close()
        return Data.Builder()
            .putLong(ATTACHMENT_ID_KEY, attachmentID)
            .putString(THREAD_ID_KEY, threadID)
            .putByteArray(MESSAGE_KEY, serializedMessage)
            .putString(MESSAGE_SEND_JOB_ID_KEY, messageSendJobID)
            .build();
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory: Job.Factory<AttachmentUploadJob> {

        override fun create(data: Data): AttachmentUploadJob {
            val serializedMessage = data.getByteArray(MESSAGE_KEY)
            val kryo = Kryo()
            kryo.isRegistrationRequired = false
            val input = Input(serializedMessage)
            val message: Message = kryo.readObject(input, Message::class.java)
            input.close()
            return AttachmentUploadJob(
                data.getLong(ATTACHMENT_ID_KEY),
                data.getString(THREAD_ID_KEY)!!,
                message,
                data.getString(MESSAGE_SEND_JOB_ID_KEY)!!
            )
        }
    }
}