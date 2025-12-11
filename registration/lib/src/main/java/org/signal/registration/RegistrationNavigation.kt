/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.os.Parcelable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.signal.core.ui.navigation.ResultEffect
import org.signal.registration.screens.accountlocked.AccountLockedScreen
import org.signal.registration.screens.accountlocked.AccountLockedScreenEvents
import org.signal.registration.screens.accountlocked.AccountLockedState
import org.signal.registration.screens.captcha.CaptchaScreen
import org.signal.registration.screens.captcha.CaptchaScreenEvents
import org.signal.registration.screens.captcha.CaptchaState
import org.signal.registration.screens.permissions.PermissionsScreen
import org.signal.registration.screens.phonenumber.PhoneNumberEntryScreenEvents
import org.signal.registration.screens.phonenumber.PhoneNumberEntryViewModel
import org.signal.registration.screens.phonenumber.PhoneNumberScreen
import org.signal.registration.screens.pincreation.PinCreationScreen
import org.signal.registration.screens.pincreation.PinCreationScreenEvents
import org.signal.registration.screens.pincreation.PinCreationState
import org.signal.registration.screens.pinentry.PinEntryScreen
import org.signal.registration.screens.registrationlock.RegistrationLockPinEntryViewModel
import org.signal.registration.screens.restore.RestoreViaQrScreen
import org.signal.registration.screens.restore.RestoreViaQrScreenEvents
import org.signal.registration.screens.restore.RestoreViaQrState
import org.signal.registration.screens.verificationcode.VerificationCodeScreen
import org.signal.registration.screens.verificationcode.VerificationCodeViewModel
import org.signal.registration.screens.welcome.WelcomeScreen
import org.signal.registration.screens.welcome.WelcomeScreenEvents

/**
 * Navigation routes for the registration flow.
 * Using @Serializable and NavKey for type-safe navigation with Navigation 3.
 */
@Parcelize
sealed interface RegistrationRoute : NavKey, Parcelable {
  @Serializable
  data object Welcome : RegistrationRoute

  @Serializable
  data class Permissions(val forRestore: Boolean = false) : RegistrationRoute

  @Serializable
  data object PhoneNumberEntry : RegistrationRoute

  @Serializable
  data object CountryCodePicker : RegistrationRoute

  @Serializable
  data class VerificationCodeEntry(val session: NetworkController.SessionMetadata, val e164: String) : RegistrationRoute

  @Serializable
  data class Captcha(val session: NetworkController.SessionMetadata) : RegistrationRoute

  @Serializable
  data object PinEntry : RegistrationRoute

  @Serializable
  data class RegistrationLockPinEntry(
    val timeRemaining: Long,
    val svrCredentials: NetworkController.SvrCredentials
  ) : RegistrationRoute

  @Serializable
  data class AccountLocked(val timeRemainingMs: Long) : RegistrationRoute

  @Serializable
  data object Profile : RegistrationRoute

  @Serializable
  data object PinSetup : RegistrationRoute

  @Serializable
  data object Restore : RegistrationRoute

  @Serializable
  data object RestoreViaQr : RegistrationRoute

  @Serializable
  data object Transfer : RegistrationRoute

  @Serializable
  data class FullyComplete(val registeredData: NetworkController.RegisterAccountResponse) : RegistrationRoute
}

private const val CAPTCHA_RESULT = "captcha_token"

/**
 * Sets up the navigation graph for the registration flow using Navigation 3.
 *
 * @param registrationViewModel The shared ViewModel for the registration flow.
 * @param permissionsState The permissions state managed at the activity level.
 * @param modifier Modifier to be applied to the NavDisplay.
 * @param onRegistrationComplete Callback invoked when registration is successfully completed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RegistrationNavHost(
  registrationRepository: RegistrationRepository,
  registrationViewModel: RegistrationViewModel,
  permissionsState: MultiplePermissionsState,
  modifier: Modifier = Modifier,
  onRegistrationComplete: () -> Unit = {}
) {
  val registrationState by registrationViewModel.state.collectAsStateWithLifecycle()
  val navigator = remember { RegistrationNavigator(eventEmitter = registrationViewModel::onEvent) }

  val entryProvider = entryProvider {
    registrationEntries(
      registrationRepository = registrationRepository,
      registrationViewModel = registrationViewModel,
      permissionsState = permissionsState,
      navigator = navigator,
      onRegistrationComplete = onRegistrationComplete
    )
  }

  val decorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator<NavKey>()
  )

  val entries = rememberDecoratedNavEntries(
    backStack = registrationState.backStack,
    entryDecorators = decorators,
    entryProvider = entryProvider
  )

  NavDisplay(
    entries = entries,
    onBack = { registrationViewModel.onEvent(RegistrationFlowEvent.NavigateBack) },
    modifier = modifier,
    transitionSpec = {
      // Slide in from right and fade in when navigating forward
      (
        slideInHorizontally(
          initialOffsetX = { it },
          animationSpec = tween(200)
        ) + fadeIn(animationSpec = tween(200))
        ) togetherWith
        // Slide out to left and fade out
        (
          slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(200)
          ) + fadeOut(animationSpec = tween(200))
          )
    },
    popTransitionSpec = {
      // Slide in from left and fade in when navigating back
      (
        slideInHorizontally(
          initialOffsetX = { -it },
          animationSpec = tween(200)
        ) + fadeIn(animationSpec = tween(200))
        ) togetherWith
        // Slide out to right and fade out
        (
          slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(200)
          ) + fadeOut(animationSpec = tween(200))
          )
    },
    predictivePopTransitionSpec = {
      // Same as popTransitionSpec for predictive back gestures
      (
        slideInHorizontally(
          initialOffsetX = { -it },
          animationSpec = tween(200)
        ) + fadeIn(animationSpec = tween(200))
        ) togetherWith
        (
          slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(200)
          ) + fadeOut(animationSpec = tween(200))
          )
    }
  )
}

/**
 * Defines all navigation entries for the registration flow.
 */
@OptIn(ExperimentalPermissionsApi::class)
private fun EntryProviderScope<NavKey>.registrationEntries(
  registrationRepository: RegistrationRepository,
  registrationViewModel: RegistrationViewModel,
  permissionsState: MultiplePermissionsState,
  navigator: RegistrationNavigator,
  onRegistrationComplete: () -> Unit
) {
  // --- Welcome Screen
  entry<RegistrationRoute.Welcome> {
    WelcomeScreen(
      onEvent = { event ->
        when (event) {
          WelcomeScreenEvents.Continue -> navigator.navigate(RegistrationRoute.Permissions(forRestore = false))
          WelcomeScreenEvents.DoesNotHaveOldPhone -> navigator.navigate(RegistrationRoute.Restore)
          WelcomeScreenEvents.HasOldPhone -> navigator.navigate(RegistrationRoute.Permissions(forRestore = true))
        }
      }
    )
  }

  // --- Permissions Screen
  entry<RegistrationRoute.Permissions> { key ->
    PermissionsScreen(
      permissionsState = permissionsState,
      onProceed = {
        if (key.forRestore) {
          navigator.navigate(RegistrationRoute.RestoreViaQr)
        } else {
          navigator.navigate(RegistrationRoute.PhoneNumberEntry)
        }
      }
    )
  }

  // -- Phone Number Entry Screen
  entry<RegistrationRoute.PhoneNumberEntry> {
    val viewModel: PhoneNumberEntryViewModel = viewModel(
      factory = PhoneNumberEntryViewModel.Factory(
        repository = registrationRepository,
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    ResultEffect<String?>(registrationViewModel.resultBus, CAPTCHA_RESULT) { captchaToken ->
      if (captchaToken != null) {
        viewModel.onEvent(PhoneNumberEntryScreenEvents.CaptchaCompleted(captchaToken))
      }
    }

    PhoneNumberScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- Country Code Picker
  entry<RegistrationRoute.CountryCodePicker> {
    // We'll also want this to be some sort of launch-for-result flow as well
    TODO()
  }

  // -- Captcha Screen
  entry<RegistrationRoute.Captcha> {
    CaptchaScreen(
      state = CaptchaState(
        captchaUrl = registrationRepository.getCaptchaUrl()
      ),
      onEvent = { event ->
        when (event) {
          is CaptchaScreenEvents.CaptchaCompleted -> {
            registrationViewModel.resultBus.sendResult(CAPTCHA_RESULT, event.token)
            navigator.goBack()
          }
          CaptchaScreenEvents.Cancel -> {
            navigator.goBack()
          }
        }
      }
    )
  }

  // -- Verification Code Entry Screen
  entry<RegistrationRoute.VerificationCodeEntry> {
    val viewModel: VerificationCodeViewModel = viewModel(
      factory = VerificationCodeViewModel.Factory(
        repository = registrationRepository,
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    VerificationCodeScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  entry<RegistrationRoute.Profile> {
    // TODO: Implement ProfileScreen
  }

  entry<RegistrationRoute.PinSetup> {
    PinCreationScreen(
      state = PinCreationState(
        inputLabel = "PIN must be at least 4 digits"
      ),
      onEvent = { event ->
        when (event) {
          is PinCreationScreenEvents.PinSubmitted -> {
            // TODO: Save PIN and navigate to next screen
            onRegistrationComplete()
          }
          PinCreationScreenEvents.ToggleKeyboard -> {
            // TODO: Toggle between numeric and alphanumeric keyboard
          }
          PinCreationScreenEvents.LearnMore -> {
            // TODO: Show learn more dialog
          }
        }
      }
    )
  }

  // -- Registration Lock PIN Entry Screen
  entry<RegistrationRoute.RegistrationLockPinEntry> { key ->
    val viewModel: RegistrationLockPinEntryViewModel = viewModel(
      factory = RegistrationLockPinEntryViewModel.Factory(
        repository = registrationRepository,
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent,
        timeRemaining = key.timeRemaining,
        svrCredentials = key.svrCredentials
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    PinEntryScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- Account Locked Screen
  entry<RegistrationRoute.AccountLocked> { key ->
    val daysRemaining = (key.timeRemainingMs / (1000 * 60 * 60 * 24)).toInt()
    AccountLockedScreen(
      state = AccountLockedState(daysRemaining = daysRemaining),
      onEvent = { event ->
        when (event) {
          AccountLockedScreenEvents.Next -> {
            // TODO: Navigate to appropriate next screen (likely back to welcome or phone entry)
            navigator.navigate(RegistrationRoute.Welcome)
          }
          AccountLockedScreenEvents.LearnMore -> {
            // TODO: Open learn more URL
          }
        }
      }
    )
  }

  entry<RegistrationRoute.Restore> {
    // TODO: Implement RestoreScreen
  }

  entry<RegistrationRoute.RestoreViaQr> {
    RestoreViaQrScreen(
      state = RestoreViaQrState(),
      onEvent = { event ->
        when (event) {
          RestoreViaQrScreenEvents.RetryQrCode -> {
            // TODO: Retry QR code generation
          }
          RestoreViaQrScreenEvents.Cancel -> {
            navigator.goBack()
          }
          RestoreViaQrScreenEvents.UseProxy -> {
            // TODO: Navigate to proxy settings
          }
          RestoreViaQrScreenEvents.DismissError -> {
            // TODO: Clear error state
          }
        }
      }
    )
  }

  entry<RegistrationRoute.Transfer> {
    // TODO: Implement TransferScreen
  }

  entry<RegistrationRoute.FullyComplete> {
    LaunchedEffect(Unit) {
      onRegistrationComplete()
    }
  }
}

/**
 * Navigator for the registration flow.
 * Handles navigation events by updating the back stack.
 */
private class RegistrationNavigator(
  private val eventEmitter: (RegistrationFlowEvent) -> Unit
) {

  fun navigate(route: RegistrationRoute) {
    eventEmitter(RegistrationFlowEvent.NavigateToScreen(route))
  }

  fun goBack() {
    eventEmitter(RegistrationFlowEvent.NavigateBack)
  }
}
