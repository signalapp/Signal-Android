/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance

import android.os.Build
import org.signal.appsettings.appearance.AppearanceSettingsState
import org.signal.core.util.AppUtil
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.DynamicTheme

/**
 * All of the storage access behind [AppearanceSettingsViewModel].
 */
object AppearanceSettingsRepository {

  fun getLanguage(): String = PlainTextKeyValueStore.language

  /** Stores the new language and restarts the app so it takes effect. */
  fun applyLanguage(language: String) {
    PlainTextKeyValueStore.language = language
    EmojiSearchIndexDownloadJob.scheduleImmediately()
    AppUtil.restart(AppDependencies.application)
  }

  fun getTheme(): AppearanceSettingsState.Theme = SignalStore.settings.theme.toAppearanceTheme()

  fun setTheme(theme: AppearanceSettingsState.Theme) {
    SignalStore.settings.theme = theme.toSettingsTheme()
  }

  fun getMessageFontSize(): Int = SignalStore.settings.messageFontSize

  fun setMessageFontSize(size: Int) {
    SignalStore.settings.messageFontSize = size
  }

  fun isCompactNavigationBar(): Boolean = SignalStore.settings.useCompactNavigationBar

  fun isSystemThemeAvailable(): Boolean = DynamicTheme.systemThemeAvailable()

  fun isAppIconSupported(): Boolean = Build.VERSION.SDK_INT >= 26
}

fun SettingsValues.Theme.toAppearanceTheme(): AppearanceSettingsState.Theme {
  return when (this) {
    SettingsValues.Theme.SYSTEM -> AppearanceSettingsState.Theme.SYSTEM
    SettingsValues.Theme.LIGHT -> AppearanceSettingsState.Theme.LIGHT
    SettingsValues.Theme.DARK -> AppearanceSettingsState.Theme.DARK
  }
}

fun AppearanceSettingsState.Theme.toSettingsTheme(): SettingsValues.Theme {
  return when (this) {
    AppearanceSettingsState.Theme.SYSTEM -> SettingsValues.Theme.SYSTEM
    AppearanceSettingsState.Theme.LIGHT -> SettingsValues.Theme.LIGHT
    AppearanceSettingsState.Theme.DARK -> SettingsValues.Theme.DARK
  }
}
