/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.R

/**
 * Shown when a group is terminated while the user is actively viewing the conversation.
 */
class TerminatedGroupBottomSheetDialog : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val ARG_ADMIN_NAME = "admin_name"

    fun show(fragmentManager: FragmentManager, adminName: String?) {
      TerminatedGroupBottomSheetDialog()
        .apply { arguments = bundleOf(ARG_ADMIN_NAME to adminName) }
        .show(fragmentManager, "terminated_group_sheet")
    }
  }

  @Composable
  override fun SheetContent() {
    TerminatedGroupSheetContent(
      adminName = requireArguments().getString(ARG_ADMIN_NAME),
      onOkClick = { dismissAllowingStateLoss() }
    )
  }
}

@Composable
private fun TerminatedGroupSheetContent(adminName: String?, onOkClick: () -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp)
  ) {
    BottomSheets.Handle()

    Text(
      text = if (adminName != null) {
        stringResource(R.string.TerminatedGroupBottomSheet__s_ended_the_group, adminName)
      } else {
        stringResource(R.string.TerminatedGroupBottomSheet__the_group_has_been_ended)
      },
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )

    Text(
      text = stringResource(R.string.TerminatedGroupBottomSheet__you_can_no_longer_send_and_receive),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 32.dp)
    )

    Buttons.LargeTonal(
      onClick = onOkClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = 24.dp)
    ) {
      Text(text = stringResource(android.R.string.ok))
    }
  }
}
