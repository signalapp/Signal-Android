package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

public class UiHints extends SignalStoreValues {

  private static final String HAS_SEEN_GROUP_SETTINGS_MENU_TOAST = "uihints.has_seen_group_settings_menu_toast";

  UiHints(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    markHasSeenGroupSettingsMenuToast();
  }

  public void markHasSeenGroupSettingsMenuToast() {
    putBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, true);
  }

  public boolean hasSeenGroupSettingsMenuToast() {
    return getBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, false);
  }
}
