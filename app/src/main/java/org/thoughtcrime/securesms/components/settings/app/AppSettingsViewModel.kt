package org.thoughtcrime.securesms.components.settings.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.home.AppSettingsAction
import org.signal.appsettings.home.AppSettingsEvent
import org.signal.appsettings.home.AppSettingsState
import org.signal.appsettings.home.AppSettingsState.Snackbar
import org.signal.appsettings.home.BackupFailureState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.openSection
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord

/**
 * Drives [org.signal.appsettings.home.AppSettingsScreen]. Navigation goes straight to [parentEventEmitter].
 */
class AppSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<AppSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(AppSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(
    AppSettingsState(
      isPrimaryDevice = AppSettingsRepository.isPrimaryDevice(),
      allowUserToGoToDonationManagementScreen = AppSettingsRepository.likelyCanManageDonations(),
      showInternalPreferences = AppSettingsRepository.showInternalPreferences(),
      showPayments = AppSettingsRepository.showPayments(),
      showAppUpdates = AppSettingsRepository.showAppUpdates()
    )
  )
  private val _actions = Channel<AppSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<AppSettingsState> = _state.asStateFlow()
  val actions: Flow<AppSettingsAction> = _actions.receiveAsFlow()

  init {
    refresh()

    viewModelScope.launch {
      AppSettingsRepository.observeProfile().collect { profile -> _state.update { it.copy(profile = profile) } }
    }

    viewModelScope.launch {
      AppSettingsRepository.observeUnreadPaymentsCount().collect { count -> _state.update { it.copy(unreadPaymentsCount = count) } }
    }

    viewModelScope.launch {
      val canManageDonations = AppSettingsRepository.canManageDonations() ?: return@launch
      _state.update { it.copy(allowUserToGoToDonationManagementScreen = canManageDonations) }
    }
  }

  override suspend fun processEvent(event: AppSettingsEvent) {
    when (event) {
      AppSettingsEvent.ScreenResumed -> refresh()
      AppSettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      AppSettingsEvent.ProfileClicked -> parentEventEmitter.openSection(AppSettingsRoute.AccountRoute.ManageProfile)
      AppSettingsEvent.UsernameLinkClicked -> parentEventEmitter.openSection(AppSettingsRoute.UsernameLinkRoute.UsernameLink)
      AppSettingsEvent.BackupWarningClicked -> onBackupWarningClicked()
      AppSettingsEvent.AccountClicked -> parentEventEmitter.openSection(AppSettingsRoute.AccountRoute.Account)
      AppSettingsEvent.LinkedDevicesClicked -> parentEventEmitter.openSection(AppSettingsRoute.LinkDeviceRoute.LinkDevice)
      AppSettingsEvent.DonateClicked -> {
        if (_state.value.allowUserToGoToDonationManagementScreen) {
          parentEventEmitter.openSection(AppSettingsRoute.DonationsRoute.Donations())
        } else {
          _actions.send(AppSettingsAction.OpenDonateUrl)
        }
      }
      AppSettingsEvent.DonateLongClicked -> copySubscriberId(InAppPaymentSubscriberRecord.Type.DONATION, Snackbar.CopiedDonorSubscriberId)
      AppSettingsEvent.AppearanceClicked -> parentEventEmitter.openSection(AppSettingsRoute.AppearanceRoute.Appearance)
      AppSettingsEvent.ChatsClicked -> parentEventEmitter.openSection(AppSettingsRoute.ChatsRoute.Chats)
      AppSettingsEvent.StoriesClicked -> parentEventEmitter.openSection(AppSettingsRoute.StoriesRoute.Privacy(titleId = R.string.preferences__stories))
      AppSettingsEvent.NotificationsClicked -> parentEventEmitter.openSection(AppSettingsRoute.NotificationsRoute.Notifications)
      AppSettingsEvent.PrivacyClicked -> parentEventEmitter.openSection(AppSettingsRoute.PrivacyRoute.Privacy)
      AppSettingsEvent.BackupsClicked -> parentEventEmitter.openSection(AppSettingsRoute.BackupsRoute.Backups())
      AppSettingsEvent.BackupsLongClicked -> {
        if (_state.value.isPrimaryDevice) {
          copySubscriberId(InAppPaymentSubscriberRecord.Type.BACKUP, Snackbar.CopiedBackupsSubscriberId)
        }
      }
      AppSettingsEvent.DataAndStorageClicked -> parentEventEmitter.openSection(AppSettingsRoute.DataAndStorageRoute.DataAndStorage)
      AppSettingsEvent.AppUpdatesClicked -> parentEventEmitter.openSection(AppSettingsRoute.AppUpdates)
      AppSettingsEvent.PaymentsClicked -> parentEventEmitter.openSection(AppSettingsRoute.Payments)
      AppSettingsEvent.HelpClicked -> parentEventEmitter.openSection(AppSettingsRoute.HelpRoute.Settings)
      AppSettingsEvent.InviteClicked -> parentEventEmitter.openSection(AppSettingsRoute.Invite)
      AppSettingsEvent.LabsClicked -> parentEventEmitter.openSection(AppSettingsRoute.LabsRoute.Labs)
      AppSettingsEvent.InternalClicked -> parentEventEmitter.openSection(AppSettingsRoute.InternalRoute.Internal)
      AppSettingsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = Snackbar.None) }
    }
  }

  private fun onBackupWarningClicked() {
    when (_state.value.backupFailureState) {
      BackupFailureState.BACKUP_FAILED, BackupFailureState.COULD_NOT_COMPLETE_BACKUP -> AppSettingsRepository.markBackupFailedIndicatorClicked()
      BackupFailureState.ALREADY_REDEEMED -> AppSettingsRepository.markBackupAlreadyRedeemedIndicatorClicked()
      BackupFailureState.SUBSCRIPTION_STATE_MISMATCH, BackupFailureState.OUT_OF_STORAGE_SPACE -> Unit
      BackupFailureState.NONE -> return
    }

    parentEventEmitter.openSection(AppSettingsRoute.BackupsRoute.Remote())
  }

  private suspend fun copySubscriberId(type: InAppPaymentSubscriberRecord.Type, snackbar: Snackbar) {
    if (AppSettingsRepository.copySubscriberIdToClipboard(type)) {
      _state.update { it.copy(snackbar = snackbar) }
    }
  }

  private fun refresh() {
    _state.update {
      it.copy(
        hasExpiredGiftBadge = AppSettingsRepository.hasExpiredGiftBadge(),
        backupFailureState = AppSettingsRepository.getBackupFailureState(),
        clientDeprecated = AppSettingsRepository.isClientDeprecated(),
        userUnregistered = AppSettingsRepository.isUserUnregistered()
      )
    }
  }
}
