/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import android.content.DialogInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.backups.BackupStateObserver
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.signal.core.ui.R as CoreUiR

/**
 * Bottom sheet allowing the user to immediately start a backup or delay.
 *
 * If the result key is true, then the user has enqueued a backup and should be directed to the
 * remote backup settings screen.
 */
class CreateBackupBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  companion object {
    const val REQUEST_KEY = "CreateBackupBottomSheet"
  }

  private var isResultSet = false

  @Composable
  override fun SheetContent() {
    val isPaidTier: Boolean = remember { BackupStateObserver.getNonIOBackupState().isLikelyPaidTier() }

    CreateBackupBottomSheetContent(
      isPaidTier = isPaidTier,
      onBackupNowClick = {
        BackupMessagesJob.enqueue()
        setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to Result.BACKUP_STARTED))
        isResultSet = true
        dismissAllowingStateLoss()
      }
    )
  }

  enum class Result {
    BACKUP_STARTED,
    BACKUP_DELAYED
  }

  override fun onDismiss(dialog: DialogInterface) {
    if (!isResultSet) {
      setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to Result.BACKUP_DELAYED))
    }

    super.onDismiss(dialog)
  }
}

@Composable
private fun CreateBackupBottomSheetContent(
  isPaidTier: Boolean,
  onBackupNowClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
      .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
      .padding(bottom = 24.dp)
  ) {
    BottomSheets.Handle()

    Image(
      painter = painterResource(id = R.drawable.image_signal_backups),
      contentDescription = null,
      modifier = Modifier
        .padding(top = 18.dp, bottom = 11.dp)
        .size(80.dp)
        .padding(4.dp)
    )

    Text(
      text = stringResource(id = R.string.CreateBackupBottomSheet__you_are_all_set),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center
    )

    val body = if (isPaidTier) {
      stringResource(id = R.string.CreateBackupBottomSheet__depending_on_the_size)
    } else {
      stringResource(id = R.string.CreateBackupBottomSheet__free_tier)
    }

    Text(
      text = body,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .padding(top = 8.dp, bottom = 64.dp)
    )

    Buttons.LargeTonal(
      onClick = onBackupNowClick,
      modifier = Modifier.widthIn(min = 220.dp)
    ) {
      Text(
        text = stringResource(id = android.R.string.ok)
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun CreateBackupBottomSheetContentPaidPreview() {
  Previews.BottomSheetPreview {
    CreateBackupBottomSheetContent(
      isPaidTier = true,
      onBackupNowClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun CreateBackupBottomSheetContentFreePreview() {
  Previews.BottomSheetPreview {
    CreateBackupBottomSheetContent(
      isPaidTier = false,
      onBackupNowClick = {}
    )
  }
}
