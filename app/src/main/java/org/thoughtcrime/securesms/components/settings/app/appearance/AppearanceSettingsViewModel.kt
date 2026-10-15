/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.appearance.AppearanceSettingsAction
import org.signal.appsettings.appearance.AppearanceSettingsEvent
import org.signal.appsettings.appearance.AppearanceSettingsState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [org.signal.appsettings.appearance.AppearanceSettingsScreen].
 */
class AppearanceSettingsViewModel : EventDrivenViewModel<AppearanceSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(AppearanceSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(readState())
  private val _actions = Channel<AppearanceSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<AppearanceSettingsState> = _state.asStateFlow()
  val actions: Flow<AppearanceSettingsAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: AppearanceSettingsEvent) {
    when (event) {
      AppearanceSettingsEvent.NavigateBackClicked -> _actions.send(AppearanceSettingsAction.NavigateBack)

      is AppearanceSettingsEvent.LanguageSelected -> {
        _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.ConfirmLanguageChange(event.language)) }
      }

      AppearanceSettingsEvent.LanguageChangeConfirmed -> {
        val dialog = _state.value.dialog as? AppearanceSettingsState.Dialog.ConfirmLanguageChange ?: return
        _state.update { it.copy(language = dialog.language, dialog = AppearanceSettingsState.Dialog.None) }
        AppearanceSettingsRepository.applyLanguage(dialog.language)
      }

      is AppearanceSettingsEvent.ThemeSelected -> {
        AppearanceSettingsRepository.setTheme(event.theme)
        _state.update { it.copy(theme = event.theme) }
        _actions.send(AppearanceSettingsAction.ApplySplashScreenTheme(event.theme))
      }

      AppearanceSettingsEvent.ChatColorAndWallpaperClicked -> _actions.send(AppearanceSettingsAction.NavigateToChatColorAndWallpaper)

      AppearanceSettingsEvent.AppIconClicked -> _actions.send(AppearanceSettingsAction.NavigateToAppIcon)

      is AppearanceSettingsEvent.MessageFontSizeSelected -> {
        AppearanceSettingsRepository.setMessageFontSize(event.size)
        _state.update { it.copy(messageFontSize = event.size) }
      }

      AppearanceSettingsEvent.NavigationBarSizeClicked -> _actions.send(AppearanceSettingsAction.ShowNavigationBarSizePicker)

      AppearanceSettingsEvent.NavigationBarSizeChanged -> {
        _state.update { it.copy(isCompactNavigationBar = AppearanceSettingsRepository.isCompactNavigationBar()) }
      }

      AppearanceSettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.None) }
    }
  }

  private fun readState(): AppearanceSettingsState {
    return AppearanceSettingsState(
      language = AppearanceSettingsRepository.getLanguage(),
      theme = AppearanceSettingsRepository.getTheme(),
      messageFontSize = AppearanceSettingsRepository.getMessageFontSize(),
      isCompactNavigationBar = AppearanceSettingsRepository.isCompactNavigationBar(),
      isSystemThemeAvailable = AppearanceSettingsRepository.isSystemThemeAvailable(),
      isAppIconSupported = AppearanceSettingsRepository.isAppIconSupported()
    )
  }
}
