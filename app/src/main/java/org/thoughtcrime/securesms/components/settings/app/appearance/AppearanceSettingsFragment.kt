package org.thoughtcrime.securesms.components.settings.app.appearance

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appearance.navbar.ChooseNavigationBarStyleFragment
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Allows the user to change language, theme, etc. from application settings.
 */
class AppearanceSettingsFragment : ComposeFragment() {

  private val viewModel: AppearanceSettingsViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    childFragmentManager.setFragmentResultListener(ChooseNavigationBarStyleFragment.REQUEST_KEY, viewLifecycleOwner) { key, bundle ->
      if (bundle.getBoolean(key, false)) {
        viewModel.refreshState()
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val callbacks = remember { Callbacks() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppearanceSettingsScreen(
      state = state,
      callbacks = callbacks
    )
  }

  private inner class Callbacks : AppearanceSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onLanguageSelected(selection: String) {
      viewModel.setLanguage(selection)
    }

    override fun onThemeSelected(selection: String) {
      viewModel.setTheme(activity, SettingsValues.Theme.deserialize(selection))
    }

    override fun onChatColorAndWallpaperClick() {
      findNavController().safeNavigate(R.id.action_appearanceSettings_to_wallpaperActivity)
    }

    override fun onAppIconClick() {
      findNavController().safeNavigate(R.id.action_appearanceSettings_to_appIconActivity)
    }

    override fun onMessageFontSizeSelected(selection: String) {
      viewModel.setMessageFontSize(selection.toInt())
    }

    override fun onNavigationBarSizeClick() {
      ChooseNavigationBarStyleFragment().show(childFragmentManager, null)
    }
  }
}

interface AppearanceSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onLanguageSelected(selection: String) = Unit
  fun onThemeSelected(selection: String) = Unit
  fun onChatColorAndWallpaperClick() = Unit
  fun onAppIconClick() = Unit
  fun onMessageFontSizeSelected(selection: String) = Unit
  fun onNavigationBarSizeClick() = Unit

  object Empty : AppearanceSettingsCallbacks
}

@Composable
private fun AppearanceSettingsScreen(
  state: AppearanceSettingsState,
  callbacks: AppearanceSettingsCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__appearance),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .then(rememberStatusBarColorNestedScrollModifier())
    ) {
      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences__language),
          labels = stringArrayResource(R.array.language_entries),
          values = stringArrayResource(R.array.language_values),
          selectedValue = state.language,
          onSelected = callbacks::onLanguageSelected
        )
      }

      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences__theme),
          labels = stringArrayResource(R.array.pref_theme_entries),
          values = stringArrayResource(R.array.pref_theme_values),
          selectedValue = state.theme.serialize(),
          onSelected = callbacks::onThemeSelected
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences__chat_color_and_wallpaper),
          onClick = callbacks::onChatColorAndWallpaperClick
        )
      }

      if (Build.VERSION.SDK_INT >= 26) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__app_icon),
            onClick = callbacks::onAppIconClick
          )
        }
      }

      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences_chats__message_text_size),
          labels = stringArrayResource(R.array.pref_message_font_size_entries),
          values = integerArrayResource(R.array.pref_message_font_size_values).map { it.toString() }.toTypedArray(),
          selectedValue = state.messageFontSize.toString(),
          onSelected = callbacks::onMessageFontSizeSelected
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
          onClick = callbacks::onNavigationBarSizeClick
        )
      }
    }
  }
}

@SignalPreview
@Composable
private fun AppearanceSettingsScreenPreview() {
  Previews.Preview {
    AppearanceSettingsScreen(
      state = AppearanceSettingsState(
        theme = SettingsValues.Theme.SYSTEM,
        messageFontSize = 0,
        language = "en-US",
        isCompactNavigationBar = false
      ),
      callbacks = AppearanceSettingsCallbacks.Empty
    )
  }
}
