package org.thoughtcrime.securesms.loki

import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.dependencies.InjectableType
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PushMessageSyncSendJob private constructor(
    parameters: Parameters,
    private val messageID: Long,
    private val recipient: Address,
    private val timestamp: Long,
    private val message: ByteArray,
    private val ttl: Int
) : BaseJob(parameters), InjectableType {

  companion object {
    const val KEY = "PushMessageSyncSendJob"

    private val TAG = PushMessageSyncSendJob::class.java.simpleName

    private val KEY_MESSAGE_ID = "message_id"
    private val KEY_RECIPIENT = "recipient"
    private val KEY_TIMESTAMP = "timestamp"
    private val KEY_MESSAGE = "message"
    private val KEY_TTL = "ttl"
  }

  @Inject
  lateinit var messageSender: SignalServiceMessageSender

  constructor(messageID: Long, recipient: Address, timestamp: Long, message: ByteArray, ttl: Int) : this(Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue(KEY)
          .setLifespan(TimeUnit.DAYS.toMillis(1))
          .setMaxAttempts(1)
          .build(),
          messageID, recipient, timestamp, message, ttl)

  override fun serialize(): Data {
    return Data.Builder()
            .putLong(KEY_MESSAGE_ID, messageID)
            .putString(KEY_RECIPIENT, recipient.serialize())
            .putLong(KEY_TIMESTAMP, timestamp)
            .putByteArray(KEY_MESSAGE, message)
            .putInt(KEY_TTL, ttl)
            .build()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  @Throws(IOException::class, UntrustedIdentityException::class)
  public override fun onRun() {
    // Don't send sync messages to a group
    if (recipient.isGroup || recipient.isEmail) { return }
    messageSender.lokiSendSyncMessage(messageID, SignalServiceAddress(recipient.toPhoneString()), timestamp, message, ttl)
  }

  public override fun onShouldRetry(e: Exception): Boolean {
    // Loki - Disable since we have our own retrying when sending messages
    return false
  }

  override fun onCanceled() {}

  class Factory : Job.Factory<PushMessageSyncSendJob> {
    override fun create(parameters: Parameters, data: Data): PushMessageSyncSendJob {
      try {
        return PushMessageSyncSendJob(parameters,
                data.getLong(KEY_MESSAGE_ID),
                Address.fromSerialized(data.getString(KEY_RECIPIENT)),
                data.getLong(KEY_TIMESTAMP),
                data.getByteArray(KEY_MESSAGE),
                data.getInt(KEY_TTL))
      } catch (e: IOException) {
        throw AssertionError(e)
      }
    }
  }
}
