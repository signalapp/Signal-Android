/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.signal.core.ui.navigation.ResultEffect
import org.signal.core.ui.navigation.TransitionSpecs
import org.signal.registration.screens.accountlocked.AccountLockedScreen
import org.signal.registration.screens.accountlocked.AccountLockedScreenEvents
import org.signal.registration.screens.accountlocked.AccountLockedState
import org.signal.registration.screens.captcha.CaptchaScreen
import org.signal.registration.screens.captcha.CaptchaScreenEvents
import org.signal.registration.screens.captcha.CaptchaState
import org.signal.registration.screens.countrycode.Country
import org.signal.registration.screens.countrycode.CountryCodePickerRepository
import org.signal.registration.screens.countrycode.CountryCodePickerScreen
import org.signal.registration.screens.countrycode.CountryCodePickerViewModel
import org.signal.registration.screens.permissions.PermissionsScreen
import org.signal.registration.screens.phonenumber.PhoneNumberEntryScreenEvents
import org.signal.registration.screens.phonenumber.PhoneNumberEntryViewModel
import org.signal.registration.screens.phonenumber.PhoneNumberScreen
import org.signal.registration.screens.pincreation.PinCreationScreen
import org.signal.registration.screens.pincreation.PinCreationViewModel
import org.signal.registration.screens.pinentry.PinEntryForRegistrationLockViewModel
import org.signal.registration.screens.pinentry.PinEntryForSmsBypassViewModel
import org.signal.registration.screens.pinentry.PinEntryForSvrRestoreViewModel
import org.signal.registration.screens.pinentry.PinEntryScreen
import org.signal.registration.screens.restore.RestoreViaQrScreen
import org.signal.registration.screens.restore.RestoreViaQrScreenEvents
import org.signal.registration.screens.restore.RestoreViaQrState
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo
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
  data object PinEntryForSvrRestore : RegistrationRoute

  @Serializable
  data class PinEntryForRegistrationLock(
    val timeRemaining: Long,
    val svrCredentials: NetworkController.SvrCredentials
  ) : RegistrationRoute

  @Serializable
  data class PinEntryForSmsBypass(val svrCredentials: NetworkController.SvrCredentials) : RegistrationRoute

  @Serializable
  data class AccountLocked(val timeRemainingMs: Long) : RegistrationRoute

  @Serializable
  data object PinCreate : RegistrationRoute

  @Serializable
  data object Restore : RegistrationRoute

  @Serializable
  data object RestoreViaQr : RegistrationRoute

  @Serializable
  data object Transfer : RegistrationRoute

  @Serializable
  data object Profile : RegistrationRoute

  @Serializable
  data object FullyComplete : RegistrationRoute
}

private const val CAPTCHA_RESULT = "captcha_token"
private const val COUNTRY_CODE_RESULT = "country_code_result"

/**
 * Sets up the navigation graph for the registration flow using Navigation 3.
 *
 * @param registrationRepository The repository for registration data.
 * @param registrationViewModel Optional ViewModel for testing. If null, creates one internally.
 * @param permissionsState Optional permissions state for testing. If null, creates one internally.
 * @param modifier Modifier to be applied to the NavDisplay.
 * @param onRegistrationComplete Callback invoked when registration is successfully completed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RegistrationNavHost(
  registrationRepository: RegistrationRepository,
  registrationViewModel: RegistrationViewModel? = null,
  permissionsState: MultiplePermissionsState? = null,
  modifier: Modifier = Modifier,
  onRegistrationComplete: () -> Unit = {}
) {
  val viewModel: RegistrationViewModel = registrationViewModel ?: viewModel(
    factory = RegistrationViewModel.Factory(registrationRepository)
  )

  val registrationState by viewModel.state.collectAsStateWithLifecycle()
  val permissions: MultiplePermissionsState = permissionsState ?: rememberMultiplePermissionsState(viewModel.getRequiredPermissions())

  val entryProvider = entryProvider {
    navigationEntries(
      registrationRepository = registrationRepository,
      registrationViewModel = viewModel,
      permissionsState = permissions,
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
    onBack = { viewModel.onEvent(RegistrationFlowEvent.NavigateBack) },
    modifier = modifier,
    transitionSpec = {
      if (targetState.key == RegistrationRoute.CountryCodePicker.toString()) {
        TransitionSpecs.VerticalSlide.transitionSpec.invoke(this)
      } else {
        TransitionSpecs.HorizontalSlide.transitionSpec.invoke(this)
      }
    },
    popTransitionSpec = {
      if (initialState.key == RegistrationRoute.CountryCodePicker.toString()) {
        TransitionSpecs.VerticalSlide.popTransitionSpec.invoke(this)
      } else {
        TransitionSpecs.HorizontalSlide.popTransitionSpec.invoke(this)
      }
    },
    predictivePopTransitionSpec = {
      if (initialState.key == RegistrationRoute.CountryCodePicker.toString()) {
        TransitionSpecs.VerticalSlide.predictivePopTransitionSpec.invoke(this, it)
      } else {
        TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec.invoke(this, it)
      }
    }
  )
}

private fun EntryProviderScope<NavKey>.navigationEntries(
  registrationRepository: RegistrationRepository,
  registrationViewModel: RegistrationViewModel,
  permissionsState: MultiplePermissionsState,
  onRegistrationComplete: () -> Unit
) {
  val parentEventEmitter: (RegistrationFlowEvent) -> Unit = registrationViewModel::onEvent

  // --- Welcome Screen
  entry<RegistrationRoute.Welcome> {
    WelcomeScreen(
      onEvent = { event ->
        when (event) {
          WelcomeScreenEvents.Continue -> parentEventEmitter.navigateTo(RegistrationRoute.Permissions(forRestore = false))
          WelcomeScreenEvents.DoesNotHaveOldPhone -> parentEventEmitter.navigateTo(RegistrationRoute.Restore)
          WelcomeScreenEvents.HasOldPhone -> parentEventEmitter.navigateTo(RegistrationRoute.Permissions(forRestore = true))
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
          parentEventEmitter.navigateTo(RegistrationRoute.RestoreViaQr)
        } else {
          parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry)
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

    ResultEffect<Country?>(registrationViewModel.resultBus, COUNTRY_CODE_RESULT) { country ->
      if (country != null) {
        viewModel.onEvent(PhoneNumberEntryScreenEvents.CountrySelected(country.countryCode, country.regionCode, country.name, country.emoji))
      }
    }

    PhoneNumberScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- Country Code Picker
  entry<RegistrationRoute.CountryCodePicker> {
    val viewModel: CountryCodePickerViewModel = viewModel(
      factory = CountryCodePickerViewModel.Factory(
        repository = CountryCodePickerRepository(),
        parentEventEmitter = parentEventEmitter,
        resultBus = registrationViewModel.resultBus,
        resultKey = COUNTRY_CODE_RESULT
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    CountryCodePickerScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
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
            parentEventEmitter.navigateBack()
          }
          CaptchaScreenEvents.Cancel -> {
            parentEventEmitter.navigateBack()
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

  // -- SVR Restore PIN Entry Screen (for users with existing backup data)
  entry<RegistrationRoute.PinEntryForSvrRestore> {
    val viewModel: PinEntryForSvrRestoreViewModel = viewModel(
      factory = PinEntryForSvrRestoreViewModel.Factory(
        repository = registrationRepository,
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    PinEntryScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- PIN Creation Screen (for new users creating their first PIN)
  entry<RegistrationRoute.PinCreate> {
    val viewModel: PinCreationViewModel = viewModel(
      factory = PinCreationViewModel.Factory(
        repository = registrationRepository,
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    PinCreationScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- Registration Lock PIN Entry Screen
  entry<RegistrationRoute.PinEntryForRegistrationLock> { key ->
    val viewModel: PinEntryForRegistrationLockViewModel = viewModel(
      factory = PinEntryForRegistrationLockViewModel.Factory(
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

  // -- SMS Bypass PIN Entry Screen
  entry<RegistrationRoute.PinEntryForSmsBypass> { key ->
    val viewModel: PinEntryForSmsBypassViewModel = viewModel(
      factory = PinEntryForSmsBypassViewModel.Factory(
        repository = registrationRepository,
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent,
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
            parentEventEmitter.navigateTo(RegistrationRoute.Welcome)
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
            parentEventEmitter.navigateBack()
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

  entry<RegistrationRoute.Profile> {
    // TODO: Implement ProfileScreen
  }

  entry<RegistrationRoute.FullyComplete> {
    LaunchedEffect(Unit) {
      onRegistrationComplete()
    }
  }
}
