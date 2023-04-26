package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

public class UiHints extends SignalStoreValues {

  private static final int NEVER_DISPLAY_PULL_TO_FILTER_TIP_THRESHOLD = 3;

  private static final String HAS_SEEN_GROUP_SETTINGS_MENU_TOAST     = "uihints.has_seen_group_settings_menu_toast";
  private static final String HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE = "uihints.has_confirmed_delete_for_everyone_once";
  private static final String HAS_SET_OR_SKIPPED_USERNAME_CREATION   = "uihints.has_set_or_skipped_username_creation";
  private static final String NEVER_DISPLAY_PULL_TO_FILTER_TIP       = "uihints.never_display_pull_to_filter_tip";
  private static final String HAS_SEEN_SCHEDULED_MESSAGES_INFO_ONCE  = "uihints.has_seen_scheduled_messages_info_once";
  private static final String HAS_SEEN_USERNAME_EDUCATION            = "uihints.has_seen_username_education";
  private static final String HAS_SEEN_TEXT_FORMATTING_ALERT         = "uihints.text_formatting.has_seen_alert";

  UiHints(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    markHasSeenGroupSettingsMenuToast();
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(NEVER_DISPLAY_PULL_TO_FILTER_TIP, HAS_SEEN_USERNAME_EDUCATION, HAS_SEEN_TEXT_FORMATTING_ALERT);
  }

  public void markHasSeenGroupSettingsMenuToast() {
    putBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, true);
  }

  public boolean hasSeenGroupSettingsMenuToast() {
    return getBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, false);
  }

  public void markHasSeenScheduledMessagesInfoSheet() {
    putBoolean(HAS_SEEN_SCHEDULED_MESSAGES_INFO_ONCE, true);
  }

  public boolean hasSeenScheduledMessagesInfoSheet() {
    return getBoolean(HAS_SEEN_SCHEDULED_MESSAGES_INFO_ONCE, false);
  }

  public void markHasConfirmedDeleteForEveryoneOnce() {
    putBoolean(HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE, true);
  }

  public boolean hasConfirmedDeleteForEveryoneOnce() {
    return getBoolean(HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE, false);
  }

  public boolean hasSetOrSkippedUsernameCreation() {
    return getBoolean(HAS_SET_OR_SKIPPED_USERNAME_CREATION, false);
  }

  public void markHasSetOrSkippedUsernameCreation() {
    putBoolean(HAS_SET_OR_SKIPPED_USERNAME_CREATION, true);
  }

  public void markHasSeenUsernameEducation() {
    putBoolean(HAS_SEEN_USERNAME_EDUCATION, true);
  }

  public boolean hasSeenUsernameEducation() {
    return getBoolean(HAS_SEEN_USERNAME_EDUCATION, false);
  }

  public void clearHasSeenUsernameEducation() {
    putBoolean(HAS_SEEN_USERNAME_EDUCATION, false);
  }


  public void resetNeverDisplayPullToRefreshCount() {
    putInteger(NEVER_DISPLAY_PULL_TO_FILTER_TIP, 0);
  }

  public boolean canDisplayPullToFilterTip() {
    return getNeverDisplayPullToFilterTip() < NEVER_DISPLAY_PULL_TO_FILTER_TIP_THRESHOLD;
  }

  public void incrementNeverDisplayPullToFilterTip() {
    int inc = Math.min(NEVER_DISPLAY_PULL_TO_FILTER_TIP_THRESHOLD, getNeverDisplayPullToFilterTip() + 1);
    putInteger(NEVER_DISPLAY_PULL_TO_FILTER_TIP, inc);
  }

  private int getNeverDisplayPullToFilterTip() {
    return getInteger(NEVER_DISPLAY_PULL_TO_FILTER_TIP, 0);
  }

  public boolean hasNotSeenTextFormattingAlert() {
    return getBoolean(HAS_SEEN_TEXT_FORMATTING_ALERT, true);
  }

  public void markHasSeenTextFormattingAlert() {
    putBoolean(HAS_SEEN_TEXT_FORMATTING_ALERT, false);
  }
}
