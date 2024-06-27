/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

enum class MessageBackupsScreen {
  EDUCATION,
  PIN_EDUCATION,
  PIN_CONFIRMATION,
  TYPE_SELECTION,
  CANCELLATION_DIALOG,
  CHECKOUT_SHEET,
  CREATING_IN_APP_PAYMENT,
  PROCESS_PAYMENT,
  PROCESS_CANCELLATION,
  COMPLETED;

  fun isAfter(other: MessageBackupsScreen): Boolean = ordinal > other.ordinal
}
