package org.thoughtcrime.securesms.keyvalue;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.webrtc.CallBandwidthMode;

public final class SettingsValues extends SignalStoreValues {

  public static final String LINK_PREVIEWS          = "settings.link_previews";
  public static final String KEEP_MESSAGES_DURATION = "settings.keep_messages_duration";

  public static final String PREFER_SYSTEM_CONTACT_PHOTOS = "settings.prefer.system.contact.photos";

  private static final String SIGNAL_BACKUP_DIRECTORY        = "settings.signal.backup.directory";
  private static final String SIGNAL_LATEST_BACKUP_DIRECTORY = "settings.signal.backup.directory,latest";

  private static final String CALL_BANDWIDTH_MODE = "settings.signal.call.bandwidth.mode";

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

  public void setSignalBackupDirectory(@NonNull Uri uri) {
    putString(SIGNAL_BACKUP_DIRECTORY, uri.toString());
    putString(SIGNAL_LATEST_BACKUP_DIRECTORY, uri.toString());
  }

  public void setPreferSystemContactPhotos(boolean preferSystemContactPhotos) {
    putBoolean(PREFER_SYSTEM_CONTACT_PHOTOS, preferSystemContactPhotos);
  }

  public boolean isPreferSystemContactPhotos() {
    return getBoolean(PREFER_SYSTEM_CONTACT_PHOTOS, false);
  }

  public @Nullable Uri getSignalBackupDirectory() {
    return getUri(SIGNAL_BACKUP_DIRECTORY);
  }

  public @Nullable Uri getLatestSignalBackupDirectory() {
    return getUri(SIGNAL_LATEST_BACKUP_DIRECTORY);
  }

  public void clearSignalBackupDirectory() {
    putString(SIGNAL_BACKUP_DIRECTORY, null);
  }

  public void setCallBandwidthMode(@NonNull CallBandwidthMode callBandwidthMode) {
    putInteger(CALL_BANDWIDTH_MODE, callBandwidthMode.getCode());
  }

  public @NonNull CallBandwidthMode getCallBandwidthMode() {
    return CallBandwidthMode.fromCode(getInteger(CALL_BANDWIDTH_MODE, CallBandwidthMode.HIGH_ALWAYS.getCode()));
  }

  private @Nullable Uri getUri(@NonNull String key) {
    String uri = getString(key, "");

    if (TextUtils.isEmpty(uri)) {
      return null;
    } else {
      return Uri.parse(uri);
    }
  }
}
