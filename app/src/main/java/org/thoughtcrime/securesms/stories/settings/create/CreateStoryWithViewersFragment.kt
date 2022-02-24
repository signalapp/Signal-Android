package org.thoughtcrime.securesms.stories.settings.create

import android.os.Bundle
import android.view.View
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.viewholders.RecipientMappingModel
import org.thoughtcrime.securesms.util.viewholders.RecipientViewHolder

/**
 * Creates a new distribution list with the passed set of viewers and entered distribution label.
 */
class CreateStoryWithViewersFragment : DSLSettingsFragment(
  titleId = R.string.CreateStoryWithViewersFragment__name_story,
  layoutId = R.layout.stories_create_with_recipients_fragment
) {

  companion object {
    const val REQUEST_KEY = "new-story"
    const val STORY_RECIPIENT = "story-recipient"
  }

  private val viewModel: CreateStoryWithViewersViewModel by viewModels(
    factoryProducer = {
      CreateStoryWithViewersViewModel.Factory(CreateStoryWithViewersRepository())
    }
  )

  private val recipientIds: Array<RecipientId>
    get() = CreateStoryWithViewersFragmentArgs.fromBundle(requireArguments()).recipients

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    adapter.registerFactory(RecipientMappingModel.RecipientIdMappingModel::class.java, LayoutFactory({ RecipientViewHolder(it, null) }, R.layout.stories_recipient_item))
    CreateStoryNameFieldItem.register(adapter) {
      viewModel.setLabel(it)
    }

    val createButton: View = requireView().findViewById(R.id.create)
    createButton.setOnClickListener { viewModel.create(recipientIds.toSet()) }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())

      when (state.saveState) {
        CreateStoryWithViewersState.SaveState.Init -> createButton.setCanPress(state.label.isNotEmpty())
        CreateStoryWithViewersState.SaveState.Saving -> createButton.setCanPress(false)
        is CreateStoryWithViewersState.SaveState.Saved -> onDone(state.saveState.recipientId)
      }
    }
  }

  private fun View.setCanPress(canPress: Boolean) {
    isEnabled = canPress
    alpha = if (canPress) 1f else 0.5f
  }

  private fun getConfiguration(state: CreateStoryWithViewersState): DSLConfiguration {
    return configure {
      customPref(
        CreateStoryNameFieldItem.Model(
          body = state.label,
          error = presentError(state.error)
        )
      )

      dividerPref()

      sectionHeaderPref(R.string.CreateStoryWithViewersFragment__viewers)

      recipientIds.forEach {
        customPref(RecipientMappingModel.RecipientIdMappingModel(it))
      }
    }
  }

  private fun presentError(error: CreateStoryWithViewersState.NameError?): String? {
    return when (error) {
      CreateStoryWithViewersState.NameError.NO_LABEL -> getString(R.string.CreateStoryWithViewersFragment__this_field_is_required)
      CreateStoryWithViewersState.NameError.DUPLICATE_LABEL -> getString(R.string.CreateStoryWithViewersFragment__there_is_already_a_story_with_this_name)
      else -> null
    }
  }

  private fun onDone(recipientId: RecipientId) {
    val callback: Callback? = findListener<Callback>()
    if (callback != null) {
      callback.onDone(recipientId)
    } else {
      setFragmentResult(
        REQUEST_KEY,
        Bundle().apply {
          putParcelable(STORY_RECIPIENT, recipientId)
        }
      )
      findNavController().popBackStack(R.id.createStoryViewerSelection, true)
    }
  }

  interface Callback {
    fun onDone(recipientId: RecipientId)
  }
}
