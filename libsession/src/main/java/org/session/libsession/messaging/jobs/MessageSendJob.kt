package org.session.libsession.messaging.jobs

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsignal.utilities.logging.Log

class MessageSendJob(val message: Message, val destination: Destination) : Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0

    // Settings
    override val maxFailureCount: Int = 10
    companion object {
        val TAG = MessageSendJob::class.simpleName
        val KEY: String = "MessageSendJob"

        // Keys used for database storage
        private val KEY_MESSAGE = "message"
        private val KEY_DESTINATION = "destination"
    }

    override fun execute() {
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val message = message as? VisibleMessage
        message?.let {
            if(!messageDataProvider.isOutgoingMessage(message.sentTimestamp!!)) return // The message has been deleted
            val attachmentIDs = mutableListOf<Long>()
            attachmentIDs.addAll(message.attachmentIDs)
            message.quote?.let { it.attachmentID?.let { attachmentID -> attachmentIDs.add(attachmentID) } }
            message.linkPreview?.let { it.attachmentID?.let { attachmentID -> attachmentIDs.add(attachmentID) } }
            val attachments = attachmentIDs.mapNotNull { messageDataProvider.getDatabaseAttachment(it) }
            val attachmentsToUpload = attachments.filter { it.url.isNullOrEmpty() }
            attachmentsToUpload.forEach {
                if (MessagingModuleConfiguration.shared.storage.getAttachmentUploadJob(it.attachmentId.rowId) != null) {
                    // Wait for it to finish
                } else {
                    val job = AttachmentUploadJob(it.attachmentId.rowId, message.threadID!!.toString(), message, id!!)
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
            if(!MessagingModuleConfiguration.shared.messageDataProvider.isOutgoingMessage(message.sentTimestamp!!)) return // The message has been deleted
        }
        delegate?.handleJobFailed(this, error)
    }

    override fun serialize(): Data {
        val kryo = Kryo()
        kryo.isRegistrationRequired = false
        val output = Output(ByteArray(4096), 10_000_000) // maxBufferSize '-1' will dynamically grow internally if we run out of room serializing the message
        kryo.writeClassAndObject(output, message)
        output.close()
        val serializedMessage = output.toBytes()
        output.clear()
        kryo.writeClassAndObject(output, destination)
        output.close()
        val serializedDestination = output.toBytes()
        return Data.Builder().putByteArray(KEY_MESSAGE, serializedMessage)
            .putByteArray(KEY_DESTINATION, serializedDestination)
            .build();
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory: Job.Factory<MessageSendJob> {

        override fun create(data: Data): MessageSendJob {
            val serializedMessage = data.getByteArray(KEY_MESSAGE)
            val serializedDestination = data.getByteArray(KEY_DESTINATION)
            val kryo = Kryo()
            var input = Input(serializedMessage)
            val message = kryo.readClassAndObject(input) as Message
            input.close()
            input = Input(serializedDestination)
            val destination = kryo.readClassAndObject(input) as Destination
            input.close()
            return MessageSendJob(message, destination)
        }
    }
}