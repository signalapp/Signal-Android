/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.appearance.AppearanceSettingsAction
import org.signal.appsettings.appearance.AppearanceSettingsEvent
import org.signal.appsettings.appearance.AppearanceSettingsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.locale.LanguageAvailability
import org.signal.locale.LanguageInstallStatus

/**
 * Drives [org.signal.appsettings.appearance.AppearanceSettingsScreen]. Navigation goes straight to [parentEventEmitter].
 */
class AppearanceSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<AppearanceSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(AppearanceSettingsViewModel::class)
    private const val APK_DOWNLOAD_URL = "https://signal.org/android/apk"
  }

  private val _state = MutableStateFlow(readState())
  private val _actions = Channel<AppearanceSettingsAction>(Channel.BUFFERED)
  private var languageDownloadJob: Job? = null

  val state: StateFlow<AppearanceSettingsState> = _state.asStateFlow()
  val actions: Flow<AppearanceSettingsAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: AppearanceSettingsEvent) {
    when (event) {
      AppearanceSettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()

      is AppearanceSettingsEvent.LanguageSelected -> {
        val dialog = when (AppearanceSettingsRepository.getLanguageAvailability(event.language)) {
          LanguageAvailability.Installed -> AppearanceSettingsState.Dialog.ConfirmLanguageChange(event.language, needsDownload = false)
          LanguageAvailability.NeedsDownload -> AppearanceSettingsState.Dialog.ConfirmLanguageChange(event.language, needsDownload = true)
          LanguageAvailability.PlayStoreDisabled -> AppearanceSettingsState.Dialog.LanguageDownloadFailed(event.language, AppearanceSettingsState.LanguageDownloadFailure.PlayStoreDisabled)
          LanguageAvailability.PlayServicesDisabled -> AppearanceSettingsState.Dialog.LanguageDownloadFailed(event.language, AppearanceSettingsState.LanguageDownloadFailure.PlayServicesDisabled)
          LanguageAvailability.PlayStoreMissing -> AppearanceSettingsState.Dialog.LanguageDownloadFailed(event.language, AppearanceSettingsState.LanguageDownloadFailure.PlayUnavailable)
        }
        _state.update { it.copy(dialog = dialog) }
      }

      AppearanceSettingsEvent.LanguageChangeConfirmed -> {
        val dialog = _state.value.dialog as? AppearanceSettingsState.Dialog.ConfirmLanguageChange ?: return
        installThenApply(dialog.language, showProgress = dialog.needsDownload)
      }

      AppearanceSettingsEvent.LanguageUnavailableWebsiteClicked -> {
        _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.None) }
        _actions.send(AppearanceSettingsAction.OpenBrowserLink(APK_DOWNLOAD_URL))
      }

      AppearanceSettingsEvent.PlayStoreSettingsClicked -> {
        _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.None) }
        _actions.send(AppearanceSettingsAction.OpenPlayStoreSettings)
      }

      AppearanceSettingsEvent.PlayServicesSettingsClicked -> {
        _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.None) }
        _actions.send(AppearanceSettingsAction.OpenPlayServicesSettings)
      }

      AppearanceSettingsEvent.LanguageDownloadConfirmationDeclined -> {
        if (_state.value.dialog is AppearanceSettingsState.Dialog.DownloadingLanguage) {
          cancelLanguageDownload()
          _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.None) }
        }
      }

      AppearanceSettingsEvent.LanguageDownloadRetried -> {
        val dialog = _state.value.dialog as? AppearanceSettingsState.Dialog.LanguageDownloadFailed ?: return
        installThenApply(dialog.language, showProgress = true)
      }

      is AppearanceSettingsEvent.ThemeSelected -> {
        AppearanceSettingsRepository.setTheme(event.theme)
        _state.update { it.copy(theme = event.theme) }
        _actions.send(AppearanceSettingsAction.ApplySplashScreenTheme(event.theme))
      }

      AppearanceSettingsEvent.ChatColorAndWallpaperClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.AppearanceRoute.Wallpaper)

      AppearanceSettingsEvent.AppIconClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.AppearanceRoute.AppIconSelection)

      is AppearanceSettingsEvent.MessageFontSizeSelected -> {
        AppearanceSettingsRepository.setMessageFontSize(event.size)
        _state.update { it.copy(messageFontSize = event.size) }
      }

      AppearanceSettingsEvent.NavigationBarSizeClicked -> {
        _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.NavigationBarSize(isCompact = it.isCompactNavigationBar)) }
      }

      is AppearanceSettingsEvent.NavigationBarSizeSelected -> {
        val dialog = _state.value.dialog as? AppearanceSettingsState.Dialog.NavigationBarSize ?: return
        _state.update { it.copy(dialog = dialog.copy(isCompact = event.isCompact)) }
      }

      AppearanceSettingsEvent.NavigationBarSizeConfirmed -> {
        val dialog = _state.value.dialog as? AppearanceSettingsState.Dialog.NavigationBarSize ?: return
        AppearanceSettingsRepository.setCompactNavigationBar(dialog.isCompact)
        _state.update { it.copy(isCompactNavigationBar = dialog.isCompact, dialog = AppearanceSettingsState.Dialog.None) }
      }

      AppearanceSettingsEvent.DialogDismissed -> {
        if (_state.value.dialog is AppearanceSettingsState.Dialog.DownloadingLanguage) {
          cancelLanguageDownload()
        }
        _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.None) }
      }
    }
  }

  private fun installThenApply(language: String, showProgress: Boolean) {
    _state.update { it.copy(dialog = if (showProgress) AppearanceSettingsState.Dialog.DownloadingLanguage else AppearanceSettingsState.Dialog.None) }
    languageDownloadJob?.cancel()
    languageDownloadJob = viewModelScope.launch {
      AppearanceSettingsRepository.installLanguage(language).collect { installState ->
        when (installState) {
          is LanguageInstallStatus.Downloading -> _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.DownloadingLanguage) }
          is LanguageInstallStatus.NeedsPlayConfirmation -> _actions.send(AppearanceSettingsAction.ConfirmLanguageDownload(installState.sessionId))
          is LanguageInstallStatus.Installed -> applyLanguage(language)
          is LanguageInstallStatus.Cancelled -> _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.None) }
          is LanguageInstallStatus.Failed -> {
            Log.w(TAG, "Language pack download failed, errorCode=${installState.playErrorCode}")
            _state.update { it.copy(dialog = AppearanceSettingsState.Dialog.LanguageDownloadFailed(language, installState.toLanguageDownloadFailure())) }
          }
        }
      }
    }
  }

  private fun cancelLanguageDownload() {
    languageDownloadJob?.cancel()
    languageDownloadJob = null
  }

  private fun applyLanguage(language: String) {
    _state.update { it.copy(language = language, dialog = AppearanceSettingsState.Dialog.None) }
    AppearanceSettingsRepository.applyLanguage(language)
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
