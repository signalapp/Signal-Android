/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.olddevice.transferaccount

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.fonts.SignalSymbols.SignalSymbol
import org.thoughtcrime.securesms.registration.data.QuickRegistrationRepository
import org.thoughtcrime.securesms.registration.olddevice.QuickTransferOldDeviceActivity
import org.thoughtcrime.securesms.registration.olddevice.QuickTransferOldDeviceState
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SpanUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferAccountScreen(
  state: QuickTransferOldDeviceState,
  emitter: (TransferScreenEvents) -> Unit = {}
) {
  Scaffold(
    topBar = { TopAppBarContent(onBackClicked = { emitter(TransferScreenEvents.NavigateBack) }) }
  ) { contentPadding ->
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(contentPadding)
        .horizontalGutters()
    ) {
      Image(
        painter = painterResource(R.drawable.image_transfer_phones),
        contentDescription = null,
        modifier = Modifier.padding(top = 20.dp, bottom = 28.dp)
      )

      val context = LocalContext.current
      val learnMore = stringResource(id = R.string.TransferAccount_learn_more)
      val fullString = stringResource(id = R.string.TransferAccount_body, learnMore)
      val spanned = SpanUtil.urlSubsequence(fullString, learnMore, QuickTransferOldDeviceActivity.LEARN_MORE_URL)
      Texts.LinkifiedText(
        textWithUrlSpans = spanned,
        onUrlClick = { CommunicationActions.openBrowserLink(context, it) },
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
      )

      Spacer(modifier = Modifier.height(28.dp))

      AnimatedContent(
        targetState = state.inProgress,
        contentAlignment = Alignment.Center
      ) { inProgress ->
        if (inProgress) {
          CircularProgressIndicator()
        } else {
          Buttons.LargeTonal(
            onClick = { emitter(TransferScreenEvents.TransferClicked) },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(text = stringResource(id = R.string.TransferAccount_button))
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = buildAnnotatedString {
          SignalSymbol(SignalSymbols.Weight.REGULAR, SignalSymbols.Glyph.LOCK)
          append(" ")
          append(stringResource(id = R.string.TransferAccount_messages_e2e))
        },
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (state.reRegisterResult) {
      QuickRegistrationRepository.TransferAccountResult.SUCCESS -> {
        ModalBottomSheet(
          dragHandle = null,
          onDismissRequest = { emitter(TransferScreenEvents.ContinueOnOtherDeviceDismiss) },
          sheetState = sheetState
        ) {
          ContinueOnOtherDevice()
        }
      }

      QuickRegistrationRepository.TransferAccountResult.FAILED -> {
        Dialogs.SimpleMessageDialog(
          message = stringResource(R.string.RegistrationActivity_unable_to_connect_to_service),
          dismiss = stringResource(android.R.string.ok),
          onDismiss = { emitter(TransferScreenEvents.ErrorDialogDismissed) }
        )
      }

      null -> Unit
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferAccountScreenPreview() {
  Previews.Preview {
    TransferAccountScreen(state = QuickTransferOldDeviceState("sgnl://rereg"))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBarContent(onBackClicked: () -> Unit) {
  TopAppBar(
    title = {
      Text(text = stringResource(R.string.TransferAccount_title))
    },
    navigationIcon = {
      IconButton(onClick = onBackClicked) {
        Icon(
          painter = SignalIcons.X.painter,
          tint = MaterialTheme.colorScheme.onSurface,
          contentDescription = null
        )
      }
    }
  )
}

/**
 * Shown after successfully sending provisioning message to new device.
 */
@Composable
fun ContinueOnOtherDevice() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
      .padding(bottom = 54.dp)
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.height(26.dp))

    Image(
      painter = painterResource(R.drawable.image_other_device),
      contentDescription = null,
      modifier = Modifier.padding(bottom = 20.dp)
    )

    Text(
      text = stringResource(id = R.string.TransferAccount_continue_on_your_other_device),
      style = MaterialTheme.typography.titleLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(id = R.string.TransferAccount_continue_on_your_other_device_details),
      style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    )

    Spacer(modifier = Modifier.height(36.dp))

    CircularProgressIndicator(modifier = Modifier.size(44.dp))
  }
}

@DayNightPreviews
@Composable
private fun ContinueOnOtherDevicePreview() {
  Previews.Preview {
    ContinueOnOtherDevice()
  }
}
