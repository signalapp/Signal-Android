package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Insert change number update items in all threads (1:1 and group) with [recipientId].
 */
class RecipientChangedNumberJob(parameters: Parameters, private val recipientId: RecipientId) : BaseJob(parameters) {

  constructor(recipientId: RecipientId) : this(
    Parameters.Builder().setQueue("RecipientChangedNumberJob_${recipientId.toQueueKey()}").build(),
    recipientId
  )

  override fun serialize(): Data {
    return Data.Builder()
      .putString(KEY_RECIPIENT_ID, recipientId.serialize())
      .build()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onRun() {
    val recipient: Recipient = Recipient.resolved(recipientId)

    if (!recipient.isBlocked && !recipient.isGroup && !recipient.isSelf) {
      Log.i(TAG, "Writing a number change event.")
      SignalDatabase.sms.insertNumberChangeMessages(recipient.id)
    } else {
      Log.i(TAG, "Number changed but not relevant. blocked: ${recipient.isBlocked} isGroup: ${recipient.isGroup} isSelf: ${recipient.isSelf}")
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  override fun onFailure() = Unit

  class Factory : Job.Factory<RecipientChangedNumberJob> {
    override fun create(parameters: Parameters, data: Data): RecipientChangedNumberJob {
      return RecipientChangedNumberJob(parameters, RecipientId.from(data.getString(KEY_RECIPIENT_ID)))
    }
  }

  companion object {
    const val KEY = "RecipientChangedNumberJob"

    private val TAG = Log.tag(RecipientChangedNumberJob::class.java)
    private const val KEY_RECIPIENT_ID = "recipient_id"
  }
}
