package org.thoughtcrime.securesms.components.settings.app.internal

import android.widget.Toast
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.stories.dialogs.StoryDialogs
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

class StoryDialogLauncherFragment : DSLSettingsFragment(titleId = R.string.preferences__internal_stories_dialog_launcher) {
  override fun bindAdapter(adapter: MappingAdapter) {
    adapter.submitList(getConfiguration().toMappingModelList())
  }

  private fun getConfiguration(): DSLConfiguration {
    return configure {
      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_retry_send),
        onClick = {
          StoryDialogs.resendStory(requireContext()) {
            Toast.makeText(requireContext(), R.string.preferences__internal_retry_send, Toast.LENGTH_SHORT).show()
          }
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_story_or_profile_selector),
        onClick = {
          StoryDialogs.displayStoryOrProfileImage(
            context = requireContext(),
            onViewStory = { Toast.makeText(requireContext(), R.string.StoryDialogs__view_story, Toast.LENGTH_SHORT).show() },
            onViewAvatar = { Toast.makeText(requireContext(), R.string.StoryDialogs__view_profile_photo, Toast.LENGTH_SHORT).show() }
          )
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_hide_story),
        onClick = {
          StoryDialogs.hideStory(requireContext(), "Spiderman") {
            Toast.makeText(requireContext(), R.string.preferences__internal_hide_story, Toast.LENGTH_SHORT).show()
          }
        }
      )
    }
  }
}
