package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

public final class SettingsValues extends SignalStoreValues {

  public static final String LINK_PREVIEWS          = "settings.link_previews";
  public static final String KEEP_MESSAGES_DURATION = "settings.keep_messages_duration";

  public static final String THREAD_TRIM_LENGTH     = "pref_trim_length";
  public static final String THREAD_TRIM_ENABLED    = "pref_trim_threads";

  SettingsValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    getStore().beginWrite()
              .putBoolean(LINK_PREVIEWS, true)
              .apply();
  }

  public boolean isLinkPreviewsEnabled() {
    return getBoolean(LINK_PREVIEWS, false);
  }

  public void setLinkPreviewsEnabled(boolean enabled) {
    putBoolean(LINK_PREVIEWS, enabled);
  }

  public @NonNull KeepMessagesDuration getKeepMessagesDuration() {
    return KeepMessagesDuration.fromId(getInteger(KEEP_MESSAGES_DURATION, 0));
  }

  public void setKeepMessagesForDuration(@NonNull KeepMessagesDuration duration) {
    putInteger(KEEP_MESSAGES_DURATION, duration.getId());
  }

  public boolean isTrimByLengthEnabled() {
    return getBoolean(THREAD_TRIM_ENABLED, false);
  }

  public void setThreadTrimByLengthEnabled(boolean enabled) {
    putBoolean(THREAD_TRIM_ENABLED, enabled);
  }

  public int getThreadTrimLength() {
    return getInteger(THREAD_TRIM_LENGTH, 500);
  }

  public void setThreadTrimLength(int length) {
    putInteger(THREAD_TRIM_LENGTH, length);
  }

}
