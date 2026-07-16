package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R

/**
 * Bottom sheet shown when confirming your recovery key after saving to password manager
 */
@Composable
fun ConfirmRecoveryKeySheet(
  onConfirm: () -> Unit = {},
  onSeeAgain: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.horizontalGutters(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.backup_confirm_80),
      tint = Color.Unspecified,
      contentDescription = null,
      modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
    )

    Text(
      text = stringResource(R.string.MessageBackupsKeyVerifyScreen__confirm_your_backup_key),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 12.dp)
    )

    Text(
      text = stringResource(R.string.MessageBackupsKeyRecordScreen__confirm_that_your_recovery),
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.size(60.dp))

    Buttons.LargeTonal(onClick = onConfirm) {
      Text(text = stringResource(R.string.MessageBackupsKeyRecordScreen__confirm_recovery))
    }

    TextButton(
      onClick = onSeeAgain,
      modifier = Modifier.padding(vertical = 16.dp)
    ) {
      Text(text = stringResource(R.string.MessageBackupsKeyRecordScreen__see_key_again))
    }
  }
}

@DayNightPreviews
@Composable
private fun ConfirmRecoveryKeyPreview() {
  Previews.BottomSheetContentPreview {
    ConfirmRecoveryKeySheet(
      onConfirm = {},
      onSeeAgain = {},
      modifier = Modifier.fillMaxSize()
    )
  }
}
