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
  private static final String HAS_SEEN_TEXT_FORMATTING_ALERT         = "uihints.text_formatting.has_seen_alert";
  private static final String HAS_NOT_SEEN_EDIT_MESSAGE_BETA_ALERT   = "uihints.edit_message.has_not_seen_beta_alert";
  private static final String HAS_SEEN_SAFETY_NUMBER_NUX             = "uihints.has_seen_safety_number_nux";
  private static final String DECLINED_NOTIFICATION_LOGS_PROMPT      = "uihints.declined_notification_logs";
  private static final String LAST_NOTIFICATION_LOGS_PROMPT_TIME     = "uihints.last_notification_logs_prompt";
  private static final String DISMISSED_BATTERY_SAVER_PROMPT         = "uihints.declined_battery_saver_prompt";
  private static final String LAST_BATTERY_SAVER_PROMPT              = "uihints.last_battery_saver_prompt";
  private static final String LAST_CRASH_PROMPT                      = "uihints.last_crash_prompt";
  private static final String HAS_COMPLETED_USERNAME_ONBOARDING      = "uihints.has_completed_username_onboarding";

  UiHints(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    markHasSeenGroupSettingsMenuToast();
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(NEVER_DISPLAY_PULL_TO_FILTER_TIP, HAS_COMPLETED_USERNAME_ONBOARDING, HAS_SEEN_TEXT_FORMATTING_ALERT);
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

  public void setHasCompletedUsernameOnboarding(boolean value) {
    putBoolean(HAS_COMPLETED_USERNAME_ONBOARDING, value);
  }

  public boolean hasCompletedUsernameOnboarding() {
    return getBoolean(HAS_COMPLETED_USERNAME_ONBOARDING, false);
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

  public boolean hasNotSeenEditMessageBetaAlert() {
    return getBoolean(HAS_NOT_SEEN_EDIT_MESSAGE_BETA_ALERT, true);
  }

  public void markHasSeenEditMessageBetaAlert() {
    putBoolean(HAS_NOT_SEEN_EDIT_MESSAGE_BETA_ALERT, false);
  }

  public boolean hasSeenSafetyNumberUpdateNux() {
    return getBoolean(HAS_SEEN_SAFETY_NUMBER_NUX, false);
  }

  public void markHasSeenSafetyNumberUpdateNux() {
    putBoolean(HAS_SEEN_SAFETY_NUMBER_NUX, true);
  }

  public long getLastNotificationLogsPrompt() {
    return getLong(LAST_NOTIFICATION_LOGS_PROMPT_TIME, 0);
  }

  public void setLastNotificationLogsPrompt(long timeMs) {
    putLong(LAST_NOTIFICATION_LOGS_PROMPT_TIME, timeMs);
  }

  public void markDeclinedShareNotificationLogs() {
    putBoolean(DECLINED_NOTIFICATION_LOGS_PROMPT, true);
  }

  public boolean hasDeclinedToShareNotificationLogs() {
    return getBoolean(DECLINED_NOTIFICATION_LOGS_PROMPT, false);
  }

  public void markDismissedBatterySaverPrompt() {
    putBoolean(DISMISSED_BATTERY_SAVER_PROMPT, true);
  }

  public boolean hasDismissedBatterySaverPrompt() {
    return getBoolean(DISMISSED_BATTERY_SAVER_PROMPT, false);
  }

  public long getLastBatterySaverPrompt() {
    return getLong(LAST_BATTERY_SAVER_PROMPT, 0);
  }

  public void setLastBatterySaverPrompt(long time) {
    putLong(LAST_BATTERY_SAVER_PROMPT, time);
  }

  public void setLastCrashPrompt(long time) {
    putLong(LAST_CRASH_PROMPT, time);
  }

  public long getLastCrashPrompt() {
    return getLong(LAST_CRASH_PROMPT, 0);
  }
}
