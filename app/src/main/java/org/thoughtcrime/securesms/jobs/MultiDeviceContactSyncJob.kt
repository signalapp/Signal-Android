package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidMessageException
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.NotPushRegisteredException
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream.IntegrityCheck
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsReader
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException
import org.whispersystems.signalservice.api.util.AttachmentPointerUtil
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Sync contact data from primary device.
 */
class MultiDeviceContactSyncJob(parameters: Parameters, private val attachmentPointer: ByteArray) : BaseJob(parameters) {

  constructor(contactsAttachment: SignalServiceAttachmentPointer) : this(
    Parameters.Builder()
      .setQueue("MultiDeviceContactSyncJob")
      .build(),
    AttachmentPointerUtil.createAttachmentPointer(contactsAttachment).encode()
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putBlobAsString(KEY_ATTACHMENT_POINTER, attachmentPointer)
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onRun() {
    if (!Recipient.self().isRegistered) {
      throw NotPushRegisteredException()
    }

    if (SignalStore.account.isPrimaryDevice) {
      Log.i(TAG, "Not linked device, aborting...")
      return
    }

    val contactAttachment: SignalServiceAttachmentPointer = AttachmentPointerUtil.createSignalAttachmentPointer(attachmentPointer)

    try {
      val contactsFile: File = AppDependencies.blobs.forNonAutoEncryptingSingleSessionOnDisk(context)
      AppDependencies.signalServiceMessageReceiver
        .retrieveAttachment(contactAttachment, contactsFile, MAX_ATTACHMENT_SIZE, IntegrityCheck.forEncryptedDigest(contactAttachment.digest.get()))
        .use(this::processContactFile)
    } catch (e: MissingConfigurationException) {
      throw IOException(e)
    } catch (e: InvalidMessageException) {
      throw IOException(e)
    }
  }

  private fun processContactFile(inputStream: InputStream) {
    val reader = DeviceContactsReader(inputStream)

    for (contact in reader.contacts()) {
      applyContact(contact)
    }

    Log.i(TAG, "[ContactSync] Sync complete. Skipped ${reader.skippedRecords} records with no address.")
  }

  private fun applyContact(contact: DeviceContact) {
    val aci = contact.aci
    val e164 = contact.e164
    val recipient: Recipient? = when {
      aci != null -> Recipient.externalPush(SignalServiceAddress(aci, e164))
      e164 != null -> Recipient.external(e164)
      else -> null
    }

    if (recipient == null) {
      Log.w(TAG, "[ContactSync] Could not resolve a recipient for a synced contact. Skipping.")
      return
    }

    if (recipient.isSelf) {
      return
    }

    val recipients = SignalDatabase.recipients

    contact.name?.let { recipients.setSystemContactName(recipient.id, it) }

    val expireTimer = contact.expireTimer
    if (expireTimer != null) {
      val syncedVersion = contact.expireTimerVersion
      if (syncedVersion == null) {
        // TODO [expireVersion] After unsupported builds expire, we can remove this branch
        recipients.setExpireMessagesWithoutIncrementingVersion(recipient.id, expireTimer)
      } else if (syncedVersion >= recipient.expireTimerVersion) {
        recipients.setExpireMessages(recipient.id, expireTimer, syncedVersion)
      } else {
        Log.w(TAG, "[ContactSync] ${recipient.id} was synced with an old expiration timer. Ignoring. Received: $syncedVersion Current: ${recipient.expireTimerVersion}")
      }
    }

    contact.avatar?.let {
      try {
        AvatarHelper.setSyncAvatar(context, recipient.id, it.inputStream)
      } catch (e: IOException) {
        Log.w(TAG, "[ContactSync] Unable to set sync avatar for ${recipient.id}")
      }
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceContactSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceContactSyncJob {
      val data = JsonJobData.deserialize(serializedData)
      return MultiDeviceContactSyncJob(parameters, data.getStringAsBlob(KEY_ATTACHMENT_POINTER))
    }
  }

  companion object {
    const val KEY = "MultiDeviceContactSyncJob"
    const val KEY_ATTACHMENT_POINTER = "attachment_pointer"
    private const val MAX_ATTACHMENT_SIZE: Long = 100 * 1024 * 1024
    private val TAG = Log.tag(MultiDeviceContactSyncJob::class.java)
  }
}
