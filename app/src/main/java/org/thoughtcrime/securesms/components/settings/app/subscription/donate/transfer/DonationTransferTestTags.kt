/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer

/**
 * Test tags for the bank-transfer (SEPA) and iDEAL Compose entry forms, so instrumentation tests can
 * drive them reliably. Follows the central-object convention used by the registration module's
 * `TestTags`.
 */
object DonationTransferTestTags {
  // SEPA bank transfer mandate
  const val SEPA_MANDATE_CONTINUE_BUTTON = "sepa_mandate_continue_button"

  // SEPA bank transfer details
  const val SEPA_DETAILS_LIST = "sepa_details_list"
  const val SEPA_IBAN_FIELD = "sepa_iban_field"
  const val SEPA_NAME_FIELD = "sepa_name_field"
  const val SEPA_EMAIL_FIELD = "sepa_email_field"
  const val SEPA_DONATE_BUTTON = "sepa_donate_button"

  // iDEAL transfer details
  const val IDEAL_DETAILS_LIST = "ideal_details_list"
  const val IDEAL_NAME_FIELD = "ideal_name_field"
  const val IDEAL_EMAIL_FIELD = "ideal_email_field"
  const val IDEAL_DONATE_BUTTON = "ideal_donate_button"
}
