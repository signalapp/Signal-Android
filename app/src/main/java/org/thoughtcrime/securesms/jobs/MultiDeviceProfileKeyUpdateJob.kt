package org.thoughtcrime.securesms.jobs

import arrow.core.Either
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Optional
import kotlin.time.Duration.Companion.days

class MultiDeviceProfileKeyUpdateJob private constructor(parameters: Parameters) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceProfileKeyUpdateJob"

    private val TAG = Log.tag(MultiDeviceProfileKeyUpdateJob::class.java)

    private const val CONTENT_TYPE = "application/octet-stream"
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setQueue("MultiDeviceProfileKeyUpdateJob")
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered, aborting...")
      return Result.failure()
    }

    if (!SignalStore.account.isMultiDevice) {
      Log.i(TAG, "Not multi device...")
      return Result.success()
    }

    val baos = ByteArrayOutputStream()
    val out = DeviceContactsOutputStream(baos)

    out.write(
      DeviceContact(
        Optional.ofNullable(SignalStore.account.aci),
        Optional.ofNullable(SignalStore.account.e164),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
      )
    )

    out.close()

    val data = baos.toByteArray()

    val attachmentStream = SignalServiceAttachment.newStreamBuilder()
      .withStream(ByteArrayInputStream(data))
      .withContentType(CONTENT_TYPE)
      .withLength(data.size.toLong())
      .build()

    val blob = when (val upload = SignalNetwork.cdnService.uploadAttachment(attachmentStream).toEither(TAG, defaultBackoff())) {
      is Either.Right -> upload.value.toAttachmentPointer(CONTENT_TYPE)
      is Either.Left -> return upload.value
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncContacts(SyncMessage.Contacts(blob = blob, complete = false)),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "profile key sync", defaultBackoff()) }
    )
  }

  override fun onFailure() {
    Log.w(TAG, "Profile key sync failed!")
  }

  class Factory : Job.Factory<MultiDeviceProfileKeyUpdateJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceProfileKeyUpdateJob {
      return MultiDeviceProfileKeyUpdateJob(parameters)
    }
  }
}
