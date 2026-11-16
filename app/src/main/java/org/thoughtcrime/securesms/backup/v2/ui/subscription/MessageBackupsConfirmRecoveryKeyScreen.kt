/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.content.Context
import android.widget.Toast
import androidx.annotation.UiContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.signallogin.card.SignalLoginCard
import org.thoughtcrime.securesms.R
import java.util.UUID
import org.signal.core.ui.R as CoreUiR
import org.signal.signallogin.R as SignalLoginR

object MessageBackupsConfirmRecoveryKeyTestTags {
  const val CONFIRM_BUTTON = "confirm-recovery-key-screen-confirm-button"
  const val ENTER_MANUALLY_BUTTON = "confirm-recovery-key-screen-enter-manually-button"
}

/**
 * Asks a user who already has a Signal Login to confirm the recovery key that came with it, by pulling the credential
 * back out of their password manager. Shown in place of the record-and-verify screens, since the key already exists and
 * was already presented to them during registration.
 */
@Composable
fun MessageBackupsConfirmRecoveryKeyScreen(
  aci: ServiceId.ACI,
  aep: AccountEntropyPool,
  onNavigationClick: () -> Unit = {},
  onViewDetailsClick: () -> Unit = {},
  onConfirmed: () -> Unit = {},
  onEnterManuallyClick: () -> Unit = {}
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val successMessage = stringResource(R.string.MessageBackupsConfirmRecoveryKeyScreen__recovery_key_confirmed)

  var displayConfirmationFailure by remember { mutableStateOf(false) }
  if (displayConfirmationFailure) {
    ConfirmationFailureDialog(
      onEnterManuallyClick = onEnterManuallyClick,
      onDismiss = { displayConfirmationFailure = false }
    )
  }

  Scaffolds.Settings(
    title = "",
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    onNavigationClick = onNavigationClick
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .verticalScroll(rememberScrollState())
      ) {
        Image(
          painter = painterResource(SignalLoginR.drawable.image_signal_login_keys),
          contentDescription = null,
          modifier = Modifier
            .padding(top = 12.dp)
            .size(96.dp)
        )

        Text(
          text = stringResource(R.string.MessageBackupsConfirmRecoveryKeyScreen__confirm_your_recovery_key),
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 24.dp)
        )

        Text(
          text = stringResource(R.string.MessageBackupsConfirmRecoveryKeyScreen__your_recovery_key_is_a_64_character_code),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 12.dp)
        )

        SignalLoginCard(
          aci = aci,
          aep = aep,
          onViewDetailsClicked = onViewDetailsClick,
          modifier = Modifier.padding(top = 32.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
      }

      Text(
        text = stringResource(R.string.MessageBackupsConfirmRecoveryKeyScreen__you_will_be_prompted_with_your_saved_password),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp)
      )

      Buttons.LargeTonal(
        onClick = {
          coroutineScope.launch {
            if (getKeyFromCredentialManager(context, aci) == aep.displayValue) {
              Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
              onConfirmed()
            } else {
              displayConfirmationFailure = true
            }
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag(MessageBackupsConfirmRecoveryKeyTestTags.CONFIRM_BUTTON)
      ) {
        Text(text = stringResource(R.string.MessageBackupsConfirmRecoveryKeyScreen__confirm_recovery_key))
      }

      Buttons.LargeTonal(
        onClick = onEnterManuallyClick,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = SignalTheme.colors.colorSurface2,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 16.dp, bottom = 16.dp)
          .testTag(MessageBackupsConfirmRecoveryKeyTestTags.ENTER_MANUALLY_BUTTON)
      ) {
        Text(text = stringResource(R.string.MessageBackupsConfirmRecoveryKeyScreen__enter_manually))
      }
    }
  }
}

@Composable
private fun ConfirmationFailureDialog(
  onEnterManuallyClick: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.MessageBackupsConfirmRecoveryKeyScreen__error_confirming_recovery_key),
    body = stringResource(R.string.MessageBackupsConfirmRecoveryKeyScreen__your_recovery_key_could_not_be_confirmed),
    confirm = stringResource(R.string.MessageBackupsConfirmRecoveryKeyScreen__enter_manually),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = onEnterManuallyClick,
    onDismiss = onDismiss
  )
}

/**
 * The password manager stores the Signal Login under the account key, so that is the credential id we ask for.
 */
private suspend fun getKeyFromCredentialManager(
  @UiContext activityContext: Context,
  aci: ServiceId.ACI
): String? {
  return SignalCredentialManager.getCredential(activityContext, aci.toString().uppercase())?.password
}

@DayNightPreviews
@Composable
private fun MessageBackupsConfirmRecoveryKeyScreenPreview() {
  Previews.Preview {
    MessageBackupsConfirmRecoveryKeyScreen(
      aci = ServiceId.ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91")),
      aep = AccountEntropyPool("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t")
    )
  }
}
