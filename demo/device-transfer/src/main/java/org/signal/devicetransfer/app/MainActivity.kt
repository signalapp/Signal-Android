/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.KeepScreenOnEffect
import org.signal.core.ui.compose.theme.SignalTheme

class MainActivity : ComponentActivity() {

  private val viewModel: DeviceTransferTestViewModel by viewModels {
    viewModelFactory {
      initializer { DeviceTransferTestViewModel(DeviceTransferTestRepository(applicationContext)) }
    }
  }

  private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    viewModel.onEvent(DeviceTransferTestEvent.PermissionsResultReceived)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()

      CollectActions(viewModel.actions) { action ->
        when (action) {
          is DeviceTransferTestAction.RequestPermissions -> permissionLauncher.launch(action.permissions.toTypedArray())
        }
      }

      KeepScreenOnEffect(enabled = state.isRunning)

      SignalTheme(incognitoKeyboardEnabled = false) {
        DeviceTransferTestScreen(
          state = state,
          onEvent = viewModel::onEvent
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.onEvent(DeviceTransferTestEvent.ScreenResumed)
  }
}
