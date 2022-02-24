package org.thoughtcrime.securesms.stories.settings.my

import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class MyStorySettingsFragment : DSLSettingsFragment(
  titleId = R.string.MyStorySettingsFragment__my_story
) {

  private val viewModel: MyStorySettingsViewModel by viewModels(
    factoryProducer = {
      MyStorySettingsViewModel.Factory(MyStorySettingsRepository())
    }
  )

  private val signalConnectionsSummary by lazy {
    SpanUtil.clickSubstring(
      getString(R.string.MyStorySettingsFragment__hide_your_story_from, getString(R.string.MyStorySettingsFragment__signal_connections)),
      getString(R.string.MyStorySettingsFragment__signal_connections),
      {
        findNavController().safeNavigate(R.id.action_myStorySettings_to_signalConnectionsBottomSheet)
      },
      ContextCompat.getColor(requireContext(), R.color.signal_text_primary)
    )
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: MyStorySettingsState): DSLConfiguration {
    return configure {
      sectionHeaderPref(R.string.MyStorySettingsFragment__who_can_see_this_story)

      clickPref(
        title = DSLSettingsText.from(R.string.MyStorySettingsFragment__hide_story_from),
        summary = DSLSettingsText.from(resources.getQuantityString(R.plurals.MyStorySettingsFragment__d_people, state.hiddenStoryFromCount, state.hiddenStoryFromCount)),
        onClick = {
          findNavController().safeNavigate(R.id.action_myStorySettings_to_hideStoryFromFragment)
        }
      )

      textPref(summary = DSLSettingsText.from(signalConnectionsSummary))
      dividerPref()
      sectionHeaderPref(R.string.MyStorySettingsFragment__replies_amp_reactions)
      switchPref(
        title = DSLSettingsText.from(R.string.MyStorySettingsFragment__allow_replies_amp_reactions),
        summary = DSLSettingsText.from(R.string.MyStorySettingsFragment__let_people_who_can_view_your_story_react_and_reply),
        isChecked = state.areRepliesAndReactionsEnabled,
        onClick = {
          viewModel.setRepliesAndReactionsEnabled(!state.areRepliesAndReactionsEnabled)
        }
      )
    }
  }
}
