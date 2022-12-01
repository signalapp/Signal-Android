package org.thoughtcrime.securesms.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.json.JSONException;
import org.json.JSONObject;
import org.signal.core.util.SetUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob;
import org.thoughtcrime.securesms.jobs.RemoteConfigRefreshJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.messageprocessingalarm.MessageProcessReceiver;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * A location for flags that can be set locally and remotely. These flags can guard features that
 * are not yet ready to be activated.
 *
 * When creating a new flag:
 * - Create a new string constant. This should almost certainly be prefixed with "android."
 * - Add a method to retrieve the value using {@link #getBoolean(String, boolean)}. You can also add
 *   other checks here, like requiring other flags.
 * - If you want to be able to change a flag remotely, place it in {@link #REMOTE_CAPABLE}.
 * - If you would like to force a value for testing, place an entry in {@link #FORCED_VALUES}.
 *   Do not commit changes to this map!
 *
 * Other interesting things you can do:
 * - Make a flag {@link #HOT_SWAPPABLE}
 * - Make a flag {@link #STICKY} -- booleans only!
 * - Register a listener for flag changes in {@link #FLAG_CHANGE_LISTENERS}
 */
public final class FeatureFlags {

  private static final String TAG = Log.tag(FeatureFlags.class);

  private static final long FETCH_INTERVAL = TimeUnit.HOURS.toMillis(2);

  private static final String PAYMENTS_KILL_SWITCH              = "android.payments.kill";
  private static final String GROUPS_V2_RECOMMENDED_LIMIT       = "global.groupsv2.maxGroupSize";
  private static final String GROUPS_V2_HARD_LIMIT              = "global.groupsv2.groupSizeHardLimit";
  private static final String GROUP_NAME_MAX_LENGTH             = "global.groupsv2.maxNameLength";
  private static final String INTERNAL_USER                     = "android.internalUser";
  private static final String VERIFY_V2                         = "android.verifyV2";
  private static final String CLIENT_EXPIRATION                 = "android.clientExpiration";
  public  static final String DONATE_MEGAPHONE                  = "android.donate.2";
  private static final String CUSTOM_VIDEO_MUXER                = "android.customVideoMuxer";
  private static final String CDS_REFRESH_INTERVAL              = "cds.syncInterval.seconds";
  private static final String AUTOMATIC_SESSION_RESET           = "android.automaticSessionReset.2";
  private static final String AUTOMATIC_SESSION_INTERVAL        = "android.automaticSessionResetInterval";
  private static final String DEFAULT_MAX_BACKOFF               = "android.defaultMaxBackoff";
  private static final String SERVER_ERROR_MAX_BACKOFF          = "android.serverErrorMaxBackoff";
  private static final String OKHTTP_AUTOMATIC_RETRY            = "android.okhttpAutomaticRetry";
  private static final String SHARE_SELECTION_LIMIT             = "android.share.limit";
  private static final String ANIMATED_STICKER_MIN_MEMORY       = "android.animatedStickerMinMemory";
  private static final String ANIMATED_STICKER_MIN_TOTAL_MEMORY = "android.animatedStickerMinTotalMemory";
  private static final String MESSAGE_PROCESSOR_ALARM_INTERVAL  = "android.messageProcessor.alarmIntervalMins";
  private static final String MESSAGE_PROCESSOR_DELAY           = "android.messageProcessor.foregroundDelayMs";
  private static final String MEDIA_QUALITY_LEVELS              = "android.mediaQuality.levels";
  private static final String RETRY_RECEIPT_LIFESPAN            = "android.retryReceiptLifespan";
  private static final String RETRY_RESPOND_MAX_AGE             = "android.retryRespondMaxAge";
  private static final String SENDER_KEY_MAX_AGE                = "android.senderKeyMaxAge";
  private static final String RETRY_RECEIPTS                    = "android.retryReceipts";
  private static final String MAX_GROUP_CALL_RING_SIZE          = "global.calling.maxGroupCallRingSize";
  private static final String GROUP_CALL_RINGING                = "android.calling.groupCallRinging.3";
  private static final String STORIES_TEXT_FUNCTIONS            = "android.stories.text.functions";
  private static final String HARDWARE_AEC_BLOCKLIST_MODELS     = "android.calling.hardwareAecBlockList";
  private static final String SOFTWARE_AEC_BLOCKLIST_MODELS     = "android.calling.softwareAecBlockList";
  private static final String USE_HARDWARE_AEC_IF_OLD           = "android.calling.useHardwareAecIfOlderThanApi29";
  private static final String USE_AEC3                          = "android.calling.useAec3";
  private static final String PAYMENTS_COUNTRY_BLOCKLIST        = "android.payments.blocklist";
  private static final String PHONE_NUMBER_PRIVACY              = "android.pnp";
  private static final String USE_FCM_FOREGROUND_SERVICE        = "android.useFcmForegroundService.3";
  private static final String STORIES_AUTO_DOWNLOAD_MAXIMUM     = "android.stories.autoDownloadMaximum";
  private static final String GIFT_BADGE_SEND_SUPPORT           = "android.giftBadges.sending.3";
  private static final String TELECOM_MANUFACTURER_ALLOWLIST    = "android.calling.telecomAllowList";
  private static final String TELECOM_MODEL_BLOCKLIST           = "android.calling.telecomModelBlockList";
  private static final String CAMERAX_MODEL_BLOCKLIST           = "android.cameraXModelBlockList";
  private static final String CAMERAX_MIXED_MODEL_BLOCKLIST     = "android.cameraXMixedModelBlockList";
  private static final String RECIPIENT_MERGE_V2                = "android.recipientMergeV2";
  private static final String SMS_EXPORTER                      = "android.sms.exporter.2";
  private static final String HIDE_CONTACTS                     = "android.hide.contacts";
  public  static final String CREDIT_CARD_PAYMENTS              = "android.credit.card.payments.3";
  private static final String PAYMENTS_REQUEST_ACTIVATE_FLOW    = "android.payments.requestActivateFlow";
  private static final String KEEP_MUTED_CHATS_ARCHIVED         = "android.keepMutedChatsArchived";
  public  static final String GOOGLE_PAY_DISABLED_REGIONS       = "global.donations.gpayDisabledRegions";
  public  static final String CREDIT_CARD_DISABLED_REGIONS      = "global.donations.ccDisabledRegions";
  public  static final String PAYPAL_DISABLED_REGIONS           = "global.donations.paypalDisabledRegions";
  private static final String CDS_HARD_LIMIT                    = "android.cds.hardLimit";
  private static final String CHAT_FILTERS                      = "android.chat.filters";
  private static final String PAYPAL_DONATIONS                  = "android.donations.paypal";

  /**
   * We will only store remote values for flags in this set. If you want a flag to be controllable
   * remotely, place it in here.
   */
  @VisibleForTesting
  static final Set<String> REMOTE_CAPABLE = SetUtil.newHashSet(
      PAYMENTS_KILL_SWITCH,
      GROUPS_V2_RECOMMENDED_LIMIT,
      GROUPS_V2_HARD_LIMIT,
      INTERNAL_USER,
      VERIFY_V2,
      CLIENT_EXPIRATION,
      DONATE_MEGAPHONE,
      CUSTOM_VIDEO_MUXER,
      CDS_REFRESH_INTERVAL,
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
      GROUP_CALL_RINGING,
      SENDER_KEY_MAX_AGE,
      STORIES_TEXT_FUNCTIONS,
      HARDWARE_AEC_BLOCKLIST_MODELS,
      SOFTWARE_AEC_BLOCKLIST_MODELS,
      USE_HARDWARE_AEC_IF_OLD,
      USE_AEC3,
      PAYMENTS_COUNTRY_BLOCKLIST,
      USE_FCM_FOREGROUND_SERVICE,
      STORIES_AUTO_DOWNLOAD_MAXIMUM,
      GIFT_BADGE_SEND_SUPPORT,
      TELECOM_MANUFACTURER_ALLOWLIST,
      TELECOM_MODEL_BLOCKLIST,
      CAMERAX_MODEL_BLOCKLIST,
      CAMERAX_MIXED_MODEL_BLOCKLIST,
      RECIPIENT_MERGE_V2,
      SMS_EXPORTER,
      HIDE_CONTACTS,
      CREDIT_CARD_PAYMENTS,
      PAYMENTS_REQUEST_ACTIVATE_FLOW,
      KEEP_MUTED_CHATS_ARCHIVED,
      GOOGLE_PAY_DISABLED_REGIONS,
      CREDIT_CARD_DISABLED_REGIONS,
      PAYPAL_DISABLED_REGIONS,
      KEEP_MUTED_CHATS_ARCHIVED,
      CDS_HARD_LIMIT,
      CHAT_FILTERS,
      PAYPAL_DONATIONS
  );

  @VisibleForTesting
  static final Set<String> NOT_REMOTE_CAPABLE = SetUtil.newHashSet(
      PHONE_NUMBER_PRIVACY
  );

  /**
   * Values in this map will take precedence over any value. This should only be used for local
   * development. Given that you specify a default when retrieving a value, and that we only store
   * remote values for things in {@link #REMOTE_CAPABLE}, there should be no need to ever *commit*
   * an addition to this map.
   */
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  @VisibleForTesting
  static final Map<String, Object> FORCED_VALUES = new HashMap<String, Object>() {{
  }};

  /**
   * By default, flags are only updated once at app start. This is to ensure that values don't
   * change within an app session, simplifying logic. However, given that this can delay how often
   * a flag is updated, you can put a flag in here to mark it as 'hot swappable'. Flags in this set
   * will be updated arbitrarily at runtime. This will make values more responsive, but also places
   * more burden on the reader to ensure that the app experience remains consistent.
   */
  @VisibleForTesting
  static final Set<String> HOT_SWAPPABLE = SetUtil.newHashSet(
      VERIFY_V2,
      CLIENT_EXPIRATION,
      CUSTOM_VIDEO_MUXER,
      CDS_REFRESH_INTERVAL,
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
      GROUP_CALL_RINGING,
      SENDER_KEY_MAX_AGE,
      DONATE_MEGAPHONE,
      HARDWARE_AEC_BLOCKLIST_MODELS,
      SOFTWARE_AEC_BLOCKLIST_MODELS,
      USE_HARDWARE_AEC_IF_OLD,
      USE_AEC3,
      PAYMENTS_COUNTRY_BLOCKLIST,
      USE_FCM_FOREGROUND_SERVICE,
      TELECOM_MANUFACTURER_ALLOWLIST,
      TELECOM_MODEL_BLOCKLIST,
      CAMERAX_MODEL_BLOCKLIST,
      RECIPIENT_MERGE_V2,
      CREDIT_CARD_PAYMENTS,
      PAYMENTS_REQUEST_ACTIVATE_FLOW,
      KEEP_MUTED_CHATS_ARCHIVED,
      CDS_HARD_LIMIT
  );

  /**
   * Flags in this set will stay true forever once they receive a true value from a remote config.
   */
  @VisibleForTesting
  static final Set<String> STICKY = SetUtil.newHashSet(
      VERIFY_V2
  );

  /**
   * Listeners that are called when the value in {@link #REMOTE_VALUES} changes. That means that
   * hot-swappable flags will have this invoked as soon as we know about that change, but otherwise
   * these will only run during initialization.
   *
   * These can be called on any thread, including the main thread, so be careful!
   *
   * Also note that this doesn't play well with {@link #FORCED_VALUES} -- changes there will not
   * trigger changes in this map, so you'll have to do some manual hacking to get yourself in the
   * desired test state.
   */
  private static final Map<String, OnFlagChange> FLAG_CHANGE_LISTENERS = new HashMap<String, OnFlagChange>() {{
    put(MESSAGE_PROCESSOR_ALARM_INTERVAL, change -> MessageProcessReceiver.startOrUpdateAlarm(ApplicationDependencies.getApplication()));
  }};

  private static final Map<String, Object> REMOTE_VALUES = new TreeMap<>();

  private FeatureFlags() {}

  public static synchronized void init() {
    Map<String, Object> current = parseStoredConfig(SignalStore.remoteConfigValues().getCurrentConfig());
    Map<String, Object> pending = parseStoredConfig(SignalStore.remoteConfigValues().getPendingConfig());
    Map<String, Change> changes = computeChanges(current, pending);

    SignalStore.remoteConfigValues().setCurrentConfig(mapToJson(pending));
    REMOTE_VALUES.putAll(pending);
    triggerFlagChangeListeners(changes);

    Log.i(TAG, "init() " + REMOTE_VALUES.toString());
  }

  public static void refreshIfNecessary() {
    long timeSinceLastFetch = System.currentTimeMillis() - SignalStore.remoteConfigValues().getLastFetchTime();

    if (timeSinceLastFetch < 0 || timeSinceLastFetch > FETCH_INTERVAL) {
      Log.i(TAG, "Scheduling remote config refresh.");
      ApplicationDependencies.getJobManager().add(new RemoteConfigRefreshJob());
    } else {
      Log.i(TAG, "Skipping remote config refresh. Refreshed " + timeSinceLastFetch + " ms ago.");
    }
  }

  @WorkerThread
  public static void refreshSync() throws IOException {
    Map<String, Object> config = ApplicationDependencies.getSignalServiceAccountManager().getRemoteConfig();
    FeatureFlags.update(config);
  }

  public static synchronized void update(@NonNull Map<String, Object> config) {
    Map<String, Object> memory  = REMOTE_VALUES;
    Map<String, Object> disk    = parseStoredConfig(SignalStore.remoteConfigValues().getPendingConfig());
    UpdateResult        result  = updateInternal(config, memory, disk, REMOTE_CAPABLE, HOT_SWAPPABLE, STICKY);

    SignalStore.remoteConfigValues().setPendingConfig(mapToJson(result.getDisk()));
    REMOTE_VALUES.clear();
    REMOTE_VALUES.putAll(result.getMemory());
    triggerFlagChangeListeners(result.getMemoryChanges());

    SignalStore.remoteConfigValues().setLastFetchTime(System.currentTimeMillis());

    Log.i(TAG, "[Memory] Before: " + memory.toString());
    Log.i(TAG, "[Memory] After : " + result.getMemory().toString());
    Log.i(TAG, "[Disk]   Before: " + disk.toString());
    Log.i(TAG, "[Disk]   After : " + result.getDisk().toString());
  }

  /** Creating usernames, sending messages by username. */
  public static synchronized boolean usernames() {
    // For now these features are paired, but leaving the separate method in case we decide to separate in the future.
    return phoneNumberPrivacy();
  }

  /**
   * Maximum number of members allowed in a group.
   */
  public static SelectionLimits groupLimits() {
    return new SelectionLimits(getInteger(GROUPS_V2_RECOMMENDED_LIMIT, 151),
                               getInteger(GROUPS_V2_HARD_LIMIT, 1001));
  }

  /** Payments Support */
  public static boolean payments() {
    return !getBoolean(PAYMENTS_KILL_SWITCH, false);
  }

  /** Internal testing extensions. */
  public static boolean internalUser() {
    return getBoolean(INTERNAL_USER, false);
  }

  /** Whether or not to use the UUID in verification codes. */
  public static boolean verifyV2() {
    return getBoolean(VERIFY_V2, false);
  }

  /** The raw client expiration JSON string. */
  public static String clientExpiration() {
    return getString(CLIENT_EXPIRATION, null);
  }

  /** The raw donate megaphone CSV string */
  public static String donateMegaphone() {
    return getString(DONATE_MEGAPHONE, "");
  }

  /**
   * Whether phone number privacy is enabled.
   * IMPORTANT: This is under active development. Enabling this *will* break your contacts in terrible, irreversible ways.
   */
  public static boolean phoneNumberPrivacy() {
    return getBoolean(PHONE_NUMBER_PRIVACY, false);
  }

  /** Whether to use the custom streaming muxer or built in android muxer. */
  public static boolean useStreamingVideoMuxer() {
    return getBoolean(CUSTOM_VIDEO_MUXER, false);
  }

  /** The time in between routine CDS refreshes, in seconds. */
  public static int cdsRefreshIntervalSeconds() {
    return getInteger(CDS_REFRESH_INTERVAL, (int) TimeUnit.HOURS.toSeconds(48));
  }

  public static @NonNull SelectionLimits shareSelectionLimit() {
    int limit = getInteger(SHARE_SELECTION_LIMIT, 5);
    return new SelectionLimits(limit, limit);
  }

  /** The maximum number of grapheme */
  public static int getMaxGroupNameGraphemeLength() {
    return Math.max(32, getInteger(GROUP_NAME_MAX_LENGTH, -1));
  }

  /** Whether or not to allow automatic session resets. */
  public static boolean automaticSessionReset() {
    return getBoolean(AUTOMATIC_SESSION_RESET, true);
  }

  /** How often we allow an automatic session reset. */
  public static int automaticSessionResetIntervalSeconds() {
    return getInteger(AUTOMATIC_SESSION_RESET, (int) TimeUnit.HOURS.toSeconds(1));
  }

  /** The default maximum backoff for jobs. */
  public static long getDefaultMaxBackoff() {
    return TimeUnit.SECONDS.toMillis(getInteger(DEFAULT_MAX_BACKOFF, 60));
  }

  /** The maximum backoff for network jobs that hit a 5xx error. */
  public static long getServerErrorMaxBackoff() {
    return TimeUnit.SECONDS.toMillis(getInteger(SERVER_ERROR_MAX_BACKOFF, (int) TimeUnit.HOURS.toSeconds(6)));
  }

  /** Whether or not to allow automatic retries from OkHttp */
  public static boolean okHttpAutomaticRetry() {
    return getBoolean(OKHTTP_AUTOMATIC_RETRY, true);
  }

  /** The minimum memory class required for rendering animated stickers in the keyboard and such */
  public static int animatedStickerMinimumMemoryClass() {
    return getInteger(ANIMATED_STICKER_MIN_MEMORY, 193);
  }

  /** The minimum total memory for rendering animated stickers in the keyboard and such */
  public static int animatedStickerMinimumTotalMemoryMb() {
    return getInteger(ANIMATED_STICKER_MIN_TOTAL_MEMORY, (int) ByteUnit.GIGABYTES.toMegabytes(3));
  }

  public static @NonNull String getMediaQualityLevels() {
    return getString(MEDIA_QUALITY_LEVELS, "");
  }

  /** Whether or not sending or responding to retry receipts is enabled. */
  public static boolean retryReceipts() {
    return getBoolean(RETRY_RECEIPTS, true);
  }

  /** How long to wait before considering a retry to be a failure. */
  public static long retryReceiptLifespan() {
    return getLong(RETRY_RECEIPT_LIFESPAN, TimeUnit.HOURS.toMillis(1));
  }

  /** How old a message is allowed to be while still resending in response to a retry receipt . */
  public static long retryRespondMaxAge() {
    return getLong(RETRY_RESPOND_MAX_AGE, TimeUnit.DAYS.toMillis(14));
  }

  /** How long a sender key can live before it needs to be rotated. */
  public static long senderKeyMaxAge() {
    return Math.min(getLong(SENDER_KEY_MAX_AGE, TimeUnit.DAYS.toMillis(14)), TimeUnit.DAYS.toMillis(90));
  }

  /** Max group size that can be use group call ringing. */
  public static long maxGroupCallRingSize() {
    return getLong(MAX_GROUP_CALL_RING_SIZE, 16);
  }

  /** Whether or not to show the group call ring toggle in the UI. */
  public static boolean groupCallRinging() {
    return getBoolean(GROUP_CALL_RINGING, false);
  }

  /** A comma-separated list of country codes where payments should be disabled. */
  public static String paymentsCountryBlocklist() {
    return getString(PAYMENTS_COUNTRY_BLOCKLIST, "98,963,53,850,7");
  }

  /**
   * Whether users can apply alignment and scale to text posts
   *
   * NOTE: This feature is still under ongoing development, do not enable.
   */
  public static boolean storiesTextFunctions() {
    return getBoolean(STORIES_TEXT_FUNCTIONS, false);
  }

  /** A comma-separated list of models that should *not* use hardware AEC for calling. */
  public static @NonNull String hardwareAecBlocklistModels() {
    return getString(HARDWARE_AEC_BLOCKLIST_MODELS, "");
  }

  /** A comma-separated list of models that should *not* use software AEC for calling. */
  public static @NonNull String softwareAecBlocklistModels() {
    return getString(SOFTWARE_AEC_BLOCKLIST_MODELS, "");
  }

  /** A comma-separated list of manufacturers that *should* use Telecom for calling. */
  public static @NonNull String telecomManufacturerAllowList() {
    return getString(TELECOM_MANUFACTURER_ALLOWLIST, "");
  }

  /** A comma-separated list of manufacturers that *should* use Telecom for calling. */
  public static @NonNull String telecomModelBlockList() {
    return getString(TELECOM_MODEL_BLOCKLIST, "");
  }

  /** A comma-separated list of manufacturers that should *not* use CameraX. */
  public static @NonNull String cameraXModelBlocklist() {
    return getString(CAMERAX_MODEL_BLOCKLIST, "");
  }

  /** A comma-separated list of manufacturers that should *not* use CameraX mixed mode. */
  public static @NonNull String cameraXMixedModelBlocklist() {
    return getString(CAMERAX_MIXED_MODEL_BLOCKLIST, "");
  }

  /** Whether or not hardware AEC should be used for calling on devices older than API 29. */
  public static boolean useHardwareAecIfOlderThanApi29() {
    return getBoolean(USE_HARDWARE_AEC_IF_OLD, false);
  }

  /** Whether or not {@link org.signal.ringrtc.CallManager.AudioProcessingMethod#ForceSoftwareAec3} can be used */
  public static boolean useAec3() {
    return getBoolean(USE_AEC3, true);
  }

  public static boolean useFcmForegroundService() {
    return getBoolean(USE_FCM_FOREGROUND_SERVICE, false);
  }

  /**
   * Prefetch count for stories from a given user.
   */
  public static int storiesAutoDownloadMaximum() {
    return getInteger(STORIES_AUTO_DOWNLOAD_MAXIMUM, 2);
  }

  /**
   * Whether or not sending Gifting Badges should be available on this client.
   */
  public static boolean giftBadgeSendSupport() {
    return getBoolean(GIFT_BADGE_SEND_SUPPORT, Environment.IS_STAGING);
  }

  /**
   * Whether or not we should enable the SMS exporter
   *
   * WARNING: This feature is under active development and is off for a reason. The exporter writes messages out to your
   * system SMS / MMS database, and hasn't been adequately tested for public use. Don't enable this. You've been warned.
   */
  public static boolean smsExporter() {
    return getBoolean(SMS_EXPORTER, false);
  }

  /**
   * Whether or not users can hide contacts.
   *
   * WARNING: This feature is intended to be enabled in tandem with other clients, as it modifies contact records.
   * Here be dragons.
   */
  public static boolean hideContacts() {
    return getBoolean(HIDE_CONTACTS, false);
  }

  /**
   * Whether or not we should allow credit card payments for donations
   */
  public static boolean creditCardPayments() {
    return getBoolean(CREDIT_CARD_PAYMENTS, Environment.IS_STAGING);
  }

  /** Whether client supports sending a request to another to activate payments */
  public static boolean paymentsRequestActivateFlow() {
    return getBoolean(PAYMENTS_REQUEST_ACTIVATE_FLOW, false);
  }

  /**
   * Whether users can enable keeping conversations with incoming messages archived if the conversation is muted.
   */
  public static boolean keepMutedChatsArchived() {
    return getBoolean(KEEP_MUTED_CHATS_ARCHIVED, false);
  }

  /**
   * @return Serialized list of regions in which Google Pay is disabled for donations
   */
  public static @NonNull String googlePayDisabledRegions() {
    return getString(GOOGLE_PAY_DISABLED_REGIONS, "*");
  }

  /**
   * @return Serialized list of regions in which credit cards are disabled for donations
   */
  public static @NonNull String creditCardDisabledRegions() {
    return getString(CREDIT_CARD_DISABLED_REGIONS, "*");
  }

  /**
   * @return Serialized list of regions in which PayPal is disabled for donations
   */
  public static @NonNull String paypalDisabledRegions() {
    return getString(PAYPAL_DISABLED_REGIONS, "*");
  }

  /**
   * If the user has more than this number of contacts, the CDS request will certainly be rejected, so we must fail.
   */
  public static int cdsHardLimit() {
    return getInteger(CDS_HARD_LIMIT, 50_000);
  }

  /**
   * Enables chat filters. Note that this UI is incomplete.
   */
  public static boolean chatFilters() {
    return getBoolean(CHAT_FILTERS, false);
  }

  /**
   * Whether or not we should allow PayPal payments for donations
   */
  public static boolean paypalDonations() {
    return getBoolean(PAYPAL_DONATIONS, Environment.IS_STAGING);
  }

  /** Only for rendering debug info. */
  public static synchronized @NonNull Map<String, Object> getMemoryValues() {
    return new TreeMap<>(REMOTE_VALUES);
  }

  /** Only for rendering debug info. */
  public static synchronized @NonNull Map<String, Object> getDiskValues() {
    return new TreeMap<>(parseStoredConfig(SignalStore.remoteConfigValues().getCurrentConfig()));
  }

  /** Only for rendering debug info. */
  public static synchronized @NonNull Map<String, Object> getPendingDiskValues() {
    return new TreeMap<>(parseStoredConfig(SignalStore.remoteConfigValues().getPendingConfig()));
  }

  /** Only for rendering debug info. */
  public static synchronized @NonNull Map<String, Object> getForcedValues() {
    return new TreeMap<>(FORCED_VALUES);
  }

  @VisibleForTesting
  static @NonNull UpdateResult updateInternal(@NonNull Map<String, Object> remote,
                                              @NonNull Map<String, Object> localMemory,
                                              @NonNull Map<String, Object> localDisk,
                                              @NonNull Set<String>         remoteCapable,
                                              @NonNull Set<String>         hotSwap,
                                              @NonNull Set<String>         sticky)
  {
    Map<String, Object> newMemory = new TreeMap<>(localMemory);
    Map<String, Object> newDisk   = new TreeMap<>(localDisk);

    Set<String> allKeys = new HashSet<>();
    allKeys.addAll(remote.keySet());
    allKeys.addAll(localDisk.keySet());
    allKeys.addAll(localMemory.keySet());

    Stream.of(allKeys)
          .filter(remoteCapable::contains)
          .forEach(key -> {
            Object remoteValue = remote.get(key);
            Object diskValue   = localDisk.get(key);
            Object newValue    = remoteValue;

            if (newValue != null && diskValue != null && newValue.getClass() != diskValue.getClass()) {
              Log.w(TAG, "Type mismatch! key: " + key);

              newDisk.remove(key);

              if (hotSwap.contains(key)) {
                newMemory.remove(key);
              }

              return;
            }

            if (sticky.contains(key) && (newValue instanceof Boolean || diskValue instanceof Boolean)) {
              newValue = diskValue == Boolean.TRUE ? Boolean.TRUE : newValue;
            } else if (sticky.contains(key)) {
              Log.w(TAG, "Tried to make a non-boolean sticky! Ignoring. (key: " + key + ")");
            }

            if (newValue != null) {
              newDisk.put(key, newValue);
            } else {
              newDisk.remove(key);
            }

            if (hotSwap.contains(key)) {
              if (newValue != null) {
                newMemory.put(key, newValue);
              } else {
                newMemory.remove(key);
              }
            }
          });

    Stream.of(allKeys)
          .filterNot(remoteCapable::contains)
          .filterNot(key -> sticky.contains(key) && localDisk.get(key) == Boolean.TRUE)
          .forEach(key -> {
            newDisk.remove(key);

            if (hotSwap.contains(key)) {
              newMemory.remove(key);
            }
          });

    return new UpdateResult(newMemory, newDisk, computeChanges(localMemory, newMemory));
  }

  @VisibleForTesting
  static @NonNull Map<String, Change> computeChanges(@NonNull Map<String, Object> oldMap, @NonNull Map<String, Object> newMap) {
    Map<String, Change> changes = new HashMap<>();
    Set<String>         allKeys = new HashSet<>();

    allKeys.addAll(oldMap.keySet());
    allKeys.addAll(newMap.keySet());

    for (String key : allKeys) {
      Object oldValue = oldMap.get(key);
      Object newValue = newMap.get(key);

      if (oldValue == null && newValue == null) {
        throw new AssertionError("Should not be possible.");
      } else if (oldValue != null && newValue == null) {
        changes.put(key, Change.REMOVED);
      } else if (newValue != oldValue && newValue instanceof Boolean) {
        changes.put(key, (boolean) newValue ? Change.ENABLED : Change.DISABLED);
      } else if (!Objects.equals(oldValue, newValue)) {
        changes.put(key, Change.CHANGED);
      }
    }

    return changes;
  }

  private static @NonNull VersionFlag getVersionFlag(@NonNull String key) {
    int versionFromKey = getInteger(key, 0);

    if (versionFromKey == 0) {
      return VersionFlag.OFF;
    }

    if (BuildConfig.CANONICAL_VERSION_CODE >= versionFromKey) {
      return VersionFlag.ON;
    } else {
      return VersionFlag.ON_IN_FUTURE_VERSION;
    }
  }

  public static long getBackgroundMessageProcessInterval() {
    int delayMinutes = getInteger(MESSAGE_PROCESSOR_ALARM_INTERVAL, (int) TimeUnit.HOURS.toMinutes(6));
    return TimeUnit.MINUTES.toMillis(delayMinutes);
  }

  /**
   * How long before a "Checking messages" foreground notification is shown to the user.
   */
  public static long getBackgroundMessageProcessForegroundDelay() {
    return getInteger(MESSAGE_PROCESSOR_DELAY, 300);
  }

  private enum VersionFlag {
    /** The flag is no set */
    OFF,

    /** The flag is set on for a version higher than the current client version */
    ON_IN_FUTURE_VERSION,

    /** The flag is set on for this version or earlier */
    ON
  }

  private static boolean getBoolean(@NonNull String key, boolean defaultValue) {
    Boolean forced = (Boolean) FORCED_VALUES.get(key);
    if (forced != null) {
      return forced;
    }

    Object remote = REMOTE_VALUES.get(key);
    if (remote instanceof Boolean) {
      return (boolean) remote;
    } else if (remote != null) {
      Log.w(TAG, "Expected a boolean for key '" + key + "', but got something else! Falling back to the default.");
    }

    return defaultValue;
  }

  private static int getInteger(@NonNull String key, int defaultValue) {
    Integer forced = (Integer) FORCED_VALUES.get(key);
    if (forced != null) {
      return forced;
    }

    Object remote = REMOTE_VALUES.get(key);
    if (remote instanceof String) {
      try {
        return Integer.parseInt((String) remote);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Expected an int for key '" + key + "', but got something else! Falling back to the default.");
      }
    }

    return defaultValue;
  }

  private static long getLong(@NonNull String key, long defaultValue) {
    Long forced = (Long) FORCED_VALUES.get(key);
    if (forced != null) {
      return forced;
    }

    Object remote = REMOTE_VALUES.get(key);
    if (remote instanceof String) {
      try {
        return Long.parseLong((String) remote);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Expected a long for key '" + key + "', but got something else! Falling back to the default.");
      }
    }

    return defaultValue;
  }

  private static String getString(@NonNull String key, String defaultValue) {
    String forced = (String) FORCED_VALUES.get(key);
    if (forced != null) {
      return forced;
    }

    Object remote = REMOTE_VALUES.get(key);
    if (remote instanceof String) {
      return (String) remote;
    }

    return defaultValue;
  }

  private static Map<String, Object> parseStoredConfig(String stored) {
    Map<String, Object> parsed = new HashMap<>();

    if (TextUtils.isEmpty(stored)) {
      Log.i(TAG, "No remote config stored. Skipping.");
      return parsed;
    }

    try {
      JSONObject       root = new JSONObject(stored);
      Iterator<String> iter = root.keys();

      while (iter.hasNext()) {
        String key = iter.next();
        parsed.put(key, root.get(key));
      }
    } catch (JSONException e) {
      throw new AssertionError("Failed to parse! Cleared storage.");
    }

    return parsed;
  }

  private static @NonNull String mapToJson(@NonNull Map<String, Object> map) {
    try {
      JSONObject json = new JSONObject();

      for (Map.Entry<String, Object> entry : map.entrySet()) {
        json.put(entry.getKey(), entry.getValue());
      }

      return json.toString();
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  private static void triggerFlagChangeListeners(Map<String, Change> changes) {
    for (Map.Entry<String, Change> change : changes.entrySet()) {
      OnFlagChange listener = FLAG_CHANGE_LISTENERS.get(change.getKey());

      if (listener != null) {
        Log.i(TAG, "Triggering change listener for: " + change.getKey());
        listener.onFlagChange(change.getValue());
      }
    }
  }

  @VisibleForTesting
  static final class UpdateResult {
    private final Map<String, Object> memory;
    private final Map<String, Object> disk;
    private final Map<String, Change> memoryChanges;

    UpdateResult(@NonNull Map<String, Object> memory, @NonNull Map<String, Object> disk, @NonNull Map<String, Change> memoryChanges) {
      this.memory        = memory;
      this.disk          = disk;
      this.memoryChanges = memoryChanges;
    }

    public @NonNull Map<String, Object> getMemory() {
      return memory;
    }

    public @NonNull Map<String, Object> getDisk() {
      return disk;
    }

    public @NonNull Map<String, Change> getMemoryChanges() {
      return memoryChanges;
    }
  }

  @VisibleForTesting
  interface OnFlagChange {
    void onFlagChange(@NonNull Change change);
  }

  enum Change {
    ENABLED, DISABLED, CHANGED, REMOVED
  }
}
