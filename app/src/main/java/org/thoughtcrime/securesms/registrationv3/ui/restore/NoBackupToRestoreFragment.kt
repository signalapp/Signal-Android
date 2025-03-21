/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registrationv3.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shown when the old device is iOS and they are trying to transfer/restore on Android without a Signal Backup.
 */
class NoBackupToRestoreFragment : ComposeFragment() {
  @Composable
  override fun FragmentContent() {
    NoBackupToRestoreContent(
      onSkipRestore = {},
      onCancel = {
        findNavController().safeNavigate(NoBackupToRestoreFragmentDirections.restartRegistrationFlow())
      }
    )
  }
}

@Composable
private fun NoBackupToRestoreContent(
  onSkipRestore: () -> Unit = {},
  onCancel: () -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(id = R.string.NoBackupToRestore_title),
    subtitle = stringResource(id = R.string.NoBackupToRestore_subtitle),
    bottomContent = {
      Column {
        Buttons.LargeTonal(
          onClick = onSkipRestore,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(text = stringResource(id = R.string.NoBackupToRestore_skip_restore))
        }

        TextButton(
          onClick = onCancel,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(text = stringResource(id = android.R.string.cancel))
        }
      }
    }
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(24.dp),
      modifier = Modifier.padding(horizontal = 32.dp)
    ) {
      StepRow(icon = painterResource(R.drawable.symbol_device_phone_24), text = stringResource(id = R.string.NoBackupToRestore_step1))

      StepRow(icon = painterResource(R.drawable.symbol_backup_24), text = stringResource(id = R.string.NoBackupToRestore_step2))

      StepRow(icon = painterResource(R.drawable.symbol_check_circle_24), text = stringResource(id = R.string.NoBackupToRestore_step3))
    }
  }
}

@Composable
private fun StepRow(
  icon: Painter,
  text: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = icon,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      contentDescription = null
    )

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    )
  }
}

@SignalPreview
@Composable
private fun NoBackupToRestoreContentPreview() {
  Previews.Preview {
    NoBackupToRestoreContent()
  }
}
