/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R

private val BUTTON_MAX_WIDTH = 220.dp

/**
 * Explains what resetting the recovery key entails before the user commits to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetRecoveryKeyBottomSheet(
  onContinueClick: () -> Unit,
  onDismissRequest: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ) {
    ResetRecoveryKeySheetContent(
      onContinueClick = onContinueClick,
      onCancelClick = onDismissRequest
    )
  }
}

@Composable
private fun ResetRecoveryKeySheetContent(
  onContinueClick: () -> Unit = {},
  onCancelClick: () -> Unit = {}
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Image(
      imageVector = ImageVector.vectorResource(R.drawable.image_signal_backups_key),
      contentDescription = null,
      modifier = Modifier
        .padding(top = 38.dp, bottom = 18.dp)
        .size(80.dp)
    )

    Text(
      text = stringResource(R.string.ResetRecoveryKeyBottomSheet__reset_your_recovery_key),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .padding(bottom = 12.dp)
        .horizontalGutters()
    )

    Column(
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(bottom = 48.dp, start = 36.dp, end = 36.dp)
    ) {
      Text(
        text = stringResource(R.string.ResetRecoveryKeyBottomSheet__resetting_your_recovery_key_will_create_a_new_key),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Text(
        text = stringResource(R.string.ResetRecoveryKeyBottomSheet__if_backups_are_enabled_you_will_have_to_re_upload),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }

    Buttons.LargeTonal(
      onClick = onContinueClick,
      modifier = Modifier
        .padding(bottom = 16.dp)
        .fillMaxWidth()
        .requiredWidthIn(min = Dp.Unspecified, max = BUTTON_MAX_WIDTH)
    ) {
      Text(text = stringResource(R.string.ResetRecoveryKeyBottomSheet__continue))
    }

    TextButton(
      onClick = onCancelClick,
      modifier = Modifier
        .padding(bottom = 48.dp)
        .fillMaxWidth()
        .requiredWidthIn(min = Dp.Unspecified, max = BUTTON_MAX_WIDTH)
    ) {
      Text(text = stringResource(android.R.string.cancel))
    }
  }
}

@DayNightPreviews
@Composable
private fun ResetRecoveryKeySheetContentPreview() {
  Previews.BottomSheetPreview {
    ResetRecoveryKeySheetContent()
  }
}
