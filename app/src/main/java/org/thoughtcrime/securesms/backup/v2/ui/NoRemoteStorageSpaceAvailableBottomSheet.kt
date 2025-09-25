/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import android.content.DialogInterface
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportDialogFragment
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.CommunicationActions

class NoRemoteStorageSpaceAvailableBottomSheet : ComposeBottomSheetDialogFragment() {
  @Composable
  override fun SheetContent() {
    val context = LocalContext.current

    NoRemoteStorageSpaceAvailableBottomSheetContent(
      onLearnMoreClick = {
        CommunicationActions.openBrowserLink(context, context.getString(R.string.backup_failed_support_url))
      },
      onContactSupportClick = {
        ContactSupportDialogFragment.create(
          subject = R.string.BackupAlertBottomSheet_network_failure_support_email,
          filter = R.string.BackupAlertBottomSheet_export_failure_filter
        ).show(parentFragmentManager, null)

        dismissAllowingStateLoss()
      },
      onOkClick = {
        dismissAllowingStateLoss()
      }
    )
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    BackupRepository.dismissOutOfRemoteStorageSpaceSheet()
  }
}

@Composable
private fun NoRemoteStorageSpaceAvailableBottomSheetContent(
  onLearnMoreClick: () -> Unit,
  onContactSupportClick: () -> Unit,
  onOkClick: () -> Unit
) {
  val primaryActionButtonLabel = stringResource(R.string.BackupAlertBottomSheet__contact_support)
  val primaryActionButtonState = remember(primaryActionButtonLabel, onContactSupportClick) {
    BackupAlertActionButtonState(
      label = primaryActionButtonLabel,
      callback = onContactSupportClick
    )
  }

  val secondaryActionButtonLabel = stringResource(android.R.string.ok)
  val secondaryActionButtonState = remember(secondaryActionButtonLabel, onOkClick) {
    BackupAlertActionButtonState(
      label = secondaryActionButtonLabel,
      callback = onOkClick
    )
  }

  BackupAlertBottomSheetContainer(
    icon = {
      BackupAlertIcon(iconColors = BackupsIconColors.Warning)
    },
    title = stringResource(R.string.BackupAlertBottomSheet__backup_failed),
    primaryActionButtonState = primaryActionButtonState,
    secondaryActionButtonState = secondaryActionButtonState
  ) {
    val text = buildAnnotatedString {
      append(stringResource(id = R.string.BackupAlertBottomSheet__an_error_occurred_and))
      append(" ")

      withLink(
        LinkAnnotation.Clickable(tag = "learn-more") {
          onLearnMoreClick()
        }
      ) {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
          append(stringResource(id = R.string.BackupAlertBottomSheet__learn_more))
        }
      }
    }

    BackupAlertText(
      text = text,
      modifier = Modifier.padding(bottom = 36.dp)
    )
  }
}

@SignalPreview
@Composable
private fun NoRemoteStorageSpaceAvailableBottomSheetContentPreview() {
  Previews.BottomSheetPreview {
    NoRemoteStorageSpaceAvailableBottomSheetContent({}, {}, {})
  }
}
