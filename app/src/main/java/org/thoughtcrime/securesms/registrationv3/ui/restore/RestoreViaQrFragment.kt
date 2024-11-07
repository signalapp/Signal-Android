/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import android.os.Bundle
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.horizontalGutters
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCode
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registrationv3.ui.shared.RegistrationScreen

/**
 * Show QR code on new device to allow registration and restore via old device.
 */
class RestoreViaQrFragment : ComposeFragment() {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel: RestoreViaQrViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        viewModel
          .state
          .mapNotNull { it.provisioningMessage }
          .distinctUntilChanged()
          .collect { message ->
            sharedViewModel.registerWithBackupKey(requireContext(), message.accountEntropyPool, message.e164, message.pin)
          }
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        sharedViewModel
          .state
          .map { it.registerAccountError }
          .filterNotNull()
          .collect {
            sharedViewModel.registerAccountErrorShown()
            viewModel.handleRegistrationFailure()
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()

    RestoreViaQrScreen(
      state = state,
      onRetryQrCode = viewModel::restart,
      onRegistrationErrorDismiss = viewModel::clearRegistrationError,
      onCancel = { findNavController().popBackStack() }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestoreViaQrScreen(
  state: RestoreViaQrViewModel.RestoreViaQrState,
  onRetryQrCode: () -> Unit = {},
  onRegistrationErrorDismiss: () -> Unit = {},
  onCancel: () -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(R.string.RestoreViaQr_title),
    subtitle = null,
    bottomContent = {
      TextButton(
        onClick = onCancel,
        modifier = Modifier.align(Alignment.Center)
      ) {
        Text(text = stringResource(android.R.string.cancel))
      }
    }
  ) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(space = 48.dp, alignment = Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(space = 48.dp),
      modifier = Modifier
        .fillMaxWidth()
        .horizontalGutters()
    ) {
      Box(
        modifier = Modifier
          .widthIn(160.dp, 320.dp)
          .aspectRatio(1f)
          .clip(RoundedCornerShape(24.dp))
          .background(SignalTheme.colors.colorSurface5)
          .padding(40.dp)
      ) {
        SignalTheme(isDarkMode = false) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surface)
              .fillMaxWidth()
              .fillMaxHeight()
              .padding(16.dp),
            contentAlignment = Alignment.Center
          ) {
            AnimatedContent(
              targetState = state.qrState,
              contentAlignment = Alignment.Center,
              label = "qr-code-progress"
            ) { qrState ->
              when (qrState) {
                is RestoreViaQrViewModel.QrState.Loaded -> {
                  QrCode(
                    data = qrState.qrData,
                    foregroundColor = Color(0xFF2449C0),
                    modifier = Modifier
                      .fillMaxWidth()
                      .fillMaxHeight()
                  )
                }

                RestoreViaQrViewModel.QrState.Loading -> {
                  CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }

                is RestoreViaQrViewModel.QrState.Scanned,
                RestoreViaQrViewModel.QrState.Failed -> {
                  Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    val text = if (state.qrState is RestoreViaQrViewModel.QrState.Scanned) {
                      stringResource(R.string.RestoreViaQr_qr_code_scanned)
                    } else {
                      stringResource(R.string.RestoreViaQr_qr_code_error)
                    }

                    Text(
                      text = text,
                      textAlign = TextAlign.Center,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Buttons.Small(
                      onClick = onRetryQrCode
                    ) {
                      Text(text = stringResource(R.string.RestoreViaQr_retry))
                    }
                  }
                }
              }
            }
          }
        }
      }

      Column(
        modifier = Modifier
          .align(alignment = Alignment.CenterVertically)
          .widthIn(160.dp, 320.dp)
      ) {
        InstructionRow(
          icon = painterResource(R.drawable.symbol_phone_24),
          instruction = stringResource(R.string.RestoreViaQr_instruction_1)
        )

        InstructionRow(
          icon = painterResource(R.drawable.symbol_camera_24),
          instruction = stringResource(R.string.RestoreViaQr_instruction_2)
        )

        InstructionRow(
          icon = painterResource(R.drawable.symbol_qrcode_24),
          instruction = stringResource(R.string.RestoreViaQr_instruction_3)
        )
      }
    }

    if (state.isRegistering) {
      Dialogs.IndeterminateProgressDialog()
    } else if (state.showRegistrationError) {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.RegistrationActivity_error_connecting_to_service),
        onDismiss = onRegistrationErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    }
  }
}

@SignalPreview
@Composable
private fun RestoreViaQrScreenPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(
        qrState = RestoreViaQrViewModel.QrState.Loaded(
          QrCodeData.forData("sgnl://rereg?uuid=asdfasdfasdfasdfasdfasdf&pub_key=asdfasdfasdfSDFSsdfsdfSDFSDffd", false)
        )
      )
    )
  }
}

@SignalPreview
@Composable
private fun RestoreViaQrScreenLoadingPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(qrState = RestoreViaQrViewModel.QrState.Loading)
    )
  }
}

@SignalPreview
@Composable
private fun RestoreViaQrScreenFailurePreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(qrState = RestoreViaQrViewModel.QrState.Failed)
    )
  }
}

@SignalPreview
@Composable
private fun RestoreViaQrScreenScannedPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(qrState = RestoreViaQrViewModel.QrState.Scanned)
    )
  }
}

@SignalPreview
@Composable
private fun RestoreViaQrScreenRegisteringPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(isRegistering = true, qrState = RestoreViaQrViewModel.QrState.Scanned)
    )
  }
}

@SignalPreview
@Composable
private fun RestoreViaQrScreenRegistrationFailedPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(isRegistering = false, showRegistrationError = true, qrState = RestoreViaQrViewModel.QrState.Scanned)
    )
  }
}

@Composable
private fun InstructionRow(
  icon: Painter,
  instruction: String
) {
  Row(
    modifier = Modifier
      .padding(vertical = 12.dp)
  ) {
    Icon(
      painter = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = instruction,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@SignalPreview
@Composable
private fun InstructionRowPreview() {
  Previews.Preview {
    InstructionRow(
      icon = painterResource(R.drawable.symbol_phone_24),
      instruction = "Instruction!"
    )
  }
}
