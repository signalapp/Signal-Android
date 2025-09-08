package org.thoughtcrime.securesms.keyvalue

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.flow.Flow
import okio.withLock
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.backups.BackupStateObserver
import org.thoughtcrime.securesms.jobmanager.impl.BackupMessagesConstraintObserver
import org.thoughtcrime.securesms.jobmanager.impl.DeletionNotAwaitingMediaDownloadConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NoRemoteArchiveGarbageCollectionPendingConstraint
import org.thoughtcrime.securesms.jobmanager.impl.RestoreAttachmentConstraintObserver
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.keyvalue.protos.BackupDownloadNotifierState
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class BackupValues(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    val TAG = Log.tag(BackupValues::class.java)
    private const val KEY_MESSAGE_CREDENTIALS = "backup.messageCredentials"
    private const val KEY_MEDIA_CREDENTIALS = "backup.mediaCredentials"
    private const val KEY_MESSAGE_CDN_READ_CREDENTIALS = "backup.messageCdnReadCredentials"
    private const val KEY_MESSAGE_CDN_READ_CREDENTIALS_TIMESTAMP = "backup.messageCdnReadCredentialsTimestamp"
    private const val KEY_MEDIA_CDN_READ_CREDENTIALS = "backup.mediaCdnReadCredentials"
    private const val KEY_MEDIA_CDN_READ_CREDENTIALS_TIMESTAMP = "backup.mediaCdnReadCredentialsTimestamp"
    private const val KEY_RESTORE_STATE = "backup.restoreState"
    private const val KEY_BACKUP_LAST_PROTO_SIZE = "backup.lastProtoSize"
    private const val KEY_BACKUP_TIER = "backup.backupTier"
    private const val KEY_BACKUP_TIER_INTERNAL_OVERRIDE = "backup.backupTier.internalOverride"
    private const val KEY_BACKUP_TIMESTAMP_RESTORED = "backup.backupTimeRestored"
    private const val KEY_LATEST_BACKUP_TIER = "backup.latestBackupTier"
    private const val KEY_LAST_CHECK_IN_MILLIS = "backup.lastCheckInMilliseconds"
    private const val KEY_LAST_CHECK_IN_SNOOZE_MILLIS = "backup.lastCheckInSnoozeMilliseconds"
    private const val KEY_FIRST_APP_VERSION = "backup.firstAppVersion"

    private const val KEY_NEXT_BACKUP_TIME = "backup.nextBackupTime"
    private const val KEY_LAST_BACKUP_TIME = "backup.lastBackupTime"
    private const val KEY_LAST_ATTACHMENT_RECONCILIATION_TIME = "backup.lastBackupMediaSyncTime"
    private const val KEY_TOTAL_RESTORABLE_ATTACHMENT_SIZE = "backup.totalRestorableAttachmentSize"
    private const val KEY_LAST_BACKUP_PROTO_VERSION = "backup.lastBackupProtoVersion"

    private const val KEY_CDN_MEDIA_PATH = "backup.cdn.mediaPath"

    private const val KEY_BACKUP_DOWNLOAD_NOTIFIER_STATE = "backup.downloadNotifierState"
    private const val KEY_BACKUP_OVER_CELLULAR = "backup.useCellular"
    private const val KEY_RESTORE_OVER_CELLULAR = "backup.restore.useCellular"
    private const val KEY_OPTIMIZE_STORAGE = "backup.optimizeStorage"
    private const val KEY_BACKUPS_INITIALIZED = "backup.initialized"

    const val KEY_ARCHIVE_UPLOAD_STATE = "backup.archiveUploadState"

    private const val KEY_BACKUP_UPLOADED = "backup.backupUploaded"
    private const val KEY_SUBSCRIPTION_STATE_MISMATCH = "backup.subscriptionStateMismatch"

    private const val KEY_BACKUP_FAIL = "backup.failed"
    private const val KEY_BACKUP_FAIL_ACKNOWLEDGED_SNOOZE_TIME = "backup.failed.acknowledged.snooze.time"
    private const val KEY_BACKUP_FAIL_ACKNOWLEDGED_SNOOZE_COUNT = "backup.failed.acknowledged.snooze.count"
    private const val KEY_BACKUP_FAIL_SHEET_SNOOZE_TIME = "backup.failed.sheet.snooze"
    private const val KEY_BACKUP_FAIL_SPACE_REMAINING = "backup.failed.space.remaining"
    private const val KEY_BACKUP_ALREADY_REDEEMED = "backup.already.redeemed"
    private const val KEY_INVALID_BACKUP_VERSION = "backup.invalid.version"
    private const val KEY_NOT_ENOUGH_REMOTE_STORAGE_SPACE = "backup.not.enough.remote.storage.space"
    private const val KEY_NOT_ENOUGH_REMOTE_STORAGE_SPACE_DISPLAY_SHEET = "backup.not.enough.remote.storage.space.display.sheet"
    private const val KEY_MANUAL_NO_BACKUP_NOTIFIED = "backup.manual.no.backup.notified"

    private const val KEY_USER_MANUALLY_SKIPPED_MEDIA_RESTORE = "backup.user.manually.skipped.media.restore"
    private const val KEY_BACKUP_EXPIRED_AND_DOWNGRADED = "backup.expired.and.downgraded"
    private const val KEY_BACKUP_DELETION_STATE = "backup.deletion.state"
    private const val KEY_REMOTE_STORAGE_GARBAGE_COLLECTION_PENDING = "backup.remoteStorageGarbageCollectionPending"

    private const val KEY_MEDIA_ROOT_BACKUP_KEY = "backup.mediaRootBackupKey"

    private const val KEY_LAST_VERIFY_KEY_TIME = "backup.last_verify_key_time"
    private const val KEY_HAS_SNOOZED_VERIFY = "backup.has_snoozed_verify"
    private const val KEY_HAS_VERIFIED_BEFORE = "backup.has_verified_before"

    private const val KEY_NEXT_BACKUP_SECRET_DATA = "backup.next_backup_secret_data"

    private val cachedCdnCredentialsExpiresIn: Duration = 12.hours

    private val lock = ReentrantLock()
  }

  public override fun onFirstEverAppLaunch() = Unit
  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  var cachedMediaCdnPath: String? by stringValue(KEY_CDN_MEDIA_PATH, null)

  var lastBackupProtoSize: Long by longValue(KEY_BACKUP_LAST_PROTO_SIZE, 0L)

  private val deletionStateValue = enumValue(KEY_BACKUP_DELETION_STATE, DeletionState.NONE, DeletionState.serializer)
  private var internalDeletionState by deletionStateValue

  var deletionState: DeletionState
    get() {
      return internalDeletionState
    }
    set(value) {
      internalDeletionState = value
      DeletionNotAwaitingMediaDownloadConstraint.Observer.notifyListeners()
    }

  val deletionStateFlow: Flow<DeletionState> = deletionStateValue.toFlow()

  var optimizeStorage: Boolean by booleanValue(KEY_OPTIMIZE_STORAGE, false).withPrecondition { RemoteConfig.internalUser || Environment.IS_STAGING || Environment.IS_INSTRUMENTATION }
  var backupWithCellular: Boolean
    get() = getBoolean(KEY_BACKUP_OVER_CELLULAR, false)
    set(value) {
      putBoolean(KEY_BACKUP_OVER_CELLULAR, value)
      BackupMessagesConstraintObserver.onChange()
    }

  var backupDownloadNotifierState: BackupDownloadNotifierState? by protoValue(KEY_BACKUP_DOWNLOAD_NOTIFIER_STATE, BackupDownloadNotifierState.ADAPTER)
    private set

  var restoreWithCellular: Boolean
    get() = getBoolean(KEY_RESTORE_OVER_CELLULAR, false)
    set(value) {
      putBoolean(KEY_RESTORE_OVER_CELLULAR, value)
      RestoreAttachmentConstraintObserver.onChange()
    }

  var nextBackupTime: Long by longValue(KEY_NEXT_BACKUP_TIME, -1)
  var lastBackupTime: Long
    get() = getLong(KEY_LAST_BACKUP_TIME, -1)
    set(value) {
      putLong(KEY_LAST_BACKUP_TIME, value)
      clearMessageBackupFailureSheetWatermark()
    }

  /** The version of the backup file we last successfully made. */
  var lastBackupProtoVersion: Long by longValue(KEY_LAST_BACKUP_PROTO_VERSION, -1)

  val daysSinceLastBackup: Int get() = (System.currentTimeMillis().milliseconds - lastBackupTime.milliseconds).inWholeDays.toInt()

  var lastAttachmentReconciliationTime: Long by longValue(KEY_LAST_ATTACHMENT_RECONCILIATION_TIME, -1)

  var userManuallySkippedMediaRestore: Boolean by booleanValue(KEY_USER_MANUALLY_SKIPPED_MEDIA_RESTORE, false)

  var backupExpiredAndDowngraded: Boolean by booleanValue(KEY_BACKUP_EXPIRED_AND_DOWNGRADED, false)

  /**
   * The last time the device notified the server that the archive is still in use.
   */
  var lastCheckInMillis: Long by longValue(KEY_LAST_CHECK_IN_MILLIS, 0L)

  /**
   * The time we last displayed the "Your media will be deleted today" sheet.
   *
   * Set when the user dismisses the "Your media will be deleted today" alert
   * Cleared when the system performs a check-in or the user subscribes to backups.
   */
  var lastCheckInSnoozeMillis: Long by longValue(KEY_LAST_CHECK_IN_SNOOZE_MILLIS, 0)

  /**
   * The first app version to make a backup. Persisted across backup/restores to help indicate backup age.
   */
  var firstAppVersion: String by stringValue(KEY_FIRST_APP_VERSION, "")

  /**
   * Key used to backup messages.
   */
  val messageBackupKey: MessageBackupKey
    get() = SignalStore.account.accountEntropyPool.deriveMessageBackupKey()

  /**
   * Key used to backup media. Purely random and separate from the message backup key.
   */
  var mediaRootBackupKey: MediaRootBackupKey
    get() {
      lock.withLock {
        val value: ByteArray? = getBlob(KEY_MEDIA_ROOT_BACKUP_KEY, null)
        if (value != null) {
          return MediaRootBackupKey(value)
        }

        Log.i(TAG, "Generating MediaRootBackupKey...", Throwable(), true)
        val newKey = MediaRootBackupKey.generate()
        store.beginWrite().putBlob(KEY_MEDIA_ROOT_BACKUP_KEY, newKey.value).commit()
        return newKey
      }
    }
    set(value) {
      lock.withLock {
        val currentValue: ByteArray? = getBlob(KEY_MEDIA_ROOT_BACKUP_KEY, null)
        if (currentValue != null) {
          val current = MediaRootBackupKey(currentValue)
          if (current == value) {
            Log.i(TAG, "MediaRootBackupKey the same, skipping.")
            return
          }
        }

        Log.i(TAG, "Setting MediaRootBackupKey...", Throwable(), true)
        store.beginWrite().putBlob(KEY_MEDIA_ROOT_BACKUP_KEY, value.value).commit()
        mediaCredentials.clearAll()
        cachedMediaCdnPath = null
      }
    }

  /**
   * This is the 'latest' backup tier. This isn't necessarily the user's current backup tier, so this should only ever
   * be used to display backup tier information to the user in the settings fragments, not to check whether the user
   * currently has backups enabled.
   */
  val latestBackupTier: MessageBackupTier?
    get() {
      backupTierInternalOverride?.let { return it }
      return MessageBackupTier.deserialize(getLong(KEY_LATEST_BACKUP_TIER, -1))
    }

  /**
   * When setting the backup tier, we also want to write to the latestBackupTier, as long as
   * the value is non-null. This gives us a 1-deep history of the selected backup tier for
   * use in the UI
   */
  var backupTier: MessageBackupTier?
    get() {
      backupTierInternalOverride?.let { return it }
      return MessageBackupTier.deserialize(getLong(KEY_BACKUP_TIER, -1))
    }
    set(value) {
      Log.i(TAG, "Setting backup tier to $value", Throwable(), true)
      val serializedValue = MessageBackupTier.serialize(value)
      val storedValue = MessageBackupTier.deserialize(getLong(KEY_BACKUP_TIER, -1))

      if (value != null) {
        store.beginWrite()
          .putLong(KEY_BACKUP_TIER, serializedValue)
          .putLong(KEY_LATEST_BACKUP_TIER, serializedValue)
          .putBoolean(KEY_BACKUP_TIMESTAMP_RESTORED, true)
          .apply()

        if (storedValue != value) {
          clearNotEnoughRemoteStorageSpace()
          clearMessageBackupFailure()
          clearMessageBackupFailureSheetWatermark()
        }

        deletionState = DeletionState.NONE
      } else {
        putLong(KEY_BACKUP_TIER, serializedValue)
      }

      BackupStateObserver.notifyBackupStateChanged()
    }

  /** An internal setting that can override the backup tier for a user. */
  var backupTierInternalOverride: MessageBackupTier? by enumValue(KEY_BACKUP_TIER_INTERNAL_OVERRIDE, null, MessageBackupTier.Serializer).withPrecondition { Environment.IS_STAGING }

  /**
   * Denotes if there was a mismatch detected between the user's Signal subscription, on-device Google Play subscription,
   * and what zk authorization we think we have.
   */
  val subscriptionStateMismatchDetectedValue = booleanValue(KEY_SUBSCRIPTION_STATE_MISMATCH, false).withPrecondition { backupTierInternalOverride == null }
  var subscriptionStateMismatchDetected: Boolean by subscriptionStateMismatchDetectedValue
  val subscriptionStateMismatchDetectedFlow: Flow<Boolean> by lazy { subscriptionStateMismatchDetectedValue.toFlow() }

  /** Set to true if we successfully restored a backup file timestamp or didn't find a file at all so a "no timestamp" value is restored. */
  var isBackupTimestampRestored: Boolean by booleanValue(KEY_BACKUP_TIMESTAMP_RESTORED, false)

  /**
   * When uploading a backup, we store the progress state here so that it can remain across app restarts.
   */
  var archiveUploadState: ArchiveUploadProgressState? by protoValue(KEY_ARCHIVE_UPLOAD_STATE, ArchiveUploadProgressState.ADAPTER)

  /** True if the user backs up media, otherwise false. */
  val backsUpMedia: Boolean
    @JvmName("backsUpMedia")
    get() = backupTier == MessageBackupTier.PAID

  /** True if the user has backups enabled, otherwise false. */
  val areBackupsEnabled: Boolean
    get() = backupTier != null

  /** True if we believe we have successfully uploaded a backup, otherwise false. */
  var hasBackupBeenUploaded: Boolean by booleanValue(KEY_BACKUP_UPLOADED, false)

  val hasBackupFailure: Boolean get() = getBoolean(KEY_BACKUP_FAIL, false)
  val nextBackupFailureSnoozeTime: Duration get() = getLong(KEY_BACKUP_FAIL_ACKNOWLEDGED_SNOOZE_TIME, 0L).milliseconds
  val nextBackupFailureSheetSnoozeTime: Duration get() = getLong(KEY_BACKUP_FAIL_SHEET_SNOOZE_TIME, getNextBackupFailureSheetSnoozeTime(lastBackupTime.milliseconds).inWholeMilliseconds).milliseconds

  var hasBackupAlreadyRedeemedError: Boolean by booleanValue(KEY_BACKUP_ALREADY_REDEEMED, false)
  var hasInvalidBackupVersion: Boolean by booleanValue(KEY_INVALID_BACKUP_VERSION, false)

  /**
   * Denotes how many bytes are still available on the disk for writing. Used to display
   * the disk full error and sheet. Set when we believe there might be an "out of space"
   * failure in BackupRestoreMediaJob and each time the application is brought into the
   * foreground. We never clear this value, so it can't be used as an indicator that
   * something bad happened, it can only be utilized as a reference for comparison.
   */
  var spaceAvailableOnDiskBytes: Long by longValue(KEY_BACKUP_FAIL_SPACE_REMAINING, -1L)

  /**
   * Sets the notifier to trigger half way between now and the entitlement expiration time.
   */
  fun setDownloadNotifierToTriggerAtHalfwayPoint(entitlementExpirationTime: Duration) {
    backupDownloadNotifierState = BackupDownloadNotifierUtil.setDownloadNotifierToTriggerAtHalfwayPoint(backupDownloadNotifierState, entitlementExpirationTime)
  }

  /**
   * Sets the notifier to trigger 24hrs before the end of the grace period.
   *
   */
  fun snoozeDownloadNotifier() {
    backupDownloadNotifierState = BackupDownloadNotifierUtil.snoozeDownloadNotifier(backupDownloadNotifierState)
  }

  /**
   * Clears the notifier state, done when the user subscribes to the paid tier.
   */
  fun clearDownloadNotifierState() {
    backupDownloadNotifierState = null
  }

  fun internalSetBackupFailedErrorState() {
    markMessageBackupFailure()
    putLong(KEY_BACKUP_FAIL_SHEET_SNOOZE_TIME, 0)
  }

  /**
   * Call when the user disables backups. Clears/resets all relevant fields.
   */
  fun disableBackups() {
    store
      .beginWrite()
      .putLong(KEY_NEXT_BACKUP_TIME, -1)
      .putLong(KEY_LAST_BACKUP_TIME, -1)
      .putBoolean(KEY_BACKUPS_INITIALIZED, false)
      .putBoolean(KEY_BACKUP_UPLOADED, false)
      .putLong(KEY_LAST_VERIFY_KEY_TIME, -1)
      .putBoolean(KEY_HAS_VERIFIED_BEFORE, false)
      .putBoolean(KEY_HAS_SNOOZED_VERIFY, false)
      .apply()
    backupTier = null
    backupTierInternalOverride = null
  }

  var backupsInitialized: Boolean by booleanValue(KEY_BACKUPS_INITIALIZED, false)

  var restoreState: RestoreState by enumValue(KEY_RESTORE_STATE, RestoreState.NONE, RestoreState.serializer)
  var totalRestorableAttachmentSize: Long by longValue(KEY_TOTAL_RESTORABLE_ATTACHMENT_SIZE, 0)

  /** Store that lets you interact with message ZK credentials. */
  val messageCredentials = CredentialStore(KEY_MESSAGE_CREDENTIALS, KEY_MESSAGE_CDN_READ_CREDENTIALS, KEY_MESSAGE_CDN_READ_CREDENTIALS_TIMESTAMP)

  /** Store that lets you interact with media ZK credentials. */
  val mediaCredentials = CredentialStore(KEY_MEDIA_CREDENTIALS, KEY_MEDIA_CDN_READ_CREDENTIALS, KEY_MEDIA_CDN_READ_CREDENTIALS_TIMESTAMP)

  val isNotEnoughRemoteStorageSpace by booleanValue(KEY_NOT_ENOUGH_REMOTE_STORAGE_SPACE, false)
  val shouldDisplayNotEnoughRemoteStorageSpaceSheet by booleanValue(KEY_NOT_ENOUGH_REMOTE_STORAGE_SPACE_DISPLAY_SHEET, false)

  var isNoBackupForManualUploadNotified by booleanValue(KEY_MANUAL_NO_BACKUP_NOTIFIED, false)

  /** Last time they successfully entered their backup key, including when they first initialized backups **/
  var lastVerifyKeyTime by longValue(KEY_LAST_VERIFY_KEY_TIME, -1)

  /** Checks if they have previously snoozed the megaphone to verify their backup key **/
  var hasSnoozedVerified by booleanValue(KEY_HAS_SNOOZED_VERIFY, false)

  /** Checks if they have ever verified their backup key before **/
  var hasVerifiedBefore by booleanValue(KEY_HAS_VERIFIED_BEFORE, false)

  /** The value from the last successful SVRB operation that must be passed to the next SVRB operation. */
  var nextBackupSecretData by nullableBlobValue(KEY_NEXT_BACKUP_SECRET_DATA, null)

  /**
   * If true, it means we have been told that remote storage is full, but we have not yet run any of our "garbage collection" tasks, like committing deletes
   * or pruning orphaned media.
   */
  var remoteStorageGarbageCollectionPending
    get() = store.getBoolean(KEY_REMOTE_STORAGE_GARBAGE_COLLECTION_PENDING, false)
    set(value) {
      store.beginWrite().putBoolean(KEY_REMOTE_STORAGE_GARBAGE_COLLECTION_PENDING, value)
      NoRemoteArchiveGarbageCollectionPendingConstraint.Observer.notifyListeners()
    }

  /**
   * When we are told by the server that we are out of storage space, we should show
   * UX treatment to make the user aware of this.
   */
  fun markNotEnoughRemoteStorageSpace() {
    store.beginWrite()
      .putBoolean(KEY_NOT_ENOUGH_REMOTE_STORAGE_SPACE, true)
      .putBoolean(KEY_NOT_ENOUGH_REMOTE_STORAGE_SPACE_DISPLAY_SHEET, false)
      .apply()
  }

  /**
   * When we've regained enough space, we can remove the error.
   */
  fun clearNotEnoughRemoteStorageSpace() {
    store.beginWrite()
      .putBoolean(KEY_NOT_ENOUGH_REMOTE_STORAGE_SPACE, false)
      .putBoolean(KEY_NOT_ENOUGH_REMOTE_STORAGE_SPACE_DISPLAY_SHEET, false)
      .apply()
  }

  /**
   * Dismisses the sheet so as not to irritate the user.
   */
  fun dismissNotEnoughRemoteStorageSpaceSheet() {
    store.beginWrite()
      .putBoolean(KEY_NOT_ENOUGH_REMOTE_STORAGE_SPACE_DISPLAY_SHEET, false)
      .apply()
  }

  fun markMessageBackupFailure() {
    store.beginWrite()
      .putBoolean(KEY_BACKUP_FAIL, true)
      .putLong(KEY_BACKUP_FAIL_ACKNOWLEDGED_SNOOZE_TIME, System.currentTimeMillis())
      .putLong(KEY_BACKUP_FAIL_ACKNOWLEDGED_SNOOZE_COUNT, 0)
      .putLong(KEY_BACKUP_FAIL_SHEET_SNOOZE_TIME, System.currentTimeMillis())
      .apply()
  }

  fun updateMessageBackupFailureWatermark() {
    if (!hasBackupFailure) {
      return
    }

    val snoozeCount = getLong(KEY_BACKUP_FAIL_ACKNOWLEDGED_SNOOZE_COUNT, 0) + 1
    val nextSnooze = when (snoozeCount) {
      1L -> 48.hours
      2L -> 72.hours
      else -> Long.MAX_VALUE.hours
    }

    store.beginWrite()
      .putLong(KEY_BACKUP_FAIL_ACKNOWLEDGED_SNOOZE_TIME, (System.currentTimeMillis().milliseconds + nextSnooze).inWholeMilliseconds)
      .putLong(KEY_BACKUP_FAIL_ACKNOWLEDGED_SNOOZE_COUNT, snoozeCount)
      .apply()
  }

  fun clearMessageBackupFailure() {
    putBoolean(KEY_BACKUP_FAIL, false)
  }

  fun updateMessageBackupFailureSheetWatermark() {
    val nextSnoozeTime = getNextBackupFailureSheetSnoozeTime(System.currentTimeMillis().milliseconds)

    putLong(KEY_BACKUP_FAIL_SHEET_SNOOZE_TIME, nextSnoozeTime.inWholeMilliseconds)
  }

  private fun clearMessageBackupFailureSheetWatermark() {
    remove(KEY_BACKUP_FAIL_SHEET_SNOOZE_TIME)
  }

  private fun getNextBackupFailureSheetSnoozeTime(previous: Duration): Duration {
    return previous + 7.days
  }

  class SerializedCredentials(
    @JsonProperty
    val credentialsByDay: Map<Long, ArchiveServiceCredential>
  )

  /**
   * A [Map] wrapper that makes it easier to get the credential for the current time.
   */
  class ArchiveServiceCredentials(map: Map<Long, ArchiveServiceCredential>) : Map<Long, ArchiveServiceCredential> by map {
    constructor() : this(mapOf())

    /**
     * Retrieves a credential that is valid for the current time, otherwise null.
     */
    fun getForCurrentTime(currentTime: Duration): ArchiveServiceCredential? {
      val startOfDayInSeconds: Long = currentTime.inWholeDays.days.inWholeSeconds
      return this[startOfDayInSeconds]
    }
  }

  inner class CredentialStore(private val authKey: String, private val cdnKey: String, private val cdnTimestampKey: String) {
    /**
     * Retrieves the stored media credentials, mapped by the day they're valid. The day is represented as
     * the unix time (in seconds) of the start of the day. Wrapped in a [ArchiveServiceCredentials]
     * type to make it easier to use. See [ArchiveServiceCredentials.getForCurrentTime].
     */
    val byDay: ArchiveServiceCredentials
      get() {
        val serialized = store.getString(authKey, null) ?: return ArchiveServiceCredentials()

        return try {
          val map = JsonUtil.fromJson(serialized, SerializedCredentials::class.java).credentialsByDay
          ArchiveServiceCredentials(map)
        } catch (e: IOException) {
          Log.w(TAG, "Invalid JSON! Clearing.", e)
          putString(authKey, null)
          ArchiveServiceCredentials()
        }
      }

    /** Adds the given credentials to the existing list of stored credentials. */
    fun add(credentials: List<ArchiveServiceCredential>) {
      val current: MutableMap<Long, ArchiveServiceCredential> = byDay.toMutableMap()
      current.putAll(credentials.associateBy { it.redemptionTime })
      putString(authKey, JsonUtil.toJson(SerializedCredentials(current)))
    }

    /** Trims out any credentials that are for days older than the given timestamp. */
    fun clearOlderThan(startOfDayInSeconds: Long) {
      val current: MutableMap<Long, ArchiveServiceCredential> = byDay.toMutableMap()
      val updated = current.filterKeys { it < startOfDayInSeconds }
      putString(authKey, JsonUtil.toJson(SerializedCredentials(updated)))
    }

    /** Clears all credentials. */
    fun clearAll() {
      putString(authKey, null)
      putString(cdnKey, null)
      putLong(cdnTimestampKey, 0)
    }

    /** Credentials to read from the CDN. */
    var cdnReadCredentials: GetArchiveCdnCredentialsResponse?
      get() {
        val cacheAge = System.currentTimeMillis() - getLong(cdnTimestampKey, 0)
        val cached = getString(cdnKey, null)

        return if (cached != null && (cacheAge > 0 && cacheAge < cachedCdnCredentialsExpiresIn.inWholeMilliseconds)) {
          try {
            JsonUtil.fromJson(cached, GetArchiveCdnCredentialsResponse::class.java)
          } catch (e: IOException) {
            Log.w(TAG, "Invalid JSON! Clearing.", e)
            putString(cdnKey, null)
            null
          }
        } else {
          null
        }
      }
      set(value) {
        putString(cdnKey, value?.let { JsonUtil.toJson(it) })
        putLong(cdnTimestampKey, System.currentTimeMillis())
      }
  }
}
