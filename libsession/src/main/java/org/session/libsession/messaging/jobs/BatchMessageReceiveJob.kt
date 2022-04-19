package org.session.libsession.messaging.jobs

import com.google.protobuf.ByteString
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.session.libsession.messaging.sending_receiving.MessageReceiver
import org.session.libsession.messaging.sending_receiving.handle
import org.session.libsession.messaging.utilities.Data
import org.session.libsignal.protos.UtilProtos
import org.session.libsignal.utilities.Log

data class MessageReceiveParameters(
    val data: ByteArray,
    val serverHash: String? = null,
    val openGroupMessageServerID: Long? = null
)

class BatchMessageReceiveJob(
    val messages: List<MessageReceiveParameters>,
    val openGroupID: String? = null
) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 10
    // Failure Exceptions must be retryable if they're a  MessageReceiver.Error
    val failures = mutableListOf<MessageReceiveParameters>()

    companion object {
        const val TAG = "BatchMessageReceiveJob"
        const val KEY = "BatchMessageReceiveJob"

        const val BATCH_DEFAULT_NUMBER = 50

        // Keys used for database storage
        private val NUM_MESSAGES_KEY = "numMessages"
        private val DATA_KEY = "data"
        private val SERVER_HASH_KEY = "serverHash"
        private val OPEN_GROUP_MESSAGE_SERVER_ID_KEY = "openGroupMessageServerID"
        private val OPEN_GROUP_ID_KEY = "open_group_id"
    }

    override fun execute() {
        executeAsync().get()
    }

    fun executeAsync(): Promise<Unit, Exception> {
        return task {
            messages.iterator().forEach { messageParameters ->
                val (data, serverHash, openGroupMessageServerID) = messageParameters
                try {
                    val (message, proto) = MessageReceiver.parse(data, openGroupMessageServerID)
                    message.serverHash = serverHash
                    MessageReceiver.handle(message, proto, this.openGroupID)
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't receive message.", e)
                    if (e is MessageReceiver.Error && !e.isRetryable) {
                        Log.e(TAG, "Message failed permanently",e)
                    } else {
                        Log.e(TAG, "Message failed",e)
                        failures += messageParameters
                    }
                }
            }
            if (failures.isEmpty()) {
                handleSuccess()
            } else {
                handleFailure()
            }
        }
    }

    private fun handleSuccess() {
        this.delegate?.handleJobSucceeded(this)
    }

    private fun handleFailure() {
        this.delegate?.handleJobFailed(this, Exception("One or more jobs resulted in failure"))
    }

    override fun serialize(): Data {
        val arraySize = messages.size
        val dataArrays = UtilProtos.ByteArrayList.newBuilder()
            .addAllContent(messages.map(MessageReceiveParameters::data).map(ByteString::copyFrom))
            .build()
        val serverHashes = messages.map { it.serverHash.orEmpty() }
        val openGroupServerIds = messages.map { it.openGroupMessageServerID ?: -1L }
        return Data.Builder()
            .putInt(NUM_MESSAGES_KEY, arraySize)
            .putByteArray(DATA_KEY, dataArrays.toByteArray())
            .putString(OPEN_GROUP_ID_KEY, openGroupID)
            .putLongArray(OPEN_GROUP_MESSAGE_SERVER_ID_KEY, openGroupServerIds.toLongArray())
            .putStringArray(SERVER_HASH_KEY, serverHashes.toTypedArray())
            .build()
    }

    override fun getFactoryKey(): String = KEY

    class Factory : Job.Factory<BatchMessageReceiveJob> {
        override fun create(data: Data): BatchMessageReceiveJob {
            val numMessages = data.getInt(NUM_MESSAGES_KEY)
            val dataArrays = data.getByteArray(DATA_KEY)
            val contents =
                UtilProtos.ByteArrayList.parseFrom(dataArrays).contentList.map(ByteString::toByteArray)
            val serverHashes =
                if (data.hasStringArray(SERVER_HASH_KEY)) data.getStringArray(SERVER_HASH_KEY) else arrayOf()
            val openGroupMessageServerIDs = data.getLongArray(OPEN_GROUP_MESSAGE_SERVER_ID_KEY)
            val openGroupID = data.getStringOrDefault(OPEN_GROUP_ID_KEY, null)

            val parameters = (0 until numMessages).map { index ->
                val data = contents[index]
                val serverHash = serverHashes[index].let { if (it.isEmpty()) null else it }
                val serverId = openGroupMessageServerIDs[index].let { if (it == -1L) null else it }
                MessageReceiveParameters(data, serverHash, serverId)
            }

            return BatchMessageReceiveJob(parameters, openGroupID)
        }
    }

}