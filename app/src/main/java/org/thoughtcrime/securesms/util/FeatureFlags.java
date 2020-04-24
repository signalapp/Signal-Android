package org.thoughtcrime.securesms.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Stream;
import com.google.android.collect.Sets;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.RemoteConfigRefreshJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * A location for flags that can be set locally and remotely. These flags can guard features that
 * are not yet ready to be activated.
 *
 * When creating a new flag:
 * - Create a new string constant. This should almost certainly be prefixed with "android."
 * - Add a method to retrieve the value using {@link #getValue(String, boolean)}. You can also add
 *   other checks here, like requiring other flags.
 * - If you want to be able to change a flag remotely, place it in {@link #REMOTE_CAPABLE}.
 * - If you would like to force a value for testing, place an entry in {@link #FORCED_VALUES}.
 *   Do not commit changes to this map!
 *
 * Other interesting things you can do:
 * - Make a flag {@link #HOT_SWAPPABLE}
 * - Make a flag {@link #STICKY}
 * - Register a listener for flag changes in  {@link #FLAG_CHANGE_LISTENERS}
 */
public final class FeatureFlags {

  private static final String TAG = Log.tag(FeatureFlags.class);

  private static final long FETCH_INTERVAL = TimeUnit.HOURS.toMillis(2);

  private static final String UUIDS                      = "android.uuids";
  private static final String MESSAGE_REQUESTS           = "android.messageRequests";
  private static final String USERNAMES                  = "android.usernames";
  private static final String PINS_FOR_ALL_LEGACY        = "android.pinsForAll";
  private static final String PINS_FOR_ALL               = "android.pinsForAll.2";
  private static final String PINS_FOR_ALL_MANDATORY     = "android.pinsForAllMandatory";
  private static final String PINS_MEGAPHONE_KILL_SWITCH = "android.pinsMegaphoneKillSwitch";
  private static final String PROFILE_NAMES_MEGAPHONE    = "android.profileNamesMegaphone";
  private static final String ATTACHMENTS_V3             = "android.attachmentsV3";
  private static final String REMOTE_DELETE              = "android.remoteDelete";

  /**
   * We will only store remote values for flags in this set. If you want a flag to be controllable
   * remotely, place it in here.
   */

  private static final Set<String> REMOTE_CAPABLE = Sets.newHashSet(
      PINS_FOR_ALL_LEGACY,
      PINS_FOR_ALL,
      PINS_FOR_ALL_MANDATORY,
      PINS_MEGAPHONE_KILL_SWITCH,
      PROFILE_NAMES_MEGAPHONE,
      MESSAGE_REQUESTS,
      ATTACHMENTS_V3,
      REMOTE_DELETE
  );

  /**
   * Values in this map will take precedence over any value. This should only be used for local
   * development. Given that you specify a default when retrieving a value, and that we only store
   * remote values for things in {@link #REMOTE_CAPABLE}, there should be no need to ever *commit*
   * an addition to this map.
   */
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final Map<String, Boolean> FORCED_VALUES = new HashMap<String, Boolean>() {{
  }};

  /**
   * By default, flags are only updated once at app start. This is to ensure that values don't
   * change within an app session, simplifying logic. However, given that this can delay how often
   * a flag is updated, you can put a flag in here to mark it as 'hot swappable'. Flags in this set
   * will be updated arbitrarily at runtime. This will make values more responsive, but also places
   * more burden on the reader to ensure that the app experience remains consistent.
   */
  private static final Set<String> HOT_SWAPPABLE = Sets.newHashSet(
      PINS_MEGAPHONE_KILL_SWITCH,
      ATTACHMENTS_V3
  );

  /**
   * Flags in this set will stay true forever once they receive a true value from a remote config.
   */
  private static final Set<String> STICKY = Sets.newHashSet(
      PINS_FOR_ALL_LEGACY,
      PINS_FOR_ALL
  );

  /**
   * Listeners that are called when the value in {@link #REMOTE_VALUES} changes. That means that
   * hot-swappable flags will have this invoked as soon as we know about that change, but otherwise
   * these will only run during initialization.
   *
   * These can be called on any thread, including the main thread, so be careful!
   *
   * Also note that this doesn't play well with {@link #FORCED_VALUES} -- changes there will not
   * trigger changes in this map, so you'll have to do some manually hacking to get yourself in the
   * desired test state.
   */
  private static final Map<String, OnFlagChange> FLAG_CHANGE_LISTENERS = new HashMap<String, OnFlagChange>() {{
    put(MESSAGE_REQUESTS, (change) -> SignalStore.setMessageRequestEnableTime(change == Change.ENABLED ? System.currentTimeMillis() : 0));
  }};

  private static final Map<String, Boolean> REMOTE_VALUES = new TreeMap<>();

  private FeatureFlags() {}

  public static synchronized void init() {
    Map<String, Boolean> current = parseStoredConfig(SignalStore.remoteConfigValues().getCurrentConfig());
    Map<String, Boolean> pending = parseStoredConfig(SignalStore.remoteConfigValues().getPendingConfig());
    Map<String, Change>  changes = computeChanges(current, pending);

    SignalStore.remoteConfigValues().setCurrentConfig(mapToJson(pending));
    REMOTE_VALUES.putAll(pending);
    triggerFlagChangeListeners(changes);

    Log.i(TAG, "init() " + REMOTE_VALUES.toString());
  }

  public static synchronized void refreshIfNecessary() {
    long timeSinceLastFetch = System.currentTimeMillis() - SignalStore.remoteConfigValues().getLastFetchTime();

    if (timeSinceLastFetch > FETCH_INTERVAL) {
      Log.i(TAG, "Scheduling remote config refresh.");
      ApplicationDependencies.getJobManager().add(new RemoteConfigRefreshJob());
    } else {
      Log.i(TAG, "Skipping remote config refresh. Refreshed " + timeSinceLastFetch + " ms ago.");
    }
  }

  public static synchronized void update(@NonNull Map<String, Boolean> config) {
    Map<String, Boolean> memory  = REMOTE_VALUES;
    Map<String, Boolean> disk    = parseStoredConfig(SignalStore.remoteConfigValues().getPendingConfig());
    UpdateResult         result  = updateInternal(config, memory, disk, REMOTE_CAPABLE, HOT_SWAPPABLE, STICKY);

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

  /** UUID-related stuff that shouldn't be activated until the user-facing launch. */
  public static synchronized boolean uuids() {
    return getValue(UUIDS, false);
  }

  /** Favoring profile names when displaying contacts. */
  public static synchronized boolean profileDisplay() {
    return messageRequests();
  }

  /** MessageRequest stuff */
  public static synchronized boolean messageRequests() {
    return getValue(MESSAGE_REQUESTS, false);
  }

  /** Creating usernames, sending messages by username. Requires {@link #uuids()}. */
  public static synchronized boolean usernames() {
    boolean value = getValue(USERNAMES, false);
    if (value && !uuids()) throw new MissingFlagRequirementError();
    return value;
  }

  /**
   * - Starts showing prompts for users to create PINs.
   * - Shows new reminder UI.
   * - Shows new settings UI.
   * - Syncs to storage service.
   */
  public static boolean pinsForAll() {
    return SignalStore.registrationValues().pinWasRequiredAtRegistration() ||
           SignalStore.kbsValues().isV2RegistrationLockEnabled()           ||
           SignalStore.kbsValues().hasPin()                                ||
           pinsForAllMandatory()                                           ||
           getValue(PINS_FOR_ALL_LEGACY, false)                            ||
           getValue(PINS_FOR_ALL, false);
  }

  /** Makes it so the user will eventually see a fullscreen splash requiring them to create a PIN. */
  public static boolean pinsForAllMandatory() {
    return getValue(PINS_FOR_ALL_MANDATORY, false);
  }

  /** Safety flag to disable Pins for All Megaphone */
  public static boolean pinsForAllMegaphoneKillSwitch() {
    return getValue(PINS_MEGAPHONE_KILL_SWITCH, false);
  }

  /** Safety switch for disabling profile names megaphone */
  public static boolean profileNamesMegaphone() {
    return getValue(PROFILE_NAMES_MEGAPHONE, false) &&
           TextSecurePreferences.getFirstInstallVersion(ApplicationDependencies.getApplication()) < 600;
  }

  /** Whether or not we use the attachments v3 form. */
  public static boolean attachmentsV3() {
    return getValue(ATTACHMENTS_V3, false);
  }

  /** Send support for remotely deleting a message. */
  public static boolean remoteDelete() {
    return getValue(REMOTE_DELETE, false);
  }

  /** Only for rendering debug info. */
  public static synchronized @NonNull Map<String, Boolean> getMemoryValues() {
    return new TreeMap<>(REMOTE_VALUES);
  }

  /** Only for rendering debug info. */
  public static synchronized @NonNull Map<String, Boolean> getDiskValues() {
    return new TreeMap<>(parseStoredConfig(SignalStore.remoteConfigValues().getCurrentConfig()));
  }

  /** Only for rendering debug info. */
  public static synchronized @NonNull Map<String, Boolean> getForcedValues() {
    return new TreeMap<>(FORCED_VALUES);
  }

  @VisibleForTesting
  static @NonNull UpdateResult updateInternal(@NonNull Map<String, Boolean> remote,
                                              @NonNull Map<String, Boolean> localMemory,
                                              @NonNull Map<String, Boolean> localDisk,
                                              @NonNull Set<String>          remoteCapable,
                                              @NonNull Set<String>          hotSwap,
                                              @NonNull Set<String>          sticky)
  {
    Map<String, Boolean> newMemory = new TreeMap<>(localMemory);
    Map<String, Boolean> newDisk   = new TreeMap<>(localDisk);

    Set<String> allKeys = new HashSet<>();
    allKeys.addAll(remote.keySet());
    allKeys.addAll(localDisk.keySet());
    allKeys.addAll(localMemory.keySet());

    Stream.of(allKeys)
          .filter(remoteCapable::contains)
          .forEach(key -> {
            Boolean remoteValue = remote.get(key);
            Boolean diskValue   = localDisk.get(key);
            Boolean newValue    = remoteValue;

            if (sticky.contains(key)) {
              newValue = diskValue == Boolean.TRUE ? Boolean.TRUE : newValue;
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
  static @NonNull Map<String, Change> computeChanges(@NonNull Map<String, Boolean> oldMap, @NonNull Map<String, Boolean> newMap) {
    Map<String, Change> changes = new HashMap<>();
    Set<String>         allKeys = new HashSet<>();

    allKeys.addAll(oldMap.keySet());
    allKeys.addAll(newMap.keySet());

    for (String key : allKeys) {
      Boolean oldValue = oldMap.get(key);
      Boolean newValue = newMap.get(key);

      if (oldValue == null && newValue == null) {
        throw new AssertionError("Should not be possible.");
      } else if (oldValue != null && newValue == null) {
        changes.put(key, Change.REMOVED);
      } else if (newValue != oldValue) {
        changes.put(key, newValue ? Change.ENABLED : Change.DISABLED);
      }
    }

    return changes;
  }

  private static boolean getValue(@NonNull String key, boolean defaultValue) {
    Boolean forced = FORCED_VALUES.get(key);
    if (forced != null) {
      return forced;
    }

    Boolean remote = REMOTE_VALUES.get(key);
    if (remote != null) {
      return remote;
    }

    return defaultValue;
  }

  private static Map<String, Boolean> parseStoredConfig(String stored) {
    Map<String, Boolean> parsed = new HashMap<>();

    if (TextUtils.isEmpty(stored)) {
      Log.i(TAG, "No remote config stored. Skipping.");
      return parsed;
    }

    try {
      JSONObject       root = new JSONObject(stored);
      Iterator<String> iter = root.keys();

      while (iter.hasNext()) {
        String key = iter.next();
        parsed.put(key, root.getBoolean(key));
      }
    } catch (JSONException e) {
      throw new AssertionError("Failed to parse! Cleared storage.");
    }

    return parsed;
  }

  private static @NonNull String mapToJson(@NonNull Map<String, Boolean> map) {
    try {
      JSONObject json = new JSONObject();

      for (Map.Entry<String, Boolean> entry : map.entrySet()) {
        json.put(entry.getKey(), (boolean) entry.getValue());
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

  private static final class MissingFlagRequirementError extends Error {
  }

  @VisibleForTesting
  static final class UpdateResult {
    private final Map<String, Boolean> memory;
    private final Map<String, Boolean> disk;
    private final Map<String, Change> memoryChanges;

    UpdateResult(@NonNull Map<String, Boolean> memory, @NonNull Map<String, Boolean> disk, @NonNull Map<String, Change> memoryChanges) {
      this.memory        = memory;
      this.disk          = disk;
      this.memoryChanges = memoryChanges;
    }

    public @NonNull Map<String, Boolean> getMemory() {
      return memory;
    }

    public @NonNull Map<String, Boolean> getDisk() {
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
    ENABLED, DISABLED, REMOVED
  }

  /** Read and write versioned profile information. */
  public static final boolean VERSIONED_PROFILES = org.whispersystems.signalservice.FeatureFlags.VERSIONED_PROFILES;
}
