/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.signal.appsettings.R
import org.signal.core.models.storage.KeepMessagesDuration
import org.signal.core.ui.compose.Rows
import java.text.NumberFormat

/**
 * Warns the user, at the bottom of a chat limit screen, what the setting above it will delete.
 */
@Composable
internal fun ChatLimitWarning(text: String) {
  Rows.TextRow(
    text = {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  )
}

internal val KeepMessagesDuration.labelRes: Int
  @StringRes get() = when (this) {
    KeepMessagesDuration.FOREVER -> R.string.preferences_storage__forever
    KeepMessagesDuration.ONE_YEAR -> R.string.preferences_storage__one_year
    KeepMessagesDuration.SIX_MONTHS -> R.string.preferences_storage__six_months
    KeepMessagesDuration.THIRTY_DAYS -> R.string.preferences_storage__thirty_days
  }

/** The label for a chat length [limit], which reads as "None" when there isn't one. */
@Composable
internal fun lengthLimitLabel(limit: Int): String {
  return if (limit == ChatLengthLimitSettingsState.NO_LIMIT) {
    stringResource(R.string.preferences_storage__none)
  } else {
    pluralStringResource(R.plurals.preferences_storage__s_messages_plural, limit, NumberFormat.getInstance().format(limit))
  }
}
