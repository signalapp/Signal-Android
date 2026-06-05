/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import kotlin.math.ceil

/**
 * Sheet shown when the user attempts to change their phone number before the
 * post-registration waiting period has elapsed.
 */
class ChangeNumberPostRegistrationWaitSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val ARG_REMAINING_SECONDS = "arg.remaining_seconds"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, remainingSeconds: Long) {
      ChangeNumberPostRegistrationWaitSheet().apply {
        arguments = Bundle().apply {
          putLong(ARG_REMAINING_SECONDS, remainingSeconds)
        }
      }.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  private val remainingSeconds: Long
    get() = requireArguments().getLong(ARG_REMAINING_SECONDS)

  @Composable
  override fun SheetContent() {
    SheetContent(
      remainingSeconds = remainingSeconds,
      onDismiss = { dismissAllowingStateLoss() }
    )
  }
}

@Composable
private fun SheetContent(
  remainingSeconds: Long,
  onDismiss: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters(gutterSize = 36.dp)
  ) {
    BottomSheets.Handle()

    Image(
      painter = painterResource(R.drawable.change_number_error),
      contentDescription = null,
      modifier = Modifier
        .padding(top = 26.dp)
    )

    Text(
      text = stringResource(R.string.ChangeNumberPostRegistrationWaitSheet__title),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier = Modifier.padding(top = 16.dp)
    )

    Text(
      text = stringResource(R.string.ChangeNumberPostRegistrationWaitSheet__body),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 12.dp)
    )

    Text(
      text = formatTryAgainIn(remainingSeconds),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 16.dp)
    )

    Buttons.LargeTonal(
      onClick = onDismiss,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 32.dp, bottom = 24.dp, start = 12.dp, end = 12.dp)
    ) {
      Text(stringResource(R.string.ChangeNumberPostRegistrationWaitSheet__ok))
    }
  }
}

@DayNightPreviews
@Composable
private fun SheetContentMinutesPreview() {
  Previews.BottomSheetContentPreview {
    SheetContent(
      remainingSeconds = 25 * 60,
      onDismiss = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun SheetContentHoursPreview() {
  Previews.BottomSheetContentPreview {
    SheetContent(
      remainingSeconds = 2 * 60 * 60,
      onDismiss = {}
    )
  }
}

@Composable
private fun formatTryAgainIn(remainingSeconds: Long): String {
  val minutes = ceil(remainingSeconds / 60.0).toInt().coerceAtLeast(1)
  return if (minutes >= 60) {
    val hours = ceil(minutes / 60.0).toInt()
    pluralStringResource(R.plurals.ChangeNumberPostRegistrationWaitSheet__try_again_in_hours, hours, hours)
  } else {
    pluralStringResource(R.plurals.ChangeNumberPostRegistrationWaitSheet__try_again_in_minutes, minutes, minutes)
  }
}
