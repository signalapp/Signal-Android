/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * Bottom sheet dialog shown on an old device after the user has decided to transfer/restore to a new device.
 */
class RestoreCompleteBottomSheetDialog : ComposeBottomSheetDialogFragment() {

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      RestoreCompleteBottomSheetDialog().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    RestoreCompleteContent(
      isAfterDeviceTransfer = SignalStore.misc.isOldDeviceTransferLocked,
      onOkayClick = this::dismissAllowingStateLoss
    )
  }
}

@Composable
private fun RestoreCompleteContent(
  isAfterDeviceTransfer: Boolean = false,
  onOkayClick: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
      .padding(bottom = 54.dp)
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.height(20.dp))

    Icon(
      painter = painterResource(R.drawable.symbol_check_circle_40),
      tint = MaterialTheme.colorScheme.primary,
      contentDescription = null,
      modifier = Modifier
        .size(64.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    val title = if (isAfterDeviceTransfer) R.string.RestoreCompleteBottomSheet_transfer_complete else R.string.RestoreCompleteBottomSheet_restore_complete
    Text(
      text = stringResource(id = title),
      style = MaterialTheme.typography.titleLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    val message = if (isAfterDeviceTransfer) R.string.RestoreCompleteBottomSheet_transfer_complete_message else R.string.RestoreCompleteBottomSheet_restore_complete_message
    Text(
      text = stringResource(id = message),
      style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    )

    Spacer(modifier = Modifier.height(54.dp))

    Buttons.LargeTonal(
      onClick = onOkayClick,
      modifier = Modifier.widthIn(min = 220.dp)
    ) {
      Text(text = stringResource(R.string.RestoreCompleteBottomSheet_button))
    }
  }
}

@SignalPreview
@Composable
private fun RestoreCompleteContentPreview() {
  Previews.Preview {
    RestoreCompleteContent(isAfterDeviceTransfer = false)
  }
}

@SignalPreview
@Composable
private fun RestoreCompleteContentAfterDeviceTransferPreview() {
  Previews.Preview {
    RestoreCompleteContent(isAfterDeviceTransfer = true)
  }
}
