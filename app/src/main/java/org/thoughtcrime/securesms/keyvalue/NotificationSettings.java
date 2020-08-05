package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

public class NotificationSettings extends SignalStoreValues {

  public static final String MENTIONS_NOTIFY_ME = "notifications.mentions.notify_me";

  NotificationSettings(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  public boolean isMentionNotifiesMeEnabled() {
    return getBoolean(MENTIONS_NOTIFY_ME, true);
  }
}
