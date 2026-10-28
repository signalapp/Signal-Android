/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.editprofile.EditProfileAction
import org.signal.appsettings.editprofile.EditProfileEvent
import org.signal.appsettings.editprofile.EditProfileState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult

/**
 * Drives the user's own profile. Avatar changes and username deletion are saved here; everything else opens its own screen.
 */
class EditProfileViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<EditProfileEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(EditProfileViewModel::class)
  }

  private val _state = MutableStateFlow(refresh(EditProfileState()))

  /** The username as of the last time the screen resumed. Resuming with a different one means the user just set it. */
  private var usernameWhenLastResumed: String? = _state.value.username
  private val _actions = Channel<EditProfileAction>(Channel.BUFFERED)

  val state: StateFlow<EditProfileState> = _state.asStateFlow()
  val actions: Flow<EditProfileAction> = _actions.receiveAsFlow()

  init {
    EditProfileRepository.refreshOwnProfile()

    viewModelScope.launch {
      EditProfileRepository.observeSelf().collect { self ->
        _state.update {
          refresh(it).copy(
            profileName = self.profileName.toString(),
            about = self.about.orEmpty(),
            aboutEmoji = self.aboutEmoji.orEmpty()
          )
        }
      }
    }
  }

  override suspend fun processEvent(event: EditProfileEvent) {
    when (event) {
      EditProfileEvent.ScreenResumed -> onScreenResumed()
      EditProfileEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      EditProfileEvent.AvatarClicked -> ifEditable { _actions.send(EditProfileAction.OpenAvatarPreview) }
      EditProfileEvent.EditPhotoClicked -> ifEditable { parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.AvatarPicker) }
      is EditProfileEvent.AvatarSelected -> ifEditable { setAvatar(event.media) }
      EditProfileEvent.AvatarCleared -> ifEditable { clearAvatar() }
      EditProfileEvent.NameClicked -> ifEditable { parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.ProfileName) }
      EditProfileEvent.AboutClicked -> ifEditable { parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.About) }
      EditProfileEvent.BadgesClicked -> ifEditable {
        if (EditProfileRepository.hasBadges()) {
          parentEventEmitter.navigateTo(AppSettingsRoute.DonationsRoute.Badges)
        } else {
          _state.update { it.copy(dialog = EditProfileState.Dialog.BecomeASustainer) }
          loadSustainerBadge()
        }
      }
      EditProfileEvent.BecomeASustainerClicked -> {
        _state.update { it.copy(dialog = EditProfileState.Dialog.None) }
        _actions.send(EditProfileAction.OpenSubscriptions)
      }
      EditProfileEvent.UsernameClicked -> ifEditable {
        if (EditProfileRepository.getUsername() != null) {
          _state.update { it.copy(dialog = EditProfileState.Dialog.UsernameOptions) }
        } else {
          parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.Username())
        }
      }
      EditProfileEvent.EditUsernameClicked -> {
        _state.update { it.copy(dialog = EditProfileState.Dialog.None) }
        parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.Username())
      }
      EditProfileEvent.DeleteUsernameClicked -> {
        val username = EditProfileRepository.getUsername()
        _state.update { it.copy(dialog = if (username != null) EditProfileState.Dialog.ConfirmDeleteUsername(username) else EditProfileState.Dialog.None) }
      }
      EditProfileEvent.DeleteUsernameConfirmed -> deleteUsername()
      EditProfileEvent.UsernameLinkClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.UsernameLinkRoute.UsernameLink)
      EditProfileEvent.UsernameLinkTooltipDismissed -> {
        EditProfileRepository.markUsernameLinkTooltipSeen()
        _state.update { it.copy(showUsernameLinkTooltip = false) }
      }
      EditProfileEvent.UpdateAppClicked -> {
        _state.update { it.copy(dialog = EditProfileState.Dialog.None) }
        _actions.send(EditProfileAction.OpenAppUpdate)
      }
      EditProfileEvent.ReregisterClicked -> {
        _state.update { it.copy(dialog = EditProfileState.Dialog.None) }
        _actions.send(EditProfileAction.StartReregistration)
      }
      EditProfileEvent.DialogDismissed -> _state.update { it.copy(dialog = EditProfileState.Dialog.None) }
      EditProfileEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = EditProfileState.Snackbar.None) }
    }
  }

  /** Waits for a resume, rather than the username changing, because until then the screen may still be covered by the one being left. */
  private fun onScreenResumed() {
    _state.update { refresh(it) }

    val username = _state.value.username
    if (username != null && username != usernameWhenLastResumed) {
      _state.update { it.copy(snackbar = EditProfileState.Snackbar.UsernameCreated) }
    }
    usernameWhenLastResumed = username
  }

  /** Loads the badge shown in [EditProfileState.Dialog.BecomeASustainer] off the event queue, since it comes from the network. */
  private fun loadSustainerBadge() {
    if (_state.value.sustainerBadge != null) {
      return
    }

    viewModelScope.launch {
      val badge = EditProfileRepository.getSustainerBadge()
      _state.update { it.copy(sustainerBadge = badge) }
    }
  }

  /** Runs [block] only if the profile can be edited, and otherwise explains why it can't. */
  private suspend fun ifEditable(block: suspend () -> Unit) {
    if (EditProfileRepository.canEdit()) {
      block()
    } else {
      val dialog = if (EditProfileRepository.isClientDeprecated()) EditProfileState.Dialog.Deprecated else EditProfileState.Dialog.Unregistered
      _state.update { it.copy(dialog = dialog) }
    }
  }

  private suspend fun setAvatar(media: Media) {
    _state.update { it.copy(busy = true) }

    val data = EditProfileRepository.readAvatar(media)
    if (data == null) {
      _state.update { it.copy(busy = false, snackbar = EditProfileState.Snackbar.AvatarReadFailed) }
      return
    }

    val result = EditProfileRepository.setAvatar(data, media.contentType)
    _state.update { it.copy(busy = false, snackbar = result.toSnackbar()) }
  }

  private suspend fun clearAvatar() {
    _state.update { it.copy(busy = true) }
    val result = EditProfileRepository.clearAvatar()
    _state.update { it.copy(busy = false, snackbar = result.toSnackbar()) }
  }

  private suspend fun deleteUsername() {
    _state.update { it.copy(dialog = EditProfileState.Dialog.None, busy = true) }

    val snackbar = when (EditProfileRepository.deleteUsername()) {
      UsernameDeleteResult.SUCCESS -> EditProfileState.Snackbar.UsernameDeleted
      UsernameDeleteResult.NETWORK_ERROR -> EditProfileState.Snackbar.UsernameDeleteFailed
    }

    _state.update { refresh(it).copy(busy = false, snackbar = snackbar) }
  }

  /**
   * Re-reads everything that lives outside the self recipient, which can change while the screen is in the background.
   */
  private fun refresh(state: EditProfileState): EditProfileState {
    val username = EditProfileRepository.getUsername()

    return state.copy(
      username = username,
      usernameSyncState = EditProfileRepository.getUsernameSyncState(),
      showUsernameLinkTooltip = EditProfileRepository.shouldShowUsernameLinkTooltip(),
      canEdit = EditProfileRepository.canEdit()
    )
  }

  private fun EditProfileRepository.SaveResult.toSnackbar(): EditProfileState.Snackbar {
    return when (this) {
      EditProfileRepository.SaveResult.SUCCESS -> EditProfileState.Snackbar.None
      EditProfileRepository.SaveResult.NETWORK_FAILURE -> EditProfileState.Snackbar.AvatarNetworkFailure
    }
  }
}
