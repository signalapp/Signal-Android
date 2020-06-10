package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.PreferenceDataStore;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.SignalUncaughtExceptionHandler;

/**
 * Simple, encrypted key-value store.
 */
public final class SignalStore {

  private static final String LAST_PREKEY_REFRESH_TIME    = "last_prekey_refresh_time";
  private static final String MESSAGE_REQUEST_ENABLE_TIME = "message_request_enable_time";

  private static final SignalStore INSTANCE = new SignalStore();

  private final KeyValueStore        store;
  private final KbsValues            kbsValues;
  private final RegistrationValues   registrationValues;
  private final PinValues            pinValues;
  private final RemoteConfigValues   remoteConfigValues;
  private final StorageServiceValues storageServiceValues;
  private final UiHints              uiHints;
  private final TooltipValues        tooltipValues;

  private SignalStore() {
    this.store                = ApplicationDependencies.getKeyValueStore();
    this.kbsValues            = new KbsValues(store);
    this.registrationValues   = new RegistrationValues(store);
    this.pinValues            = new PinValues(store);
    this.remoteConfigValues   = new RemoteConfigValues(store);
    this.storageServiceValues = new StorageServiceValues(store);
    this.uiHints              = new UiHints(store);
    this.tooltipValues        = new TooltipValues(store);
  }

  public static void onFirstEverAppLaunch() {
    registrationValues().onFirstEverAppLaunch();
    uiHints().onFirstEverAppLaunch();
    tooltips().onFirstEverAppLaunch();
  }

  public static @NonNull KbsValues kbsValues() {
    return INSTANCE.kbsValues;
  }

  public static @NonNull RegistrationValues registrationValues() {
    return INSTANCE.registrationValues;
  }

  public static @NonNull PinValues pinValues() {
    return INSTANCE.pinValues;
  }

  public static @NonNull RemoteConfigValues remoteConfigValues() {
    return INSTANCE.remoteConfigValues;
  }

  public static @NonNull StorageServiceValues storageServiceValues() {
    return INSTANCE.storageServiceValues;
  }

  public static @NonNull UiHints uiHints() {
    return INSTANCE.uiHints;
  }

  public static @NonNull TooltipValues tooltips() {
    return INSTANCE.tooltipValues;
  }

  public static @NonNull GroupsV2AuthorizationSignalStoreCache groupsV2AuthorizationCache() {
    return new GroupsV2AuthorizationSignalStoreCache(getStore());
  }

  public static long getLastPrekeyRefreshTime() {
    return getStore().getLong(LAST_PREKEY_REFRESH_TIME, 0);
  }

  public static void setLastPrekeyRefreshTime(long time) {
    putLong(LAST_PREKEY_REFRESH_TIME, time);
  }

  public static long getMessageRequestEnableTime() {
    return getStore().getLong(MESSAGE_REQUEST_ENABLE_TIME, 0);
  }

  public static void setMessageRequestEnableTime(long time) {
    putLong(MESSAGE_REQUEST_ENABLE_TIME, time);
  }

  public static @NonNull PreferenceDataStore getPreferenceDataStore() {
    return new SignalPreferenceDataStore(getStore());
  }

  /**
   * Ensures any pending writes are finished. Only intended to be called by
   * {@link SignalUncaughtExceptionHandler}.
   */
  public static void blockUntilAllWritesFinished() {
    getStore().blockUntilAllWritesFinished();
  }

  private static @NonNull KeyValueStore getStore() {
    return INSTANCE.store;
  }

  private static void putBlob(@NonNull String key, byte[] value) {
    getStore().beginWrite().putBlob(key, value).apply();
  }

  private static void putBoolean(@NonNull String key, boolean value) {
    getStore().beginWrite().putBoolean(key, value).apply();
  }

  private static void putFloat(@NonNull String key, float value) {
    getStore().beginWrite().putFloat(key, value).apply();
  }

  private static void putInteger(@NonNull String key, int value) {
    getStore().beginWrite().putInteger(key, value).apply();
  }

  private static void putLong(@NonNull String key, long value) {
    getStore().beginWrite().putLong(key, value).apply();
  }

  private static void putString(@NonNull String key, String value) {
    getStore().beginWrite().putString(key, value).apply();
  }
}
