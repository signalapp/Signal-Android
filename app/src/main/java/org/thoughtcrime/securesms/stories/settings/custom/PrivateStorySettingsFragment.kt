package org.thoughtcrime.securesms.stories.settings.custom

import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.DialogFragmentDisplayManager
import org.thoughtcrime.securesms.components.ProgressCardDialogFragment
import org.thoughtcrime.securesms.components.WrapperDialogFragment
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.dialogs.StoryDialogs
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewholders.RecipientMappingModel
import org.thoughtcrime.securesms.util.viewholders.RecipientViewHolder

class PrivateStorySettingsFragment : DSLSettingsFragment(
  menuId = R.menu.story_private_menu
) {

  private val progressDisplayManager = DialogFragmentDisplayManager { ProgressCardDialogFragment() }

  private val viewModel: PrivateStorySettingsViewModel by viewModels(
    factoryProducer = {
      PrivateStorySettingsViewModel.Factory(PrivateStorySettingsFragmentArgs.fromBundle(requireArguments()).distributionListId, PrivateStorySettingsRepository())
    }
  )

  private val distributionListId: DistributionListId
    get() = PrivateStorySettingsFragmentArgs.fromBundle(requireArguments()).distributionListId

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    adapter.registerFactory(RecipientMappingModel.RecipientIdMappingModel::class.java, LayoutFactory({ RecipientViewHolder(it, RecipientEventListener()) }, R.layout.stories_recipient_item))
    PrivateStoryItem.register(adapter)

    val toolbar: Toolbar = requireView().findViewById(R.id.toolbar)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      if (state.isActionInProgress) {
        progressDisplayManager.show(viewLifecycleOwner, childFragmentManager)
      } else {
        progressDisplayManager.hide()
      }

      toolbar.title = state.privateStory?.name
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: PrivateStorySettingsState): DSLConfiguration {
    if (state.privateStory == null) {
      return configure { }
    }

    return configure {
      sectionHeaderPref(R.string.MyStorySettingsFragment__who_can_view_this_story)
      customPref(
        PrivateStoryItem.AddViewerModel(
          onClick = {
            findNavController().safeNavigate(PrivateStorySettingsFragmentDirections.actionPrivateStorySettingsToEditStoryViewers(distributionListId))
          }
        )
      )

      state.privateStory.members.forEach {
        customPref(RecipientMappingModel.RecipientIdMappingModel(it))
      }

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

      dividerPref()
      clickPref(
        title = DSLSettingsText.from(R.string.PrivateStorySettingsFragment__delete_custom_story, DSLSettingsText.ColorModifier(ContextCompat.getColor(requireContext(), R.color.signal_alert_primary))),
        onClick = {
          val privateStoryName = viewModel.state.value?.privateStory?.name
          handleDeletePrivateStory(privateStoryName)
        }
      )
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return if (item.itemId == R.id.action_edit) {
      val action = PrivateStorySettingsFragmentDirections.actionPrivateStorySettingsToEditStoryNameFragment(distributionListId, viewModel.getName())
      findNavController().navigate(action)
      true
    } else {
      false
    }
  }

  private fun handleRemoveRecipient(recipient: Recipient) {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(getString(R.string.PrivateStorySettingsFragment__remove_s, recipient.getDisplayName(requireContext())))
      .setMessage(R.string.PrivateStorySettingsFragment__this_person_will_no_longer)
      .setPositiveButton(R.string.PrivateStorySettingsFragment__remove) { _, _ -> viewModel.remove(recipient) }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }

  private fun handleDeletePrivateStory(privateStoryName: String?) {
    val name = privateStoryName ?: return

    StoryDialogs.deleteDistributionList(requireContext(), name) {
      viewModel.delete().subscribe { findNavController().popBackStack() }
    }
  }

  override fun onToolbarNavigationClicked() {
    findListener<WrapperDialogFragment>()?.dismiss() ?: super.onToolbarNavigationClicked()
  }

  inner class RecipientEventListener : RecipientViewHolder.EventListener<RecipientMappingModel.RecipientIdMappingModel> {
    override fun onClick(recipient: Recipient) {
      handleRemoveRecipient(recipient)
    }
  }

  class Dialog : WrapperDialogFragment() {
    override fun getWrappedFragment(): Fragment {
      return NavHostFragment.create(R.navigation.private_story_settings, requireArguments())
    }
  }

  companion object {
    fun createAsDialog(distributionListId: DistributionListId): DialogFragment {
      return Dialog().apply {
        arguments = PrivateStorySettingsFragmentArgs.Builder(distributionListId).build().toBundle()
      }
    }
  }
}
