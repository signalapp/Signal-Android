/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
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
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.Serializable
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.navigation.ResultEffect
import org.signal.core.ui.navigation.TransitionSpecs
import org.signal.core.util.serialization.AccountEntropyPoolSerializer
import org.signal.registration.screens.accountlocked.AccountLockedScreen
import org.signal.registration.screens.accountlocked.AccountLockedScreenEvents
import org.signal.registration.screens.accountlocked.AccountLockedState
import org.signal.registration.screens.aepentry.EnterAepForLocalBackupViewModel
import org.signal.registration.screens.aepentry.EnterAepForRemoteBackupPostRegistrationViewModel
import org.signal.registration.screens.aepentry.EnterAepForRemoteBackupPreRegistrationViewModel
import org.signal.registration.screens.aepentry.EnterAepScreen
import org.signal.registration.screens.captcha.CaptchaScreen
import org.signal.registration.screens.captcha.CaptchaScreenEvents
import org.signal.registration.screens.captcha.CaptchaState
import org.signal.registration.screens.countrycode.Country
import org.signal.registration.screens.countrycode.CountryCodePickerRepository
import org.signal.registration.screens.countrycode.CountryCodePickerScreen
import org.signal.registration.screens.countrycode.CountryCodePickerViewModel
import org.signal.registration.screens.localbackuprestore.EnterLocalBackupV1PassphaseScreen
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreEvents
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreResult
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreScreen
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreViewModel
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
import org.signal.registration.screens.quickrestore.QuickRestoreQrScreen
import org.signal.registration.screens.quickrestore.QuickRestoreQrViewModel
import org.signal.registration.screens.remotebackuprestore.RemoteBackupRestoreViewModel
import org.signal.registration.screens.remotebackuprestore.RemoteRestoreScreen
import org.signal.registration.screens.restoreselection.ArchiveRestoreOption
import org.signal.registration.screens.restoreselection.ArchiveRestoreSelectionScreen
import org.signal.registration.screens.restoreselection.ArchiveRestoreSelectionViewModel
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo
import org.signal.registration.screens.verificationcode.VerificationCodeScreen
import org.signal.registration.screens.verificationcode.VerificationCodeViewModel
import org.signal.registration.screens.welcome.WelcomeScreen
import org.signal.registration.screens.welcome.WelcomeScreenEvents
import org.signal.registration.util.AccountEntropyPoolParceler

/**
 * Navigation routes for the registration flow.
 * Using @Serializable and NavKey for type-safe navigation with Navigation 3.
 */
@Serializable
@Parcelize
sealed interface RegistrationRoute : NavKey, Parcelable {
  @Serializable
  data object Welcome : RegistrationRoute

  @Serializable
  data class Permissions(val nextRoute: RegistrationRoute) : RegistrationRoute

  @Serializable
  data object PhoneNumberEntry : RegistrationRoute

  @Serializable
  data class CountryCodePicker(val country: Country? = null) : RegistrationRoute

  @Serializable
  data object VerificationCodeEntry : RegistrationRoute

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
  data class ArchiveRestoreSelection(val restoreOptions: List<ArchiveRestoreOption>, val isPreRegistration: Boolean) : RegistrationRoute {
    companion object {
      fun forQuickRestore(hasRemoteBackup: Boolean): ArchiveRestoreSelection {
        return ArchiveRestoreSelection(
          restoreOptions = buildList {
            if (hasRemoteBackup) {
              add(ArchiveRestoreOption.SignalSecureBackup)
            }
            add(ArchiveRestoreOption.LocalBackup)
            add(ArchiveRestoreOption.DeviceTransfer)
          },
          isPreRegistration = true
        )
      }

      fun forManualRestore(): ArchiveRestoreSelection {
        return ArchiveRestoreSelection(
          restoreOptions = buildList {
            add(ArchiveRestoreOption.SignalSecureBackup)
            add(ArchiveRestoreOption.LocalBackup)
            add(ArchiveRestoreOption.DeviceTransfer)
          },
          isPreRegistration = true
        )
      }

      fun forPostRegister(): ArchiveRestoreSelection {
        return ArchiveRestoreSelection(
          restoreOptions = buildList {
            add(ArchiveRestoreOption.SignalSecureBackup)
            add(ArchiveRestoreOption.LocalBackup)
            add(ArchiveRestoreOption.DeviceTransfer)
            add(ArchiveRestoreOption.None)
          },
          isPreRegistration = false
        )
      }
    }
  }

  @Serializable
  data class LocalBackupRestore(val isPreRegistration: Boolean) : RegistrationRoute

  @Serializable
  data object EnterLocalBackupV1Passphrase : RegistrationRoute

  @Serializable
  data object EnterAepForLocalBackup : RegistrationRoute

  @Serializable
  data class EnterAepForRemoteBackupPreRegistration(val e164: String) : RegistrationRoute

  @Serializable
  data object EnterAepForRemoteBackupPostRegistration : RegistrationRoute

  @Serializable
  @TypeParceler<AccountEntropyPool, AccountEntropyPoolParceler>
  data class RemoteRestore(@Serializable(with = AccountEntropyPoolSerializer::class) val aep: AccountEntropyPool) : RegistrationRoute

  @Serializable
  data object QuickRestoreQrScan : RegistrationRoute

  @Serializable
  data object Transfer : RegistrationRoute

  @Serializable
  data object Profile : RegistrationRoute

  @Serializable
  data object FullyComplete : RegistrationRoute
}

private const val CAPTCHA_RESULT = "captcha_token"
private const val COUNTRY_CODE_RESULT = "country_code_result"
private const val BACKUP_CREDENTIAL_RESULT = "backup_credential_result"
private const val LOCAL_BACKUP_RESTORE_RESULT = "local_backup_restore_result"

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

  if (registrationState.isRestoringNavigationState) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val entryProvider = entryProvider {
    navigationEntries(
      registrationRepository = registrationRepository,
      registrationViewModel = viewModel,
      permissionsState = permissionsState,
      onRegistrationComplete = onRegistrationComplete
    )
  }

  val decorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
    rememberViewModelStoreNavEntryDecorator()
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
      if (targetState.key is RegistrationRoute.CountryCodePicker) {
        TransitionSpecs.VerticalSlide.transitionSpec.invoke(this)
      } else {
        TransitionSpecs.HorizontalSlide.transitionSpec.invoke(this)
      }
    },
    popTransitionSpec = {
      when {
        initialState.key is RegistrationRoute.CountryCodePicker -> {
          TransitionSpecs.VerticalSlide.popTransitionSpec.invoke(this)
        }
        initialState.key == RegistrationRoute.EnterAepForLocalBackup.toString() || initialState.key == RegistrationRoute.EnterAepForRemoteBackupPreRegistration.toString() -> {
          TransitionSpecs.HorizontalSlide.transitionSpec.invoke(this)
        }
        initialState.key == RegistrationRoute.LocalBackupRestore.toString() && targetState.key == RegistrationRoute.PhoneNumberEntry.toString() -> {
          TransitionSpecs.HorizontalSlide.transitionSpec.invoke(this)
        }
        else -> {
          TransitionSpecs.HorizontalSlide.popTransitionSpec.invoke(this)
        }
      }
    },
    predictivePopTransitionSpec = {
      if (initialState.key is RegistrationRoute.CountryCodePicker) {
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
  permissionsState: MultiplePermissionsState?,
  onRegistrationComplete: () -> Unit
) {
  val parentEventEmitter: (RegistrationFlowEvent) -> Unit = registrationViewModel::onEvent

  // --- Welcome Screen
  entry<RegistrationRoute.Welcome> {
    WelcomeScreen(
      onEvent = { event ->
        when (event) {
          WelcomeScreenEvents.Continue -> parentEventEmitter.navigateTo(RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry))
          WelcomeScreenEvents.LinkDevice -> throw NotImplementedError("Haven't implemented linked devices yet")
          WelcomeScreenEvents.HasOldPhone -> parentEventEmitter.navigateTo(RegistrationRoute.Permissions(nextRoute = RegistrationRoute.QuickRestoreQrScan))
          WelcomeScreenEvents.DoesNotHaveOldPhone -> parentEventEmitter.navigateTo(RegistrationRoute.Permissions(nextRoute = RegistrationRoute.ArchiveRestoreSelection.forManualRestore()))
        }
      }
    )
  }

  // --- Permissions Screen
  entry<RegistrationRoute.Permissions> { key ->
    val onProceed = { parentEventEmitter.navigateTo(key.nextRoute) }
    val localPermissionsState = permissionsState ?: rememberMultiplePermissionsState(
      permissions = registrationViewModel.getRequiredPermissions(),
      onPermissionsResult = { onProceed() }
    )
    PermissionsScreen(
      permissionsState = localPermissionsState,
      onProceed = onProceed
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

    ResultEffect<LocalBackupRestoreResult>(registrationViewModel.resultBus, LOCAL_BACKUP_RESTORE_RESULT) { result ->
      viewModel.onEvent(PhoneNumberEntryScreenEvents.LocalBackupRestoreCompleted(result))
    }

    PhoneNumberScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- Country Code Picker
  entry<RegistrationRoute.CountryCodePicker> { key ->
    val viewModel: CountryCodePickerViewModel = viewModel(
      factory = CountryCodePickerViewModel.Factory(
        repository = CountryCodePickerRepository(),
        parentEventEmitter = parentEventEmitter,
        resultBus = registrationViewModel.resultBus,
        resultKey = COUNTRY_CODE_RESULT,
        initialCountry = key.country
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

  // -- Archive Restore Selection for Quick Restore Screen
  entry<RegistrationRoute.ArchiveRestoreSelection> { key ->
    val viewModel: ArchiveRestoreSelectionViewModel = viewModel(
      factory = ArchiveRestoreSelectionViewModel.Factory(
        restoreOptions = key.restoreOptions,
        isPreRegistration = key.isPreRegistration,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    ArchiveRestoreSelectionScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- Remote Restore Screen
  entry<RegistrationRoute.RemoteRestore> { key ->
    val viewModel: RemoteBackupRestoreViewModel = viewModel(
      factory = RemoteBackupRestoreViewModel.Factory(
        aep = key.aep,
        repository = registrationRepository,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    RemoteRestoreScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- Local Backup Restore Screen
  entry<RegistrationRoute.LocalBackupRestore> { key ->
    val viewModel: LocalBackupRestoreViewModel = viewModel(
      factory = LocalBackupRestoreViewModel.Factory(
        repository = registrationRepository,
        parentEventEmitter = registrationViewModel::onEvent,
        isPreRegistration = key.isPreRegistration,
        resultBus = registrationViewModel.resultBus,
        resultKey = LOCAL_BACKUP_RESTORE_RESULT
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    ResultEffect<String?>(registrationViewModel.resultBus, BACKUP_CREDENTIAL_RESULT) { passphrase ->
      if (passphrase != null) {
        viewModel.onEvent(LocalBackupRestoreEvents.PassphraseSubmitted(passphrase))
      }
    }

    LocalBackupRestoreScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- Enter Backup Passphrase (V1)
  entry<RegistrationRoute.EnterLocalBackupV1Passphrase> {
    EnterLocalBackupV1PassphaseScreen(
      onSubmit = { passphrase ->
        registrationViewModel.resultBus.sendResult(BACKUP_CREDENTIAL_RESULT, passphrase)
        parentEventEmitter.navigateBack()
      },
      onCancel = {
        parentEventEmitter.navigateBack()
      }
    )
  }

  // TODO I think we can re-use the screen but attach different viewmodels to progress forward rather than do for-result flows?

  // -- Enter AEP
  entry<RegistrationRoute.EnterAepForLocalBackup> {
    val viewModel: EnterAepForLocalBackupViewModel = viewModel(
      factory = EnterAepForLocalBackupViewModel.Factory(
        parentEventEmitter = registrationViewModel::onEvent,
        resultBus = registrationViewModel.resultBus,
        resultKey = BACKUP_CREDENTIAL_RESULT
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnterAepScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  entry<RegistrationRoute.EnterAepForRemoteBackupPreRegistration> { key ->
    val viewModel: EnterAepForRemoteBackupPreRegistrationViewModel = viewModel(
      factory = EnterAepForRemoteBackupPreRegistrationViewModel.Factory(
        e164 = key.e164,
        repository = registrationRepository,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnterAepScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  entry<RegistrationRoute.EnterAepForRemoteBackupPostRegistration> {
    val viewModel: EnterAepForRemoteBackupPostRegistrationViewModel = viewModel(
      factory = EnterAepForRemoteBackupPostRegistrationViewModel.Factory(
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnterAepScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  entry<RegistrationRoute.QuickRestoreQrScan> {
    val viewModel: QuickRestoreQrViewModel = viewModel(
      factory = QuickRestoreQrViewModel.Factory(
        repository = registrationRepository,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    QuickRestoreQrScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
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
