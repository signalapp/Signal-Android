/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.link

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCode
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import java.lang.IllegalStateException

/**
 * Crude show QR code on link device to allow linking from primary device.
 */
class RegisterLinkDeviceQrFragment : ComposeFragment() {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel: RegisterLinkDeviceQrViewModel by viewModels()

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
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel
          .state
          .mapNotNull { it.provisionMessage }
          .distinctUntilChanged()
          .collect { message ->
            withContext(Dispatchers.IO) {
              val result = sharedViewModel.registerAsLinkedDevice(requireContext().applicationContext, message)

              when (result) {
                RegisterLinkDeviceResult.Success -> Unit
                else -> viewModel.setRegisterAsLinkedDeviceError(result)
              }
            }
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()

    RegisterLinkDeviceQrScreen(
      state = state,
      onRetryQrCode = viewModel::restartProvisioningSocket,
      onErrorDismiss = viewModel::clearErrors,
      onCancel = { findNavController().popBackStack() }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterLinkDeviceQrScreen(
  state: RegisterLinkDeviceQrViewModel.RegisterLinkDeviceState,
  onRetryQrCode: () -> Unit = {},
  onErrorDismiss: () -> Unit = {},
  onCancel: () -> Unit = {}
) {
  // TODO [link-device] use actual design
  RegistrationScreen(
    title = "Scan this code with your phone",
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
                is RegisterLinkDeviceQrViewModel.QrState.Loaded -> {
                  QrCode(
                    data = qrState.qrData,
                    foregroundColor = Color(0xFF2449C0),
                    modifier = Modifier
                      .fillMaxWidth()
                      .fillMaxHeight()
                  )
                }

                RegisterLinkDeviceQrViewModel.QrState.Loading -> {
                  Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                  }
                }

                is RegisterLinkDeviceQrViewModel.QrState.Scanned,
                RegisterLinkDeviceQrViewModel.QrState.Failed -> {
                  Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    val text = if (state.qrState is RegisterLinkDeviceQrViewModel.QrState.Scanned) {
                      "Scanned on device"
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

      // TODO [link-device] use actual copy
      Column(
        modifier = Modifier
          .align(alignment = Alignment.CenterVertically)
          .widthIn(160.dp, 320.dp)
      ) {
        InstructionRow(
          icon = painterResource(R.drawable.symbol_settings_android_24),
          instruction = "Open Signal Settings on your device"
        )

        InstructionRow(
          icon = painterResource(R.drawable.symbol_link_24),
          instruction = "Tap \"Linked devices\""
        )

        InstructionRow(
          icon = painterResource(R.drawable.symbol_qrcode_24),
          instruction = "Tap \"Link a new device\" and scan this code"
        )
      }
    }

    if (state.isRegistering) {
      Dialogs.IndeterminateProgressDialog()
    } else if (state.showProvisioningError) {
      Dialogs.SimpleMessageDialog(
        message = "failed provision",
        onDismiss = onErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    } else if (state.registrationErrorResult != null) {
      val message = when (state.registrationErrorResult) {
        RegisterLinkDeviceResult.IncorrectVerification -> "incorrect verification"
        RegisterLinkDeviceResult.InvalidRequest -> "invalid request"
        RegisterLinkDeviceResult.MaxLinkedDevices -> "max linked devices reached"
        RegisterLinkDeviceResult.MissingCapability -> "missing capability, must update"
        is RegisterLinkDeviceResult.NetworkException -> "network exception ${state.registrationErrorResult.t.message}"
        is RegisterLinkDeviceResult.RateLimited -> "rate limited ${state.registrationErrorResult.retryAfter}"
        is RegisterLinkDeviceResult.UnexpectedException -> "unexpected exception ${state.registrationErrorResult.t.message}"
        RegisterLinkDeviceResult.Success -> throw IllegalStateException()
      }

      Dialogs.SimpleMessageDialog(
        message = message,
        onDismiss = onErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    }
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

@DayNightPreviews
@Composable
private fun InstructionRowPreview() {
  Previews.Preview {
    InstructionRow(
      icon = painterResource(R.drawable.symbol_phone_24),
      instruction = "Instruction!"
    )
  }
}
