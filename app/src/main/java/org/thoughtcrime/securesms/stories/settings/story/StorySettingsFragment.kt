package org.thoughtcrime.securesms.stories.settings.story

import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class StorySettingsFragment : DSLSettingsFragment(
  titleId = R.string.StorySettingsFragment__story_settings
) {

  private val viewModel: StorySettingsViewModel by viewModels(
    factoryProducer = {
      StorySettingsViewModel.Factory(StorySettingsRepository())
    }
  )

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    PrivateStoryItem.register(adapter)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: StorySettingsState): DSLConfiguration {
    return configure {
      customPref(
        PrivateStoryItem.RecipientModel(
          recipient = Recipient.self(),
          onClick = {
            findNavController().safeNavigate(R.id.action_storySettings_to_myStorySettings)
          }
        )
      )

      dividerPref()
      sectionHeaderPref(R.string.StorySettingsFragment__private_stories)

      customPref(
        PrivateStoryItem.NewModel(
          onClick = {
            findNavController().safeNavigate(R.id.action_storySettings_to_newStory)
          }
        )
      )

      state.privateStories.forEach { itemData ->
        customPref(
          PrivateStoryItem.PartialModel(
            privateStoryItemData = itemData,
            onClick = {
              findNavController().safeNavigate(StorySettingsFragmentDirections.actionStorySettingsToPrivateStorySettings(it.privateStoryItemData.id))
            }
          )
        )
      }
    }
  }
}
