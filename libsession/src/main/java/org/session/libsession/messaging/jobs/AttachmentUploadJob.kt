package org.session.libsession.messaging.jobs

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import nl.komponents.kovenant.Promise
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.file_server.FileServerAPI
import org.session.libsession.messaging.file_server.FileServerAPIV2
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
import org.session.libsignal.service.internal.push.http.DigestingRequestBody
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
            val storage = MessagingModuleConfiguration.shared.storage
            val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
            val attachment = messageDataProvider.getScaledSignalAttachmentStream(attachmentID)
                ?: return handleFailure(Error.NoAttachment)
            val v2OpenGroup = storage.getV2OpenGroup(threadID)
            val v1OpenGroup = storage.getOpenGroup(threadID)
            if (v2OpenGroup != null) {
                val keyAndResult = upload(attachment, v2OpenGroup.server, false) {
                    OpenGroupAPIV2.upload(it, v2OpenGroup.room, v2OpenGroup.server)
                }
                handleSuccess(attachment, keyAndResult.first, keyAndResult.second)
            } else if (v1OpenGroup == null) {
                val keyAndResult = upload(attachment, FileServerAPIV2.DEFAULT_SERVER, true) {
                    FileServerAPIV2.upload(it)
                }
                handleSuccess(attachment, keyAndResult.first, keyAndResult.second)
            } else { // V1 open group
                val server = v1OpenGroup.server
                val pushData = PushAttachmentData(attachment.contentType, attachment.inputStream,
                    attachment.length, PlaintextOutputStreamFactory(), attachment.listener)
                val result = FileServerAPI.shared.uploadAttachment(server, pushData)
                handleSuccess(attachment, ByteArray(0), result)
            }
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

    private fun upload(attachment: SignalServiceAttachmentStream, server: String, encrypt: Boolean, upload: (ByteArray) -> Promise<Long, Exception>): Pair<ByteArray, DotNetAPI.UploadResult> {
        val key = if (encrypt) Util.getSecretBytes(64) else ByteArray(0)
        val rawLength = attachment.length
        val length = if (encrypt) PaddingInputStream.getPaddedSize(rawLength) else rawLength
        val stream = if (encrypt) PaddingInputStream(attachment.inputStream, rawLength) else attachment.inputStream
        val ciphertextLength = if (encrypt) AttachmentCipherOutputStream.getCiphertextLength(length) else rawLength
        val outputStreamFactory = if (encrypt) AttachmentCipherOutputStreamFactory(key) else PlaintextOutputStreamFactory()
        val pushData = PushAttachmentData(attachment.contentType, stream, ciphertextLength, outputStreamFactory, attachment.listener)
        val file = DigestingRequestBody(pushData.data, outputStreamFactory, attachment.contentType, pushData.dataSize, attachment.listener)
        val id = upload(pushData.data.readBytes()).get()
        return Pair(key, DotNetAPI.UploadResult(id, "${server}/files/$id", file.transmittedDigest))
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