package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingChangeNumberMetadata
import org.thoughtcrime.securesms.jobmanager.impl.ChangeNumberConstraintObserver
import org.thoughtcrime.securesms.keyvalue.protos.LeastActiveLinkedDevice

class MiscellaneousValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    private const val LAST_PREKEY_REFRESH_TIME = "last_prekey_refresh_time"
    private const val MESSAGE_REQUEST_ENABLE_TIME = "message_request_enable_time"
    private const val LAST_PROFILE_REFRESH_TIME = "misc.last_profile_refresh_time"
    private const val CLIENT_DEPRECATED = "misc.client_deprecated"
    private const val OLD_DEVICE_TRANSFER_LOCKED = "misc.old_device.transfer.locked"
    private const val HAS_EVER_HAD_AN_AVATAR = "misc.has.ever.had.an.avatar"
    private const val CHANGE_NUMBER_LOCK = "misc.change_number.lock"
    private const val PENDING_CHANGE_NUMBER_METADATA = "misc.pending_change_number.metadata"
    private const val CENSORSHIP_LAST_CHECK_TIME = "misc.censorship.last_check_time"
    private const val CENSORSHIP_SERVICE_REACHABLE = "misc.censorship.service_reachable"
    private const val LAST_GV2_PROFILE_CHECK_TIME = "misc.last_gv2_profile_check_time"
    private const val CDS_TOKEN = "misc.cds_token"
    private const val CDS_BLOCKED_UNTIL = "misc.cds_blocked_until"
    private const val LAST_FOREGROUND_TIME = "misc.last_foreground_time"
    private const val PNI_INITIALIZED_DEVICES = "misc.pni_initialized_devices"
    private const val LINKED_DEVICES_REMINDER = "misc.linked_devices_reminder"
    private const val HAS_LINKED_DEVICES = "misc.linked_devices_present"
    private const val USERNAME_QR_CODE_COLOR = "mis.username_qr_color_scheme"
    private const val KEYBOARD_LANDSCAPE_HEIGHT = "misc.keyboard.landscape_height"
    private const val KEYBOARD_PORTRAIT_HEIGHT = "misc.keyboard.protrait_height"
    private const val LAST_CONSISTENCY_CHECK_TIME = "misc.last_consistency_check_time"
    private const val SERVER_TIME_OFFSET = "misc.server_time_offset"
    private const val LAST_SERVER_TIME_OFFSET_UPDATE = "misc.last_server_time_offset_update"
    private const val NEEDS_USERNAME_RESTORE = "misc.needs_username_restore"
    private const val LAST_FORCED_PREKEY_REFRESH = "misc.last_forced_prekey_refresh"
    private const val LAST_CDS_FOREGROUND_SYNC = "misc.last_cds_foreground_sync"
    private const val LINKED_DEVICE_LAST_ACTIVE_CHECK_TIME = "misc.linked_device.last_active_check_time"
    private const val LEAST_ACTIVE_LINKED_DEVICE = "misc.linked_device.least_active"
    private const val NEXT_DATABASE_ANALYSIS_TIME = "misc.next_database_analysis_time"
    private const val LOCK_SCREEN_ATTEMPT_COUNT = "misc.lock_screen_attempt_count"
    private const val LAST_NETWORK_RESET_TIME = "misc.last_network_reset_time"
    private const val LAST_WEBSOCKET_CONNECT_TIME = "misc.last_websocket_connect_time"
    private const val LAST_CONNECTIVITY_WARNING_TIME = "misc.last_connectivity_warning_time"
  }

  public override fun onFirstEverAppLaunch() {
    putLong(MESSAGE_REQUEST_ENABLE_TIME, 0)
    putBoolean(NEEDS_USERNAME_RESTORE, true)
  }

  public override fun getKeysToIncludeInBackup(): List<String> {
    return emptyList()
  }

  /**
   * Represents the last time a _full_ prekey refreshed finished. That means signed+one-time prekeys for both ACI and PNI.
   */
  var lastFullPrekeyRefreshTime by longValue(LAST_PREKEY_REFRESH_TIME, 0)

  val messageRequestEnableTime by longValue(MESSAGE_REQUEST_ENABLE_TIME, 0)

  /**
   * Get the last time we successfully completed a forced prekey refresh.
   */
  var lastForcedPreKeyRefresh by longValue(LAST_FORCED_PREKEY_REFRESH, 0)

  /**
   * The last time we completed a routine profile refresh.
   */
  var lastProfileRefreshTime by longValue(LAST_PROFILE_REFRESH_TIME, 0)

  /**
   * Whether or not the client is currently in a 'deprecated' state, disallowing network access.
   */
  var isClientDeprecated: Boolean by booleanValue(CLIENT_DEPRECATED, false)

  /**
   * Whether or not we've locked the device after they've transferred to a new one.
   */
  var isOldDeviceTransferLocked by booleanValue(OLD_DEVICE_TRANSFER_LOCKED, false)

  /**
   * Whether or not the user has ever had an avatar.
   */
  var hasEverHadAnAvatar by booleanValue(HAS_EVER_HAD_AN_AVATAR, false)

  val isChangeNumberLocked: Boolean by booleanValue(CHANGE_NUMBER_LOCK, false)

  fun lockChangeNumber() {
    putBoolean(CHANGE_NUMBER_LOCK, true)
    ChangeNumberConstraintObserver.onChange()
  }

  fun unlockChangeNumber() {
    putBoolean(CHANGE_NUMBER_LOCK, false)
    ChangeNumberConstraintObserver.onChange()
  }

  val pendingChangeNumberMetadata: PendingChangeNumberMetadata?
    get() = getObject(PENDING_CHANGE_NUMBER_METADATA, null, PendingChangeNumberMetadataSerializer)

  /** Store pending new PNI data to be applied after successful change number  */
  fun setPendingChangeNumberMetadata(metadata: PendingChangeNumberMetadata) {
    putObject(PENDING_CHANGE_NUMBER_METADATA, metadata, PendingChangeNumberMetadataSerializer)
  }

  /** Clear pending new PNI data after confirmed successful or failed change number  */
  fun clearPendingChangeNumberMetadata() {
    remove(PENDING_CHANGE_NUMBER_METADATA)
  }

  /**
   * The last time we checked if the service was reachable without censorship circumvention.
   */
  var lastCensorshipServiceReachabilityCheckTime by longValue(CENSORSHIP_LAST_CHECK_TIME, 0)

  /**
   * Whether or not the service is reachable without censorship circumvention.
   */
  var isServiceReachableWithoutCircumvention by booleanValue(CENSORSHIP_SERVICE_REACHABLE, false)

  /**
   * The last time we did a routing check to see if our GV2 groups have the latest version of our profile key.
   */
  var lastGv2ProfileCheckTime by longValue(LAST_GV2_PROFILE_CHECK_TIME, 0)

  /**
   * The CDS token that is used for rate-limiting.
   */
  var cdsToken by nullableBlobValue(CDS_TOKEN, null)

  /**
   * Indicates that a CDS request will never succeed at the current contact count.
   */
  fun markCdsPermanentlyBlocked() {
    putLong(CDS_BLOCKED_UNTIL, Long.MAX_VALUE)
  }

  /**
   * Clears any rate limiting state related to CDS.
   */
  fun clearCdsBlocked() {
    cdsBlockedUtil = 0
  }

  /** Whether or not we expect the next CDS request to succeed.*/
  val isCdsBlocked: Boolean
    get() = cdsBlockedUtil > 0

  /**
   * This represents the next time we think we'll be able to make a successful CDS request. If it is before this time, we expect the request will fail
   * (assuming the user still has the same number of new E164s).
   */
  var cdsBlockedUtil by longValue(CDS_BLOCKED_UNTIL, 0)

  /**
   * The last time the user foregrounded the app.
   */
  var lastForegroundTime by longValue(LAST_FOREGROUND_TIME, 0)

  /**
   * Whether or not we've done the initial "PNP Hello World" dance.
   */
  var hasPniInitializedDevices by booleanValue(PNI_INITIALIZED_DEVICES, true)

  /**
   * Whether or not the user has linked devices.
   */
  var hasLinkedDevices by booleanValue(HAS_LINKED_DEVICES, false)

  /**
   * Whether or not we should show a reminder for the user to relink their devices after re-registering.
   */
  var shouldShowLinkedDevicesReminder by booleanValue(LINKED_DEVICES_REMINDER, false)

  /**
   * The color the user saved for rendering their shareable username QR code.
   */
  var usernameQrCodeColorScheme: UsernameQrCodeColorScheme
    get() {
      val serialized = getString(USERNAME_QR_CODE_COLOR, null)
      return UsernameQrCodeColorScheme.deserialize(serialized)
    }
    set(color) {
      putString(USERNAME_QR_CODE_COLOR, color.serialize())
    }

  /**
   * Cached landscape keyboard height.
   */
  var keyboardLandscapeHeight by integerValue(KEYBOARD_LANDSCAPE_HEIGHT, 0)

  /**
   * Cached portrait keyboard height.
   */
  var keyboardPortraitHeight by integerValue(KEYBOARD_PORTRAIT_HEIGHT, 0)

  /**
   * The last time we ran an account consistency check via [org.thoughtcrime.securesms.jobs.AccountConsistencyWorkerJob]
   */
  var lastConsistencyCheckTime by longValue(LAST_CONSISTENCY_CHECK_TIME, 0)

  /**
   * The last-known offset between our local clock and the server. To get an estimate of the server time, take your current time and subtract this offset. e.g.
   *
   * estimatedServerTime = System.currentTimeMillis() - SignalStore.misc.getLastKnownServerTimeOffset()
   */
  val lastKnownServerTimeOffset by longValue(SERVER_TIME_OFFSET, 0)

  /**
   * An estimate of the server time, based on the last-known server time offset.
   */
  val estimatedServerTime: Long
    get() = System.currentTimeMillis() - lastKnownServerTimeOffset

  /**
   * The last time (using our local clock) we updated the server time offset returned by [.getLastKnownServerTimeOffset]}.
   */
  val lastKnownServerTimeOffsetUpdateTime by longValue(LAST_SERVER_TIME_OFFSET_UPDATE, 0)

  /**
   * Sets the last-known server time.
   */
  fun setLastKnownServerTime(serverTime: Long, currentTime: Long) {
    store
      .beginWrite()
      .putLong(SERVER_TIME_OFFSET, currentTime - serverTime)
      .putLong(LAST_SERVER_TIME_OFFSET_UPDATE, System.currentTimeMillis())
      .apply()
  }

  /**
   * Whether or not we should attempt to restore the user's username and link.
   */
  var needsUsernameRestore by booleanValue(NEEDS_USERNAME_RESTORE, false)

  /**
   * How long it's been since the last foreground CDS sync, which we do in response to new threads being created.
   */
  var lastCdsForegroundSyncTime by longValue(LAST_CDS_FOREGROUND_SYNC, 0)

  /**
   * The last time we checked for linked device activity.
   */
  var linkedDeviceLastActiveCheckTime by longValue(LINKED_DEVICE_LAST_ACTIVE_CHECK_TIME, 0)

  /**
   * Details about the least-active linked device.
   */
  var leastActiveLinkedDevice: LeastActiveLinkedDevice? by protoValue(LEAST_ACTIVE_LINKED_DEVICE, LeastActiveLinkedDevice.ADAPTER)

  /**
   * When the next scheduled database analysis is.
   */
  var nextDatabaseAnalysisTime: Long by longValue(NEXT_DATABASE_ANALYSIS_TIME, 0)

  /**
   * How many times the lock screen has been seen and _not_ unlocked. Used to determine if the user is confused by how to bypass the lock screen.
   */
  var lockScreenAttemptCount: Int by integerValue(LOCK_SCREEN_ATTEMPT_COUNT, 0)

  fun incrementLockScreenAttemptCount() {
    lockScreenAttemptCount++
  }

  var lastNetworkResetDueToStreamResets: Long by longValue(LAST_NETWORK_RESET_TIME, 0L)

  /**
   * The last time you successfully connected to the websocket.
   */
  var lastWebSocketConnectTime: Long by longValue(LAST_WEBSOCKET_CONNECT_TIME, System.currentTimeMillis())

  /**
   * The last time we prompted the user regarding a [org.thoughtcrime.securesms.util.ConnectivityWarning].
   */
  var lastConnectivityWarningTime: Long by longValue(LAST_CONNECTIVITY_WARNING_TIME, 0)
}
