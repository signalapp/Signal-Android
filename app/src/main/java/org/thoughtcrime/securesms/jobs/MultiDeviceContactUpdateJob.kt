package org.thoughtcrime.securesms.jobs

import android.Manifest
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import arrow.core.Either
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.AppForegroundObserver
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactAvatar
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Optional
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class MultiDeviceContactUpdateJob private constructor(
  parameters: Parameters,
  private val recipientId: RecipientId?,
  private val forceSync: Boolean
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceContactUpdateJob"

    private val TAG = Log.tag(MultiDeviceContactUpdateJob::class.java)

    private val FULL_SYNC_TIME = 6.hours.inWholeMilliseconds

    private const val KEY_RECIPIENT = "recipient"
    private const val KEY_FORCE_SYNC = "force_sync"

    private const val CONTENT_TYPE = "application/octet-stream"
  }

  @JvmOverloads
  constructor(forceSync: Boolean = false) : this(null, forceSync)

  @JvmOverloads
  constructor(recipientId: RecipientId?, forceSync: Boolean = true) : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setQueue("MultiDeviceContactUpdateJob")
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    recipientId,
    forceSync
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_RECIPIENT, recipientId?.serialize())
      .putBoolean(KEY_FORCE_SYNC, forceSync)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered, aborting...")
      return Result.failure()
    }

    if (!SignalStore.account.isMultiDevice) {
      Log.i(TAG, "Not multi device, aborting...")
      return Result.success()
    }

    if (SignalStore.account.isLinkedDevice) {
      Log.i(TAG, "Not primary device, aborting...")
      return Result.success()
    }

    return if (recipientId == null) {
      generateFullContactUpdate()
    } else {
      generateSingleContactUpdate(recipientId)
    }
  }

  private suspend fun generateSingleContactUpdate(recipientId: RecipientId): Result {
    val writeDetails = createTempFile()

    var updateUri: Uri? = null
    try {
      val out = DeviceContactsOutputStream(writeDetails.outputStream)
      val recipient = Recipient.resolved(recipientId)

      if (recipient.registered == RegisteredState.NOT_REGISTERED) {
        Log.w(TAG, "$recipientId not registered!")
        return Result.failure()
      }

      if (!recipient.hasE164 && !recipient.hasAci) {
        Log.w(TAG, "$recipientId has no valid identifier!")
        return Result.success()
      }

      val inboxPositions = SignalDatabase.threads.getInboxPositions()

      out.write(
        DeviceContact(
          recipient.aci,
          recipient.e164,
          Optional.ofNullable(if (recipient.isGroup || recipient.isSystemContact) recipient.getDisplayName(context) else null),
          Optional.ofNullable(getSystemAvatar(recipient.contactUri)),
          Optional.ofNullable(recipient.expiresInSeconds.takeIf { it > 0 }),
          Optional.of(recipient.expireTimerVersion),
          Optional.ofNullable(inboxPositions[recipientId])
        )
      )

      out.close()
      updateUri = writeDetails.getUri()

      val length = AppDependencies.blobs.calculateFileSize(context, updateUri)

      return AppDependencies.blobs.getStream(context, updateUri).use { stream -> sendUpdate(stream, length, complete = false) }
    } catch (e: InterruptedException) {
      Log.w(TAG, e)
      return Result.success()
    } finally {
      if (updateUri != null) {
        AppDependencies.blobs.delete(context, updateUri)
      }
    }
  }

  private suspend fun generateFullContactUpdate(): Result {
    val isAppVisible = AppForegroundObserver.isForegrounded()
    val timeSinceLastSync = System.currentTimeMillis() - SignalStore.misc.lastFullContactSyncTime

    Log.d(TAG, "Requesting a full contact sync. forced = $forceSync, appVisible = $isAppVisible, timeSinceLastSync = $timeSinceLastSync ms")

    if (!forceSync && !isAppVisible && timeSinceLastSync < FULL_SYNC_TIME) {
      Log.i(TAG, "App is backgrounded and the last contact sync was too soon ($timeSinceLastSync ms ago). Marking that we need a sync. Skipping multi-device contact update...")
      SignalStore.misc.needsFullContactSync = true
      return Result.success()
    }

    SignalStore.misc.lastFullContactSyncTime = System.currentTimeMillis()
    SignalStore.misc.needsFullContactSync = false

    val writeDetails = createTempFile()

    var updateUri: Uri? = null
    try {
      val out = DeviceContactsOutputStream(writeDetails.outputStream)
      val recipients = SignalDatabase.recipients.getRecipientsForMultiDeviceSync()
      val inboxPositions = SignalDatabase.threads.getInboxPositions()

      for (recipient in recipients) {
        if (!recipient.hasE164 && !recipient.hasAci) {
          Log.w(TAG, "${recipient.id} has no valid identifier! Skipping.")
          continue
        }

        out.write(
          DeviceContact(
            recipient.aci,
            recipient.e164,
            Optional.ofNullable(if (recipient.isSystemContact) recipient.getDisplayName(context) else recipient.getGroupName(context)),
            Optional.ofNullable(getSystemAvatar(recipient.contactUri)),
            Optional.ofNullable(recipient.expiresInSeconds.takeIf { it > 0 }),
            Optional.of(recipient.expireTimerVersion),
            Optional.ofNullable(inboxPositions[recipient.id])
          )
        )
      }

      val self = Recipient.self()

      if (self.profileKey != null && (self.hasAci || self.hasE164)) {
        out.write(
          DeviceContact(
            self.aci,
            self.e164,
            Optional.empty(),
            Optional.empty(),
            Optional.ofNullable(self.expiresInSeconds.takeIf { it > 0 }),
            Optional.of(self.expireTimerVersion),
            Optional.ofNullable(inboxPositions[self.id])
          )
        )
      }

      out.close()

      updateUri = writeDetails.getUri()

      val length = AppDependencies.blobs.calculateFileSize(context, updateUri)

      return AppDependencies.blobs.getStream(context, updateUri).use { stream -> sendUpdate(stream, length, complete = true) }
    } catch (e: InterruptedException) {
      Log.w(TAG, e)
      return Result.success()
    } finally {
      if (updateUri != null) {
        AppDependencies.blobs.delete(context, updateUri)
      }
    }
  }

  override fun onFailure() = Unit

  private suspend fun sendUpdate(stream: InputStream, length: Long, complete: Boolean): Result {
    if (length <= 0) {
      Log.w(TAG, "Nothing to write!")
      return Result.success()
    }

    val attachmentStream = SignalServiceAttachment.newStreamBuilder()
      .withStream(stream)
      .withContentType(CONTENT_TYPE)
      .withLength(length)
      .build()

    val blob = when (val upload = SignalNetwork.cdnService.uploadAttachment(attachmentStream).toEither(TAG, defaultBackoff())) {
      is Either.Right -> upload.value.toAttachmentPointer(CONTENT_TYPE)
      is Either.Left -> return upload.value
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncContacts(SyncMessage.Contacts(blob = blob, complete = complete)),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "contacts sync", defaultBackoff()) }
    )
  }

  private fun getSystemAvatar(uri: Uri?): DeviceContactAvatar? {
    if (uri == null) {
      return null
    }

    if (!Permissions.hasAny(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      return null
    }

    val displayPhotoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO)

    try {
      val fd = context.contentResolver.openAssetFileDescriptor(displayPhotoUri, "r") ?: return null
      return DeviceContactAvatar(fd.createInputStream(), fd.length, "image/*")
    } catch (e: IOException) {
      // Ignored
    }

    val photoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
    val projection = arrayOf(ContactsContract.CommonDataKinds.Photo.PHOTO, ContactsContract.CommonDataKinds.Phone.MIMETYPE)

    context.contentResolver.query(photoUri, projection, null, null, null)?.use { cursor ->
      if (cursor.moveToNext()) {
        val data = cursor.getBlob(0)
        if (data != null) {
          return DeviceContactAvatar(ByteArrayInputStream(data), data.size.toLong(), "image/*")
        }
      }
    }

    return null
  }

  private fun createTempFile(): WriteDetails {
    val pipe = ParcelFileDescriptor.createPipe()
    val inputStream = ParcelFileDescriptor.AutoCloseInputStream(pipe[0])
    val futureUri = AppDependencies.blobs
      .forData(inputStream, 0)
      .withFileName("multidevice-contact-update")
      .createForSingleSessionOnDiskAsync(context)

    return WriteDetails(futureUri, ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]))
  }

  private class WriteDetails(private val futureUri: Future<Uri>, val outputStream: OutputStream) {
    fun getUri(): Uri {
      try {
        return futureUri.get()
      } catch (e: ExecutionException) {
        throw e.cause as? IOException ?: RuntimeException(e.cause)
      }
    }
  }

  class Factory : Job.Factory<MultiDeviceContactUpdateJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceContactUpdateJob {
      val data = JsonJobData.deserialize(serializedData)
      val recipientId = data.getString(KEY_RECIPIENT)?.let { RecipientId.from(it) }

      return MultiDeviceContactUpdateJob(parameters, recipientId, data.getBoolean(KEY_FORCE_SYNC))
    }
  }
}
