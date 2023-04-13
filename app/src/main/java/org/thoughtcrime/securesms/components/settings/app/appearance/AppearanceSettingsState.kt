package org.thoughtcrime.securesms.components.settings.app.appearance

import org.thoughtcrime.securesms.keyvalue.SettingsValues

data class AppearanceSettingsState(
  val theme: SettingsValues.Theme,
  val messageFontSize: Int,
  val language: String,
  val isCompactNavigationBar: Boolean
)
