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
 * - Create a new string constant using {@link #generateKey(String)})
 * - Add a method to retrieve the value using {@link #getValue(String, boolean)}. You can also add
 *   other checks here, like requiring other flags.
 * - If you want to be able to change a flag remotely, place it in {@link #REMOTE_CAPABLE}.
 * - If you would like to force a value for testing, place an entry in {@link #FORCED_VALUES}.
 *   Do not commit changes to this map!
 *
 * Other interesting things you can do:
 * - Make a flag {@link #HOT_SWAPPABLE}
 * - Make a flag {@link #STICKY}
 */
public final class FeatureFlags {

  private static final String TAG = Log.tag(FeatureFlags.class);

  private static final String PREFIX         = "android.";
  private static final long   FETCH_INTERVAL = TimeUnit.HOURS.toMillis(2);

  private static final String UUIDS                      = generateKey("uuids");
  private static final String PROFILE_DISPLAY            = generateKey("profileDisplay");
  private static final String MESSAGE_REQUESTS           = generateKey("messageRequests");
  private static final String USERNAMES                  = generateKey("usernames");
  private static final String STORAGE_SERVICE            = generateKey("storageService");
  private static final String PINS_FOR_ALL               = generateKey("pinsForAll");
  private static final String PINS_MEGAPHONE_KILL_SWITCH = generateKey("pinsMegaphoneKillSwitch");

  /**
   * We will only store remote values for flags in this set. If you want a flag to be controllable
   * remotely, place it in here.
   */
  private static final Set<String> REMOTE_CAPABLE = Sets.newHashSet(
      PINS_FOR_ALL,
      PINS_MEGAPHONE_KILL_SWITCH
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
    PINS_MEGAPHONE_KILL_SWITCH
  );

  /**
   * Flags in this set will stay true forever once they receive a true value from a remote config.
   */
  private static final Set<String> STICKY = Sets.newHashSet(
    PINS_FOR_ALL
  );

  private static final Map<String, Boolean> REMOTE_VALUES = new TreeMap<>();

  private FeatureFlags() {}

  public static synchronized void init() {
    REMOTE_VALUES.putAll(parseStoredConfig());
    Log.i(TAG, "init() " + REMOTE_VALUES.toString());
  }

  public static synchronized void refresh() {
    long timeSinceLastFetch = System.currentTimeMillis() - SignalStore.getRemoteConfigLastFetchTime();

    if (timeSinceLastFetch > FETCH_INTERVAL) {
      Log.i(TAG, "Scheduling remote config refresh.");
      ApplicationDependencies.getJobManager().add(new RemoteConfigRefreshJob());
    } else {
      Log.i(TAG, "Skipping remote config refresh. Refreshed " + timeSinceLastFetch + " ms ago.");
    }
  }

  public static synchronized void update(@NonNull Map<String, Boolean> config) {
    Map<String, Boolean> memory = REMOTE_VALUES;
    Map<String, Boolean> disk   = parseStoredConfig();
    UpdateResult         result = updateInternal(config, memory, disk, REMOTE_CAPABLE, HOT_SWAPPABLE, STICKY);

    SignalStore.setRemoteConfig(mapToJson(result.getDisk()).toString());
    REMOTE_VALUES.clear();
    REMOTE_VALUES.putAll(result.getMemory());

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
    return getValue(PROFILE_DISPLAY, false);
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

  /** Storage service. */
  public static boolean storageService() {
    return getValue(STORAGE_SERVICE, false);
  }

  /** Enables new KBS UI and notices but does not require user to set a pin */
  public static boolean pinsForAll() {
    return SignalStore.registrationValues().pinWasRequiredAtRegistration() ||
           SignalStore.kbsValues().hasMigratedToPinsForAll()               ||
           getValue(PINS_FOR_ALL, false);
  }

  /** Safety flag to disable Pins for All Megaphone */
  public static boolean pinsForAllMegaphoneKillSwitch() {
    return getValue(PINS_MEGAPHONE_KILL_SWITCH, false);
  }

  /** Only for rendering debug info. */
  public static synchronized @NonNull Map<String, Boolean> getMemoryValues() {
    return new TreeMap<>(REMOTE_VALUES);
  }

  /** Only for rendering debug info. */
  public static synchronized @NonNull Map<String, Boolean> getDiskValues() {
    return new TreeMap<>(parseStoredConfig());
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

    return new UpdateResult(newMemory, newDisk);
  }

  private static @NonNull String generateKey(@NonNull String key) {
    return PREFIX + key;
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

  private static Map<String, Boolean> parseStoredConfig() {
    Map<String, Boolean> parsed = new HashMap<>();
    String               stored = SignalStore.getRemoteConfig();

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
      SignalStore.setRemoteConfig(null);
      throw new AssertionError("Failed to parse! Cleared storage.");
    }

    return parsed;
  }

  private static JSONObject mapToJson(@NonNull Map<String, Boolean> map) {
    try {
      JSONObject json = new JSONObject();

      for (Map.Entry<String, Boolean> entry : map.entrySet()) {
        json.put(entry.getKey(), (boolean) entry.getValue());
      }

      return json;
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  private static final class MissingFlagRequirementError extends Error {
  }

  @VisibleForTesting
  static final class UpdateResult {
    private final Map<String, Boolean> memory;
    private final Map<String, Boolean> disk;

    UpdateResult(@NonNull Map<String, Boolean> memory, @NonNull Map<String, Boolean> disk) {
      this.memory = memory;
      this.disk   = disk;
    }

    public @NonNull Map<String, Boolean> getMemory() {
      return memory;
    }

    public @NonNull Map<String, Boolean> getDisk() {
      return disk;
    }
  }
}
