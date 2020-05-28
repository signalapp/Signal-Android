package org.thoughtcrime.securesms.keyvalue;

public class UiHints {

  private static final String HAS_SEEN_GROUP_SETTINGS_MENU_TOAST = "uihints.has_seen_group_settings_menu_toast";

  private final KeyValueStore store;

  UiHints(KeyValueStore store) {
    this.store = store;
  }

  void onFirstEverAppLaunch() {
    markHasSeenGroupSettingsMenuToast();
  }

  public void markHasSeenGroupSettingsMenuToast() {
    store.beginWrite().putBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, true).apply();
  }

  public boolean hasSeenGroupSettingsMenuToast() {
    return store.getBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, false);
  }
}
