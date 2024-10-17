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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.jobs.BackupMessagesJob

/**
 * Bottom sheet allowing the user to immediately start a backup or delay.
 *
 * If the result key is true, then the user has enqueued a backup and should be directed to the
 * remote backup settings screen.
 */
class CreateBackupBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    const val REQUEST_KEY = "CreateBackupBottomSheet"
  }

  private var isResultSet = false

  @Composable
  override fun SheetContent() {
    CreateBackupBottomSheetContent(
      onBackupNowClick = {
        BackupMessagesJob.enqueue()
        setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to Result.BACKUP_STARTED))
        isResultSet = true
        dismissAllowingStateLoss()
      },
      onBackupLaterClick = {
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
  onBackupNowClick: () -> Unit,
  onBackupLaterClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
      .padding(horizontal = dimensionResource(R.dimen.core_ui__gutter))
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

    Text(
      text = stringResource(id = R.string.CreateBackupBottomSheet__depending_on_the_size),
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
        text = stringResource(id = R.string.CreateBackupBottomSheet__back_up_now)
      )
    }

    TextButton(
      onClick = onBackupLaterClick,
      modifier = Modifier.widthIn(min = 220.dp).padding(top = 16.dp)
    ) {
      Text(
        text = stringResource(id = R.string.CreateBackupBottomSheet__back_up_later)
      )
    }
  }
}

@SignalPreview
@Composable
private fun CreateBackupBottomSheetContentPreview() {
  Previews.BottomSheetPreview {
    CreateBackupBottomSheetContent(
      onBackupNowClick = {},
      onBackupLaterClick = {}
    )
  }
}
