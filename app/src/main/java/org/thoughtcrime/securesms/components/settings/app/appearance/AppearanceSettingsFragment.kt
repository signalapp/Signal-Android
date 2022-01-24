package org.thoughtcrime.securesms.components.settings.app.appearance

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class AppearanceSettingsFragment : DSLSettingsFragment(R.string.preferences__appearance) {

  private lateinit var viewModel: AppearanceSettingsViewModel

  private val themeLabels by lazy { resources.getStringArray(R.array.pref_theme_entries) }
  private val themeValues by lazy { resources.getStringArray(R.array.pref_theme_values) }

  private val messageFontSizeLabels by lazy { resources.getStringArray(R.array.pref_message_font_size_entries) }
  private val messageFontSizeValues by lazy { resources.getStringArray(R.array.pref_message_font_size_values) }

  private val languageLabels by lazy { resources.getStringArray(R.array.language_entries) }
  private val languageValues by lazy { resources.getStringArray(R.array.language_values) }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    viewModel = ViewModelProvider(this)[AppearanceSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: AppearanceSettingsState): DSLConfiguration {
    return configure {
      radioListPref(
        title = DSLSettingsText.from(R.string.preferences__theme),
        listItems = themeLabels,
        selected = themeValues.indexOf(state.theme),
        onSelected = {
          viewModel.setTheme(themeValues[it])
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__chat_color_and_wallpaper),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_appearanceSettings_to_wallpaperActivity)
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences_chats__message_text_size),
        listItems = messageFontSizeLabels,
        selected = messageFontSizeValues.indexOf(state.messageFontSize.toString()),
        onSelected = {
          viewModel.setMessageFontSize(messageFontSizeValues[it].toInt())
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences__language),
        listItems = languageLabels,
        selected = languageValues.indexOf(state.language),
        onSelected = {
          viewModel.setLanguage(languageValues[it])
        }
      )
    }
  }
}
