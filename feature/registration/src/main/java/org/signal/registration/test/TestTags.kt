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
  const val WELCOME_GET_STARTED_BUTTON = "welcome_get_started_button"
  const val WELCOME_RESTORE_OR_TRANSFER_BUTTON = "welcome_restore_or_transfer_button"
  const val WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON = "welcome_restore_has_old_phone_button"
  const val WELCOME_RESTORE_NO_OLD_PHONE_BUTTON = "welcome_restore_no_old_phone_button"

  // Permissions Screen
  const val PERMISSIONS_NEXT_BUTTON = "permissions_next_button"
  const val PERMISSIONS_NOT_NOW_BUTTON = "permissions_not_now_button"

  // Phone Number Screen
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
}
