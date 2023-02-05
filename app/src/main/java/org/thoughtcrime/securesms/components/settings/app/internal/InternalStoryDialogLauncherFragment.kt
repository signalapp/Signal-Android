package org.thoughtcrime.securesms.components.settings.app.internal

import android.widget.Toast
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.stories.dialogs.StoryDialogs
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

class InternalStoryDialogLauncherFragment : DSLSettingsFragment(titleId = R.string.preferences__internal_stories_dialog_launcher) {
  override fun bindAdapter(adapter: MappingAdapter) {
    adapter.submitList(getConfiguration().toMappingModelList())
  }

  private fun getConfiguration(): DSLConfiguration {
    return configure {
      clickPref(
        title = DSLSettingsText.from("Remove group story"),
        onClick = {
          StoryDialogs.removeGroupStory(requireContext(), "Family") {
            Toast.makeText(requireContext(), "Remove group story", Toast.LENGTH_SHORT).show()
          }
        }
      )

      clickPref(
        title = DSLSettingsText.from("Retry send"),
        onClick = {
          StoryDialogs.resendStory(requireContext()) {
            Toast.makeText(requireContext(), "Retry send", Toast.LENGTH_SHORT).show()
          }
        }
      )

      clickPref(
        title = DSLSettingsText.from("Story or profile selector"),
        onClick = {
          StoryDialogs.displayStoryOrProfileImage(
            context = requireContext(),
            onViewStory = { Toast.makeText(requireContext(), R.string.StoryDialogs__view_story, Toast.LENGTH_SHORT).show() },
            onViewAvatar = { Toast.makeText(requireContext(), R.string.StoryDialogs__view_profile_photo, Toast.LENGTH_SHORT).show() }
          )
        }
      )

      clickPref(
        title = DSLSettingsText.from("Hide story"),
        onClick = {
          StoryDialogs.hideStory(requireContext(), "Spiderman") {
            Toast.makeText(requireContext(), "Hide story", Toast.LENGTH_SHORT).show()
          }
        }
      )

      clickPref(
        title = DSLSettingsText.from("Turn off stories"),
        onClick = {
          StoryDialogs.disableStories(requireContext(), false) {
            Toast.makeText(requireContext(), "Turn off stories", Toast.LENGTH_SHORT).show()
          }
        }
      )

      clickPref(
        title = DSLSettingsText.from("Turn off stories (with stories on disk)"),
        onClick = {
          StoryDialogs.disableStories(requireContext(), true) {
            Toast.makeText(requireContext(), "Turn off stories (with stories on disk)", Toast.LENGTH_SHORT).show()
          }
        }
      )

      clickPref(
        title = DSLSettingsText.from("Delete custom story"),
        onClick = {
          StoryDialogs.deleteDistributionList(requireContext(), "Family") {
            Toast.makeText(requireContext(), "Delete custom story", Toast.LENGTH_SHORT).show()
          }
        }
      )
    }
  }
}
