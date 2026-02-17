/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.signal.core.ui.navigation.TransitionSpecs
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.thoughtcrime.video.app.transcode.TranscodeTestViewModel
import org.thoughtcrime.video.app.transcode.composables.ConfigureEncodingParameters
import org.thoughtcrime.video.app.transcode.composables.TranscodingScreen
import org.thoughtcrime.video.app.transcode.composables.VideoSelectionScreen
import org.thoughtcrime.video.app.ui.theme.SignalTheme

enum class Screen : NavKey {
  VideoSelection,
  Configuration,
  Transcoding
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    Log.initialize(AndroidLogger)
    setContent {
      SignalTheme {
        Surface(
          modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
          color = MaterialTheme.colorScheme.background
        ) {
          TranscodeApp()
        }
      }
    }
  }
}

@Composable
private fun TranscodeApp() {
  val backStack = rememberNavBackStack(Screen.VideoSelection)
  val viewModel: TranscodeTestViewModel = viewModel()
  val context = LocalContext.current

  val pickMedia = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      viewModel.selectedVideo = uri
      backStack.add(Screen.Configuration)
    }
  }

  NavDisplay(
    backStack = backStack,
    transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
    popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
    predictivePopTransitionSpec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec,
    entryProvider = entryProvider {
      addEntryProvider(
        key = Screen.VideoSelection,
        contentKey = Screen.VideoSelection,
        metadata = emptyMap()
      ) { _: Screen ->
        VideoSelectionScreen(
          onSelectVideo = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
          }
        )
      }

      addEntryProvider(
        key = Screen.Configuration,
        contentKey = Screen.Configuration,
        metadata = emptyMap()
      ) { _: Screen ->
        ConfigureEncodingParameters(
          onTranscodeClicked = {
            viewModel.startTranscode(context)
            backStack.remove(Screen.Configuration)
            backStack.add(Screen.Transcoding)
          },
          modifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
          viewModel = viewModel
        )
      }

      addEntryProvider(
        key = Screen.Transcoding,
        contentKey = Screen.Transcoding,
        metadata = emptyMap()
      ) { _: Screen ->
        val transcodingState by viewModel.transcodingState.collectAsStateWithLifecycle()
        TranscodingScreen(
          state = transcodingState,
          onCancel = { viewModel.cancelTranscode() },
          onReset = {
            viewModel.reset()
            backStack.remove(Screen.Transcoding)
          }
        )
      }
    }
  )
}
