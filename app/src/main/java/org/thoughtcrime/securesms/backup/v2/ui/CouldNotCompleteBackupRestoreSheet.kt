/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Sheet displayed when the user's backup restoration failed during media import. Generally due
 * to the files no longer being available.
 */
class CouldNotCompleteBackupRestoreSheet : ComposeBottomSheetDialogFragment() {
  @Composable
  override fun SheetContent() {
    CouldNotCompleteBackupRestoreSheetContent(
      onOkClick = { dismiss() },
      onLearnMoreClick = {
        dismiss()
        CommunicationActions.openBrowserLink(requireContext(), getString(R.string.backup_support_url))
      }
    )
  }
}

@Composable
private fun CouldNotCompleteBackupRestoreSheetContent(
  onOkClick: () -> Unit = {},
  onLearnMoreClick: () -> Unit = {}
) {
  val ok = stringResource(android.R.string.ok)
  val primaryActionButtonState: BackupAlertActionButtonState = remember(ok, onOkClick) {
    BackupAlertActionButtonState(
      label = ok,
      callback = onOkClick
    )
  }

  val learnMore = stringResource(R.string.preferences__app_icon_learn_more)
  val secondaryActionButtonState: BackupAlertActionButtonState = remember(learnMore, onLearnMoreClick) {
    BackupAlertActionButtonState(
      label = learnMore,
      callback = onLearnMoreClick
    )
  }

  BackupAlertBottomSheetContainer(
    icon = {
      BackupAlertIcon(iconColors = BackupsIconColors.Error)
    },
    title = stringResource(R.string.CouldNotCompleteBackupRestoreSheet__title),
    primaryActionButtonState = primaryActionButtonState,
    secondaryActionButtonState = secondaryActionButtonState
  ) {
    Text(
      text = stringResource(R.string.CouldNotCompleteBackupRestoreSheet__body_error)
    )

    Text(
      text = stringResource(R.string.CouldNotCompleteBackupRestoreSheet__body_retry)
    )
  }
}

@DayNightPreviews
@Composable
private fun CouldNotCompleteBackupRestoreSheetContentPreview() {
  Previews.BottomSheetContentPreview {
    CouldNotCompleteBackupRestoreSheetContent()
  }
}
