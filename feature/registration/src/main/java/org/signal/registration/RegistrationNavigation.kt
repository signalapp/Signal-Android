/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration

import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.Serializable
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.navigation.ResultEffect
import org.signal.core.ui.navigation.TransitionSpecs
import org.signal.core.util.LinkActions
import org.signal.core.util.LinkActions.OpenUrlError
import org.signal.core.util.serialization.AccountEntropyPoolSerializer
import org.signal.registration.screens.accountlocked.AccountLockedScreen
import org.signal.registration.screens.accountlocked.AccountLockedScreenEvents
import org.signal.registration.screens.accountlocked.AccountLockedState
import org.signal.registration.screens.aepentry.EnterAepForLocalBackupResult
import org.signal.registration.screens.aepentry.EnterAepForLocalBackupViewModel
import org.signal.registration.screens.aepentry.EnterAepForRemoteBackupPostRegistrationViewModel
import org.signal.registration.screens.aepentry.EnterAepForRemoteBackupPreRegistrationViewModel
import org.signal.registration.screens.aepentry.EnterAepScreen
import org.signal.registration.screens.allownotifications.AllowNotificationsScreen
import org.signal.registration.screens.captcha.CaptchaScreen
import org.signal.registration.screens.captcha.CaptchaScreenEvents
import org.signal.registration.screens.captcha.CaptchaState
import org.signal.registration.screens.countrycode.Country
import org.signal.registration.screens.countrycode.CountryCodePickerRepository
import org.signal.registration.screens.countrycode.CountryCodePickerScreen
import org.signal.registration.screens.countrycode.CountryCodePickerViewModel
import org.signal.registration.screens.createprofile.CreateProfileScreen
import org.signal.registration.screens.createprofile.CreateProfileScreenEvents
import org.signal.registration.screens.createprofile.CreateProfileViewModel
import org.signal.registration.screens.devicetransfer.complete.DeviceTransferCompleteScreen
import org.signal.registration.screens.devicetransfer.complete.DeviceTransferCompleteViewModel
import org.signal.registration.screens.devicetransfer.instructions.DeviceTransferInstructionsScreen
import org.signal.registration.screens.devicetransfer.instructions.DeviceTransferInstructionsViewModel
import org.signal.registration.screens.devicetransfer.progress.DeviceTransferProgressScreen
import org.signal.registration.screens.devicetransfer.progress.DeviceTransferProgressViewModel
import org.signal.registration.screens.devicetransfer.setup.DeviceTransferSetupScreen
import org.signal.registration.screens.devicetransfer.setup.DeviceTransferSetupViewModel
import org.signal.registration.screens.discoverability.PhoneNumberDiscoverabilityScreen
import org.signal.registration.screens.discoverability.PhoneNumberDiscoverabilityViewModel
import org.signal.registration.screens.linkaccount.LinkAccountScreen
import org.signal.registration.screens.linkaccount.LinkAccountScreenEvent
import org.signal.registration.screens.linkaccount.LinkAccountViewModel
import org.signal.registration.screens.localbackuprestore.EnterLocalBackupV1PassphaseScreen
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreEvents
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreResult
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreScreen
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreViewModel
import org.signal.registration.screens.messagesync.MessageSyncScreen
import org.signal.registration.screens.messagesync.MessageSyncScreenEvent
import org.signal.registration.screens.messagesync.MessageSyncViewModel
import org.signal.registration.screens.permissions.PermissionsScreen
import org.signal.registration.screens.phonenumber.PhoneNumberEntryScreenEvents
import org.signal.registration.screens.phonenumber.PhoneNumberEntryViewModel
import org.signal.registration.screens.phonenumber.PhoneNumberScreen
import org.signal.registration.screens.pincreation.PinCreationScreen
import org.signal.registration.screens.pincreation.PinCreationScreenEvents
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
import org.signal.registration.screens.restoreselection.RegisteredState
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo
import org.signal.registration.screens.verificationcode.VerificationCodeScreen
import org.signal.registration.screens.verificationcode.VerificationCodeViewModel
import org.signal.registration.screens.welcome.WelcomeScreen
import org.signal.registration.screens.welcome.WelcomeScreenEvents
import org.signal.registration.util.AccountEntropyPoolParceler
import org.signal.registration.util.RegistrationCredentialManager

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
  data class AllowNotifications(val nextRoute: RegistrationRoute) : RegistrationRoute

  @Serializable
  data class LinkAccount(val showCreateAccount: Boolean = true) : RegistrationRoute

  @Serializable
  data object MessageSync : RegistrationRoute

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
  @TypeParceler<AccountEntropyPool?, AccountEntropyPoolParceler>
  data class ArchiveRestoreSelection(
    val restoreOptions: List<ArchiveRestoreOption>,
    val registeredState: RegisteredState,
    @Serializable(with = AccountEntropyPoolSerializer::class) val aep: AccountEntropyPool? = null
  ) : RegistrationRoute {
    companion object {

      fun forQuickRestore(aep: AccountEntropyPool, hasRemoteBackup: Boolean, hasPin: Boolean): ArchiveRestoreSelection {
        return ArchiveRestoreSelection(
          restoreOptions = buildList {
            if (hasRemoteBackup) {
              add(ArchiveRestoreOption.SignalSecureBackup)
            }
            add(ArchiveRestoreOption.LocalBackup)
            add(ArchiveRestoreOption.DeviceTransfer)
            add(ArchiveRestoreOption.None)
          },
          registeredState = if (hasPin) RegisteredState.RegisteredAndPinKnown else RegisteredState.RegisteredAndPinUnknown,
          aep = aep
        )
      }

      fun forManualRestore(): ArchiveRestoreSelection {
        return ArchiveRestoreSelection(
          restoreOptions = buildList {
            add(ArchiveRestoreOption.SignalSecureBackup)
            add(ArchiveRestoreOption.LocalBackup)
            add(ArchiveRestoreOption.None)
          },
          registeredState = RegisteredState.NotRegistered
        )
      }

      fun forPostRegisterWithPinUnknown(): ArchiveRestoreSelection {
        return ArchiveRestoreSelection(
          restoreOptions = buildList {
            add(ArchiveRestoreOption.SignalSecureBackup)
            add(ArchiveRestoreOption.LocalBackup)
            add(ArchiveRestoreOption.None)
          },
          registeredState = RegisteredState.RegisteredAndPinUnknown
        )
      }

      fun forPostRegisterWithPinKnown(): ArchiveRestoreSelection {
        return ArchiveRestoreSelection(
          restoreOptions = buildList {
            add(ArchiveRestoreOption.SignalSecureBackup)
            add(ArchiveRestoreOption.LocalBackup)
            add(ArchiveRestoreOption.None)
          },
          registeredState = RegisteredState.RegisteredAndPinKnown
        )
      }
    }
  }

  @Serializable
  @TypeParceler<AccountEntropyPool?, AccountEntropyPoolParceler>
  data class LocalBackupRestore(
    val isPreRegistration: Boolean,
    @Serializable(with = AccountEntropyPoolSerializer::class) val aep: AccountEntropyPool? = null
  ) : RegistrationRoute

  @Serializable
  data object EnterLocalBackupV1Passphrase : RegistrationRoute

  /**
   * Recovery key entry for a local V2 backup.
   *
   * When [isPreRegistration] is true (pre-registration manual restore), submitting the key first verifies it can
   * decrypt the backup at [backupUri], then registers the account via the recovery password derived from it. A backup
   * belonging to a different account is surfaced to the user, who can choose to restore it after verifying over SMS.
   * When false (already registered), the key is simply handed back to the restore screen to decrypt the backup.
   */
  @Serializable
  data class EnterAepForLocalBackup(
    val isPreRegistration: Boolean = false,
    val backupUri: String? = null
  ) : RegistrationRoute

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
  data object DeviceTransferInstructions : RegistrationRoute

  @Serializable
  data object DeviceTransferSetup : RegistrationRoute

  @Serializable
  data object DeviceTransferProgress : RegistrationRoute

  @Serializable
  data object DeviceTransferComplete : RegistrationRoute

  @Serializable
  data object Profile : RegistrationRoute

  @Serializable
  data class PhoneNumberDiscoverability(val initialDiscoverable: Boolean) : RegistrationRoute

  @Serializable
  data object FullyComplete : RegistrationRoute
}

private const val CAPTCHA_RESULT = "captcha_token"
private const val COUNTRY_CODE_RESULT = "country_code_result"
private const val BACKUP_CREDENTIAL_RESULT = "backup_credential_result"
private const val AEP_FOR_LOCAL_BACKUP_RESULT = "aep_for_local_backup_result"
private const val LOCAL_BACKUP_RESTORE_RESULT = "local_backup_restore_result"
private const val PHONE_NUMBER_DISCOVERABILITY_RESULT = "phone_number_discoverability_result"
private const val PIN_LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059792"

/**
 * Sets up the navigation graph for the registration flow using Navigation 3.
 *
 * @param registrationRepository The repository for registration data.
 * @param registrationViewModel Optional ViewModel for testing. If null, creates one internally.
 * @param startFresh When true, any persisted registration data is not restored and the user starts the flow fresh from
 *   the beginning.
 * @param permissionsState Optional permissions state for testing. If null, creates one internally.
 * @param startDestination Optional route to open directly as the sole start destination, instead of showing [RegistrationRoute.Welcome] or restoring a previous flow.
 * @param modifier Modifier to be applied to the NavDisplay.
 * @param onRegistrationComplete Callback invoked when registration is successfully completed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RegistrationNavHost(
  registrationRepository: RegistrationRepository,
  registrationViewModel: RegistrationViewModel? = null,
  startFresh: Boolean = false,
  permissionsState: MultiplePermissionsState? = null,
  startDestination: RegistrationRoute? = null,
  modifier: Modifier = Modifier,
  onRegistrationComplete: () -> Unit = {}
) {
  val viewModel: RegistrationViewModel = registrationViewModel ?: viewModel(
    factory = RegistrationViewModel.Factory(registrationRepository, startDestination, startFresh)
  )

  val registrationState by viewModel.state.collectAsStateWithLifecycle()

  if (registrationState.isRestoringNavigationState) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  LaunchedEffect(viewModel, backDispatcher) {
    viewModel.finishRequests.collect {
      backDispatcher?.onBackPressed()
    }
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
    transitionSpec = { TransitionSpecs.HorizontalSlide.transitionSpec },
    popTransitionSpec = {
      when {
        initialState.key.toString().startsWith("EnterAepForLocalBackup") || initialState.key == RegistrationRoute.EnterAepForRemoteBackupPreRegistration.toString() -> {
          TransitionSpecs.HorizontalSlide.transitionSpec
        }

        initialState.key == RegistrationRoute.LocalBackupRestore.toString() && targetState.key == RegistrationRoute.PhoneNumberEntry.toString() -> {
          TransitionSpecs.HorizontalSlide.transitionSpec
        }

        else -> {
          TransitionSpecs.HorizontalSlide.popTransitionSpec
        }
      }
    },
    predictivePopTransitionSpec = { TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec }
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
    val context = LocalContext.current
    val termsAndPrivacyUrl = stringResource(R.string.terms_and_privacy_policy_url)

    val navigateRequestingPermissions = { nextRoute: RegistrationRoute ->
      if (RegistrationPermissions.hasAllRequiredPermissions(context)) {
        parentEventEmitter.navigateTo(nextRoute)
      } else {
        parentEventEmitter.navigateTo(RegistrationRoute.Permissions(nextRoute = nextRoute))
      }
    }

    WelcomeScreen(
      isLinkAndSyncAvailable = registrationRepository.isLinkAndSyncAvailable,
      onEvent = { event ->
        when (event) {
          WelcomeScreenEvents.Continue -> navigateRequestingPermissions(RegistrationRoute.PhoneNumberEntry)
          WelcomeScreenEvents.LinkDevice -> {
            if (registrationViewModel.getRequiredLinkedDevicePermission().isNullOrBlank()) {
              parentEventEmitter.navigateTo(RegistrationRoute.LinkAccount())
            } else {
              parentEventEmitter.navigateTo(RegistrationRoute.AllowNotifications(RegistrationRoute.LinkAccount()))
            }
          }
          WelcomeScreenEvents.HasOldPhone -> navigateRequestingPermissions(RegistrationRoute.QuickRestoreQrScan)
          WelcomeScreenEvents.DoesNotHaveOldPhone -> navigateRequestingPermissions(RegistrationRoute.ArchiveRestoreSelection.forManualRestore())
          WelcomeScreenEvents.ViewTermsAndPrivacy -> {
            LinkActions.openUrl(context, termsAndPrivacyUrl) { error ->
              when (error) {
                OpenUrlError.NoBrowserFound -> Toast.makeText(context, R.string.LinkActions_error_no_browser_found, Toast.LENGTH_SHORT).show()
              }
            }
          }
        }
      }
    )
  }

  // --- Permissions Screen
  entry<RegistrationRoute.Permissions> { key ->
    val context = LocalContext.current
    val onProceed = { parentEventEmitter.navigateTo(key.nextRoute) }
    val localPermissionsState = permissionsState ?: rememberMultiplePermissionsState(
      permissions = RegistrationPermissions.getRequiredPermissions(context),
      onPermissionsResult = { onProceed() }
    )
    PermissionsScreen(
      permissionsState = localPermissionsState,
      onProceed = onProceed
    )
  }

  // --- Allow Notifications Screen
  entry<RegistrationRoute.AllowNotifications> { key ->
    val onProceed = { parentEventEmitter.navigateTo(key.nextRoute) }
    val localPermissionState = rememberPermissionState(
      permission = registrationViewModel.getRequiredLinkedDevicePermission()!!,
      onPermissionResult = { onProceed() }
    )

    AllowNotificationsScreen(
      permissionState = localPermissionState,
      onProceed = onProceed
    )
  }

  // --- Link account Screen
  entry<RegistrationRoute.LinkAccount> { key ->
    val viewModel: LinkAccountViewModel = viewModel(
      factory = LinkAccountViewModel.Factory(
        repository = registrationRepository,
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent,
        showCreateAccount = key.showCreateAccount
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val url = "https://support.signal.org/hc/en-us/articles/360007320551"

    LinkAccountScreen(
      state = state,
      onEvent = {
        when (it) {
          LinkAccountScreenEvent.GetHelpClick -> LinkActions.openUrl(context, url) {
            Toast.makeText(context, R.string.LinkActions_error_no_browser_found, Toast.LENGTH_SHORT).show()
          }
          else -> viewModel.onEvent(it)
        }
      }
    )
  }

  // --- Message Sync Screen
  entry<RegistrationRoute.MessageSync> {
    val viewModel: MessageSyncViewModel = viewModel(
      factory = MessageSyncViewModel.Factory(
        repository = registrationRepository,
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val url = "https://support.signal.org/hc/articles/360007320391"

    MessageSyncScreen(
      state = state,
      onEvent = {
        when (it) {
          MessageSyncScreenEvent.LearnMoreClick -> LinkActions.openUrl(context, url) {
            Toast.makeText(context, R.string.LinkActions_error_no_browser_found, Toast.LENGTH_SHORT).show()
          }
          else -> viewModel.onEvent(it)
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

    ResultEffect<LocalBackupRestoreResult>(registrationViewModel.resultBus, LOCAL_BACKUP_RESTORE_RESULT) { result ->
      viewModel.onEvent(PhoneNumberEntryScreenEvents.LocalBackupRestoreCompleted(result))
    }

    PhoneNumberScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  // -- Country Code Picker
  entry<RegistrationRoute.CountryCodePicker>(metadata = TransitionSpecs.VerticalSlide.metadata) { key ->
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
    val context = LocalContext.current.applicationContext
    val viewModel: VerificationCodeViewModel = viewModel(
      factory = VerificationCodeViewModel.Factory(
        context = context,
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
    val context = LocalContext.current

    PinCreationScreen(
      state = state,
      onEvent = { event ->
        when (event) {
          PinCreationScreenEvents.LearnMore -> {
            LinkActions.openUrl(context, PIN_LEARN_MORE_URL) { error ->
              when (error) {
                OpenUrlError.NoBrowserFound -> Toast.makeText(context, R.string.LinkActions_error_no_browser_found, Toast.LENGTH_SHORT).show()
              }
            }
          }

          else -> viewModel.onEvent(event)
        }
      }
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
    val context = LocalContext.current
    val learnMoreUrl = stringResource(R.string.AccountLockedScreen__learn_more_url)
    AccountLockedScreen(
      state = AccountLockedState(daysRemaining = daysRemaining),
      onEvent = { event ->
        when (event) {
          AccountLockedScreenEvents.Next -> {
            // TODO: Navigate to appropriate next screen (likely back to welcome or phone entry)
            parentEventEmitter.navigateTo(RegistrationRoute.Welcome)
          }

          AccountLockedScreenEvents.LearnMore -> {
            LinkActions.openUrl(context, learnMoreUrl) { error ->
              when (error) {
                OpenUrlError.NoBrowserFound -> Toast.makeText(context, R.string.LinkActions_error_no_browser_found, Toast.LENGTH_SHORT).show()
              }
            }
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
        registeredState = key.registeredState,
        knownAep = key.aep,
        repository = registrationRepository,
        parentState = registrationViewModel.state,
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
        parentState = registrationViewModel.state,
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
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent,
        isPreRegistration = key.isPreRegistration,
        knownAep = key.aep,
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

    ResultEffect<EnterAepForLocalBackupResult>(registrationViewModel.resultBus, AEP_FOR_LOCAL_BACKUP_RESULT) { result ->
      when (result) {
        is EnterAepForLocalBackupResult.RestoreReady -> viewModel.onEvent(LocalBackupRestoreEvents.PassphraseSubmitted(result.key))
        is EnterAepForLocalBackupResult.RegistrationDeferredToSms -> viewModel.onEvent(LocalBackupRestoreEvents.RegistrationDeferredToSms)
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
  entry<RegistrationRoute.EnterAepForLocalBackup> { key ->
    val context = LocalContext.current
    val viewModel: EnterAepForLocalBackupViewModel = viewModel(
      factory = EnterAepForLocalBackupViewModel.Factory(
        isPreRegistration = key.isPreRegistration,
        backupUri = key.backupUri,
        repository = registrationRepository,
        parentState = registrationViewModel.state,
        parentEventEmitter = registrationViewModel::onEvent,
        resultBus = registrationViewModel.resultBus,
        resultKey = AEP_FOR_LOCAL_BACKUP_RESULT,
        isPasswordManagerAvailable = RegistrationCredentialManager.isSupported(context)
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnterAepScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  entry<RegistrationRoute.EnterAepForRemoteBackupPreRegistration> { key ->
    val context = LocalContext.current
    val viewModel: EnterAepForRemoteBackupPreRegistrationViewModel = viewModel(
      factory = EnterAepForRemoteBackupPreRegistrationViewModel.Factory(
        e164 = key.e164,
        repository = registrationRepository,
        parentEventEmitter = registrationViewModel::onEvent,
        isPasswordManagerAvailable = RegistrationCredentialManager.isSupported(context)
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnterAepScreen(
      state = state,
      onEvent = { viewModel.onEvent(it) }
    )
  }

  entry<RegistrationRoute.EnterAepForRemoteBackupPostRegistration> {
    val context = LocalContext.current
    val viewModel: EnterAepForRemoteBackupPostRegistrationViewModel = viewModel(
      factory = EnterAepForRemoteBackupPostRegistrationViewModel.Factory(
        repository = registrationRepository,
        parentEventEmitter = registrationViewModel::onEvent,
        isPasswordManagerAvailable = RegistrationCredentialManager.isSupported(context)
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

  // -- Device Transfer: Instructions
  entry<RegistrationRoute.DeviceTransferInstructions> {
    val viewModel: DeviceTransferInstructionsViewModel = viewModel(
      factory = DeviceTransferInstructionsViewModel.Factory(parentEventEmitter)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeviceTransferInstructionsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  // -- Device Transfer: Setup (permissions, wifi, verify SAS)
  entry<RegistrationRoute.DeviceTransferSetup> {
    val context = LocalContext.current.applicationContext
    val viewModel: DeviceTransferSetupViewModel = viewModel(
      factory = DeviceTransferSetupViewModel.Factory(
        context = context,
        networkController = RegistrationDependencies.get().networkController,
        setupEvents = DeviceTransferSetupViewModel.transferStatusFlow(),
        parentState = registrationViewModel.state,
        parentEventEmitter = parentEventEmitter
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeviceTransferSetupScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  // -- Device Transfer: Progress (receiving + importing)
  entry<RegistrationRoute.DeviceTransferProgress> {
    val context = LocalContext.current.applicationContext
    val viewModel: DeviceTransferProgressViewModel = viewModel(
      factory = DeviceTransferProgressViewModel.Factory(
        context = context,
        progressEvents = DeviceTransferProgressViewModel.restoreStatusFlow(),
        parentEventEmitter = parentEventEmitter
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showCancelDialog by viewModel.showCancelDialog.collectAsState()
    DeviceTransferProgressScreen(
      state = state,
      showCancelDialog = showCancelDialog,
      onEvent = viewModel::onEvent
    )
  }

  // -- Device Transfer: Complete
  entry<RegistrationRoute.DeviceTransferComplete> {
    val viewModel: DeviceTransferCompleteViewModel = viewModel(
      factory = DeviceTransferCompleteViewModel.Factory(registrationRepository, parentEventEmitter)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeviceTransferCompleteScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  entry<RegistrationRoute.Profile> {
    val viewModel: CreateProfileViewModel = viewModel(
      factory = CreateProfileViewModel.Factory(
        repository = registrationRepository,
        parentEventEmitter = registrationViewModel::onEvent
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    ResultEffect<Boolean>(registrationViewModel.resultBus, PHONE_NUMBER_DISCOVERABILITY_RESULT) { discoverable ->
      viewModel.onEvent(CreateProfileScreenEvents.DiscoverabilityChanged(discoverable))
    }

    CreateProfileScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  entry<RegistrationRoute.PhoneNumberDiscoverability> { key ->
    val viewModel: PhoneNumberDiscoverabilityViewModel = viewModel(
      factory = PhoneNumberDiscoverabilityViewModel.Factory(
        initialDiscoverable = key.initialDiscoverable,
        parentEventEmitter = registrationViewModel::onEvent,
        resultBus = registrationViewModel.resultBus,
        resultKey = PHONE_NUMBER_DISCOVERABILITY_RESULT
      )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    PhoneNumberDiscoverabilityScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  entry<RegistrationRoute.FullyComplete> {
    LaunchedEffect(Unit) {
      onRegistrationComplete()
    }
  }
}
