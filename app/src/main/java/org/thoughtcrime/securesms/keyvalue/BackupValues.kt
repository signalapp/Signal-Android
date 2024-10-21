package org.thoughtcrime.securesms.keyvalue

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.flow.Flow
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class BackupValues(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    val TAG = Log.tag(BackupValues::class.java)
    private const val KEY_CREDENTIALS = "backup.credentials"
    private const val KEY_CDN_READ_CREDENTIALS = "backup.cdn.readCredentials"
    private const val KEY_CDN_READ_CREDENTIALS_TIMESTAMP = "backup.cdn.readCredentials.timestamp"
    private const val KEY_RESTORE_STATE = "backup.restoreState"
    private const val KEY_BACKUP_USED_MEDIA_SPACE = "backup.usedMediaSpace"
    private const val KEY_BACKUP_LAST_PROTO_SIZE = "backup.lastProtoSize"
    private const val KEY_BACKUP_TIER = "backup.backupTier"
    private const val KEY_LATEST_BACKUP_TIER = "backup.latestBackupTier"

    private const val KEY_NEXT_BACKUP_TIME = "backup.nextBackupTime"
    private const val KEY_LAST_BACKUP_TIME = "backup.lastBackupTime"
    private const val KEY_LAST_BACKUP_MEDIA_SYNC_TIME = "backup.lastBackupMediaSyncTime"
    private const val KEY_TOTAL_RESTORABLE_ATTACHMENT_SIZE = "backup.totalRestorableAttachmentSize"
    private const val KEY_BACKUP_FREQUENCY = "backup.backupFrequency"

    private const val KEY_CDN_BACKUP_DIRECTORY = "backup.cdn.directory"
    private const val KEY_CDN_BACKUP_MEDIA_DIRECTORY = "backup.cdn.mediaDirectory"

    private const val KEY_BACKUP_OVER_CELLULAR = "backup.useCellular"
    private const val KEY_OPTIMIZE_STORAGE = "backup.optimizeStorage"
    private const val KEY_BACKUPS_INITIALIZED = "backup.initialized"

    private const val KEY_ARCHIVE_UPLOAD_STATE = "backup.archiveUploadState"

    private const val KEY_BACKUP_UPLOADED = "backup.backupUploaded"

    private val cachedCdnCredentialsExpiresIn: Duration = 12.hours
  }

  override fun onFirstEverAppLaunch() = Unit
  override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  private var cachedCdnCredentialsTimestamp: Long by longValue(KEY_CDN_READ_CREDENTIALS_TIMESTAMP, 0L)
  private var cachedCdnCredentials: String? by stringValue(KEY_CDN_READ_CREDENTIALS, null)
  var cachedBackupDirectory: String? by stringValue(KEY_CDN_BACKUP_DIRECTORY, null)
  var cachedBackupMediaDirectory: String? by stringValue(KEY_CDN_BACKUP_MEDIA_DIRECTORY, null)
  var usedBackupMediaSpace: Long by longValue(KEY_BACKUP_USED_MEDIA_SPACE, 0L)
  var lastBackupProtoSize: Long by longValue(KEY_BACKUP_LAST_PROTO_SIZE, 0L)

  var restoreState: RestoreState by enumValue(KEY_RESTORE_STATE, RestoreState.NONE, RestoreState.serializer)
  var optimizeStorage: Boolean by booleanValue(KEY_OPTIMIZE_STORAGE, false)
  var backupWithCellular: Boolean by booleanValue(KEY_BACKUP_OVER_CELLULAR, false)

  var nextBackupTime: Long by longValue(KEY_NEXT_BACKUP_TIME, -1)
  var lastBackupTime: Long by longValue(KEY_LAST_BACKUP_TIME, -1)
  var lastMediaSyncTime: Long by longValue(KEY_LAST_BACKUP_MEDIA_SYNC_TIME, -1)
  var backupFrequency: BackupFrequency by enumValue(KEY_BACKUP_FREQUENCY, BackupFrequency.MANUAL, BackupFrequency.Serializer)

  /**
   * This is the 'latest' backup tier. This isn't necessarily the user's current backup tier, so this should only ever
   * be used to display backup tier information to the user in the settings fragments, not to check whether the user
   * currently has backups enabled.
   */
  val latestBackupTier: MessageBackupTier? by enumValue(KEY_LATEST_BACKUP_TIER, null, MessageBackupTier.Serializer)

  /**
   * When seting the backup tier, we also want to write to the latestBackupTier, as long as
   * the value is non-null. This gives us a 1-deep history of the selected backup tier for
   * use in the UI
   */
  var backupTier: MessageBackupTier?
    get() = MessageBackupTier.deserialize(getLong(KEY_BACKUP_TIER, -1))
    set(value) {
      val serializedValue = MessageBackupTier.serialize(value)
      if (value != null) {
        store.beginWrite()
          .putLong(KEY_BACKUP_TIER, serializedValue)
          .putLong(KEY_LATEST_BACKUP_TIER, serializedValue)
          .apply()
      } else {
        putLong(KEY_BACKUP_TIER, serializedValue)
      }
    }

  /**
   * When uploading a backup, we store the progress state here so that I can remain across app restarts.
   */
  var archiveUploadState: ArchiveUploadProgressState? by protoValue(KEY_ARCHIVE_UPLOAD_STATE, ArchiveUploadProgressState.ADAPTER)

  val totalBackupSize: Long get() = lastBackupProtoSize + usedBackupMediaSpace

  /** True if the user backs up media, otherwise false. */
  val backsUpMedia: Boolean
    @JvmName("backsUpMedia")
    get() = backupTier == MessageBackupTier.PAID

  /** True if the user has backups enabled, otherwise false. */
  val areBackupsEnabled: Boolean
    get() = backupTier != null

  /** True if we believe we have successfully uploaded a backup, otherwise false. */
  var hasBackupBeenUploaded: Boolean by booleanValue(KEY_BACKUP_UPLOADED, false)

  /**
   * Call when the user disables backups. Clears/resets all relevant fields.
   */
  fun disableBackups() {
    store
      .beginWrite()
      .putLong(KEY_NEXT_BACKUP_TIME, -1)
      .putBoolean(KEY_BACKUPS_INITIALIZED, false)
      .putBoolean(KEY_BACKUP_UPLOADED, false)
      .apply()
    backupTier = null
  }

  var backupsInitialized: Boolean by booleanValue(KEY_BACKUPS_INITIALIZED, false)

  private val totalRestorableAttachmentSizeValue = longValue(KEY_TOTAL_RESTORABLE_ATTACHMENT_SIZE, 0)
  var totalRestorableAttachmentSize: Long by totalRestorableAttachmentSizeValue
  val totalRestorableAttachmentSizeFlow: Flow<Long>
    get() = totalRestorableAttachmentSizeValue.toFlow()

  val isRestoreInProgress: Boolean
    get() = totalRestorableAttachmentSize > 0

  /**
   * Retrieves the stored credentials, mapped by the day they're valid. The day is represented as
   * the unix time (in seconds) of the start of the day. Wrapped in a [ArchiveServiceCredentials]
   * type to make it easier to use. See [ArchiveServiceCredentials.getForCurrentTime].
   */
  val credentialsByDay: ArchiveServiceCredentials
    get() {
      val serialized = store.getString(KEY_CREDENTIALS, null) ?: return ArchiveServiceCredentials()

      return try {
        val map = JsonUtil.fromJson(serialized, SerializedCredentials::class.java).credentialsByDay
        ArchiveServiceCredentials(map)
      } catch (e: IOException) {
        Log.w(TAG, "Invalid JSON! Clearing.", e)
        putString(KEY_CREDENTIALS, null)
        ArchiveServiceCredentials()
      }
    }

  var cdnReadCredentials: GetArchiveCdnCredentialsResponse?
    get() {
      val cacheAge = System.currentTimeMillis() - cachedCdnCredentialsTimestamp
      val cached = cachedCdnCredentials

      return if (cached != null && (cacheAge > 0 && cacheAge < cachedCdnCredentialsExpiresIn.inWholeMilliseconds)) {
        try {
          JsonUtil.fromJson(cached, GetArchiveCdnCredentialsResponse::class.java)
        } catch (e: IOException) {
          Log.w(TAG, "Invalid JSON! Clearing.", e)
          cachedCdnCredentials = null
          null
        }
      } else {
        null
      }
    }
    set(value) {
      cachedCdnCredentials = value?.let { JsonUtil.toJson(it) }
      cachedCdnCredentialsTimestamp = System.currentTimeMillis()
    }

  /**
   * Adds the given credentials to the existing list of stored credentials.
   */
  fun addCredentials(credentials: List<ArchiveServiceCredential>) {
    val current: MutableMap<Long, ArchiveServiceCredential> = credentialsByDay.toMutableMap()
    current.putAll(credentials.associateBy { it.redemptionTime })
    putString(KEY_CREDENTIALS, JsonUtil.toJson(SerializedCredentials(current)))
  }

  /**
   * Trims out any credentials that are for days older than the given timestamp.
   */
  fun clearCredentialsOlderThan(startOfDayInSeconds: Long) {
    val current: MutableMap<Long, ArchiveServiceCredential> = credentialsByDay.toMutableMap()
    val updated = current.filterKeys { it < startOfDayInSeconds }
    putString(KEY_CREDENTIALS, JsonUtil.toJson(SerializedCredentials(updated)))
  }

  fun clearAllCredentials() {
    putString(KEY_CREDENTIALS, null)
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
}
