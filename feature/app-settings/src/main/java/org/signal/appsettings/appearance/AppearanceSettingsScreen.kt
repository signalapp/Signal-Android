/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme

@VisibleForTesting
object AppearanceSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_LANGUAGE = "row-language"
  const val ROW_THEME = "row-theme"
  const val ROW_CHAT_COLOR_AND_WALLPAPER = "row-chat-color-and-wallpaper"
  const val ROW_APP_ICON = "row-app-icon"
  const val ROW_MESSAGE_FONT_SIZE = "row-message-font-size"
  const val ROW_NAVIGATION_BAR_SIZE = "row-navigation-bar-size"
  const val BUTTON_NAVIGATION_BAR_NORMAL = "button-navigation-bar-normal"
  const val BUTTON_NAVIGATION_BAR_COMPACT = "button-navigation-bar-compact"
  const val BUTTON_NAVIGATION_BAR_CONFIRM = "button-navigation-bar-confirm"
}

/**
 * Allows the user to change language, theme, etc. from application settings.
 */
@Composable
fun AppearanceSettingsScreen(
  state: AppearanceSettingsState,
  onEvent: (AppearanceSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__appearance),
    onNavigationClick = { onEvent(AppearanceSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier,
    collapseTopBarInSplitPane = true
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues,
      modifier = Modifier.testTag(AppearanceSettingsTestTags.SCROLLER)
    ) {
      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences__language),
          labels = stringArrayResource(R.array.language_entries),
          values = stringArrayResource(R.array.language_values),
          selectedValue = state.language,
          onSelected = { onEvent(AppearanceSettingsEvent.LanguageSelected(it)) },
          modifier = Modifier.testTag(AppearanceSettingsTestTags.ROW_LANGUAGE)
        )
      }

      item {
        val themes = state.availableThemes

        Rows.RadioListRow(
          text = stringResource(R.string.preferences__theme),
          labels = themes.map { stringResource(it.labelRes) }.toTypedArray(),
          values = themes.map { it.name }.toTypedArray(),
          selectedValue = state.theme.name,
          onSelected = { onEvent(AppearanceSettingsEvent.ThemeSelected(AppearanceSettingsState.Theme.valueOf(it))) },
          modifier = Modifier.testTag(AppearanceSettingsTestTags.ROW_THEME)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences__chat_color_and_wallpaper),
          onClick = { onEvent(AppearanceSettingsEvent.ChatColorAndWallpaperClicked) },
          modifier = Modifier.testTag(AppearanceSettingsTestTags.ROW_CHAT_COLOR_AND_WALLPAPER)
        )
      }

      if (state.isAppIconSupported) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__app_icon),
            onClick = { onEvent(AppearanceSettingsEvent.AppIconClicked) },
            modifier = Modifier.testTag(AppearanceSettingsTestTags.ROW_APP_ICON)
          )
        }
      }

      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences_chats__message_text_size),
          labels = stringArrayResource(R.array.pref_message_font_size_entries),
          values = integerArrayResource(R.array.pref_message_font_size_values).map { it.toString() }.toTypedArray(),
          selectedValue = state.messageFontSize.toString(),
          onSelected = { onEvent(AppearanceSettingsEvent.MessageFontSizeSelected(it.toInt())) },
          modifier = Modifier.testTag(AppearanceSettingsTestTags.ROW_MESSAGE_FONT_SIZE)
        )
      }

      item {
        val label = if (state.isCompactNavigationBar) {
          R.string.preferences_compact
        } else {
          R.string.preferences_normal
        }

        Rows.TextRow(
          text = stringResource(R.string.preferences_navigation_bar_size),
          label = stringResource(label),
          onClick = { onEvent(AppearanceSettingsEvent.NavigationBarSizeClicked) },
          modifier = Modifier.testTag(AppearanceSettingsTestTags.ROW_NAVIGATION_BAR_SIZE)
        )
      }
    }

    when (val dialog = state.dialog) {
      AppearanceSettingsState.Dialog.None -> Unit
      is AppearanceSettingsState.Dialog.ConfirmLanguageChange -> {
        Dialogs.SimpleAlertDialog(
          title = "",
          body = stringResource(R.string.preferences_language_change_confirmation_message),
          confirm = stringResource(android.R.string.ok),
          dismiss = stringResource(android.R.string.cancel),
          onConfirm = { onEvent(AppearanceSettingsEvent.LanguageChangeConfirmed) },
          onDeny = { onEvent(AppearanceSettingsEvent.DialogDismissed) },
          onDismissRequest = { onEvent(AppearanceSettingsEvent.DialogDismissed) }
        )
      }
      is AppearanceSettingsState.Dialog.NavigationBarSize -> NavigationBarSizeDialog(dialog = dialog, onEvent = onEvent)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationBarSizeDialog(
  dialog: AppearanceSettingsState.Dialog.NavigationBarSize,
  onEvent: (AppearanceSettingsEvent) -> Unit
) {
  Dialog(onDismissRequest = { onEvent(AppearanceSettingsEvent.DialogDismissed) }) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = SignalTheme.colors.colorSurface2
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.verticalScroll(rememberScrollState())
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .horizontalGutters()
            .padding(bottom = 16.dp)
        ) {
          Image(
            painter = painterResource(if (dialog.isCompact) R.drawable.navbar_compact else R.drawable.navbar_normal),
            contentDescription = null
          )
        }

        Text(
          text = stringResource(R.string.ChooseNavigationBarStyleFragment__navigation_bar_size),
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier
            .horizontalGutters()
            .padding(top = 24.dp)
        )

        SingleChoiceSegmentedButtonRow(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalGutters()
            .padding(top = 16.dp)
        ) {
          SegmentedButton(
            selected = !dialog.isCompact,
            onClick = { onEvent(AppearanceSettingsEvent.NavigationBarSizeSelected(isCompact = false)) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag(AppearanceSettingsTestTags.BUTTON_NAVIGATION_BAR_NORMAL)
          ) {
            Text(stringResource(R.string.ChooseNavigationBarStyleFragment__normal))
          }

          SegmentedButton(
            selected = dialog.isCompact,
            onClick = { onEvent(AppearanceSettingsEvent.NavigationBarSizeSelected(isCompact = true)) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag(AppearanceSettingsTestTags.BUTTON_NAVIGATION_BAR_COMPACT)
          ) {
            Text(stringResource(R.string.ChooseNavigationBarStyleFragment__compact))
          }
        }

        Buttons.LargeTonal(
          onClick = { onEvent(AppearanceSettingsEvent.NavigationBarSizeConfirmed) },
          modifier = Modifier
            .padding(vertical = 28.dp)
            .testTag(AppearanceSettingsTestTags.BUTTON_NAVIGATION_BAR_CONFIRM)
        ) {
          Text(stringResource(android.R.string.ok))
        }
      }
    }
  }
}

private val AppearanceSettingsState.Theme.labelRes: Int
  @StringRes get() = when (this) {
    AppearanceSettingsState.Theme.SYSTEM -> R.string.preferences__system_default
    AppearanceSettingsState.Theme.LIGHT -> R.string.preferences__light_theme
    AppearanceSettingsState.Theme.DARK -> R.string.preferences__dark_theme
  }

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppearanceSettingsScreenPreview() {
  AppearanceSettingsScreen(
    state = AppearanceSettingsState(language = "en"),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppearanceSettingsScreenConfirmLanguagePreview() {
  AppearanceSettingsScreen(
    state = AppearanceSettingsState(dialog = AppearanceSettingsState.Dialog.ConfirmLanguageChange("de")),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppearanceSettingsScreenNavigationBarSizePreview() {
  AppearanceSettingsScreen(
    state = AppearanceSettingsState(dialog = AppearanceSettingsState.Dialog.NavigationBarSize(isCompact = true)),
    onEvent = {}
  )
}
