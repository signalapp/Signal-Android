package org.thoughtcrime.securesms.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.RemoteConfigRefreshJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
 * - If you would like to force a value for testing, place an entry in {@link #FORCED_VALUES}. When
 *   launching a feature that is planned to be updated via a remote config, do not forget to
 *   remove the entry!
 */
public final class FeatureFlags {

  private static final String TAG = Log.tag(FeatureFlags.class);

  private static final String PREFIX         = "android.";
  private static final long   FETCH_INTERVAL = TimeUnit.HOURS.toMillis(2);

  private static final String UUIDS             = generateKey("uuids");
  private static final String PROFILE_DISPLAY   = generateKey("profileDisplay");
  private static final String MESSAGE_REQUESTS  = generateKey("messageRequests");
  private static final String USERNAMES         = generateKey("usernames");
  private static final String KBS               = generateKey("kbs");
  private static final String STORAGE_SERVICE   = generateKey("storageService");
  private static final String REACTION_SENDING  = generateKey("reactionSending");

  /**
   * Values in this map will take precedence over any value. If you do not wish to have any sort of
   * override, simply don't put a value in this map. You should never commit additions to this map
   * for flags that you plan on updating remotely.
   */
  private static final Map<String, Boolean> FORCED_VALUES = new HashMap<String, Boolean>() {{
    put(UUIDS, false);
    put(PROFILE_DISPLAY, false);
    put(MESSAGE_REQUESTS, false);
    put(USERNAMES, false);
    put(KBS, false);
    put(STORAGE_SERVICE, false);
  }};

  private static final Map<String, Boolean> REMOTE_VALUES = new HashMap<>();

  private FeatureFlags() {}

  public static void init() {
    scheduleFetchIfNecessary();
    REMOTE_VALUES.putAll(parseStoredConfig());
  }

  public static void updateDiskCache(@NonNull Map<String, Boolean> config) {
    try {
      JSONObject filtered = new JSONObject();

      for (Map.Entry<String, Boolean> entry : config.entrySet()) {
        if (entry.getKey().startsWith(PREFIX)) {
          filtered.put(entry.getKey(), (boolean) entry.getValue());
        }
      }

      SignalStore.setRemoteConfig(filtered.toString());
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  /** UUID-related stuff that shouldn't be activated until the user-facing launch. */
  public static boolean uuids() {
    return getValue(UUIDS, false);
  }

  /** Favoring profile names when displaying contacts. */
  public static boolean profileDisplay() {
    return getValue(PROFILE_DISPLAY, false);
  }

  /** MessageRequest stuff */
  public static boolean messageRequests() {
    return getValue(MESSAGE_REQUESTS, false);
  }

  /** Creating usernames, sending messages by username. Requires {@link #uuids()}. */
  public static boolean usernames() {
    boolean value = getValue(USERNAMES, false);
    if (value && !uuids()) throw new MissingFlagRequirementError();
    return value;
  }

  /** Set or migrate PIN to KBS */
  public static boolean kbs() {
    return getValue(KBS, false);
  }

  /** Storage service. Requires {@link #kbs()}. */
  public static boolean storageService() {
    boolean value = getValue(STORAGE_SERVICE, false);
    if (value && !kbs()) throw new MissingFlagRequirementError();
    return value;
  }

  /** Send support for reactions. */
  public static boolean reactionSending() {
    return getValue(REACTION_SENDING, false);
  }

  /** Only for rendering debug info. */
  public static @NonNull Map<String, Boolean> getRemoteValues() {
    return new TreeMap<>(REMOTE_VALUES);
  }

  /** Only for rendering debug info. */
  public static @NonNull Map<String, Boolean> getForcedValues() {
    return new TreeMap<>(FORCED_VALUES);
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

  private static void scheduleFetchIfNecessary() {
    long timeSinceLastFetch = System.currentTimeMillis() - SignalStore.getRemoteConfigLastFetchTime();

    if (timeSinceLastFetch > FETCH_INTERVAL) {
      Log.i(TAG, "Scheduling remote config refresh.");
      ApplicationDependencies.getJobManager().add(new RemoteConfigRefreshJob());
    } else {
      Log.i(TAG, "Skipping remote config refresh. Refreshed " + timeSinceLastFetch + " ms ago.");
    }
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

  private static final class MissingFlagRequirementError extends Error {
  }
}
