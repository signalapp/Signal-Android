/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.thoughtcrime.securesms.R

/**
 * Displays a modal bottom sheet that explains where to find the information necessary to perform
 * a bank transfer.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FindAccountInfoSheet(
  onDismissRequest: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    dragHandle = { BottomSheets.Handle() }
  ) {
    Image(
      painter = painterResource(id = R.drawable.find_account_info),
      contentDescription = null,
      modifier = Modifier
        .align(CenterHorizontally)
        .padding(vertical = 32.dp)
    )

    Text(
      text = stringResource(id = R.string.FindAccountInfoSheet__find_your_account_information),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 60.dp)
        .align(CenterHorizontally)
    )

    Text(
      text = stringResource(id = R.string.FindAccountInfoSheet__look_for_your_iban_at),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp, bottom = 48.dp, start = 60.dp, end = 60.dp)
        .align(CenterHorizontally)
    )
  }
}
