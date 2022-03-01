package org.thoughtcrime.securesms.stories.my

import androidx.fragment.app.viewModels
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.dialogs.StoryContextMenu
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import org.thoughtcrime.securesms.util.LifecycleDisposable

class MyStoriesFragment : DSLSettingsFragment(
  titleId = R.string.StoriesLandingFragment__my_stories
) {

  private val lifecycleDisposable = LifecycleDisposable()

  private val viewModel: MyStoriesViewModel by viewModels(
    factoryProducer = {
      MyStoriesViewModel.Factory(MyStoriesRepository(requireContext()))
    }
  )

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    MyStoriesItem.register(adapter)

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
      if (it.distributionSets.isEmpty()) {
        requireActivity().finish()
      }
    }
  }

  private fun getConfiguration(state: MyStoriesState): DSLConfiguration {
    return configure {
      val nonEmptySets = state.distributionSets.filter { it.stories.isNotEmpty() }
      nonEmptySets
        .forEachIndexed { index, distributionSet ->
          sectionHeaderPref(
            if (distributionSet.label == null) {
              DSLSettingsText.from(getString(R.string.MyStories__ss_story, Recipient.self().getShortDisplayName(requireContext())))
            } else {
              DSLSettingsText.from(distributionSet.label)
            }
          )
          distributionSet.stories.forEach { conversationMessage ->
            customPref(
              MyStoriesItem.Model(
                distributionStory = conversationMessage,
                onClick = {
                  // TODO [stories] pass in something more specific to start with the correct progress
                  startActivity(StoryViewerActivity.createIntent(requireContext(), Recipient.self().id))
                },
                onSaveClick = {
                  StoryContextMenu.save(requireContext(), it.distributionStory.messageRecord)
                },
                onDeleteClick = this@MyStoriesFragment::handleDeleteClick,
                onForwardClick = { item ->
                  MultiselectForwardFragmentArgs.create(
                    requireContext(),
                    item.distributionStory.multiselectCollection.toSet()
                  ) {
                    MultiselectForwardFragment.showBottomSheet(childFragmentManager, it)
                  }
                },
                onShareClick = {
                  StoryContextMenu.share(this@MyStoriesFragment, it.distributionStory.messageRecord as MediaMmsMessageRecord)
                }
              )
            )
          }

          if (index != nonEmptySets.lastIndex) {
            dividerPref()
          }
        }
    }
  }

  private fun handleDeleteClick(model: MyStoriesItem.Model) {
    lifecycleDisposable += StoryContextMenu.delete(requireContext(), setOf(model.distributionStory.messageRecord)).subscribe()
  }
}
