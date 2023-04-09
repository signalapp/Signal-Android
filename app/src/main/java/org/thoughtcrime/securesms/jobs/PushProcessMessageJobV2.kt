package org.thoughtcrime.securesms.jobs

import androidx.annotation.WorkerThread
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groups
import org.thoughtcrime.securesms.groups.GroupChangeBusyException
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.ChangeNumberConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.GroupUtil
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.crypto.protos.CompleteMessage
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.whispersystems.signalservice.api.crypto.protos.EnvelopeMetadata as EnvelopeMetadataProto

class PushProcessMessageJobV2 private constructor(
  parameters: Parameters,
  private val envelope: Envelope,
  private val content: Content,
  private val metadata: EnvelopeMetadata,
  private val serverDeliveredTimestamp: Long
) : BaseJob(parameters) {

  @WorkerThread
  constructor(
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long
  ) : this(createParameters(content, metadata), envelope.toBuilder().clearContent().build(), content, metadata, serverDeliveredTimestamp)

  override fun shouldTrace() = true

  override fun serialize(): ByteArray {
    return CompleteMessage(
      envelope = envelope.toByteArray().toByteString(),
      content = content.toByteArray().toByteString(),
      metadata = EnvelopeMetadataProto(
        sourceServiceId = ByteString.of(*metadata.sourceServiceId.toByteArray()),
        sourceE164 = metadata.sourceE164,
        sourceDeviceId = metadata.sourceDeviceId,
        sealedSender = metadata.sealedSender,
        groupId = if (metadata.groupId != null) metadata.groupId!!.toByteString() else null,
        destinationServiceId = ByteString.of(*metadata.destinationServiceId.toByteArray())
      ),
      serverDeliveredTimestamp = serverDeliveredTimestamp
    ).encode()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  public override fun onRun() {
    val processor = MessageContentProcessorV2.create(context)
    processor.process(envelope, content, metadata, serverDeliveredTimestamp)
  }

  public override fun onShouldRetry(e: Exception): Boolean {
    return e is PushNetworkException ||
      e is NoCredentialForRedemptionTimeException ||
      e is GroupChangeBusyException
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<PushProcessMessageJobV2?> {
    override fun create(parameters: Parameters, data: ByteArray?): PushProcessMessageJobV2 {
      return try {
        val completeMessage = CompleteMessage.ADAPTER.decode(data!!)
        PushProcessMessageJobV2(
          parameters = parameters,
          envelope = Envelope.parseFrom(completeMessage.envelope.toByteArray()),
          content = Content.parseFrom(completeMessage.content.toByteArray()),
          metadata = EnvelopeMetadata(
            sourceServiceId = ServiceId.parseOrThrow(completeMessage.metadata.sourceServiceId.toByteArray()),
            sourceE164 = completeMessage.metadata.sourceE164,
            sourceDeviceId = completeMessage.metadata.sourceDeviceId,
            sealedSender = completeMessage.metadata.sealedSender,
            groupId = completeMessage.metadata.groupId?.toByteArray(),
            destinationServiceId = ServiceId.parseOrThrow(completeMessage.metadata.destinationServiceId.toByteArray())
          ),
          serverDeliveredTimestamp = completeMessage.serverDeliveredTimestamp
        )
      } catch (e: IOException) {
        throw AssertionError(e)
      }
    }
  }

  companion object {
    const val KEY = "PushProcessMessageJobV2"
    const val QUEUE_PREFIX = "__PUSH_PROCESS_JOB__"

    private val TAG = Log.tag(PushProcessMessageJobV2::class.java)

    private fun getQueueName(recipientId: RecipientId): String {
      return QUEUE_PREFIX + recipientId.toQueueKey()
    }

    @WorkerThread
    private fun createParameters(content: Content, metadata: EnvelopeMetadata): Parameters {
      val queueName: String
      val builder = Parameters.Builder()
        .setMaxAttempts(Parameters.UNLIMITED)
        .addConstraint(ChangeNumberConstraint.KEY)

      val groupContext = GroupUtil.getGroupContextIfPresent(content)
      val groupId = groupContext?.groupId

      if (groupContext != null && groupId != null) {
        queueName = getQueueName(Recipient.externalPossiblyMigratedGroup(groupId).id)

        if (groupId.isV2) {
          val localRevision = groups.getGroupV2Revision(groupId.requireV2())

          if (groupContext.revision > localRevision || groups.getGroupV1ByExpectedV2(groupId.requireV2()).isPresent) {
            Log.i(TAG, "Adding network constraint to group-related job.")
            builder.addConstraint(NetworkConstraint.KEY).setLifespan(TimeUnit.DAYS.toMillis(30))
          }
        }
      } else if (content.hasSyncMessage() && content.syncMessage.hasSent() && content.syncMessage.sent.hasDestinationUuid()) {
        queueName = getQueueName(RecipientId.from(ServiceId.parseOrThrow(content.syncMessage.sent.destinationUuid)))
      } else {
        queueName = getQueueName(RecipientId.from(metadata.sourceServiceId))
      }

      builder.setQueue(queueName)

      return builder.build()
    }
  }
}
