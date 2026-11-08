package org.signal.storage.keyvalue

import android.content.Context
import org.signal.core.util.logging.Log

class UiHintValues(store: KeyValueStore, context: Context) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(UiHintValues::class)

    private const val NEVER_DISPLAY_PULL_TO_FILTER_TIP_THRESHOLD = 3
    private const val HAS_SEEN_PINNED_MESSAGE_SHEET_THRESHOLD = 3

    private const val HAS_SEEN_GROUP_SETTINGS_MENU_TOAST = "uihints.has_seen_group_settings_menu_toast"
    private const val HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE = "uihints.has_confirmed_delete_for_everyone_once"
    private const val HAS_SET_OR_SKIPPED_USERNAME_CREATION = "uihints.has_set_or_skipped_username_creation"
    private const val NEVER_DISPLAY_PULL_TO_FILTER_TIP = "uihints.never_display_pull_to_filter_tip"
    private const val HAS_SEEN_SCHEDULED_MESSAGES_INFO_ONCE = "uihints.has_seen_scheduled_messages_info_once"
    private const val HAS_SEEN_SAFETY_NUMBER_NUX = "uihints.has_seen_safety_number_nux"
    private const val DECLINED_NOTIFICATION_LOGS_PROMPT = "uihints.declined_notification_logs"
    private const val LAST_NOTIFICATION_LOGS_PROMPT_TIME = "uihints.last_notification_logs_prompt"
    private const val DISMISSED_BATTERY_SAVER_PROMPT = "uihints.declined_battery_saver_prompt"
    private const val LAST_BATTERY_SAVER_PROMPT = "uihints.last_battery_saver_prompt"
    private const val LAST_CRASH_PROMPT = "uihints.last_crash_prompt"
    private const val HAS_COMPLETED_USERNAME_ONBOARDING = "uihints.has_completed_username_onboarding"
    private const val HAS_SEEN_DOUBLE_TAP_EDIT_EDUCATION_SHEET = "uihints.has_seen_double_tap_edit_education_sheet"
    private const val DISMISSED_CONTACTS_PERMISSION_BANNER = "uihints.dismissed_contacts_permission_banner"
    private const val LAST_SUPPORT_VERSION_SEEN = "uihints.last_support_version_seen"
    private const val HAS_EVER_ENABLED_REMOTE_BACKUPS = "uihints.has_ever_enabled_remote_backups"
    private const val HAS_SEEN_CHAT_FOLDERS_EDUCATION_SHEET = "uihints.has_seen_chat_folders_education_sheet"
    private const val HAS_SEEN_LINK_DEVICE_QR_EDUCATION_SHEET = "uihints.has_seen_link_device_qr_education_sheet"
    private const val HAS_DISMISSED_SAVE_STORAGE_WARNING = "uihints.has_dismissed_save_storage_warning"
    private const val HAS_SEEN_PINNED_MESSAGE_SHEET = "uihints.has_seen_pinned_message_sheet"
    private const val HAS_SEEN_VERIFY_AUTO_SHEET = "uihints.has_seen_verify_auto_sheet"
    private const val HAS_DISMISSED_MEMBER_LABEL_ABOUT_OVERRIDE_WARNING = "uihints.has_dismissed_member_label_about_override_warning"
    private const val HAS_SEEN_ADMIN_DELETE_EDUCATION_DIALOG = "uihints.has_seen_admin_delete_education_dialog"
    private const val DISMISSED_SET_UP_USERNAME_BANNER = "uihints.dismissed_set_up_username_banner"
    private const val HAS_PROMPTED_OPTIMIZE_DOZE = "uihints.has_prompted_optimize_doze"
    private const val TWO_FACTOR_LAST_VERIFIED_TIME = "uihints.two_factor_last_verified_time"
    private const val HAS_VERIFIED_TWO_FACTOR_BEFORE = "uihints.has_verified_two_factor_before"
    private const val HAS_SNOOZED_TWO_FACTOR_REMINDER = "uihints.has_snoozed_two_factor_reminder"
    private const val SIGNAL_LOGIN_LAST_VERIFIED_TIME = "uihints.signal_login_last_verified_time"
    private const val HAS_VERIFIED_SIGNAL_LOGIN_BEFORE = "uihints.has_verified_signal_login_before"
    private const val HAS_SNOOZED_SIGNAL_LOGIN_REMINDER = "uihints.has_snoozed_signal_login_reminder"
  }

  init {
    if (!store.containsKey(HAS_PROMPTED_OPTIMIZE_DOZE)) {
      migrateFromSharedPrefsV1(context)
    }
  }

  /** Do not alter. If you need to migrate more stuff, create a new method. */
  private fun migrateFromSharedPrefsV1(context: Context) {
    Log.i(TAG, "[V1] Migrating ui hint values from shared prefs.")

    putBoolean(HAS_PROMPTED_OPTIMIZE_DOZE, LegacySharedPrefs.getBoolean(context, "pref_prompted_optimize_doze", false))
  }

  fun hasPromptedOptimizeDoze(): Boolean {
    return getBoolean(HAS_PROMPTED_OPTIMIZE_DOZE, false)
  }

  fun setHasPromptedOptimizeDoze(value: Boolean) {
    putBoolean(HAS_PROMPTED_OPTIMIZE_DOZE, value)
  }

  public override fun onFirstEverAppLaunch() {
    markHasSeenGroupSettingsMenuToast()
  }

  override val keysToIncludeInBackup: List<String>
    get() = listOf(NEVER_DISPLAY_PULL_TO_FILTER_TIP, HAS_COMPLETED_USERNAME_ONBOARDING, HAS_EVER_ENABLED_REMOTE_BACKUPS)

  fun markHasSeenGroupSettingsMenuToast() {
    putBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, true)
  }

  fun hasSeenGroupSettingsMenuToast(): Boolean {
    return getBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, false)
  }

  fun markHasSeenScheduledMessagesInfoSheet() {
    putBoolean(HAS_SEEN_SCHEDULED_MESSAGES_INFO_ONCE, true)
  }

  fun hasSeenScheduledMessagesInfoSheet(): Boolean {
    return getBoolean(HAS_SEEN_SCHEDULED_MESSAGES_INFO_ONCE, false)
  }

  fun markHasConfirmedDeleteForEveryoneOnce() {
    putBoolean(HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE, true)
  }

  fun hasConfirmedDeleteForEveryoneOnce(): Boolean {
    return getBoolean(HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE, false)
  }

  fun markHasDismissedSetUpUsernameBanner() {
    putBoolean(DISMISSED_SET_UP_USERNAME_BANNER, true)
  }

  fun hasDismissedSetUpUsernameBanner(): Boolean {
    return getBoolean(DISMISSED_SET_UP_USERNAME_BANNER, false)
  }

  fun hasSetOrSkippedUsernameCreation(): Boolean {
    return getBoolean(HAS_SET_OR_SKIPPED_USERNAME_CREATION, false)
  }

  fun markHasSetOrSkippedUsernameCreation() {
    putBoolean(HAS_SET_OR_SKIPPED_USERNAME_CREATION, true)
  }

  fun setHasCompletedUsernameOnboarding(value: Boolean) {
    putBoolean(HAS_COMPLETED_USERNAME_ONBOARDING, value)
  }

  fun hasCompletedUsernameOnboarding(): Boolean {
    return getBoolean(HAS_COMPLETED_USERNAME_ONBOARDING, false)
  }

  fun resetNeverDisplayPullToRefreshCount() {
    putInteger(NEVER_DISPLAY_PULL_TO_FILTER_TIP, 0)
  }

  fun canDisplayPullToFilterTip(): Boolean {
    return neverDisplayPullToFilterTip < NEVER_DISPLAY_PULL_TO_FILTER_TIP_THRESHOLD
  }

  fun incrementNeverDisplayPullToFilterTip() {
    val inc = minOf(NEVER_DISPLAY_PULL_TO_FILTER_TIP_THRESHOLD, neverDisplayPullToFilterTip + 1)
    putInteger(NEVER_DISPLAY_PULL_TO_FILTER_TIP, inc)
  }

  private val neverDisplayPullToFilterTip: Int by integerValue(NEVER_DISPLAY_PULL_TO_FILTER_TIP, 0)

  fun hasSeenSafetyNumberUpdateNux(): Boolean {
    return getBoolean(HAS_SEEN_SAFETY_NUMBER_NUX, false)
  }

  fun markHasSeenSafetyNumberUpdateNux() {
    putBoolean(HAS_SEEN_SAFETY_NUMBER_NUX, true)
  }

  var lastNotificationLogsPrompt: Long by longValue(LAST_NOTIFICATION_LOGS_PROMPT_TIME, 0)

  fun markDeclinedShareNotificationLogs() {
    putBoolean(DECLINED_NOTIFICATION_LOGS_PROMPT, true)
  }

  fun hasDeclinedToShareNotificationLogs(): Boolean {
    return getBoolean(DECLINED_NOTIFICATION_LOGS_PROMPT, false)
  }

  fun markDismissedBatterySaverPrompt() {
    putBoolean(DISMISSED_BATTERY_SAVER_PROMPT, true)
  }

  fun hasDismissedBatterySaverPrompt(): Boolean {
    return getBoolean(DISMISSED_BATTERY_SAVER_PROMPT, false)
  }

  var lastBatterySaverPrompt: Long by longValue(LAST_BATTERY_SAVER_PROMPT, 0)

  var lastCrashPrompt: Long by longValue(LAST_CRASH_PROMPT, 0)

  var hasSeenDoubleTapEditEducationSheet: Boolean by booleanValue(HAS_SEEN_DOUBLE_TAP_EDIT_EDUCATION_SHEET, false)

  fun markDismissedContactsPermissionBanner() {
    putBoolean(DISMISSED_CONTACTS_PERMISSION_BANNER, true)
  }

  val dismissedContactsPermissionBanner: Boolean by booleanValue(DISMISSED_CONTACTS_PERMISSION_BANNER, false)

  /**
   * The last version of the support article for delayed notifications that users have seen. Versions are increased in a remote config.
   * Users see the article if they have device-specific notifications issues.
   */
  var lastSupportVersionSeen: Int by integerValue(LAST_SUPPORT_VERSION_SEEN, 0)

  fun markHasEverEnabledRemoteBackups() {
    putBoolean(HAS_EVER_ENABLED_REMOTE_BACKUPS, true)
  }

  val hasEverEnabledRemoteBackups: Boolean by booleanValue(HAS_EVER_ENABLED_REMOTE_BACKUPS, false)

  var hasSeenChatFoldersEducationSheet: Boolean by booleanValue(HAS_SEEN_CHAT_FOLDERS_EDUCATION_SHEET, false)

  fun markHasSeenLinkDeviceQrEducationSheet() {
    putBoolean(HAS_SEEN_LINK_DEVICE_QR_EDUCATION_SHEET, true)
  }

  fun hasSeenLinkDeviceQrEducationSheet(): Boolean {
    return getBoolean(HAS_SEEN_LINK_DEVICE_QR_EDUCATION_SHEET, false)
  }

  fun hasDismissedSaveStorageWarning(): Boolean {
    return getBoolean(HAS_DISMISSED_SAVE_STORAGE_WARNING, false)
  }

  fun markDismissedSaveStorageWarning() {
    putBoolean(HAS_DISMISSED_SAVE_STORAGE_WARNING, true)
  }

  fun shouldDisplayPinnedSheet(): Boolean {
    return seenPinnedSheetCount < HAS_SEEN_PINNED_MESSAGE_SHEET_THRESHOLD
  }

  fun incrementSeenPinnedSheetCount() {
    putInteger(HAS_SEEN_PINNED_MESSAGE_SHEET, seenPinnedSheetCount + 1)
  }

  private val seenPinnedSheetCount: Int by integerValue(HAS_SEEN_PINNED_MESSAGE_SHEET, 0)

  fun hasSeenVerifyAutomaticallySheet(): Boolean {
    return getBoolean(HAS_SEEN_VERIFY_AUTO_SHEET, false)
  }

  fun setSeenVerifyAutomaticallySheet() {
    putBoolean(HAS_SEEN_VERIFY_AUTO_SHEET, true)
  }

  fun hasDismissedMemberLabelAboutOverrideWarning(): Boolean {
    return getBoolean(HAS_DISMISSED_MEMBER_LABEL_ABOUT_OVERRIDE_WARNING, false)
  }

  fun markMemberLabelAboutOverrideWarningDismissed() {
    putBoolean(HAS_DISMISSED_MEMBER_LABEL_ABOUT_OVERRIDE_WARNING, true)
  }

  fun hasSeenAdminDeleteEducationDialog(): Boolean {
    return getBoolean(HAS_SEEN_ADMIN_DELETE_EDUCATION_DIALOG, false)
  }

  fun setHasSeenAdminDeleteEducationDialog() {
    putBoolean(HAS_SEEN_ADMIN_DELETE_EDUCATION_DIALOG, true)
  }

  /** When the user last proved they still hold a two-factor method, or when we first learned the account had one. */
  var twoFactorLastVerifiedTime: Long by longValue(TWO_FACTOR_LAST_VERIFIED_TIME, 0)

  /** Whether the user has ever completed the two-factor reminder's verification, which spaces later reminders out further. */
  fun hasVerifiedTwoFactorBefore(): Boolean {
    return getBoolean(HAS_VERIFIED_TWO_FACTOR_BEFORE, false)
  }

  fun setHasVerifiedTwoFactorBefore(verified: Boolean) {
    putBoolean(HAS_VERIFIED_TWO_FACTOR_BEFORE, verified)
  }

  /** Whether the user has snoozed the current two-factor reminder, which they only get to do once per reminder. */
  fun hasSnoozedTwoFactorReminder(): Boolean {
    return getBoolean(HAS_SNOOZED_TWO_FACTOR_REMINDER, false)
  }

  fun setHasSnoozedTwoFactorReminder(snoozed: Boolean) {
    putBoolean(HAS_SNOOZED_TWO_FACTOR_REMINDER, snoozed)
  }

  /**
   * When the user last proved they know their Signal Login, whether by logging in with it or through the reminder. Zero
   * until the app first notices the account has one.
   */
  var signalLoginLastVerifiedTime: Long by longValue(SIGNAL_LOGIN_LAST_VERIFIED_TIME, 0)

  /** Whether the user has ever completed the Signal Login reminder's verification, which spaces later reminders out further. */
  fun hasVerifiedSignalLoginBefore(): Boolean {
    return getBoolean(HAS_VERIFIED_SIGNAL_LOGIN_BEFORE, false)
  }

  fun setHasVerifiedSignalLoginBefore(verified: Boolean) {
    putBoolean(HAS_VERIFIED_SIGNAL_LOGIN_BEFORE, verified)
  }

  /** Whether the user has snoozed the current Signal Login reminder, which they only get to do once per reminder. */
  fun hasSnoozedSignalLoginReminder(): Boolean {
    return getBoolean(HAS_SNOOZED_SIGNAL_LOGIN_REMINDER, false)
  }

  fun setHasSnoozedSignalLoginReminder(snoozed: Boolean) {
    putBoolean(HAS_SNOOZED_SIGNAL_LOGIN_REMINDER, snoozed)
  }
}
