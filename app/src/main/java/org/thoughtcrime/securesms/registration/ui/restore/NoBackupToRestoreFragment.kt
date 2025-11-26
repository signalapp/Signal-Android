/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.os.Bundle
import android.view.View
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel
import kotlin.getValue

/**
 * Shown when the old device is iOS and they are trying to transfer/restore on Android without a Signal Backup.
 */
class NoBackupToRestoreFragment : ComposeFragment() {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel by viewModel {
    NoBackupToRestoreViewModel(sharedViewModel.registrationProvisioningMessage!!)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        sharedViewModel
          .state
          .map { it.registerAccountError }
          .filterNotNull()
          .collect {
            sharedViewModel.registerAccountErrorShown()
            viewModel.handleRegistrationFailure(it)
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()

    NoBackupToRestoreContent(
      state = state,
      onSkipRestore = {
        viewModel.skipRestoreAndRegister()

        val message = viewModel.state.value.provisioningMessage
        sharedViewModel.registerWithBackupKey(
          context = requireContext(),
          backupKey = message.accountEntropyPool,
          e164 = message.e164,
          pin = message.pin,
          aciIdentityKeyPair = message.aciIdentityKeyPair,
          pniIdentityKeyPair = message.pniIdentityKeyPair
        )
      },
      onCancel = {
        sharedViewModel.registrationProvisioningMessage = null
        findNavController().safeNavigate(NoBackupToRestoreFragmentDirections.restartRegistrationFlow())
      },
      onRegistrationErrorDismiss = viewModel::clearRegistrationError
    )
  }
}

@Composable
private fun NoBackupToRestoreContent(
  state: NoBackupToRestoreViewModel.NoBackupToRestoreState,
  onSkipRestore: () -> Unit = {},
  onCancel: () -> Unit = {},
  onRegistrationErrorDismiss: () -> Unit = {}
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

    if (state.isRegistering) {
      Dialogs.IndeterminateProgressDialog()
    } else if (state.showRegistrationError) {
      val message = when (state.registerAccountResult) {
        is RegisterAccountResult.IncorrectRecoveryPassword -> stringResource(R.string.RestoreViaQr_registration_error)
        is RegisterAccountResult.RateLimited -> stringResource(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
        else -> stringResource(R.string.RegistrationActivity_error_connecting_to_service)
      }

      Dialogs.SimpleMessageDialog(
        message = message,
        onDismiss = onRegistrationErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
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

@DayNightPreviews
@Composable
private fun NoBackupToRestoreContentPreview() {
  Previews.Preview {
    NoBackupToRestoreContent(state = NoBackupToRestoreViewModel.NoBackupToRestoreState(provisioningMessage = RegistrationProvisionMessage()))
  }
}
