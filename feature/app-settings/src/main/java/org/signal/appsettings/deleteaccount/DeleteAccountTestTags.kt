/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

import androidx.annotation.VisibleForTesting

/**
 * Tags for both delete account screens and the dialogs they share.
 */
@VisibleForTesting
object DeleteAccountTestTags {
  const val SCROLLER = "scroller"
  const val ROW_COUNTRY_PICKER = "row-country-picker"
  const val FIELD_COUNTRY_CODE = "field-country-code"
  const val FIELD_NUMBER = "field-number"
  const val BUTTON_DELETE = "button-delete"
  const val DIALOG_NUMBER_DOES_NOT_MATCH = "dialog-number-does-not-match"
  const val DIALOG_CONFIRM_DELETION = "dialog-confirm-deletion"
  const val DIALOG_DELETION_FAILED = "dialog-deletion-failed"
  const val DIALOG_LOCAL_DATA_DELETION_FAILED = "dialog-local-data-deletion-failed"
  const val DIALOG_PROGRESS = "dialog-progress"

  const val NUMBERLESS_SCROLLER = "numberless-scroller"
  const val NUMBERLESS_BUTTON_DELETE = "numberless-button-delete"
  const val NUMBERLESS_DIALOG_CONFIRM_DELETION = "numberless-dialog-confirm-deletion"
  const val NUMBERLESS_ROW_CONFIRMATION = "numberless-row-confirmation"
  const val NUMBERLESS_BUTTON_CONFIRM = "numberless-button-confirm"
}
