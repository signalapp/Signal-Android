package org.thoughtcrime.securesms.stories.settings.custom

import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.settings.story.PrivateStoryItem
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewholders.RecipientMappingModel
import org.thoughtcrime.securesms.util.viewholders.RecipientViewHolder

class PrivateStorySettingsFragment : DSLSettingsFragment(
  menuId = R.menu.story_private_menu
) {

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

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    adapter.registerFactory(RecipientMappingModel.RecipientIdMappingModel::class.java, LayoutFactory({ RecipientViewHolder(it, RecipientEventListener()) }, R.layout.stories_recipient_item))
    PrivateStoryItem.register(adapter)

    val toolbar: Toolbar = requireView().findViewById(R.id.toolbar)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      toolbar.title = state.privateStory?.name
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: PrivateStorySettingsState): DSLConfiguration {
    if (state.privateStory == null) {
      return configure { }
    }

    return configure {
      customPref(
        PrivateStoryItem.Model(
          privateStoryItemData = state.privateStory,
          onClick = {
            // TODO [stories] -- is this even clickable?
          }
        )
      )

      dividerPref()
      sectionHeaderPref(R.string.MyStorySettingsFragment__who_can_see_this_story)
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
        title = DSLSettingsText.from(R.string.PrivateStorySettingsFragment__delete_private_story, DSLSettingsText.ColorModifier(ContextCompat.getColor(requireContext(), R.color.signal_alert_primary))),
        onClick = {
          handleDeletePrivateStory()
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

  private fun handleDeletePrivateStory() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PrivateStorySettingsFragment__are_you_sure)
      .setMessage(R.string.PrivateStorySettingsFragment__this_action_cannot)
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete().subscribe { findNavController().popBackStack() } }
      .show()
  }

  inner class RecipientEventListener : RecipientViewHolder.EventListener<RecipientMappingModel.RecipientIdMappingModel> {
    override fun onClick(recipient: Recipient) {
      handleRemoveRecipient(recipient)
    }
  }
}
