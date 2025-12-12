/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.serialization.Serializable
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.RegistrationDependencies
import org.signal.registration.RegistrationNavHost
import org.signal.registration.RegistrationRepository
import org.signal.registration.sample.debug.NetworkDebugOverlay
import org.signal.registration.sample.screens.RegistrationCompleteScreen
import org.signal.registration.sample.screens.main.MainScreen
import org.signal.registration.sample.screens.main.MainScreenViewModel
import org.signal.registration.sample.screens.pinsettings.PinSettingsScreen
import org.signal.registration.sample.screens.pinsettings.PinSettingsViewModel

private const val ANIMATION_DURATION = 300

/**
 * Navigation routes for the sample app.
 */
sealed interface SampleRoute : NavKey {
  @Serializable
  data object Main : SampleRoute

  @Serializable
  data object Registration : SampleRoute

  @Serializable
  data object RegistrationComplete : SampleRoute

  @Serializable
  data object PinSettings : SampleRoute
}

/**
 * Sample app activity that launches the registration flow for testing.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      SignalTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          AppScreen(RegistrationDependencies.get())
        }
      }
    }
  }
}

@Composable
fun AppScreen(registrationDependencies: RegistrationDependencies) {
  val backStack = rememberNavBackStack(SampleRoute.Main)

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    SampleNavHost(
      backStack = backStack,
      registrationDependencies = registrationDependencies,
      onStartOver = {
        backStack.clear()
        backStack.add(SampleRoute.Main)
      }
    )

    // Debug overlay for forcing network responses
    NetworkDebugOverlay(
      modifier = Modifier.fillMaxSize()
    )
  }
}

@Composable
private fun SampleNavHost(
  onStartOver: () -> Unit,
  registrationDependencies: RegistrationDependencies,
  backStack: NavBackStack<NavKey>,
  modifier: Modifier = Modifier
) {
  val registrationRepository = remember {
    RegistrationRepository(
      networkController = registrationDependencies.networkController,
      storageController = registrationDependencies.storageController
    )
  }

  val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<SampleRoute.Main> {
      val viewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModel.Factory(
          storageController = registrationDependencies.storageController,
          onLaunchRegistration = { backStack.add(SampleRoute.Registration) },
          onOpenPinSettings = { backStack.add(SampleRoute.PinSettings) }
        )
      )
      val state by viewModel.state.collectAsStateWithLifecycle()

      LifecycleResumeEffect(Unit) {
        viewModel.refreshData()
        onPauseOrDispose { }
      }

      MainScreen(
        state = state,
        onEvent = { viewModel.onEvent(it) }
      )
    }

    entry<SampleRoute.Registration> {
      RegistrationNavHost(
        registrationRepository,
        modifier = Modifier.fillMaxSize(),
        onRegistrationComplete = {
          backStack.add(SampleRoute.RegistrationComplete)
        }
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
          networkController = registrationDependencies.networkController,
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
