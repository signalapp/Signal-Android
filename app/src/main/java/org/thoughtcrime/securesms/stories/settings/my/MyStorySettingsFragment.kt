package org.thoughtcrime.securesms.stories.settings.my

import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.WrapperDialogFragment
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.stories.settings.connections.ViewAllSignalConnectionsFragment
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class MyStorySettingsFragment : DSLSettingsFragment(
  titleId = R.string.MyStorySettingsFragment__my_story
) {

  private val viewModel: MyStorySettingsViewModel by viewModels()

  private lateinit var lifecycleDisposable: LifecycleDisposable

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    lifecycleDisposable = LifecycleDisposable()
    lifecycleDisposable.bindTo(viewLifecycleOwner)
    super.onViewCreated(view, savedInstanceState)
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    AllSignalConnectionsRowItem.register(adapter)
    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: MyStorySettingsState): DSLConfiguration {
    return configure {
      sectionHeaderPref(R.string.MyStorySettingsFragment__who_can_view_this_story)

      customPref(
        AllSignalConnectionsRowItem.Model(
          isChecked = state.myStoryPrivacyState.privacyMode == DistributionListPrivacyMode.ALL && state.hasUserPerformedManualSelection,
          count = state.allSignalConnectionsCount,
          onRowClicked = {
            lifecycleDisposable += viewModel.setMyStoryPrivacyMode(DistributionListPrivacyMode.ALL)
              .subscribe()
          },
          onViewClicked = {
            ViewAllSignalConnectionsFragment.Dialog.show(childFragmentManager)
          }
        )
      )

      val exceptText = if (state.myStoryPrivacyState.privacyMode == DistributionListPrivacyMode.ALL_EXCEPT) {
        DSLSettingsText.from(resources.getQuantityString(R.plurals.MyStorySettingsFragment__d_people_excluded, state.myStoryPrivacyState.connectionCount, state.myStoryPrivacyState.connectionCount))
      } else {
        DSLSettingsText.from(R.string.MyStorySettingsFragment__hide_your_story_from_specific_people)
      }

      radioPref(
        title = DSLSettingsText.from(R.string.MyStorySettingsFragment__all_except),
        summary = exceptText,
        isChecked = state.myStoryPrivacyState.privacyMode == DistributionListPrivacyMode.ALL_EXCEPT && state.hasUserPerformedManualSelection,
        onClick = {
          lifecycleDisposable += viewModel.setMyStoryPrivacyMode(DistributionListPrivacyMode.ALL_EXCEPT)
            .subscribe { findNavController().safeNavigate(R.id.action_myStorySettings_to_allExceptFragment) }
        }
      )

      val onlyWithText = if (state.myStoryPrivacyState.privacyMode == DistributionListPrivacyMode.ONLY_WITH) {
        DSLSettingsText.from(resources.getQuantityString(R.plurals.MyStorySettingsFragment__d_people, state.myStoryPrivacyState.connectionCount, state.myStoryPrivacyState.connectionCount))
      } else {
        DSLSettingsText.from(R.string.MyStorySettingsFragment__only_share_with_selected_people)
      }

      radioPref(
        title = DSLSettingsText.from(R.string.MyStorySettingsFragment__only_share_with),
        summary = onlyWithText,
        isChecked = state.myStoryPrivacyState.privacyMode == DistributionListPrivacyMode.ONLY_WITH && state.hasUserPerformedManualSelection,
        onClick = {
          lifecycleDisposable += viewModel.setMyStoryPrivacyMode(DistributionListPrivacyMode.ONLY_WITH)
            .subscribe { findNavController().safeNavigate(R.id.action_myStorySettings_to_onlyShareWithFragment) }
        }
      )

      learnMoreTextPref(
        summary = DSLSettingsText.from(R.string.MyStorySettingsFragment__choose_who_can_view_your_story),
        onClick = {
          findNavController().safeNavigate(R.id.action_myStorySettings_to_signalConnectionsBottomSheet)
        }
      )

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

  class Dialog : WrapperDialogFragment() {
    override fun getWrappedFragment(): Fragment {
      return NavHostFragment.create(R.navigation.my_story_settings)
    }
  }

  companion object {
    fun createAsDialog(): DialogFragment {
      return Dialog()
    }
  }
}
