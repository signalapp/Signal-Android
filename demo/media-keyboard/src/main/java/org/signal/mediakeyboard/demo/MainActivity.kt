/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediakeyboard.demo.main.MainScreen
import org.signal.mediakeyboard.demo.main.MainScreenViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: MainScreenViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val repository = (application as MediaKeyboardDemoApplication).repository

    setContent {
      SignalTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          val state by viewModel.state.collectAsStateWithLifecycle()

          MainScreen(
            state = state,
            onEvent = viewModel::onEvent,
            repository = repository
          )
        }
      }
    }
  }
}
