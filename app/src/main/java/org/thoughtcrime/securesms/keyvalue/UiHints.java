package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

public class UiHints extends SignalStoreValues {

  private static final String HAS_SEEN_GROUP_SETTINGS_MENU_TOAST     = "uihints.has_seen_group_settings_menu_toast";
  private static final String HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE = "uihints.has_confirmed_delete_for_everyone_once";

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

  public void markHasConfirmedDeleteForEveryoneOnce() {
    putBoolean(HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE, true);
  }

  public boolean hasConfirmedDeleteForEveryoneOnce() {
    return getBoolean(HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE, false);
  }
}
