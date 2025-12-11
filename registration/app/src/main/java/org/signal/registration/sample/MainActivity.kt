/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.navigation.ResultEffect
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationActivity
import org.signal.registration.RegistrationDependencies
import org.signal.registration.StorageController
import org.signal.registration.sample.MainActivity.Companion.REGISTRATION_RESULT
import org.signal.registration.sample.screens.RegistrationCompleteScreen
import org.signal.registration.sample.screens.main.MainScreen
import org.signal.registration.sample.screens.main.MainScreenViewModel
import org.signal.registration.sample.screens.pinsettings.PinSettingsScreen
import org.signal.registration.sample.screens.pinsettings.PinSettingsViewModel

private const val ANIMATION_DURATION = 300

/**
 * Transition spec for bottom sheet style screens that slide up from the bottom.
 */
private val BottomSheetTransitionSpec = NavDisplay.transitionSpec {
  (
    slideInVertically(
      initialOffsetY = { it },
      animationSpec = tween(ANIMATION_DURATION)
    ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))
    ) togetherWith ExitTransition.KeepUntilTransitionsFinished
} + NavDisplay.popTransitionSpec {
  EnterTransition.None togetherWith (
    slideOutVertically(
      targetOffsetY = { it },
      animationSpec = tween(ANIMATION_DURATION)
    ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
    )
} + NavDisplay.predictivePopTransitionSpec {
  EnterTransition.None togetherWith (
    slideOutVertically(
      targetOffsetY = { it },
      animationSpec = tween(ANIMATION_DURATION)
    ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
    )
}

/**
 * Navigation routes for the sample app.
 */
sealed interface SampleRoute : NavKey {
  @Serializable
  data object Main : SampleRoute

  @Serializable
  data object RegistrationComplete : SampleRoute

  @Serializable
  data object PinSettings : SampleRoute
}

/**
 * Sample app activity that launches the registration flow for testing.
 */
class MainActivity : ComponentActivity() {
  companion object {
    const val REGISTRATION_RESULT = "registration_result"
  }

  private val viewModel: AppViewModel by viewModels()

  private val registrationLauncher: ActivityResultLauncher<Unit> = registerForActivityResult(RegistrationActivity.RegistrationContract()) { success ->
    viewModel.resultEventBus.sendResult(REGISTRATION_RESULT, success)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      SignalTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val backStack = rememberNavBackStack(SampleRoute.Main)

          SampleNavHost(
            onLaunchRegistration = { registrationLauncher.launch(Unit) },
            backStack = backStack,
            resultEventBus = viewModel.resultEventBus,
            storageController = RegistrationDependencies.get().storageController,
            networkController = RegistrationDependencies.get().networkController,
            onStartOver = {
              backStack.clear()
              backStack.add(SampleRoute.Main)
            }
          )
        }
      }
    }
  }
}

@Composable
private fun SampleNavHost(
  onLaunchRegistration: () -> Unit,
  onStartOver: () -> Unit,
  backStack: NavBackStack<NavKey>,
  resultEventBus: ResultEventBus,
  storageController: StorageController,
  networkController: NetworkController,
  modifier: Modifier = Modifier
) {
  val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<SampleRoute.Main> {
      val viewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModel.Factory(
          storageController = storageController,
          onLaunchRegistration = onLaunchRegistration,
          onOpenPinSettings = { backStack.add(SampleRoute.PinSettings) }
        )
      )
      val state by viewModel.state.collectAsStateWithLifecycle()

      LifecycleResumeEffect(Unit) {
        viewModel.refreshData()
        onPauseOrDispose { }
      }

      ResultEffect<Boolean>(resultEventBus, REGISTRATION_RESULT) { success ->
        if (success) {
          viewModel.refreshData()
          backStack.add(SampleRoute.RegistrationComplete)
        }
      }

      MainScreen(
        state = state,
        onEvent = { viewModel.onEvent(it) }
      )
    }

    entry<SampleRoute.RegistrationComplete> {
      RegistrationCompleteScreen(onStartOver = onStartOver)
    }

    entry<SampleRoute.PinSettings>(
      metadata = BottomSheetTransitionSpec
    ) {
      val viewModel: PinSettingsViewModel = viewModel(
        factory = PinSettingsViewModel.Factory(
          networkController = networkController,
          onBack = { backStack.removeLastOrNull() }
        )
      )
      val state by viewModel.state.collectAsStateWithLifecycle()

      PinSettingsScreen(
        state = state,
        onEvent = { viewModel.onEvent(it) }
      )
    }
  }

  val decorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator<NavKey>()
  )

  val entries = rememberDecoratedNavEntries(
    backStack = backStack,
    entryDecorators = decorators,
    entryProvider = entryProvider
  )

  NavDisplay(
    entries = entries,
    onBack = { backStack.removeLastOrNull() },
    modifier = modifier,
    transitionSpec = {
      // Default: slide in from right, previous screen shrinks back
      (
        slideInHorizontally(
          initialOffsetX = { it },
          animationSpec = tween(ANIMATION_DURATION)
        ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))
        ) togetherWith (
        fadeOut(animationSpec = tween(ANIMATION_DURATION)) +
          scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(ANIMATION_DURATION)
          )
        )
    },
    popTransitionSpec = {
      // Default pop: scale up from background, current slides out right
      (
        fadeIn(animationSpec = tween(ANIMATION_DURATION)) +
          scaleIn(
            initialScale = 0.9f,
            animationSpec = tween(ANIMATION_DURATION)
          )
        ) togetherWith (
        slideOutHorizontally(
          targetOffsetX = { it },
          animationSpec = tween(ANIMATION_DURATION)
        ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
        )
    },
    predictivePopTransitionSpec = {
      (
        fadeIn(animationSpec = tween(ANIMATION_DURATION)) +
          scaleIn(
            initialScale = 0.9f,
            animationSpec = tween(ANIMATION_DURATION)
          )
        ) togetherWith (
        slideOutHorizontally(
          targetOffsetX = { it },
          animationSpec = tween(ANIMATION_DURATION)
        ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
        )
    }
  )
}
