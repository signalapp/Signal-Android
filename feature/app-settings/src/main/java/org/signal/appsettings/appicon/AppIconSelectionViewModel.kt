/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appicon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [AppIconSelectionScreen].
 */
class AppIconSelectionViewModel(
  private val appIconUtility: AppIconUtility,
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<AppIconSelectionEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(AppIconSelectionViewModel::class)
  }

  private val _state = MutableStateFlow(AppIconSelectionState(currentIcon = appIconUtility.currentAppIcon))

  val state: StateFlow<AppIconSelectionState> = _state.asStateFlow()

  override suspend fun processEvent(event: AppIconSelectionEvent) {
    when (event) {
      AppIconSelectionEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      AppIconSelectionEvent.LearnMoreClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.AppearanceRoute.AppIconTutorial)
      is AppIconSelectionEvent.IconClicked -> {
        if (event.preset != _state.value.selectedIcon) {
          _state.update { it.copy(dialog = AppIconSelectionState.Dialog.ConfirmIconChange(event.preset)) }
        }
      }
      AppIconSelectionEvent.ChangeConfirmed -> applyPendingIcon()
      AppIconSelectionEvent.DialogDismissed -> _state.update { it.copy(dialog = AppIconSelectionState.Dialog.None) }
    }
  }

  private fun applyPendingIcon() {
    val dialog = _state.value.dialog as? AppIconSelectionState.Dialog.ConfirmIconChange ?: return

    if (!appIconUtility.isCurrentlySelected(dialog.preset)) {
      appIconUtility.setNewAppIcon(dialog.preset)
    }

    _state.update { it.copy(currentIcon = dialog.preset, dialog = AppIconSelectionState.Dialog.None) }
  }
}
