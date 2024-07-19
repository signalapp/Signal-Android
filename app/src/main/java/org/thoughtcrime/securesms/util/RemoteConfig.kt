package org.thoughtcrime.securesms.util

import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.json.JSONException
import org.json.JSONObject
import org.signal.core.util.gibiBytes
import org.signal.core.util.logging.Log
import org.signal.core.util.mebiBytes
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.jobs.RemoteConfigRefreshJob
import org.thoughtcrime.securesms.jobs.Svr3MirrorJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messageprocessingalarm.RoutineMessageFetchReceiver
import org.thoughtcrime.securesms.util.RemoteConfig.Config
import org.thoughtcrime.securesms.util.RemoteConfig.remoteBoolean
import org.thoughtcrime.securesms.util.RemoteConfig.remoteValue
import java.io.IOException
import java.util.TreeMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A location for accessing remotely-configured values.
 *
 * When creating a new config:
 * - At the bottom of the file, create a new `val` with the name you'd like.
 * - Use one of the helper delegates, like [remoteBoolean] or [remoteValue], to define your `val`.
 * - See the documentation for [Config] to understand all of the fields.
 */
object RemoteConfig {
  private val TAG = Log.tag(RemoteConfig::class.java)

  // region Core behavior

  private val FETCH_INTERVAL = 2.hours

  @VisibleForTesting
  val REMOTE_VALUES: MutableMap<String, Any> = TreeMap()

  @VisibleForTesting
  val configsByKey: MutableMap<String, Config<*>> = mutableMapOf()

  @GuardedBy("initLock")
  @Volatile
  @VisibleForTesting
  var initialized: Boolean = false
  private val initLock: ReentrantLock = ReentrantLock()

  @JvmStatic
  fun init() {
    initLock.withLock {
      val current = parseStoredConfig(SignalStore.remoteConfig.currentConfig)
      val pending = parseStoredConfig(SignalStore.remoteConfig.pendingConfig)
      val changes = computeChanges(current, pending)

      SignalStore.remoteConfig.currentConfig = mapToJson(pending)
      REMOTE_VALUES.putAll(pending)
      triggerFlagChangeListeners(changes)

      Log.i(TAG, "init() $REMOTE_VALUES")

      initialized = true
    }
  }

  @JvmStatic
  fun refreshIfNecessary() {
    val timeSinceLastFetch = System.currentTimeMillis() - SignalStore.remoteConfig.lastFetchTime

    if (timeSinceLastFetch < 0 || timeSinceLastFetch > FETCH_INTERVAL.inWholeMilliseconds) {
      Log.i(TAG, "Scheduling remote config refresh.")
      AppDependencies.jobManager.add(RemoteConfigRefreshJob())
    } else {
      Log.i(TAG, "Skipping remote config refresh. Refreshed $timeSinceLastFetch ms ago.")
    }
  }

  @JvmStatic
  @WorkerThread
  @Throws(IOException::class)
  fun refreshSync() {
    val result = AppDependencies.signalServiceAccountManager.getRemoteConfig()
    update(result.config)
  }

  @JvmStatic
  @Synchronized
  fun update(config: Map<String, Any?>) {
    val memory: Map<String, Any> = REMOTE_VALUES
    val disk = parseStoredConfig(SignalStore.remoteConfig.pendingConfig)

    val remoteCapable: Set<String> = configsByKey.filterValues { it.active }.keys
    val hotSwap: Set<String> = configsByKey.filterValues { it.hotSwappable }.keys
    val sticky: Set<String> = configsByKey.filterValues { it.sticky }.keys

    val result = updateInternal(config, memory, disk, remoteCapable, hotSwap, sticky)

    SignalStore.remoteConfig.pendingConfig = mapToJson(result.disk)
    REMOTE_VALUES.clear()
    REMOTE_VALUES.putAll(result.memory)
    triggerFlagChangeListeners(result.memoryChanges)

    SignalStore.remoteConfig.lastFetchTime = System.currentTimeMillis()

    Log.i(TAG, "[Memory] Before: $memory")
    Log.i(TAG, "[Memory] After : ${result.memory}")
    Log.i(TAG, "[Disk]   Before: $disk")
    Log.i(TAG, "[Disk]   After : ${result.disk}")
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
    get() = TreeMap(parseStoredConfig(SignalStore.remoteConfig.currentConfig))

  /** Only for rendering debug info.  */
  @JvmStatic
  @get:Synchronized
  val debugPendingDiskValues: Map<String, Any>
    get() = TreeMap(parseStoredConfig(SignalStore.remoteConfig.pendingConfig))

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
          newValue = if (diskValue == true) true else newValue
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
  fun computeChanges(oldMap: Map<String, Any>, newMap: Map<String, Any>): Map<String, ConfigChange> {
    val allKeys: Set<String> = oldMap.keys + newMap.keys

    return allKeys
      .filter { oldMap[it] != newMap[it] }
      .associateWith { key ->
        ConfigChange(
          oldValue = oldMap[key],
          newValue = newMap[key]
        )
      }
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

  private fun triggerFlagChangeListeners(changes: Map<String, ConfigChange>) {
    for ((key, value) in changes) {
      val listener = configsByKey[key]?.onChangeListener

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
    val memoryChanges: Map<String, ConfigChange>
  )

  data class ConfigChange(val oldValue: Any?, val newValue: Any?)

  @VisibleForTesting
  fun interface OnFlagChange {
    fun onFlagChange(change: ConfigChange)
  }

  // endregion

  // region Conversion utilities
  private fun Any?.asBoolean(defaultValue: Boolean): Boolean {
    return when (this) {
      is Boolean -> this
      is String -> this.toBoolean()
      else -> defaultValue
    }
  }

  private fun Any?.asInteger(defaultValue: Int): Int {
    return when (this) {
      is String -> this.toIntOrNull() ?: defaultValue
      else -> defaultValue
    }
  }

  private fun Any?.asLong(defaultValue: Long): Long {
    return when (this) {
      is String -> this.toLongOrNull() ?: defaultValue
      else -> defaultValue
    }
  }

  private fun <T : String?> Any?.asString(defaultValue: T): T {
    @Suppress("UNCHECKED_CAST")
    return when (this) {
      is String -> this as T
      else -> defaultValue
    }
  }

  // endregion

  // region Delegates
  data class Config<T>(
    /**
     * The key used to identify the remote config on the service.
     */
    val key: String,

    /**
     * By default, flags are only updated once at app start. This is to ensure that values don't
     * change within an app session, simplifying logic. However, given that this can delay how often
     * a flag is updated, you can put a flag in here to mark it as 'hot swappable'. Flags in this set
     * will be updated arbitrarily at runtime. This will make values more responsive, but also places
     * more burden on the reader to ensure that the app experience remains consistent.
     */
    val hotSwappable: Boolean,

    /**
     * Flags in this set will stay true forever once they receive a true value from a remote config.
     */
    val sticky: Boolean,

    /**
     * If this is false, the remote value of the flag will be ignored, and we'll only ever use the default value.
     */
    val active: Boolean,

    /**
     * Listeners that are called when the value in [REMOTE_VALUES] changes. That means that
     * hot-swappable flags will have this invoked as soon as we know about that change, but otherwise
     * these will only run during initialization.
     *
     * These can be called on any thread, including the main thread, so be careful!
     */
    val onChangeListener: OnFlagChange? = null,

    /**
     * Takes the remote value and coerces it to the value you want to read. If implementing this directly,
     * consider using helpers like [asBoolean] or [asInteger] to make your life easier.
     */
    val transformer: (Any?) -> T
  ) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
      if (!initialized) {
        Log.w(TAG, "Tried to read $key before initialization. Initializing now.")
        initLock.withLock {
          if (!initialized) {
            init()
          }
        }
      }

      return transformer(REMOTE_VALUES[key])
    }
  }

  private fun remoteBoolean(
    key: String,
    defaultValue: Boolean,
    hotSwappable: Boolean,
    sticky: Boolean = false,
    active: Boolean = true,
    onChangeListener: OnFlagChange? = null
  ): Config<Boolean> {
    return remoteValue(
      key = key,
      hotSwappable = hotSwappable,
      sticky = sticky,
      active = active,
      onChangeListener = onChangeListener,
      transformer = { it.asBoolean(defaultValue) }
    )
  }

  private fun remoteInt(
    key: String,
    defaultValue: Int,
    hotSwappable: Boolean,
    active: Boolean = true,
    onChangeListener: OnFlagChange? = null
  ): Config<Int> {
    return remoteValue(
      key = key,
      hotSwappable = hotSwappable,
      sticky = false,
      active = active,
      onChangeListener = onChangeListener,
      transformer = { it.asInteger(defaultValue) }
    )
  }

  private fun remoteLong(
    key: String,
    defaultValue: Long,
    hotSwappable: Boolean,
    active: Boolean = true,
    onChangeListener: OnFlagChange? = null
  ): Config<Long> {
    return remoteValue(
      key = key,
      hotSwappable = hotSwappable,
      sticky = false,
      active = active,
      onChangeListener = onChangeListener,
      transformer = { it.asLong(defaultValue) }
    )
  }

  private fun <T : String?> remoteString(
    key: String,
    defaultValue: T,
    hotSwappable: Boolean,
    active: Boolean = true,
    onChangeListener: OnFlagChange? = null
  ): Config<T> {
    return remoteValue(
      key = key,
      hotSwappable = hotSwappable,
      sticky = false,
      active = active,
      onChangeListener = onChangeListener,
      transformer = { it.asString(defaultValue) }
    )
  }

  private fun <T> remoteValue(
    key: String,
    hotSwappable: Boolean,
    sticky: Boolean = false,
    active: Boolean = true,
    onChangeListener: OnFlagChange? = null,
    transformer: (Any?) -> T
  ): Config<T> {
    val config = Config(key = key, active = active, hotSwappable = hotSwappable, sticky = sticky, onChangeListener = onChangeListener, transformer = transformer)
    configsByKey[config.key] = config
    return config
  }

  // endregion

  // region Config definitions

  /** Payments support */
  val payments: Boolean by remoteValue(
    key = "android.payments.kill",
    hotSwappable = false
  ) { value ->
    !value.asBoolean(false)
  }

  /** Whether or not to use the UUID in verification codes.  */
  val verifyV2: Boolean by remoteBoolean(
    key = "android.verifyV2",
    defaultValue = false,
    hotSwappable = true,
    sticky = true
  )

  /** Maximum number of members allowed in a group. */
  @JvmStatic
  @get:JvmName("groupLimits")
  val groupLimits: SelectionLimits
    get() = SelectionLimits(groupRecommendedLimit, groupHardLimit)

  private val groupRecommendedLimit: Int by remoteInt(
    key = "global.groupsv2.maxGroupSize",
    defaultValue = 151,
    hotSwappable = true
  )

  private val groupHardLimit: Int by remoteInt(
    key = "global.groupsv2.groupSizeHardLimit",
    defaultValue = 1001,
    hotSwappable = true
  )

  /** The maximum number of grapheme  */
  @JvmStatic
  val maxGroupNameGraphemeLength: Int by remoteValue(
    key = "global.groupsv2.maxNameLength",
    hotSwappable = true
  ) { value ->
    val remote = value.asInteger(-1)
    max(32, remote)
  }

  /** Whether or not the user is an 'internal' one, which activates certain developer tools. */
  @JvmStatic
  @get:JvmName("internalUser")
  val internalUser: Boolean by remoteValue(
    key = "android.internalUser",
    hotSwappable = true
  ) { value ->
    value.asBoolean(false) || Environment.IS_NIGHTLY || Environment.IS_STAGING
  }

  /** The raw client expiration JSON string.  */
  @JvmStatic
  @get:JvmName("clientExpiration")
  val clientExpiration: String? by remoteString(
    key = "android.clientExpiration",
    hotSwappable = true,
    defaultValue = null
  )

  /** Whether to use the custom streaming muxer or built in android muxer.  */
  @JvmStatic
  @get:JvmName("useStreamingVideoMuxer")
  val useStreamingVideoMuxer: Boolean by remoteBoolean(
    key = "android.customVideoMuxer.1",
    defaultValue = false,
    hotSwappable = true
  )

  /** The time in between routine CDS refreshes, in seconds.  */
  @JvmStatic
  @get:JvmName("cdsRefreshIntervalSeconds")
  val cdsRefreshIntervalSeconds: Int by remoteInt(
    key = "cds.syncInterval.seconds",
    defaultValue = 48.hours.inWholeSeconds.toInt(),
    hotSwappable = true
  )

  /** The minimum time in between foreground CDS refreshes initiated via message requests, in milliseconds.  */
  val cdsForegroundSyncInterval: Long by remoteValue(
    key = "cds.foregroundSyncInterval.seconds",
    hotSwappable = true
  ) { value ->
    val inSeconds = value.asLong(4.hours.inWholeSeconds)
    inSeconds.seconds.inWholeMilliseconds
  }

  val shareSelectionLimit: SelectionLimits by remoteValue(
    key = "android.share.limit",
    hotSwappable = true
  ) { value ->
    val limit = value.asInteger(5)
    SelectionLimits(limit, limit)
  }

  /** Whether or not to allow automatic session resets.  */
  @JvmStatic
  @get:JvmName("automaticSessionReset")
  val automaticSessionReset: Boolean by remoteBoolean(
    key = "android.automaticSessionReset.2",
    defaultValue = true,
    hotSwappable = true
  )

  /** How often we allow an automatic session reset.  */
  @JvmStatic
  @get:JvmName("automaticSessionResetIntervalSeconds")
  val automaticSessionResetIntervalSeconds: Int by remoteInt(
    key = "android.automaticSessionResetInterval",
    defaultValue = 1.hours.inWholeSeconds.toInt(),
    hotSwappable = true
  )

  @JvmStatic
  val defaultMaxBackoff: Long by remoteValue(
    key = "android.defaultMaxBackoff",
    hotSwappable = true
  ) { value ->
    val inSeconds = value.asLong(60)
    inSeconds.seconds.inWholeMilliseconds
  }

  @JvmStatic
  val serverErrorMaxBackoff: Long by remoteValue(
    key = "android.serverErrorMaxBackoff",
    hotSwappable = true
  ) { value ->
    val inSeconds = value.asLong(6.hours.inWholeSeconds)
    inSeconds.seconds.inWholeMilliseconds
  }

  /** Whether or not to allow automatic retries from OkHttp  */
  @JvmStatic
  @get:JvmName("okHttpAutomaticRetry")
  val okHttpAutomaticRetry: Boolean by remoteBoolean(
    key = "android.okhttpAutomaticRetry",
    defaultValue = true,
    hotSwappable = true
  )

  /** The minimum memory class required for rendering animated stickers in the keyboard and such  */
  @JvmStatic
  @get:JvmName("animatedStickerMinimumMemoryClass")
  val animatedStickerMinimumMemoryClass: Int by remoteInt(
    key = "android.animatedStickerMinMemory",
    defaultValue = 193,
    hotSwappable = true
  )

  /** The minimum total memory for rendering animated stickers in the keyboard and such  */
  @JvmStatic
  @get:JvmName("animatedStickerMinimumTotalMemoryMb")
  val animatedStickerMinimumTotalMemoryMb: Int by remoteInt(
    key = "android.animatedStickerMinTotalMemory",
    defaultValue = 3.gibiBytes.inWholeMebiBytes.toInt(),
    hotSwappable = true
  )

  @JvmStatic
  val mediaQualityLevels: String by remoteString(
    key = "android.mediaQuality.levels",
    defaultValue = "",
    hotSwappable = true
  )

  /** Whether or not sending or responding to retry receipts is enabled.  */
  @JvmStatic
  @get:JvmName("retryReceipts")
  val retryReceipts: Boolean by remoteBoolean(
    key = "android.retryReceipts",
    defaultValue = true,
    hotSwappable = true
  )

  /** How old a message is allowed to be while still resending in response to a retry receipt .  */
  @JvmStatic
  @get:JvmName("retryRespondMaxAge")
  val retryRespondMaxAge: Long by remoteLong(
    key = "android.retryRespondMaxAge",
    defaultValue = 14.days.inWholeMilliseconds,
    hotSwappable = true
  )

  /** The max number of retry receipts sends we allow (within [retryReceiptMaxCountResetAge]) before we consider the volume too large and stop responding. */
  @JvmStatic
  @get:JvmName("retryReceiptMaxCount")
  val retryReceiptMaxCount: Long by remoteLong(
    key = "android.retryReceipt.maxCount",
    defaultValue = 10,
    hotSwappable = true
  )

  /** If the last retry receipt send was older than this, then we reset the retry receipt sent count. (For use with [retryReceiptMaxCount]) */
  @JvmStatic
  @get:JvmName("retryReceiptMaxCountResetAge")
  val retryReceiptMaxCountResetAge: Long by remoteLong(
    key = "android.retryReceipt.maxCountResetAge",
    defaultValue = 3.hours.inWholeMilliseconds,
    hotSwappable = true
  )

  /** How long a sender key can live before it needs to be rotated.  */
  @JvmStatic
  @get:JvmName("senderKeyMaxAge")
  val senderKeyMaxAge: Long by remoteValue(
    key = "android.senderKeyMaxAge",
    hotSwappable = true
  ) { value ->
    val remoteValue = value.asLong(14.days.inWholeMilliseconds)
    min(remoteValue, 90.days.inWholeMilliseconds)
  }

  /** Max group size that can be use group call ringing.  */
  @JvmStatic
  @get:JvmName("maxGroupCallRingSize")
  val maxGroupCallRingSize: Long by remoteLong(
    key = "global.calling.maxGroupCallRingSize",
    defaultValue = 16,
    hotSwappable = true
  )

  /** A comma-separated list of country codes where payments should be disabled.  */
  @JvmStatic
  @get:JvmName("paymentsCountryBlocklist")
  val paymentsCountryBlocklist: String by remoteString(
    key = "global.payments.disabledRegions",
    defaultValue = "98,963,53,850,7",
    hotSwappable = true
  )

  /**
   * Whether users can apply alignment and scale to text posts
   *
   * NOTE: This feature is still under ongoing development, do not enable.
   */
  val storiesTextFunctions: Boolean by remoteBoolean(
    key = "android.stories.text.functions",
    defaultValue = false,
    hotSwappable = false
  )

  /** A comma-separated list of models that should *not* use hardware AEC for calling.  */
  val hardwareAecBlocklistModels: String by remoteString(
    key = "android.calling.hardwareAecBlockList",
    defaultValue = "",
    hotSwappable = true
  )

  /** A comma-separated list of models that should *not* use software AEC for calling.  */
  val softwareAecBlocklistModels: String by remoteString(
    key = "android.calling.softwareAecBlockList",
    defaultValue = "",
    hotSwappable = true
  )

  /** A comma-separated list of manufacturers that *should* use Telecom for calling.  */
  val telecomManufacturerAllowList: String by remoteString(
    key = "android.calling.telecomAllowList",
    defaultValue = "",
    hotSwappable = true
  )

  /** A comma-separated list of manufacturers that *should* use Telecom for calling.  */
  val telecomModelBlocklist: String by remoteString(
    key = "android.calling.telecomModelBlockList",
    defaultValue = "",
    hotSwappable = true
  )

  /** A comma-separated list of manufacturers that should *not* use CameraX.  */
  val cameraXModelBlocklist: String by remoteString(
    key = "android.cameraXModelBlockList",
    defaultValue = "",
    hotSwappable = true
  )

  /** A comma-separated list of manufacturers that should *not* use CameraX mixed mode.  */
  val cameraXMixedModelBlocklist: String by remoteString(
    key = "android.cameraXMixedModelBlockList",
    defaultValue = "",
    hotSwappable = false
  )

  /** Whether or not hardware AEC should be used for calling on devices older than API 29.  */
  val useHardwareAecIfOlderThanApi29: Boolean by remoteBoolean(
    key = "android.calling.useHardwareAecIfOlderThanApi29",
    defaultValue = false,
    hotSwappable = true
  )

  /** Prefetch count for stories from a given user. */
  val storiesAutoDownloadMaximum: Int by remoteInt(
    key = "android.stories.autoDownloadMaximum",
    defaultValue = 2,
    hotSwappable = false
  )

  /** Whether client supports sending a request to another to activate payments  */
  @JvmStatic
  @get:JvmName("paymentsRequestActivateFlow")
  val paymentsRequestActivateFlow: Boolean by remoteBoolean(
    key = "android.payments.requestActivateFlow",
    defaultValue = false,
    hotSwappable = true
  )

  /** Serialized list of regions in which Google Pay is disabled for donations */
  @JvmStatic
  @get:JvmName("googlePayDisabledRegions")
  val googlePayDisabledRegions: String by remoteString(
    key = "global.donations.gpayDisabledRegions",
    defaultValue = "*",
    hotSwappable = false
  )

  /** Serialized list of regions in which credit cards are disabled for donations */
  @JvmStatic
  @get:JvmName("creditCardDisabledRegions")
  val creditCardDisabledRegions: String by remoteString(
    key = "global.donations.ccDisabledRegions",
    defaultValue = "*",
    hotSwappable = false
  )

  /** @return Serialized list of regions in which PayPal is disabled for donations */
  @JvmStatic
  @get:JvmName("paypalDisabledRegions")
  val paypalDisabledRegions: String by remoteString(
    key = "global.donations.paypalDisabledRegions",
    defaultValue = "*",
    hotSwappable = false
  )

  /** If the user has more than this number of contacts, the CDS request will certainly be rejected, so we must fail. */
  val cdsHardLimit: Int by remoteInt(
    key = "android.cds.hardLimit",
    defaultValue = 50000,
    hotSwappable = true
  )

  /** Whether or not we should allow PayPal payments for one-time donations */
  val paypalOneTimeDonations: Boolean by remoteBoolean(
    key = "android.oneTimePayPalDonations.2",
    defaultValue = Environment.IS_STAGING,
    hotSwappable = false
  )

  /** Whether or not we should allow PayPal payments for recurring donations */
  val paypalRecurringDonations: Boolean by remoteBoolean(
    key = "android.recurringPayPalDonations.3",
    defaultValue = Environment.IS_STAGING,
    hotSwappable = false
  )

  /** Enable/disable RingRTC field trial for "AnyAddressPortsKillSwitch" */
  @JvmStatic
  @get:JvmName("callingFieldTrialAnyAddressPortsKillSwitch")
  val callingFieldTrialAnyAddressPortsKillSwitch: Boolean by remoteBoolean(
    key = "android.calling.fieldTrial.anyAddressPortsKillSwitch",
    defaultValue = false,
    hotSwappable = false
  )

  /**
   * Enable/disable for notification when we cannot fetch messages despite receiving an urgent push.
   */
  val fcmMayHaveMessagesNotificationKillSwitch: Boolean by remoteBoolean(
    key = "android.fcmNotificationFallbackKillSwitch",
    defaultValue = false,
    hotSwappable = true,
    sticky = true
  )

  /**
   * Whether or not ad-hoc calling is enabled
   */
  @JvmStatic
  @get:JvmName("adHocCalling")
  val adHocCalling: Boolean by remoteBoolean(
    key = "android.calling.ad.hoc.3",
    defaultValue = false,
    hotSwappable = false
  )

  /** Maximum number of attachments allowed to be sent/received.  */
  val maxAttachmentCount: Int by remoteInt(
    key = "android.attachments.maxCount",
    defaultValue = 32,
    hotSwappable = true
  )

  /** Maximum attachment size for ciphertext in bytes.  */
  val maxAttachmentReceiveSizeBytes: Long by remoteValue(
    key = "global.attachments.maxReceiveBytes",
    hotSwappable = true
  ) { value ->
    val maxAttachmentSize = maxAttachmentSizeBytes
    val maxReceiveSize = value.asLong((maxAttachmentSize * 1.25).toInt().toLong())
    max(maxAttachmentSize, maxReceiveSize)
  }

  /** Maximum attachment ciphertext size when sending in bytes  */
  val maxAttachmentSizeBytes: Long by remoteLong(
    key = "global.attachments.maxBytes",
    defaultValue = 100.mebiBytes.inWholeBytes,
    hotSwappable = true
  )

  /** Maximum input size when opening a video to send in bytes  */
  @JvmStatic
  @get:JvmName("maxSourceTranscodeVideoSizeBytes")
  val maxSourceTranscodeVideoSizeBytes: Long by remoteLong(
    key = "android.media.sourceTranscodeVideo.maxBytes",
    defaultValue = 500L.mebiBytes.inWholeBytes,
    hotSwappable = true
  )

  const val PROMPT_FOR_NOTIFICATION_LOGS: String = "android.logs.promptNotifications"

  @JvmStatic
  @get:JvmName("promptForDelayedNotificationLogs")
  val promptForDelayedNotificationLogs: String by remoteString(
    key = RemoteConfig.PROMPT_FOR_NOTIFICATION_LOGS,
    defaultValue = "*",
    hotSwappable = true
  )

  val delayedNotificationsPromptConfig: String by remoteString(
    key = "android.logs.promptNotificationsConfig",
    defaultValue = "",
    hotSwappable = true
  )

  const val PROMPT_BATTERY_SAVER: String = "android.promptBatterySaver"

  @JvmStatic
  @get:JvmName("promptBatterySaver")
  val promptBatterySaver: String by remoteString(
    key = PROMPT_BATTERY_SAVER,
    defaultValue = "*",
    hotSwappable = true
  )

  const val DEVICE_SPECIFIC_NOTIFICATION_CONFIG: String = "android.deviceSpecificNotificationConfig"

  val deviceSpecificNotificationConfig: String by remoteString(
    key = DEVICE_SPECIFIC_NOTIFICATION_CONFIG,
    defaultValue = "",
    hotSwappable = true
  )

  const val CRASH_PROMPT_CONFIG: String = "android.crashPromptConfig.2"

  /** Config object for what crashes to prompt about.  */
  val crashPromptConfig: String by remoteString(
    key = CRASH_PROMPT_CONFIG,
    defaultValue = "",
    hotSwappable = true
  )

  /** Whether or not SEPA debit payments for donations are enabled. */
  val sepaDebitDonations: Boolean by remoteBoolean(
    key = "android.sepa.debit.donations.5",
    defaultValue = false,
    hotSwappable = false
  )

  val idealDonations: Boolean by remoteBoolean(
    key = "android.ideal.donations.5",
    defaultValue = false,
    hotSwappable = false
  )

  @JvmStatic
  @get:JvmName("idealEnabledRegions")
  val idealEnabledRegions: String by remoteString(
    key = "global.donations.idealEnabledRegions",
    defaultValue = "",
    hotSwappable = false
  )

  @JvmStatic
  @get:JvmName("sepaEnabledRegions")
  val sepaEnabledRegions: String by remoteString(
    key = "global.donations.sepaEnabledRegions",
    defaultValue = "",
    hotSwappable = false
  )

  /** List of device products that are blocked from showing notification thumbnails.  */
  val notificationThumbnailProductBlocklist: String by remoteString(
    key = "android.notificationThumbnailProductBlocklist",
    defaultValue = "",
    hotSwappable = true
  )

  /** Whether or not to use active call manager instead of WebRtcCallService.  */
  @JvmStatic
  @get:JvmName("useActiveCallManager")
  val useActiveCallManager: Boolean by remoteBoolean(
    key = "android.calling.useActiveCallManager.5",
    defaultValue = false,
    hotSwappable = false
  )

  /** Whether the in-app GIF search is available for use.  */
  @JvmStatic
  @get:JvmName("gifSearchAvailable")
  val gifSearchAvailable: Boolean by remoteBoolean(
    key = "global.gifSearch",
    defaultValue = true,
    hotSwappable = true
  )

  /** Allow media converters to remux audio instead of transcoding it.  */
  @JvmStatic
  @get:JvmName("allowAudioRemuxing")
  val allowAudioRemuxing: Boolean by remoteBoolean(
    key = "android.media.audioRemux.1",
    defaultValue = false,
    hotSwappable = false
  )

  /** Get the default video zoom, expressed as 10x the actual Float value due to the service limiting us to whole numbers.  */
  @JvmStatic
  @get:JvmName("startVideoRecordAt1x")
  val startVideoRecordAt1x: Boolean by remoteBoolean(
    key = "android.media.videoCaptureDefaultZoom",
    defaultValue = false,
    hotSwappable = true
  )

  /** How often we allow a forced prekey refresh.  */
  val preKeyForceRefreshInterval: Long by remoteLong(
    key = "android.prekeyForceRefreshInterval",
    defaultValue = 1.hours.inWholeMilliseconds,
    hotSwappable = true
  )

  /** Make CDSI lookups via libsignal-net instead of native websocket.  */
  val useLibsignalNetForCdsiLookup: Boolean by remoteBoolean(
    key = "android.cds.libsignal.4",
    defaultValue = false,
    hotSwappable = true
  )

  /** The lifespan of a linked device (i.e. the time it can be inactive for before it expires), in milliseconds.  */
  @JvmStatic
  val linkedDeviceLifespan: Long by remoteValue(
    key = "android.linkedDeviceLifespanSeconds",
    hotSwappable = true
  ) { value ->
    val inSeconds = value.asLong(30.days.inWholeSeconds)
    inSeconds.seconds.inWholeMilliseconds
  }

  /**
   * Enable Message Backups UI
   * Note: This feature is in active development and is not intended to currently function.
   */
  @JvmStatic
  @get:JvmName("messageBackups")
  val messageBackups: Boolean by remoteValue(
    key = "android.messageBackups",
    hotSwappable = false,
    active = false
  ) { value ->
    BuildConfig.MESSAGE_BACKUP_RESTORE_ENABLED || value.asBoolean(false)
  }

  /** Whether or not to use the custom CameraX controller class  */
  @JvmStatic
  @get:JvmName("customCameraXController")
  val customCameraXController: Boolean by remoteBoolean(
    key = "android.cameraXCustomController",
    defaultValue = false,
    hotSwappable = true
  )

  /** Whether unauthenticated chat web socket is backed by libsignal-net  */
  @JvmStatic
  @get:JvmName("libSignalWebSocketEnabled")
  val libSignalWebSocketEnabled: Boolean by remoteBoolean(
    key = "android.libsignalWebSocketEnabled",
    defaultValue = false,
    hotSwappable = false
  )

  /** Whether or not to launch the restore activity after registration is complete, rather than before.  */
  @JvmStatic
  @get:JvmName("restoreAfterRegistration")
  val restoreAfterRegistration: Boolean by remoteValue(
    key = "android.registration.restorePostRegistration",
    hotSwappable = false,
    active = false
  ) { value ->
    BuildConfig.MESSAGE_BACKUP_RESTORE_ENABLED || value.asBoolean(false)
  }

  /**
   * Percentage [0, 100] of web socket requests that will be "shadowed" by sending
   * an unauthenticated keep-alive via libsignal-net. Default: 0
   */
  @JvmStatic
  @get:JvmName("libSignalWebSocketShadowingPercentage")
  val libSignalWebSocketShadowingPercentage: Int by remoteValue(
    key = "android.libsignalWebSocketShadowingPercentage",
    hotSwappable = false
  ) { value ->
    val remote = value.asInteger(0)
    remote.coerceIn(0, 100)
  }

  @JvmStatic
  val backgroundMessageProcessInterval: Long by remoteValue(
    key = "android.messageProcessor.alarmIntervalMins",
    hotSwappable = true,
    onChangeListener = { RoutineMessageFetchReceiver.startOrUpdateAlarm(AppDependencies.application) }
  ) { value ->
    val inMinutes = value.asLong(6.hours.inWholeMinutes)
    inMinutes.minutes.inWholeMilliseconds
  }

  @JvmStatic
  val backgroundMessageProcessForegroundDelay: Int by remoteInt(
    key = "android.messageProcessor.foregroundDelayMs",
    defaultValue = 300,
    hotSwappable = true
  )

  /** Which phase we're in for the SVR3 migration  */
  val svr3MigrationPhase: Int by remoteInt(
    key = "global.svr3.phase",
    defaultValue = 0,
    hotSwappable = true,
    onChangeListener = {
      if ((it.oldValue == null || it.oldValue == 0) && it.newValue == 1) {
        Log.w(TAG, "Detected the SVR3 migration phase change to 1! Enqueuing a mirroring job.")
        AppDependencies.jobManager.add(Svr3MirrorJob())
      }
    }
  )

  /** JSON object representing some details about how we might want to warn the user around connectivity issues. */
  val connectivityWarningConfig: String by remoteString(
    key = "android.connectivityWarningConfig",
    defaultValue = "",
    hotSwappable = true
  )

  // endregion
}
