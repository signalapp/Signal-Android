package org.thoughtcrime.securesms.jobs

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groups
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupChangeBusyException
import org.thoughtcrime.securesms.groups.GroupsV1MigratedCache
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.ChangeNumberConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2
import org.thoughtcrime.securesms.messages.MessageDecryptor
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.SignalLocalMetrics
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

    /**
     * Cache to keep track of empty 1:1 processing queues. Once a 1:1 queue is empty
     * we no longer enqueue jobs on it and instead process inline. This is not
     * true for groups, as with groups we may have to do network fetches
     * to get group state up to date.
     */
    private val empty1to1QueueCache = HashSet<String>()

    private fun getQueueName(recipientId: RecipientId): String {
      return QUEUE_PREFIX + recipientId.toQueueKey()
    }

    fun processOrDefer(messageProcessor: MessageContentProcessorV2, result: MessageDecryptor.Result.Success, localReceiveMetric: SignalLocalMetrics.MessageReceive): PushProcessMessageJobV2? {
      val queueName: String

      val groupContext = GroupUtil.getGroupContextIfPresent(result.content)
      val groupId = groupContext?.groupId
      var requireNetwork = false

      if (groupId != null) {
        queueName = getQueueName(RecipientId.from(groupId))

        if (groupId.isV2) {
          val localRevision = groups.getGroupV2Revision(groupId.requireV2())

          if (groupContext.revision > localRevision || GroupsV1MigratedCache.hasV1Group(groupId)) {
            Log.i(TAG, "Adding network constraint to group-related job.")
            requireNetwork = true
          }
        }
      } else if (result.content.hasSyncMessage() && result.content.syncMessage.hasSent() && result.content.syncMessage.sent.hasDestinationServiceId()) {
        queueName = getQueueName(RecipientId.from(ServiceId.parseOrThrow(result.content.syncMessage.sent.destinationServiceId)))
      } else {
        queueName = getQueueName(RecipientId.from(result.metadata.sourceServiceId))
      }

      return if (requireNetwork || !isQueueEmpty(queueName = queueName, isGroup = groupId != null)) {
        val builder = Parameters.Builder()
          .setMaxAttempts(Parameters.UNLIMITED)
          .addConstraint(ChangeNumberConstraint.KEY)
          .setQueue(queueName)
        if (requireNetwork) {
          builder.addConstraint(NetworkConstraint.KEY).setLifespan(TimeUnit.DAYS.toMillis(30))
        }
        PushProcessMessageJobV2(builder.build(), result.envelope.toBuilder().clearContent().build(), result.content, result.metadata, result.serverDeliveredTimestamp)
      } else {
        try {
          messageProcessor.process(result.envelope, result.content, result.metadata, result.serverDeliveredTimestamp, localMetric = localReceiveMetric)
        } catch (e: Exception) {
          Log.e(TAG, "Failed to process message with timestamp ${result.envelope.timestamp}. Dropping.")
        }
        null
      }
    }

    private fun isQueueEmpty(queueName: String, isGroup: Boolean): Boolean {
      if (!isGroup && empty1to1QueueCache.contains(queueName)) {
        return true
      }
      val queueEmpty = ApplicationDependencies.getJobManager().isQueueEmpty(queueName)
      if (!isGroup && queueEmpty) {
        empty1to1QueueCache.add(queueName)
      }
      return queueEmpty
    }
  }
}
