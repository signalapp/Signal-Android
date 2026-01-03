/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R

enum class BackupFrequencyV1(val days: Int) {
  DAILY(1),
  WEEKLY(7),
  MONTHLY(30),
  QUARTERLY(90),
  NEVER(999);

  @StringRes
  fun getResourceId(): Int {
    return when (this) {
      DAILY -> R.string.BackupsPreferenceFragment__frequency_label_daily
      WEEKLY -> R.string.BackupsPreferenceFragment__frequency_label_weekly
      MONTHLY -> R.string.BackupsPreferenceFragment__frequency_label_monthly
      QUARTERLY -> R.string.BackupsPreferenceFragment__frequency_label_quarterly
      NEVER -> R.string.BackupsPreferenceFragment__frequency_label_never
    }
  }
}
