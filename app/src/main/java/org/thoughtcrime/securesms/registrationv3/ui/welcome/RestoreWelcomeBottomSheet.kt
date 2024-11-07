/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.welcome

import android.content.DialogInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

/**
 * Restore flow starting bottom sheet that allows user to progress through quick restore or manual restore flows
 * from the Welcome screen.
 */
class RestoreWelcomeBottomSheet : ComposeBottomSheetDialogFragment() {

  private var result: WelcomeUserSelection = WelcomeUserSelection.CONTINUE

  companion object {
    const val REQUEST_KEY = "RestoreWelcomeBottomSheet"
  }

  @Composable
  override fun SheetContent() {
    Sheet(
      onHasOldPhone = {
        result = WelcomeUserSelection.RESTORE_WITH_OLD_PHONE
        dismissAllowingStateLoss()
      },
      onNoPhone = {
        result = WelcomeUserSelection.RESTORE_WITH_NO_PHONE
        dismissAllowingStateLoss()
      }
    )
  }

  override fun onDismiss(dialog: DialogInterface) {
    setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to result))

    super.onDismiss(dialog)
  }
}

@Composable
private fun Sheet(
  onHasOldPhone: () -> Unit = {},
  onNoPhone: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
      .padding(bottom = 54.dp)
  ) {
    BottomSheets.Handle()

    val context = LocalContext.current

    Spacer(modifier = Modifier.size(26.dp))

    RestoreActionRow(
      icon = painterResource(R.drawable.symbol_qrcode_24),
      title = stringResource(R.string.WelcomeFragment_restore_action_i_have_my_old_phone),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_scan_qr),
      onRowClick = onHasOldPhone
    )

    RestoreActionRow(
      icon = painterResource(R.drawable.symbol_no_phone_44),
      title = stringResource(R.string.WelcomeFragment_restore_action_i_dont_have_my_old_phone),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_reinstalling),
      onRowClick = onNoPhone
    )
  }
}

@Composable
@SignalPreview
private fun SheetPreview() {
  Previews.BottomSheetPreview {
    Sheet()
  }
}

@Composable
fun RestoreActionRow(
  icon: Painter,
  title: String,
  subtitle: String,
  onRowClick: () -> Unit = {}
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .horizontalGutters()
      .padding(vertical = 8.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.background)
      .clickable(enabled = true, onClick = onRowClick)
      .padding(horizontal = 24.dp, vertical = 16.dp)
  ) {
    Icon(
      painter = icon,
      tint = MaterialTheme.colorScheme.primary,
      contentDescription = null,
      modifier = Modifier.size(44.dp)
    )

    Column(
      modifier = Modifier.padding(start = 16.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge
      )

      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@SignalPreview
@Composable
private fun RestoreActionRowPreview() {
  Previews.Preview {
    RestoreActionRow(
      icon = painterResource(R.drawable.symbol_qrcode_24),
      title = stringResource(R.string.WelcomeFragment_restore_action_i_have_my_old_phone),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_scan_qr)
    )
  }
}
