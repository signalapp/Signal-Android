package org.thoughtcrime.securesms.util

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.json.JSONException
import org.json.JSONObject
import org.signal.core.util.SetUtil
import org.signal.core.util.gibiBytes
import org.signal.core.util.logging.Log
import org.signal.core.util.mebiBytes
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies.application
import org.thoughtcrime.securesms.dependencies.AppDependencies.jobManager
import org.thoughtcrime.securesms.dependencies.AppDependencies.signalServiceAccountManager
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.jobs.RemoteConfigRefreshJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messageprocessingalarm.RoutineMessageFetchReceiver
import java.io.IOException
import java.util.Locale
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A location for flags that can be set locally and remotely. These flags can guard features that
 * are not yet ready to be activated.
 *
 * When creating a new flag:
 * - Create a new string constant. This should almost certainly be prefixed with "android."
 * - Add a method to retrieve the value using [.getBoolean]. You can also add
 * other checks here, like requiring other flags.
 * - If you want to be able to change a flag remotely, place it in [.REMOTE_CAPABLE].
 * - If you would like to force a value for testing, place an entry in [.FORCED_VALUES].
 * Do not commit changes to this map!
 *
 * Other interesting things you can do:
 * - Make a flag [.HOT_SWAPPABLE]
 * - Make a flag [.STICKY] -- booleans only!
 * - Register a listener for flag changes in [.FLAG_CHANGE_LISTENERS]
 */
object FeatureFlags {
  private val TAG = Log.tag(FeatureFlags::class.java)

  private val FETCH_INTERVAL = TimeUnit.HOURS.toMillis(2)

  private const val PAYMENTS_KILL_SWITCH = "android.payments.kill"
  private const val GROUPS_V2_RECOMMENDED_LIMIT = "global.groupsv2.maxGroupSize"
  private const val GROUPS_V2_HARD_LIMIT = "global.groupsv2.groupSizeHardLimit"
  private const val GROUP_NAME_MAX_LENGTH = "global.groupsv2.maxNameLength"
  private const val INTERNAL_USER = "android.internalUser"
  private const val VERIFY_V2 = "android.verifyV2"
  private const val CLIENT_EXPIRATION = "android.clientExpiration"
  private const val CUSTOM_VIDEO_MUXER = "android.customVideoMuxer.1"
  private const val CDS_REFRESH_INTERVAL = "cds.syncInterval.seconds"
  private const val CDS_FOREGROUND_SYNC_INTERVAL = "cds.foregroundSyncInterval.seconds"
  private const val AUTOMATIC_SESSION_RESET = "android.automaticSessionReset.2"
  private const val AUTOMATIC_SESSION_INTERVAL = "android.automaticSessionResetInterval"
  private const val DEFAULT_MAX_BACKOFF = "android.defaultMaxBackoff"
  private const val SERVER_ERROR_MAX_BACKOFF = "android.serverErrorMaxBackoff"
  private const val OKHTTP_AUTOMATIC_RETRY = "android.okhttpAutomaticRetry"
  private const val SHARE_SELECTION_LIMIT = "android.share.limit"
  private const val ANIMATED_STICKER_MIN_MEMORY = "android.animatedStickerMinMemory"
  private const val ANIMATED_STICKER_MIN_TOTAL_MEMORY = "android.animatedStickerMinTotalMemory"
  private const val MESSAGE_PROCESSOR_ALARM_INTERVAL = "android.messageProcessor.alarmIntervalMins"
  private const val MESSAGE_PROCESSOR_DELAY = "android.messageProcessor.foregroundDelayMs"
  private const val MEDIA_QUALITY_LEVELS = "android.mediaQuality.levels"
  private const val RETRY_RECEIPT_LIFESPAN = "android.retryReceiptLifespan"
  private const val RETRY_RESPOND_MAX_AGE = "android.retryRespondMaxAge"
  private const val SENDER_KEY_MAX_AGE = "android.senderKeyMaxAge"
  private const val RETRY_RECEIPTS = "android.retryReceipts"
  private const val MAX_GROUP_CALL_RING_SIZE = "global.calling.maxGroupCallRingSize"
  private const val STORIES_TEXT_FUNCTIONS = "android.stories.text.functions"
  private const val HARDWARE_AEC_BLOCKLIST_MODELS = "android.calling.hardwareAecBlockList"
  private const val SOFTWARE_AEC_BLOCKLIST_MODELS = "android.calling.softwareAecBlockList"
  private const val USE_HARDWARE_AEC_IF_OLD = "android.calling.useHardwareAecIfOlderThanApi29"
  private const val PAYMENTS_COUNTRY_BLOCKLIST = "global.payments.disabledRegions"
  private const val STORIES_AUTO_DOWNLOAD_MAXIMUM = "android.stories.autoDownloadMaximum"
  private const val TELECOM_MANUFACTURER_ALLOWLIST = "android.calling.telecomAllowList"
  private const val TELECOM_MODEL_BLOCKLIST = "android.calling.telecomModelBlockList"
  private const val CAMERAX_MODEL_BLOCKLIST = "android.cameraXModelBlockList"
  private const val CAMERAX_MIXED_MODEL_BLOCKLIST = "android.cameraXMixedModelBlockList"
  private const val PAYMENTS_REQUEST_ACTIVATE_FLOW = "android.payments.requestActivateFlow"
  const val GOOGLE_PAY_DISABLED_REGIONS: String = "global.donations.gpayDisabledRegions"
  const val CREDIT_CARD_DISABLED_REGIONS: String = "global.donations.ccDisabledRegions"
  const val PAYPAL_DISABLED_REGIONS: String = "global.donations.paypalDisabledRegions"
  private const val CDS_HARD_LIMIT = "android.cds.hardLimit"
  private const val PAYPAL_ONE_TIME_DONATIONS = "android.oneTimePayPalDonations.2"
  private const val PAYPAL_RECURRING_DONATIONS = "android.recurringPayPalDonations.3"
  private const val ANY_ADDRESS_PORTS_KILL_SWITCH = "android.calling.fieldTrial.anyAddressPortsKillSwitch"
  private const val AD_HOC_CALLING = "android.calling.ad.hoc.3"
  private const val MAX_ATTACHMENT_COUNT = "android.attachments.maxCount"
  private const val MAX_ATTACHMENT_RECEIVE_SIZE_BYTES = "global.attachments.maxReceiveBytes"
  private const val MAX_ATTACHMENT_SIZE_BYTES = "global.attachments.maxBytes"
  private const val CDS_DISABLE_COMPAT_MODE = "cds.disableCompatibilityMode"
  private const val FCM_MAY_HAVE_MESSAGES_KILL_SWITCH = "android.fcmNotificationFallbackKillSwitch"
  const val PROMPT_FOR_NOTIFICATION_LOGS: String = "android.logs.promptNotifications"
  private const val PROMPT_FOR_NOTIFICATION_CONFIG = "android.logs.promptNotificationsConfig"
  const val PROMPT_BATTERY_SAVER: String = "android.promptBatterySaver"
  const val CRASH_PROMPT_CONFIG: String = "android.crashPromptConfig"
  private const val SEPA_DEBIT_DONATIONS = "android.sepa.debit.donations.5"
  private const val IDEAL_DONATIONS = "android.ideal.donations.5"
  const val IDEAL_ENABLED_REGIONS: String = "global.donations.idealEnabledRegions"
  const val SEPA_ENABLED_REGIONS: String = "global.donations.sepaEnabledRegions"
  private const val NOTIFICATION_THUMBNAIL_BLOCKLIST = "android.notificationThumbnailProductBlocklist"
  private const val USE_ACTIVE_CALL_MANAGER = "android.calling.useActiveCallManager.5"
  private const val GIF_SEARCH = "global.gifSearch"
  private const val AUDIO_REMUXING = "android.media.audioRemux.1"
  private const val VIDEO_RECORD_1X_ZOOM = "android.media.videoCaptureDefaultZoom"
  private const val RETRY_RECEIPT_MAX_COUNT = "android.retryReceipt.maxCount"
  private const val RETRY_RECEIPT_MAX_COUNT_RESET_AGE = "android.retryReceipt.maxCountResetAge"
  private const val PREKEY_FORCE_REFRESH_INTERVAL = "android.prekeyForceRefreshInterval"
  private const val CDSI_LIBSIGNAL_NET = "android.cds.libsignal.4"
  private const val RX_MESSAGE_SEND = "android.rxMessageSend.2"
  private const val LINKED_DEVICE_LIFESPAN_SECONDS = "android.linkedDeviceLifespanSeconds"
  private const val MESSAGE_BACKUPS = "android.messageBackups"
  private const val CAMERAX_CUSTOM_CONTROLLER = "android.cameraXCustomController"
  private const val REGISTRATION_V2 = "android.registration.v2"
  private const val LIBSIGNAL_WEB_SOCKET_ENABLED = "android.libsignalWebSocketEnabled"
  private const val RESTORE_POST_REGISTRATION = "android.registration.restorePostRegistration"
  private const val LIBSIGNAL_WEB_SOCKET_SHADOW_PCT = "android.libsignalWebSocketShadowingPercentage"
  private const val DELETE_SYNC_SEND_RECEIVE = "android.deleteSyncSendReceive"
  private const val LINKED_DEVICES_V2 = "android.linkedDevices.v2"
  private const val SVR3_MIGRATION_PHASE = "global.svr3.phase"

  /**
   * We will only store remote values for flags in this set. If you want a flag to be controllable
   * remotely, place it in here.
   */
  @JvmField
  @VisibleForTesting
  val REMOTE_CAPABLE: Set<String> = SetUtil.newHashSet(
    PAYMENTS_KILL_SWITCH,
    GROUPS_V2_RECOMMENDED_LIMIT, GROUPS_V2_HARD_LIMIT,
    INTERNAL_USER,
    VERIFY_V2,
    CLIENT_EXPIRATION,
    CUSTOM_VIDEO_MUXER,
    CDS_REFRESH_INTERVAL,
    CDS_FOREGROUND_SYNC_INTERVAL,
    GROUP_NAME_MAX_LENGTH,
    AUTOMATIC_SESSION_RESET,
    AUTOMATIC_SESSION_INTERVAL,
    DEFAULT_MAX_BACKOFF,
    SERVER_ERROR_MAX_BACKOFF,
    OKHTTP_AUTOMATIC_RETRY,
    SHARE_SELECTION_LIMIT,
    ANIMATED_STICKER_MIN_MEMORY,
    ANIMATED_STICKER_MIN_TOTAL_MEMORY,
    MESSAGE_PROCESSOR_ALARM_INTERVAL,
    MESSAGE_PROCESSOR_DELAY,
    MEDIA_QUALITY_LEVELS,
    RETRY_RECEIPT_LIFESPAN,
    RETRY_RESPOND_MAX_AGE,
    RETRY_RECEIPTS,
    MAX_GROUP_CALL_RING_SIZE,
    SENDER_KEY_MAX_AGE,
    STORIES_TEXT_FUNCTIONS,
    HARDWARE_AEC_BLOCKLIST_MODELS,
    SOFTWARE_AEC_BLOCKLIST_MODELS,
    USE_HARDWARE_AEC_IF_OLD,
    PAYMENTS_COUNTRY_BLOCKLIST,
    STORIES_AUTO_DOWNLOAD_MAXIMUM,
    TELECOM_MANUFACTURER_ALLOWLIST,
    TELECOM_MODEL_BLOCKLIST,
    CAMERAX_MODEL_BLOCKLIST,
    CAMERAX_MIXED_MODEL_BLOCKLIST,
    PAYMENTS_REQUEST_ACTIVATE_FLOW,
    GOOGLE_PAY_DISABLED_REGIONS,
    CREDIT_CARD_DISABLED_REGIONS,
    PAYPAL_DISABLED_REGIONS,
    CDS_HARD_LIMIT,
    PAYPAL_ONE_TIME_DONATIONS,
    PAYPAL_RECURRING_DONATIONS,
    ANY_ADDRESS_PORTS_KILL_SWITCH,
    MAX_ATTACHMENT_COUNT,
    MAX_ATTACHMENT_RECEIVE_SIZE_BYTES,
    MAX_ATTACHMENT_SIZE_BYTES,
    AD_HOC_CALLING,
    CDS_DISABLE_COMPAT_MODE,
    FCM_MAY_HAVE_MESSAGES_KILL_SWITCH,
    PROMPT_FOR_NOTIFICATION_LOGS,
    PROMPT_FOR_NOTIFICATION_CONFIG,
    PROMPT_BATTERY_SAVER,
    CRASH_PROMPT_CONFIG,
    SEPA_DEBIT_DONATIONS,
    IDEAL_DONATIONS,
    IDEAL_ENABLED_REGIONS,
    SEPA_ENABLED_REGIONS,
    NOTIFICATION_THUMBNAIL_BLOCKLIST,
    USE_ACTIVE_CALL_MANAGER,
    GIF_SEARCH,
    AUDIO_REMUXING,
    VIDEO_RECORD_1X_ZOOM,
    RETRY_RECEIPT_MAX_COUNT,
    RETRY_RECEIPT_MAX_COUNT_RESET_AGE,
    PREKEY_FORCE_REFRESH_INTERVAL,
    CDSI_LIBSIGNAL_NET,
    RX_MESSAGE_SEND,
    LINKED_DEVICE_LIFESPAN_SECONDS,
    CAMERAX_CUSTOM_CONTROLLER,
    LIBSIGNAL_WEB_SOCKET_ENABLED,
    LIBSIGNAL_WEB_SOCKET_SHADOW_PCT,
    DELETE_SYNC_SEND_RECEIVE,
    SVR3_MIGRATION_PHASE
  )

  @JvmField
  @VisibleForTesting
  val NOT_REMOTE_CAPABLE: Set<String> = SetUtil.newHashSet(MESSAGE_BACKUPS, REGISTRATION_V2, RESTORE_POST_REGISTRATION, LINKED_DEVICES_V2)

  /**
   * Values in this map will take precedence over any value. This should only be used for local
   * development. Given that you specify a default when retrieving a value, and that we only store
   * remote values for things in [.REMOTE_CAPABLE], there should be no need to ever *commit*
   * an addition to this map.
   */
  @JvmField
  @VisibleForTesting
  val FORCED_VALUES: Map<String, Any> = mapOf()

  /**
   * By default, flags are only updated once at app start. This is to ensure that values don't
   * change within an app session, simplifying logic. However, given that this can delay how often
   * a flag is updated, you can put a flag in here to mark it as 'hot swappable'. Flags in this set
   * will be updated arbitrarily at runtime. This will make values more responsive, but also places
   * more burden on the reader to ensure that the app experience remains consistent.
   */
  @JvmField
  @VisibleForTesting
  val HOT_SWAPPABLE: Set<String> = setOf(
    VERIFY_V2,
    CLIENT_EXPIRATION,
    CUSTOM_VIDEO_MUXER,
    CDS_REFRESH_INTERVAL,
    CDS_FOREGROUND_SYNC_INTERVAL,
    GROUP_NAME_MAX_LENGTH,
    AUTOMATIC_SESSION_RESET,
    AUTOMATIC_SESSION_INTERVAL,
    DEFAULT_MAX_BACKOFF,
    SERVER_ERROR_MAX_BACKOFF,
    OKHTTP_AUTOMATIC_RETRY,
    SHARE_SELECTION_LIMIT,
    ANIMATED_STICKER_MIN_MEMORY,
    ANIMATED_STICKER_MIN_TOTAL_MEMORY,
    MESSAGE_PROCESSOR_ALARM_INTERVAL,
    MESSAGE_PROCESSOR_DELAY,
    MEDIA_QUALITY_LEVELS,
    RETRY_RECEIPT_LIFESPAN,
    RETRY_RESPOND_MAX_AGE,
    RETRY_RECEIPTS,
    MAX_GROUP_CALL_RING_SIZE,
    SENDER_KEY_MAX_AGE,
    HARDWARE_AEC_BLOCKLIST_MODELS,
    SOFTWARE_AEC_BLOCKLIST_MODELS,
    USE_HARDWARE_AEC_IF_OLD,
    PAYMENTS_COUNTRY_BLOCKLIST,
    TELECOM_MANUFACTURER_ALLOWLIST,
    TELECOM_MODEL_BLOCKLIST,
    CAMERAX_MODEL_BLOCKLIST,
    PAYMENTS_REQUEST_ACTIVATE_FLOW,
    CDS_HARD_LIMIT,
    MAX_ATTACHMENT_COUNT,
    MAX_ATTACHMENT_RECEIVE_SIZE_BYTES,
    MAX_ATTACHMENT_SIZE_BYTES,
    CDS_DISABLE_COMPAT_MODE,
    FCM_MAY_HAVE_MESSAGES_KILL_SWITCH,
    PROMPT_FOR_NOTIFICATION_LOGS,
    PROMPT_FOR_NOTIFICATION_CONFIG,
    PROMPT_BATTERY_SAVER,
    CRASH_PROMPT_CONFIG,
    NOTIFICATION_THUMBNAIL_BLOCKLIST,
    VIDEO_RECORD_1X_ZOOM,
    RETRY_RECEIPT_MAX_COUNT,
    RETRY_RECEIPT_MAX_COUNT_RESET_AGE,
    PREKEY_FORCE_REFRESH_INTERVAL,
    CDSI_LIBSIGNAL_NET,
    RX_MESSAGE_SEND,
    LINKED_DEVICE_LIFESPAN_SECONDS,
    CAMERAX_CUSTOM_CONTROLLER,
    DELETE_SYNC_SEND_RECEIVE,
    SVR3_MIGRATION_PHASE
  )

  /**
   * Flags in this set will stay true forever once they receive a true value from a remote config.
   */
  @JvmField
  @VisibleForTesting
  val STICKY: Set<String> = setOf(
    VERIFY_V2,
    FCM_MAY_HAVE_MESSAGES_KILL_SWITCH
  )

  /**
   * Listeners that are called when the value in [.REMOTE_VALUES] changes. That means that
   * hot-swappable flags will have this invoked as soon as we know about that change, but otherwise
   * these will only run during initialization.
   *
   * These can be called on any thread, including the main thread, so be careful!
   *
   * Also note that this doesn't play well with [.FORCED_VALUES] -- changes there will not
   * trigger changes in this map, so you'll have to do some manual hacking to get yourself in the
   * desired test state.
   */
  private val FLAG_CHANGE_LISTENERS: Map<String, OnFlagChange> = mapOf(
    MESSAGE_PROCESSOR_ALARM_INTERVAL to OnFlagChange { RoutineMessageFetchReceiver.startOrUpdateAlarm(application) }
    // TODO [svr3] we need to know what it changed from and to so we can enqueue for 0 -> 1
//    put(SVR3_MIGRATION_PHASE, change -> if (change));
  )

  private val REMOTE_VALUES: MutableMap<String, Any> = TreeMap()

  @JvmStatic
  @Synchronized
  fun init() {
    val current = parseStoredConfig(SignalStore.remoteConfigValues().currentConfig)
    val pending = parseStoredConfig(SignalStore.remoteConfigValues().pendingConfig)
    val changes = computeChanges(current, pending)

    SignalStore.remoteConfigValues().currentConfig = mapToJson(pending)
    REMOTE_VALUES.putAll(pending)
    triggerFlagChangeListeners(changes)

    Log.i(TAG, "init() $REMOTE_VALUES")
  }

  @JvmStatic
  fun refreshIfNecessary() {
    val timeSinceLastFetch = System.currentTimeMillis() - SignalStore.remoteConfigValues().lastFetchTime

    if (timeSinceLastFetch < 0 || timeSinceLastFetch > FETCH_INTERVAL) {
      Log.i(TAG, "Scheduling remote config refresh.")
      jobManager.add(RemoteConfigRefreshJob())
    } else {
      Log.i(TAG, "Skipping remote config refresh. Refreshed $timeSinceLastFetch ms ago.")
    }
  }

  @JvmStatic
  @WorkerThread
  @Throws(IOException::class)
  fun refreshSync() {
    val result = signalServiceAccountManager.remoteConfig
    update(result.config)
  }

  @JvmStatic
  @Synchronized
  fun update(config: Map<String, Any?>) {
    val memory: Map<String, Any> = REMOTE_VALUES
    val disk = parseStoredConfig(SignalStore.remoteConfigValues().pendingConfig)
    val result = updateInternal(config, memory, disk, REMOTE_CAPABLE, HOT_SWAPPABLE, STICKY)

    SignalStore.remoteConfigValues().pendingConfig = mapToJson(result.disk)
    REMOTE_VALUES.clear()
    REMOTE_VALUES.putAll(result.memory)
    triggerFlagChangeListeners(result.memoryChanges)

    SignalStore.remoteConfigValues().lastFetchTime = System.currentTimeMillis()

    Log.i(TAG, "[Memory] Before: $memory")
    Log.i(TAG, "[Memory] After : ${result.memory}")
    Log.i(TAG, "[Disk]   Before: $disk")
    Log.i(TAG, "[Disk]   After : ${result.disk}")
  }

  /**
   * Maximum number of members allowed in a group.
   */
  @JvmStatic
  fun groupLimits(): SelectionLimits {
    return SelectionLimits(getInteger(GROUPS_V2_RECOMMENDED_LIMIT, 151), getInteger(GROUPS_V2_HARD_LIMIT, 1001))
  }

  /** Payments Support  */
  fun payments(): Boolean {
    return !getBoolean(PAYMENTS_KILL_SWITCH, false)
  }

  /** Internal testing extensions.  */
  @JvmStatic
  fun internalUser(): Boolean {
    return getBoolean(INTERNAL_USER, false) || Environment.IS_NIGHTLY || Environment.IS_STAGING
  }

  /** Whether or not to use the UUID in verification codes.  */
  fun verifyV2(): Boolean {
    return getBoolean(VERIFY_V2, false)
  }

  /** The raw client expiration JSON string.  */
  @JvmStatic
  fun clientExpiration(): String? {
    return getNullableString(CLIENT_EXPIRATION, null)
  }

  /** Whether to use the custom streaming muxer or built in android muxer.  */
  @JvmStatic
  fun useStreamingVideoMuxer(): Boolean {
    return getBoolean(CUSTOM_VIDEO_MUXER, false)
  }

  /** The time in between routine CDS refreshes, in seconds.  */
  @JvmStatic
  fun cdsRefreshIntervalSeconds(): Int {
    return getInteger(CDS_REFRESH_INTERVAL, 48.hours.inWholeSeconds.toInt())
  }

  /** The minimum time in between foreground CDS refreshes initiated via message requests, in milliseconds.  */
  fun cdsForegroundSyncInterval(): Long {
    return getLong(CDS_FOREGROUND_SYNC_INTERVAL, 4.hours.inWholeSeconds).seconds.inWholeMilliseconds
  }

  fun shareSelectionLimit(): SelectionLimits {
    val limit = getInteger(SHARE_SELECTION_LIMIT, 5)
    return SelectionLimits(limit, limit)
  }

  @JvmStatic
  val maxGroupNameGraphemeLength: Int
    /** The maximum number of grapheme  */
    get() = max(32.0, getInteger(GROUP_NAME_MAX_LENGTH, -1).toDouble()).toInt()

  /** Whether or not to allow automatic session resets.  */
  @JvmStatic
  fun automaticSessionReset(): Boolean {
    return getBoolean(AUTOMATIC_SESSION_RESET, true)
  }

  /** How often we allow an automatic session reset.  */
  @JvmStatic
  fun automaticSessionResetIntervalSeconds(): Int {
    return getInteger(AUTOMATIC_SESSION_RESET, 1.hours.inWholeSeconds.toInt())
  }

  @JvmStatic
  fun getDefaultMaxBackoff(): Long {
    return getInteger(DEFAULT_MAX_BACKOFF, 60).seconds.inWholeMilliseconds
  }

  @JvmStatic
  fun getServerErrorMaxBackoff(): Long {
    return getLong(SERVER_ERROR_MAX_BACKOFF, 6.hours.inWholeSeconds).seconds.inWholeMilliseconds
  }

  /** Whether or not to allow automatic retries from OkHttp  */
  @JvmStatic
  fun okHttpAutomaticRetry(): Boolean {
    return getBoolean(OKHTTP_AUTOMATIC_RETRY, true)
  }

  /** The minimum memory class required for rendering animated stickers in the keyboard and such  */
  @JvmStatic
  fun animatedStickerMinimumMemoryClass(): Int {
    return getInteger(ANIMATED_STICKER_MIN_MEMORY, 193)
  }

  /** The minimum total memory for rendering animated stickers in the keyboard and such  */
  @JvmStatic
  fun animatedStickerMinimumTotalMemoryMb(): Int {
    return getInteger(ANIMATED_STICKER_MIN_TOTAL_MEMORY, 3.gibiBytes.inWholeMebiBytes.toInt())
  }

  @JvmStatic
  fun getMediaQualityLevels(): String {
    return getString(MEDIA_QUALITY_LEVELS, "")
  }

  /** Whether or not sending or responding to retry receipts is enabled.  */
  fun retryReceipts(): Boolean {
    return getBoolean(RETRY_RECEIPTS, true)
  }

  /** How old a message is allowed to be while still resending in response to a retry receipt .  */
  @JvmStatic
  fun retryRespondMaxAge(): Long {
    return getLong(RETRY_RESPOND_MAX_AGE, 14.days.inWholeMilliseconds)
  }

  /**
   * The max number of retry receipts sends we allow (within @link{#retryReceiptMaxCountResetAge()}) before we consider the volume too large and stop responding.
   */
  fun retryReceiptMaxCount(): Long {
    return getLong(RETRY_RECEIPT_MAX_COUNT, 10)
  }

  /**
   * If the last retry receipt send was older than this, then we reset the retry receipt sent count. (For use with @link{#retryReceiptMaxCount()})
   */
  fun retryReceiptMaxCountResetAge(): Long {
    return getLong(RETRY_RECEIPT_MAX_COUNT_RESET_AGE, 3.hours.inWholeMilliseconds)
  }

  /** How long a sender key can live before it needs to be rotated.  */
  @JvmStatic
  fun senderKeyMaxAge(): Long {
    val remoteValue = getLong(SENDER_KEY_MAX_AGE, 14.days.inWholeMilliseconds)
    return min(remoteValue, 90.days.inWholeMilliseconds)
  }

  /** Max group size that can be use group call ringing.  */
  @JvmStatic
  fun maxGroupCallRingSize(): Long {
    return getLong(MAX_GROUP_CALL_RING_SIZE, 16)
  }

  /** A comma-separated list of country codes where payments should be disabled.  */
  @JvmStatic
  fun paymentsCountryBlocklist(): String {
    return getString(PAYMENTS_COUNTRY_BLOCKLIST, "98,963,53,850,7")
  }

  /**
   * Whether users can apply alignment and scale to text posts
   *
   * NOTE: This feature is still under ongoing development, do not enable.
   */
  fun storiesTextFunctions(): Boolean {
    return getBoolean(STORIES_TEXT_FUNCTIONS, false)
  }

  /** A comma-separated list of models that should *not* use hardware AEC for calling.  */
  fun hardwareAecBlocklistModels(): String {
    return getString(HARDWARE_AEC_BLOCKLIST_MODELS, "")
  }

  /** A comma-separated list of models that should *not* use software AEC for calling.  */
  fun softwareAecBlocklistModels(): String {
    return getString(SOFTWARE_AEC_BLOCKLIST_MODELS, "")
  }

  /** A comma-separated list of manufacturers that *should* use Telecom for calling.  */
  fun telecomManufacturerAllowList(): String {
    return getString(TELECOM_MANUFACTURER_ALLOWLIST, "")
  }

  /** A comma-separated list of manufacturers that *should* use Telecom for calling.  */
  fun telecomModelBlockList(): String {
    return getString(TELECOM_MODEL_BLOCKLIST, "")
  }

  /** A comma-separated list of manufacturers that should *not* use CameraX.  */
  fun cameraXModelBlocklist(): String {
    return getString(CAMERAX_MODEL_BLOCKLIST, "")
  }

  /** A comma-separated list of manufacturers that should *not* use CameraX mixed mode.  */
  fun cameraXMixedModelBlocklist(): String {
    return getString(CAMERAX_MIXED_MODEL_BLOCKLIST, "")
  }

  /** Whether or not hardware AEC should be used for calling on devices older than API 29.  */
  fun useHardwareAecIfOlderThanApi29(): Boolean {
    return getBoolean(USE_HARDWARE_AEC_IF_OLD, false)
  }

  /**
   * Prefetch count for stories from a given user.
   */
  fun storiesAutoDownloadMaximum(): Int {
    return getInteger(STORIES_AUTO_DOWNLOAD_MAXIMUM, 2)
  }

  /** Whether client supports sending a request to another to activate payments  */
  @JvmStatic
  fun paymentsRequestActivateFlow(): Boolean {
    return getBoolean(PAYMENTS_REQUEST_ACTIVATE_FLOW, false)
  }

  /**
   * @return Serialized list of regions in which Google Pay is disabled for donations
   */
  @JvmStatic
  fun googlePayDisabledRegions(): String {
    return getString(GOOGLE_PAY_DISABLED_REGIONS, "*")
  }

  /**
   * @return Serialized list of regions in which credit cards are disabled for donations
   */
  @JvmStatic
  fun creditCardDisabledRegions(): String {
    return getString(CREDIT_CARD_DISABLED_REGIONS, "*")
  }

  /**
   * @return Serialized list of regions in which PayPal is disabled for donations
   */
  @JvmStatic
  fun paypalDisabledRegions(): String {
    return getString(PAYPAL_DISABLED_REGIONS, "*")
  }

  /**
   * If the user has more than this number of contacts, the CDS request will certainly be rejected, so we must fail.
   */
  fun cdsHardLimit(): Int {
    return getInteger(CDS_HARD_LIMIT, 50000)
  }

  /**
   * Whether or not we should allow PayPal payments for one-time donations
   */
  fun paypalOneTimeDonations(): Boolean {
    return getBoolean(PAYPAL_ONE_TIME_DONATIONS, Environment.IS_STAGING)
  }

  /**
   * Whether or not we should allow PayPal payments for recurring donations
   */
  fun paypalRecurringDonations(): Boolean {
    return getBoolean(PAYPAL_RECURRING_DONATIONS, Environment.IS_STAGING)
  }

  /**
   * Enable/disable RingRTC field trial for "AnyAddressPortsKillSwitch"
   */
  @JvmStatic
  fun callingFieldTrialAnyAddressPortsKillSwitch(): Boolean {
    return getBoolean(ANY_ADDRESS_PORTS_KILL_SWITCH, false)
  }

  /**
   * Enable/disable for notification when we cannot fetch messages despite receiving an urgent push.
   */
  fun fcmMayHaveMessagesNotificationKillSwitch(): Boolean {
    return getBoolean(FCM_MAY_HAVE_MESSAGES_KILL_SWITCH, false)
  }

  /**
   * Whether or not ad-hoc calling is enabled
   */
  @JvmStatic
  fun adHocCalling(): Boolean {
    return getBoolean(AD_HOC_CALLING, false)
  }

  /** Maximum number of attachments allowed to be sent/received.  */
  fun maxAttachmentCount(): Int {
    return getInteger(MAX_ATTACHMENT_COUNT, 32)
  }

  /** Maximum attachment size for ciphertext in bytes.  */
  fun maxAttachmentReceiveSizeBytes(): Long {
    val maxAttachmentSize = maxAttachmentSizeBytes()
    val maxReceiveSize = getLong(MAX_ATTACHMENT_RECEIVE_SIZE_BYTES, (maxAttachmentSize * 1.25).toInt().toLong())
    return max(maxAttachmentSize.toDouble(), maxReceiveSize.toDouble()).toLong()
  }

  /** Maximum attachment ciphertext size when sending in bytes  */
  fun maxAttachmentSizeBytes(): Long {
    return getLong(MAX_ATTACHMENT_SIZE_BYTES, 100.mebiBytes.inWholeBytes)
  }

  @JvmStatic
  fun promptForDelayedNotificationLogs(): String {
    return getString(PROMPT_FOR_NOTIFICATION_LOGS, "*")
  }

  fun delayedNotificationsPromptConfig(): String {
    return getString(PROMPT_FOR_NOTIFICATION_CONFIG, "")
  }

  @JvmStatic
  fun promptBatterySaver(): String {
    return getString(PROMPT_BATTERY_SAVER, "*")
  }

  /** Config object for what crashes to prompt about.  */
  fun crashPromptConfig(): String {
    return getString(CRASH_PROMPT_CONFIG, "")
  }

  /**
   * Whether or not SEPA debit payments for donations are enabled.
   * WARNING: This feature is under heavy development and is *not* ready for wider use.
   */
  fun sepaDebitDonations(): Boolean {
    return getBoolean(SEPA_DEBIT_DONATIONS, false)
  }

  fun idealDonations(): Boolean {
    return getBoolean(IDEAL_DONATIONS, false)
  }

  @JvmStatic
  fun idealEnabledRegions(): String {
    return getString(IDEAL_ENABLED_REGIONS, "")
  }

  @JvmStatic
  fun sepaEnabledRegions(): String {
    return getString(SEPA_ENABLED_REGIONS, "")
  }

  /** List of device products that are blocked from showing notification thumbnails.  */
  fun notificationThumbnailProductBlocklist(): String {
    return getString(NOTIFICATION_THUMBNAIL_BLOCKLIST, "")
  }

  /** Whether or not to use active call manager instead of WebRtcCallService.  */
  @JvmStatic
  fun useActiveCallManager(): Boolean {
    return getBoolean(USE_ACTIVE_CALL_MANAGER, false)
  }

  /** Whether the in-app GIF search is available for use.  */
  @JvmStatic
  fun gifSearchAvailable(): Boolean {
    return getBoolean(GIF_SEARCH, true)
  }

  /** Allow media converters to remux audio instead of transcoding it.  */
  @JvmStatic
  fun allowAudioRemuxing(): Boolean {
    return getBoolean(AUDIO_REMUXING, false)
  }

  /** Get the default video zoom, expressed as 10x the actual Float value due to the service limiting us to whole numbers.  */
  @JvmStatic
  fun startVideoRecordAt1x(): Boolean {
    return getBoolean(VIDEO_RECORD_1X_ZOOM, false)
  }

  /** How often we allow a forced prekey refresh.  */
  fun preKeyForceRefreshInterval(): Long {
    return getLong(PREKEY_FORCE_REFRESH_INTERVAL, 1.hours.inWholeMilliseconds)
  }

  /** Make CDSI lookups via libsignal-net instead of native websocket.  */
  fun useLibsignalNetForCdsiLookup(): Boolean {
    return getBoolean(CDSI_LIBSIGNAL_NET, false)
  }

  /** Use Rx threading model to do sends.  */
  @JvmStatic
  fun useRxMessageSending(): Boolean {
    return getBoolean(RX_MESSAGE_SEND, false)
  }

  /** The lifespan of a linked device (i.e. the time it can be inactive for before it expires), in milliseconds.  */
  @JvmStatic
  fun linkedDeviceLifespan(): Long {
    return getLong(LINKED_DEVICE_LIFESPAN_SECONDS, 30.days.inWholeSeconds).seconds.inWholeMilliseconds
  }

  /**
   * Enable Message Backups UI
   * Note: This feature is in active development and is not intended to currently function.
   */
  @JvmStatic
  fun messageBackups(): Boolean {
    return BuildConfig.MESSAGE_BACKUP_RESTORE_ENABLED || getBoolean(MESSAGE_BACKUPS, false)
  }

  /** Whether or not to use the custom CameraX controller class  */
  @JvmStatic
  fun customCameraXController(): Boolean {
    return getBoolean(CAMERAX_CUSTOM_CONTROLLER, false)
  }

  /** Whether or not to use the V2 refactor of registration.  */
  @JvmStatic
  fun registrationV2(): Boolean {
    return getBoolean(REGISTRATION_V2, true)
  }

  /** Whether unauthenticated chat web socket is backed by libsignal-net  */
  @JvmStatic
  fun libSignalWebSocketEnabled(): Boolean {
    return getBoolean(LIBSIGNAL_WEB_SOCKET_ENABLED, false)
  }

  /** Whether or not to launch the restore activity after registration is complete, rather than before.  */
  @JvmStatic
  fun restoreAfterRegistration(): Boolean {
    return BuildConfig.MESSAGE_BACKUP_RESTORE_ENABLED || getBoolean(RESTORE_POST_REGISTRATION, false)
  }

  /**
   * Percentage [0, 100] of web socket requests that will be "shadowed" by sending
   * an unauthenticated keep-alive via libsignal-net. Default: 0
   */
  @JvmStatic
  fun libSignalWebSocketShadowingPercentage(): Int {
    val value = getInteger(LIBSIGNAL_WEB_SOCKET_SHADOW_PCT, 0)
    return max(0.0, min(value.toDouble(), 100.0)).toInt()
  }

  @JvmStatic
  fun getBackgroundMessageProcessInterval(): Long {
    val delayMinutes = getLong(MESSAGE_PROCESSOR_ALARM_INTERVAL, 6.hours.inWholeMinutes)
    return delayMinutes.minutes.inWholeMilliseconds
  }

  @JvmStatic
  fun getBackgroundMessageProcessForegroundDelay(): Long {
    return getInteger(MESSAGE_PROCESSOR_DELAY, 300).toLong()
  }

  /** Whether or not to delete syncing is enabled.  */
  @JvmStatic
  fun deleteSyncEnabled(): Boolean {
    return getBoolean(DELETE_SYNC_SEND_RECEIVE, false)
  }

  /** Whether or not to use V2 of linked devices.  */
  fun linkedDevicesV2(): Boolean {
    return getBoolean(LINKED_DEVICES_V2, false)
  }

  /** Which phase we're in for the SVR3 migration  */
  fun svr3MigrationPhase(): Int {
    return getInteger(SVR3_MIGRATION_PHASE, 0)
  }

  /** Only for rendering debug info.  */
  @JvmStatic
  @get:Synchronized
  val debugMemoryValues: Map<String, Any>
    get() = TreeMap(REMOTE_VALUES)

  /** Only for rendering debug info.  */
  @JvmStatic
  @get:Synchronized
  val debugDiskValues: Map<String, Any>
    get() = TreeMap(parseStoredConfig(SignalStore.remoteConfigValues().currentConfig))

  /** Only for rendering debug info.  */
  @JvmStatic
  @get:Synchronized
  val debugPendingDiskValues: Map<String, Any>
    get() = TreeMap(parseStoredConfig(SignalStore.remoteConfigValues().pendingConfig))

  /** Only for rendering debug info.  */
  @JvmStatic
  @get:Synchronized
  val debugForcedValues: Map<String, Any>
    get() = TreeMap(FORCED_VALUES)

  @JvmStatic
  @VisibleForTesting
  fun updateInternal(
    remote: Map<String, Any?>,
    localMemory: Map<String, Any>,
    localDisk: Map<String, Any>,
    remoteCapable: Set<String>,
    hotSwap: Set<String>,
    sticky: Set<String>
  ): UpdateResult {
    val newMemory: MutableMap<String, Any> = TreeMap(localMemory)
    val newDisk: MutableMap<String, Any> = TreeMap(localDisk)

    val allKeys: Set<String> = remote.keys + localDisk.keys + localMemory.keys

    allKeys
      .filter { remoteCapable.contains(it) }
      .forEach { key: String ->
        val remoteValue = remote[key]
        val diskValue = localDisk[key]
        var newValue = remoteValue

        if (newValue != null && diskValue != null && newValue.javaClass != diskValue.javaClass) {
          Log.w(TAG, "Type mismatch! key: $key")

          newDisk.remove(key)

          if (hotSwap.contains(key)) {
            newMemory.remove(key)
          }

          return@forEach
        }

        if (sticky.contains(key) && (newValue is Boolean || diskValue is Boolean)) {
          newValue = if (diskValue === java.lang.Boolean.TRUE) java.lang.Boolean.TRUE else newValue
        } else if (sticky.contains(key)) {
          Log.w(TAG, "Tried to make a non-boolean sticky! Ignoring. (key: $key)")
        }

        if (newValue != null) {
          newDisk[key] = newValue
        } else {
          newDisk.remove(key)
        }
        if (hotSwap.contains(key)) {
          if (newValue != null) {
            newMemory[key] = newValue
          } else {
            newMemory.remove(key)
          }
        }
      }

    allKeys
      .filterNot { remoteCapable.contains(it) }
      .filterNot { key -> sticky.contains(key) && localDisk[key] == java.lang.Boolean.TRUE }
      .forEach { key: String ->
        newDisk.remove(key)
        if (hotSwap.contains(key)) {
          newMemory.remove(key)
        }
      }

    return UpdateResult(newMemory, newDisk, computeChanges(localMemory, newMemory))
  }

  @JvmStatic
  @VisibleForTesting
  fun computeChanges(oldMap: Map<String, Any>, newMap: Map<String, Any>): Map<String, Change> {
    val changes: MutableMap<String, Change> = mutableMapOf()
    val allKeys: MutableSet<String> = mutableSetOf()

    allKeys += oldMap.keys
    allKeys += newMap.keys

    for (key in allKeys) {
      val oldValue = oldMap[key]
      val newValue = newMap[key]

      if (oldValue == null && newValue == null) {
        throw AssertionError("Should not be possible.")
      } else if (oldValue != null && newValue == null) {
        changes[key] = Change.REMOVED
      } else if (newValue !== oldValue && newValue is Boolean) {
        changes[key] = if (newValue) Change.ENABLED else Change.DISABLED
      } else if (oldValue != newValue) {
        changes[key] = Change.CHANGED
      }
    }

    return changes
  }

  private fun getBoolean(key: String, defaultValue: Boolean): Boolean {
    val forced = FORCED_VALUES[key] as? Boolean
    if (forced != null) {
      return forced
    }

    val remote = REMOTE_VALUES[key]
    if (remote is Boolean) {
      return remote
    } else if (remote is String) {
      val stringValue = remote.lowercase(Locale.getDefault())
      if (stringValue == "true") {
        return true
      } else if (stringValue == "false") {
        return false
      } else {
        Log.w(TAG, "Expected a boolean for key '$key', but got something else ($stringValue)! Falling back to the default.")
      }
    } else if (remote != null) {
      Log.w(TAG, "Expected a boolean for key '$key', but got something else! Falling back to the default.")
    }

    return defaultValue
  }

  private fun getInteger(key: String, defaultValue: Int): Int {
    val forced = FORCED_VALUES[key] as? Int
    if (forced != null) {
      return forced
    }

    val remote = REMOTE_VALUES[key]
    if (remote is String) {
      try {
        return remote.toInt()
      } catch (e: NumberFormatException) {
        Log.w(TAG, "Expected an int for key '$key', but got something else! Falling back to the default.")
      }
    }

    return defaultValue
  }

  private fun getLong(key: String, defaultValue: Long): Long {
    val forced = FORCED_VALUES[key] as? Long
    if (forced != null) {
      return forced
    }

    val remote = REMOTE_VALUES[key]
    if (remote is String) {
      try {
        return remote.toLong()
      } catch (e: NumberFormatException) {
        Log.w(TAG, "Expected a long for key '$key', but got something else! Falling back to the default.")
      }
    }

    return defaultValue
  }

  private fun getString(key: String, defaultValue: String): String {
    return getNullableString(key, defaultValue)!!
  }

  private fun getNullableString(key: String, defaultValue: String?): String? {
    val forced = FORCED_VALUES[key] as String?
    if (forced != null) {
      return forced
    }

    val remote = REMOTE_VALUES[key]
    if (remote is String) {
      return remote
    }

    return defaultValue
  }

  private fun parseStoredConfig(stored: String?): Map<String, Any> {
    val parsed: MutableMap<String, Any> = HashMap()

    if (stored.isNullOrEmpty()) {
      Log.i(TAG, "No remote config stored. Skipping.")
      return parsed
    }

    try {
      val root = JSONObject(stored)
      val iter = root.keys()

      while (iter.hasNext()) {
        val key = iter.next()
        parsed[key] = root[key]
      }
    } catch (e: JSONException) {
      throw AssertionError("Failed to parse! Cleared storage.")
    }

    return parsed
  }

  private fun mapToJson(map: Map<String, Any>): String {
    try {
      val json = JSONObject()

      for ((key, value) in map) {
        json.put(key, value)
      }

      return json.toString()
    } catch (e: JSONException) {
      throw AssertionError(e)
    }
  }

  private fun triggerFlagChangeListeners(changes: Map<String, Change>) {
    for ((key, value) in changes) {
      val listener = FLAG_CHANGE_LISTENERS[key]

      if (listener != null) {
        Log.i(TAG, "Triggering change listener for: $key")
        listener.onFlagChange(value)
      }
    }
  }

  @VisibleForTesting
  class UpdateResult(
    val memory: Map<String, Any>,
    val disk: Map<String, Any>,
    val memoryChanges: Map<String, Change>
  )

  @VisibleForTesting
  internal fun interface OnFlagChange {
    fun onFlagChange(change: Change)
  }

  enum class Change {
    ENABLED, DISABLED, CHANGED, REMOVED
  }
}
