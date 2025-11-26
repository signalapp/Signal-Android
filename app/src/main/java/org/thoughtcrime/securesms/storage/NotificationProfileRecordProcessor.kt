package org.thoughtcrime.securesms.storage

import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.NotificationProfileTables
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.storage.SignalNotificationProfileRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.OptionalUtil.asOptional
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.storage.protos.Recipient
import java.util.Optional
import java.util.UUID

/**
 * Record processor for [SignalNotificationProfileRecord].
 * Handles merging and updating our local store when processing remote notification profile storage records.
 */
class NotificationProfileRecordProcessor : DefaultStorageRecordProcessor<SignalNotificationProfileRecord>() {

  companion object {
    private val TAG = Log.tag(NotificationProfileRecordProcessor::class)
  }

  override fun compare(o1: SignalNotificationProfileRecord, o2: SignalNotificationProfileRecord): Int {
    return if (o1.proto.id == o2.proto.id) {
      0
    } else {
      1
    }
  }

  /**
   * Notification profiles must have a valid identifier
   * Notification profiles must have a name
   * All allowed members must have a valid serviceId
   */
  override fun isInvalid(remote: SignalNotificationProfileRecord): Boolean {
    return UuidUtil.parseOrNull(remote.proto.id) == null ||
      remote.proto.name.isEmpty() ||
      containsInvalidServiceId(remote.proto.allowedMembers)
  }

  override fun getMatching(remote: SignalNotificationProfileRecord, keyGenerator: StorageKeyGenerator): Optional<SignalNotificationProfileRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val uuid: UUID = UuidUtil.parseOrThrow(remote.proto.id)
    val query = SqlUtil.buildQuery("${NotificationProfileTables.NotificationProfileTable.NOTIFICATION_PROFILE_ID} = ?", NotificationProfileId(uuid))

    val notificationProfile = SignalDatabase.notificationProfiles.getProfile(query)

    return if (notificationProfile?.storageServiceId != null) {
      StorageSyncModels.localToRemoteNotificationProfile(notificationProfile, notificationProfile.storageServiceId.raw).asOptional()
    } else if (notificationProfile != null) {
      Log.d(TAG, "Notification profile was missing a storage service id, generating one")
      val storageId = StorageId.forNotificationProfile(keyGenerator.generate())
      SignalDatabase.notificationProfiles.applyStorageIdUpdate(notificationProfile.notificationProfileId, storageId)
      StorageSyncModels.localToRemoteNotificationProfile(notificationProfile, storageId.raw).asOptional()
    } else {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      Optional.empty<SignalNotificationProfileRecord>()
    }
  }

  /**
   * A deleted record takes precedence over a non-deleted record
   * while an earlier deletion takes precedence over a later deletion
   */
  override fun merge(remote: SignalNotificationProfileRecord, local: SignalNotificationProfileRecord, keyGenerator: StorageKeyGenerator): SignalNotificationProfileRecord {
    val isRemoteDeleted = remote.proto.deletedAtTimestampMs > 0
    val isLocalDeleted = local.proto.deletedAtTimestampMs > 0

    return when {
      isRemoteDeleted && isLocalDeleted -> if (remote.proto.deletedAtTimestampMs <= local.proto.deletedAtTimestampMs) remote else local
      isRemoteDeleted -> remote
      isLocalDeleted -> local
      else -> remote
    }
  }

  override fun insertLocal(record: SignalNotificationProfileRecord) {
    SignalDatabase.notificationProfiles.insertNotificationProfileFromStorageSync(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalNotificationProfileRecord>) {
    SignalDatabase.notificationProfiles.updateNotificationProfileFromStorageSync(update.new)
  }

  private fun containsInvalidServiceId(recipients: List<Recipient>): Boolean {
    return recipients.any { recipient ->
      recipient.contact != null && ServiceId.parseOrNull(recipient.contact!!.serviceId, recipient.contact!!.serviceIdBinary) == null
    }
  }
}
