/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R

/**
 * Displayed after user taps "Learn more" when being notified that their subscription
 * could not be found. This state is entered when a user has a Signal service backups
 * subscription that is active but no on-device (Google Play Billing) subscription.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionNotFoundBottomSheet(
  onDismiss: () -> Unit,
  onContactSupport: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    sheetState = sheetState,
    onDismissRequest = onDismiss,
    dragHandle = { BottomSheets.Handle() }
  ) {
    SubscriptionNotFoundContent(
      onDismiss = onDismiss,
      onContactSupport = onContactSupport
    )
  }
}

@Composable
private fun ColumnScope.SubscriptionNotFoundContent(
  onDismiss: () -> Unit = {},
  onContactSupport: () -> Unit = {}
) {
  Text(
    text = stringResource(R.string.SubscriptionNotFoundBottomSheet__subscription_not_found),
    style = MaterialTheme.typography.titleLarge,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .padding(top = 28.dp)
      .horizontalGutters()
      .align(Alignment.CenterHorizontally)
  )

  Text(
    text = stringResource(R.string.SubscriptionNotFoundBottomSheet__your_subscription_couldnt_be_restored),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .padding(top = 12.dp)
      .horizontalGutters()
      .align(Alignment.CenterHorizontally)
  )

  Text(
    text = stringResource(R.string.SubscriptionNotFoundBottomSheet__this_could_happen_if),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .horizontalGutters()
      .padding(bottom = 12.dp)
      .align(Alignment.CenterHorizontally)
  )

  SubscriptionNotFoundReason(stringResource(R.string.SubscriptionNotFoundBottomSheet__youre_signed_into_the_play_store_with_a_different_google_account))

  SubscriptionNotFoundReason(stringResource(R.string.SubscriptionNotFoundBottomSheet__you_transferred_from_an_iphone))

  SubscriptionNotFoundReason(stringResource(R.string.SubscriptionNotFoundBottomSheet__your_subscription_recently_expired))

  Text(
    text = stringResource(R.string.SubscriptionNotFoundBottomSheet__if_you_have_an_active_subscription_on),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .padding(top = 24.dp)
      .horizontalGutters()
      .align(Alignment.CenterHorizontally)
  )

  Buttons.LargeTonal(
    onClick = onDismiss,
    modifier = Modifier
      .defaultMinSize(minWidth = 220.dp)
      .padding(top = 36.dp)
      .align(Alignment.CenterHorizontally)
  ) {
    Text(text = stringResource(R.string.SubscriptionNotFoundBottomSheet__got_it))
  }

  TextButton(
    onClick = onContactSupport,
    modifier = Modifier
      .defaultMinSize(minWidth = 220.dp)
      .padding(top = 16.dp, bottom = 48.dp)
      .align(Alignment.CenterHorizontally)
  ) {
    Text(
      text = stringResource(R.string.SubscriptionNotFoundBottomSheet__contact_support)
    )
  }
}

@Composable
private fun SubscriptionNotFoundReason(text: String) {
  Row(
    modifier = Modifier
      .height(IntrinsicSize.Min)
      .padding(horizontal = 36.dp)
      .padding(top = 12.dp)
  ) {
    Box(
      modifier = Modifier
        .padding(end = 12.dp)
        .fillMaxHeight()
        .padding(vertical = 2.dp)
        .width(4.dp)
        .background(
          color = SignalTheme.colors.colorTransparentInverse2,
          shape = RoundedCornerShape(2.dp)
        )
    )

    Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f)
    )
  }
}

@DayNightPreviews
@Composable
private fun SubscriptionNotFoundContentPreview() {
  Previews.BottomSheetPreview {
    Column {
      SubscriptionNotFoundContent()
    }
  }
}
