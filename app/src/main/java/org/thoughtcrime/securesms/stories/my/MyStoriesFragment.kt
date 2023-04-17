package org.thoughtcrime.securesms.stories.my

import android.net.Uri
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.viewModels
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.dialogs.StoryContextMenu
import org.thoughtcrime.securesms.stories.dialogs.StoryDialogs
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.visible

class MyStoriesFragment : DSLSettingsFragment(
  layoutId = R.layout.stories_my_stories_fragment,
  titleId = R.string.StoriesLandingFragment__my_stories
) {

  private val lifecycleDisposable = LifecycleDisposable()

  private val viewModel: MyStoriesViewModel by viewModels(
    factoryProducer = {
      MyStoriesViewModel.Factory(MyStoriesRepository(requireContext()))
    }
  )

  override fun bindAdapter(adapter: MappingAdapter) {
    MyStoriesItem.register(adapter)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          requireActivity().finish()
        }
      }
    )

    val emptyNotice = requireView().findViewById<View>(R.id.empty_notice)
    lifecycleDisposable.bindTo(viewLifecycleOwner)
    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
      emptyNotice.visible = it.distributionSets.isEmpty()
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
                onClick = { it, preview ->
                  openStoryViewer(it, preview, false)
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
                },
                onInfoClick = { model, preview ->
                  openStoryViewer(model, preview, true)
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

  private fun openStoryViewer(it: MyStoriesItem.Model, preview: View, isFromInfoContextMenuAction: Boolean) {
    if (it.distributionStory.messageRecord.isOutgoing && it.distributionStory.messageRecord.isFailed) {
      if (it.distributionStory.messageRecord.isIdentityMismatchFailure) {
        SafetyNumberBottomSheet
          .forMessageRecord(requireContext(), it.distributionStory.messageRecord)
          .show(childFragmentManager)
      } else {
        StoryDialogs.resendStory(requireContext()) {
          lifecycleDisposable += viewModel.resend(it.distributionStory.messageRecord).subscribe()
        }
      }
    } else {
      val recipient = if (it.distributionStory.messageRecord.recipient.isGroup) {
        it.distributionStory.messageRecord.recipient
      } else {
        Recipient.self()
      }

      val record = it.distributionStory.messageRecord as MmsMessageRecord
      val blur = record.slideDeck.thumbnailSlide?.placeholderBlur
      val (text: StoryTextPostModel?, image: Uri?) = if (record.storyType.isTextStory) {
        StoryTextPostModel.parseFrom(record) to null
      } else {
        null to record.slideDeck.thumbnailSlide?.uri
      }

      val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), preview, ViewCompat.getTransitionName(preview) ?: "")
      startActivity(
        StoryViewerActivity.createIntent(
          context = requireContext(),
          storyViewerArgs = StoryViewerArgs(
            recipientId = recipient.id,
            storyId = it.distributionStory.messageRecord.id,
            isInHiddenStoryMode = recipient.shouldHideStory(),
            storyThumbTextModel = text,
            storyThumbUri = image,
            storyThumbBlur = blur,
            isFromInfoContextMenuAction = isFromInfoContextMenuAction,
            isFromMyStories = true
          )
        ),
        options.toBundle()
      )
    }
  }

  private fun handleDeleteClick(model: MyStoriesItem.Model) {
    lifecycleDisposable += StoryContextMenu.delete(requireContext(), setOf(model.distributionStory.messageRecord)).subscribe()
  }
}
