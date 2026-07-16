/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.test

/**
 * Test tags for Compose UI testing.
 */
object TestTags {

  // Welcome Screen
  const val WELCOME_SCREEN = "welcome_screen"
  const val WELCOME_HEADLINE = "welcome_headline"
  const val WELCOME_GET_STARTED_BUTTON = "welcome_get_started_button"
  const val WELCOME_LINK_DEVICE_BUTTON = "welcome_link_device_button"
  const val WELCOME_RESTORE_OR_TRANSFER_BUTTON = "welcome_restore_or_transfer_button"
  const val WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON = "welcome_restore_has_old_phone_button"
  const val WELCOME_RESTORE_NO_OLD_PHONE_BUTTON = "welcome_restore_no_old_phone_button"

  // Permissions Screen
  const val PERMISSIONS_SCREEN = "permissions_screen"
  const val PERMISSIONS_NEXT_BUTTON = "permissions_next_button"
  const val PERMISSIONS_NOT_NOW_BUTTON = "permissions_not_now_button"

  // Allow Notifications Screen
  const val ALLOW_NOTIFICATIONS_SCREEN = "allow_notifications_screen"
  const val ALLOW_NOTIFICATIONS_NEXT_BUTTON = "allow_notifications_next_button"
  const val ALLOW_NOTIFICATIONS_NOT_NOW_BUTTON = "allow_notifications_not_now_button"

  // Link Account Screen
  const val LINK_ACCOUNT_SCREEN = "link_account_screen"
  const val LINK_ACCOUNT_GET_HELP_BUTTON = "link_account_get_help_button"
  const val LINK_ACCOUNT_CREATE_ACCOUNT_LINK = "link_account_create_account_link"
  const val LINK_ACCOUNT_DISPLAY_OVERLAY_BUTTON = "link_account_display_overlay_button"
  const val LINK_ACCOUNT_HIDE_OVERLAY_BUTTON = "link_account_hide_overlay_button"

  // Message Sync Screen
  const val MESSAGE_SYNC_SCREEN = "message_sync_screen"
  const val MESSAGE_SYNC_LEARN_MORE_LINK = "message_sync_learn_more_link"
  const val MESSAGE_SYNC_CANCEL_BUTTON = "message_sync_cancel_button"

  // Phone Number Screen
  const val PHONE_NUMBER_SCREEN = "phone_number_screen"
  const val PHONE_NUMBER_COUNTRY_PICKER = "phone_number_country_picker"
  const val PHONE_NUMBER_COUNTRY_CODE_FIELD = "phone_number_country_code_field"
  const val PHONE_NUMBER_PHONE_FIELD = "phone_number_phone_field"
  const val PHONE_NUMBER_NEXT_BUTTON = "phone_number_next_button"

  // Verification Code Screen
  const val VERIFICATION_CODE_INPUT = "verification_code_input"
  const val VERIFICATION_CODE_DIGIT_0 = "verification_code_digit_0"
  const val VERIFICATION_CODE_DIGIT_1 = "verification_code_digit_1"
  const val VERIFICATION_CODE_DIGIT_2 = "verification_code_digit_2"
  const val VERIFICATION_CODE_DIGIT_3 = "verification_code_digit_3"
  const val VERIFICATION_CODE_DIGIT_4 = "verification_code_digit_4"
  const val VERIFICATION_CODE_DIGIT_5 = "verification_code_digit_5"
  const val VERIFICATION_CODE_WRONG_NUMBER_BUTTON = "verification_code_wrong_number_button"
  const val VERIFICATION_CODE_RESEND_SMS_BUTTON = "verification_code_resend_sms_button"
  const val VERIFICATION_CODE_CALL_ME_BUTTON = "verification_code_call_me_button"
  const val VERIFICATION_CODE_HAVING_TROUBLE_BUTTON = "verification_code_having_trouble_button"

  // Archive Restore Selection Screen
  const val ARCHIVE_RESTORE_SELECTION_SCREEN = "archive_restore_selection_screen"
  const val ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS = "archive_restore_selection_from_signal_backups"
  const val ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER = "archive_restore_selection_from_backup_folder"
  const val ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FILE = "archive_restore_selection_from_backup_file"
  const val ARCHIVE_RESTORE_SELECTION_DEVICE_TRANSFER = "archive_restore_selection_device_transfer"
  const val ARCHIVE_RESTORE_SELECTION_NONE = "archive_restore_selection_none"

  // Local Backup Restore Screen
  const val LOCAL_BACKUP_RESTORE_SCREEN = "local_backup_restore_screen"
  const val LOCAL_BACKUP_RESTORE_SELECT_FOLDER_BUTTON = "local_backup_restore_select_folder_button"
  const val LOCAL_BACKUP_RESTORE_BACKUP_INFO_CARD = "local_backup_restore_backup_info_card"
  const val LOCAL_BACKUP_RESTORE_RESTORE_BUTTON = "local_backup_restore_restore_button"
  const val LOCAL_BACKUP_RESTORE_PROGRESS_BAR = "local_backup_restore_progress_bar"
  const val LOCAL_BACKUP_RESTORE_CONTINUE_BUTTON = "local_backup_restore_continue_button"

  // Account Locked Screen
  const val ACCOUNT_LOCKED_SCREEN = "account_locked_screen"
  const val ACCOUNT_LOCKED_NEXT_BUTTON = "account_locked_next_button"
  const val ACCOUNT_LOCKED_LEARN_MORE_BUTTON = "account_locked_learn_more_button"

  // Enter AEP Screen
  const val ENTER_AEP_SCREEN = "enter_aep_screen"
  const val ENTER_AEP_INPUT = "enter_aep_input"
  const val ENTER_AEP_NEXT_BUTTON = "enter_aep_next_button"
  const val ENTER_AEP_NO_KEY_BUTTON = "enter_aep_no_key_button"

  // Captcha Screen
  const val CAPTCHA_SCREEN = "captcha_screen"
  const val CAPTCHA_CANCEL_BUTTON = "captcha_cancel_button"

  // Country Code Picker Screen
  const val COUNTRY_CODE_PICKER_SCREEN = "country_code_picker_screen"
  const val COUNTRY_CODE_SEARCH_FIELD = "country_code_search_field"
  const val COUNTRY_CODE_CLOSE_BUTTON = "country_code_close_button"

  // Create Profile Screen
  const val CREATE_PROFILE_SCREEN = "create_profile_screen"
  const val CREATE_PROFILE_GIVEN_NAME_FIELD = "create_profile_given_name_field"
  const val CREATE_PROFILE_FAMILY_NAME_FIELD = "create_profile_family_name_field"
  const val CREATE_PROFILE_WHO_CAN_FIND_ME_ROW = "create_profile_who_can_find_me_row"
  const val CREATE_PROFILE_NEXT_BUTTON = "create_profile_next_button"

  // Phone Number Discoverability Screen
  const val PHONE_NUMBER_DISCOVERABILITY_SCREEN = "phone_number_discoverability_screen"
  const val PHONE_NUMBER_DISCOVERABILITY_EVERYONE_OPTION = "phone_number_discoverability_everyone_option"
  const val PHONE_NUMBER_DISCOVERABILITY_NOBODY_OPTION = "phone_number_discoverability_nobody_option"
  const val PHONE_NUMBER_DISCOVERABILITY_SAVE_BUTTON = "phone_number_discoverability_save_button"
  const val PHONE_NUMBER_DISCOVERABILITY_BACK_BUTTON = "phone_number_discoverability_back_button"

  // Device Transfer Complete Screen
  const val DEVICE_TRANSFER_COMPLETE_SCREEN = "device_transfer_complete_screen"
  const val DEVICE_TRANSFER_COMPLETE_CONTINUE_BUTTON = "device_transfer_complete_continue_button"

  // Device Transfer Instructions Screen
  const val DEVICE_TRANSFER_INSTRUCTIONS_SCREEN = "device_transfer_instructions_screen"
  const val DEVICE_TRANSFER_INSTRUCTIONS_CONTINUE_BUTTON = "device_transfer_instructions_continue_button"

  // Device Transfer Progress Screen
  const val DEVICE_TRANSFER_PROGRESS_SCREEN = "device_transfer_progress_screen"
  const val DEVICE_TRANSFER_PROGRESS_CANCEL_BUTTON = "device_transfer_progress_cancel_button"
  const val DEVICE_TRANSFER_PROGRESS_TRY_AGAIN_BUTTON = "device_transfer_progress_try_again_button"

  // Device Transfer Setup Screen
  const val DEVICE_TRANSFER_SETUP_SCREEN = "device_transfer_setup_screen"
  const val DEVICE_TRANSFER_SETUP_NUMBERS_MATCH_BUTTON = "device_transfer_setup_numbers_match_button"
  const val DEVICE_TRANSFER_SETUP_NUMBERS_DO_NOT_MATCH_BUTTON = "device_transfer_setup_numbers_do_not_match_button"
  const val DEVICE_TRANSFER_SETUP_ERROR_ACTION_BUTTON = "device_transfer_setup_error_action_button"
  const val DEVICE_TRANSFER_SETUP_TROUBLESHOOTING_RETRY_BUTTON = "device_transfer_setup_troubleshooting_retry_button"

  // Enter Local Backup Passphrase Screen
  const val ENTER_LOCAL_BACKUP_PASSPHRASE_SCREEN = "enter_local_backup_passphrase_screen"
  const val ENTER_LOCAL_BACKUP_PASSPHRASE_INPUT = "enter_local_backup_passphrase_input"
  const val ENTER_LOCAL_BACKUP_PASSPHRASE_NEXT_BUTTON = "enter_local_backup_passphrase_next_button"
  const val ENTER_LOCAL_BACKUP_PASSPHRASE_NO_PASSPHRASE_BUTTON = "enter_local_backup_passphrase_no_passphrase_button"

  // Pin Creation Screen
  const val PIN_CREATION_SCREEN = "pin_creation_screen"
  const val PIN_CREATION_INPUT = "pin_creation_input"
  const val PIN_CREATION_CONFIRM_INPUT = "pin_creation_confirm_input"
  const val PIN_CREATION_NEXT_BUTTON = "pin_creation_next_button"
  const val PIN_CREATION_TOGGLE_KEYBOARD_BUTTON = "pin_creation_toggle_keyboard_button"
  const val PIN_CREATION_MENU_BUTTON = "pin_creation_menu_button"
  const val PIN_CREATION_DISABLE_PIN_MENU_ITEM = "pin_creation_disable_pin_menu_item"

  // Pin Entry Screen
  const val PIN_ENTRY_SCREEN = "pin_entry_screen"
  const val PIN_ENTRY_INPUT = "pin_entry_input"
  const val PIN_ENTRY_CONTINUE_BUTTON = "pin_entry_continue_button"
  const val PIN_ENTRY_TOGGLE_KEYBOARD_BUTTON = "pin_entry_toggle_keyboard_button"
  const val PIN_ENTRY_SKIP_BUTTON = "pin_entry_skip_button"

  // Quick Restore QR Screen
  const val QUICK_RESTORE_QR_SCREEN = "quick_restore_qr_screen"
  const val QUICK_RESTORE_QR_RETRY_BUTTON = "quick_restore_qr_retry_button"
  const val QUICK_RESTORE_QR_CANCEL_BUTTON = "quick_restore_qr_cancel_button"

  // Remote Backup Restore Screen
  const val REMOTE_BACKUP_RESTORE_SCREEN = "remote_backup_restore_screen"
  const val REMOTE_BACKUP_RESTORE_RESTORE_BUTTON = "remote_backup_restore_restore_button"
  const val REMOTE_BACKUP_RESTORE_CANCEL_BUTTON = "remote_backup_restore_cancel_button"
}
