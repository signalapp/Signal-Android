/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import android.os.Bundle
import android.view.View
import android.view.WindowManager
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCode
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registrationv3.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Show QR code on new device to allow registration and restore via old device.
 */
class RestoreViaQrFragment : ComposeFragment() {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel: RestoreViaQrViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onResume(owner: LifecycleOwner) {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }

      override fun onPause(owner: LifecycleOwner) {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    })

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        viewModel
          .state
          .mapNotNull { it.provisioningMessage }
          .distinctUntilChanged()
          .collect { message ->
            if (message.platform == RegistrationProvisionMessage.Platform.ANDROID || message.tier != null) {
              sharedViewModel.registerWithBackupKey(requireContext(), message.accountEntropyPool, message.e164, message.pin, message.aciIdentityKeyPair, message.pniIdentityKeyPair)
            } else {
              findNavController().safeNavigate(RestoreViaQrFragmentDirections.goToNoBackupToRestore())
            }
          }
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        viewModel
          .state
          .mapNotNull { it.registerAccountResult }
          .filter { it !is RegisterAccountResult.Success }
          .distinctUntilChanged()
          .collect { result ->
            when (result) {
              is RegisterAccountResult.AttemptsExhausted -> {
                findNavController().safeNavigate(RestoreViaQrFragmentDirections.goToAccountLocked())
              }
              else -> Unit
            }
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
            viewModel.handleRegistrationFailure(it)
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
              contentKey = { it::class },
              contentAlignment = Alignment.Center,
              label = "qr-code-progress",
              modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
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
                  Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                  }
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
