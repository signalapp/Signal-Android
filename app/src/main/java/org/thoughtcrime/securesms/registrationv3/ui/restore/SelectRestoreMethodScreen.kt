/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registrationv3.ui.shared.RegistrationScreen

/**
 * Screen showing various restore methods available during quick and manual re-registration.
 */
@Composable
fun SelectRestoreMethodScreen(
  restoreMethods: List<RestoreMethod>,
  onRestoreMethodClicked: (RestoreMethod) -> Unit = {},
  onSkip: () -> Unit = {},
  extraContent: @Composable ColumnScope.() -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(id = R.string.SelectRestoreMethodFragment__restore_or_transfer_account),
    subtitle = stringResource(id = R.string.SelectRestoreMethodFragment__get_your_signal_account),
    bottomContent = {
      TextButton(
        onClick = onSkip,
        modifier = Modifier.align(Alignment.Center)
      ) {
        Text(text = stringResource(R.string.registration_activity__skip_restore))
      }
    }
  ) {
    for (method in restoreMethods) {
      RestoreRow(
        icon = painterResource(method.iconRes),
        title = stringResource(method.titleRes),
        subtitle = stringResource(method.subtitleRes),
        onRowClick = { onRestoreMethodClicked(method) }
      )
    }

    extraContent()
  }
}

@SignalPreview
@Composable
private fun SelectRestoreMethodScreenPreview() {
  SignalTheme {
    SelectRestoreMethodScreen(listOf(RestoreMethod.FROM_SIGNAL_BACKUPS, RestoreMethod.FROM_OLD_DEVICE, RestoreMethod.FROM_LOCAL_BACKUP_V1))
  }
}
