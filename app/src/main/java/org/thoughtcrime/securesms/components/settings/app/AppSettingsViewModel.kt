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
import org.signal.appsettings.routes.navigateTo
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
      AppSettingsEvent.ProfileClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.ManageProfile)
      AppSettingsEvent.UsernameLinkClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.UsernameLinkRoute.UsernameLink)
      AppSettingsEvent.BackupWarningClicked -> onBackupWarningClicked()
      AppSettingsEvent.AccountClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.Account)
      AppSettingsEvent.LinkedDevicesClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.LinkDeviceRoute.LinkDevice)
      AppSettingsEvent.DonateClicked -> {
        if (_state.value.allowUserToGoToDonationManagementScreen) {
          parentEventEmitter.navigateTo(AppSettingsRoute.DonationsRoute.Donations())
        } else {
          _actions.send(AppSettingsAction.OpenDonateUrl)
        }
      }
      AppSettingsEvent.DonateLongClicked -> copySubscriberId(InAppPaymentSubscriberRecord.Type.DONATION, Snackbar.CopiedDonorSubscriberId)
      AppSettingsEvent.AppearanceClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.AppearanceRoute.Appearance)
      AppSettingsEvent.ChatsClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.ChatsRoute.Chats)
      AppSettingsEvent.StoriesClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.StoriesRoute.Privacy(titleId = R.string.preferences__stories))
      AppSettingsEvent.NotificationsClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.NotificationsRoute.Notifications)
      AppSettingsEvent.PrivacyClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.Privacy)
      AppSettingsEvent.BackupsClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.BackupsRoute.Backups())
      AppSettingsEvent.BackupsLongClicked -> {
        if (_state.value.isPrimaryDevice) {
          copySubscriberId(InAppPaymentSubscriberRecord.Type.BACKUP, Snackbar.CopiedBackupsSubscriberId)
        }
      }
      AppSettingsEvent.DataAndStorageClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.DataAndStorageRoute.DataAndStorage)
      AppSettingsEvent.AppUpdatesClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.AppUpdates)
      AppSettingsEvent.PaymentsClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.Payments)
      AppSettingsEvent.HelpClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.HelpRoute.Settings())
      AppSettingsEvent.InviteClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.Invite)
      AppSettingsEvent.LabsClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.LabsRoute.Labs)
      AppSettingsEvent.InternalClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.InternalRoute.Internal)
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

    parentEventEmitter.navigateTo(AppSettingsRoute.BackupsRoute.Remote())
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
