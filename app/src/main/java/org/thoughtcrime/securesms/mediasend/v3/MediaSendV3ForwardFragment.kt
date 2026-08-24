/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.getParcelableArrayListCompat
import org.signal.core.util.logging.Log
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendFlowActivityContract
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.MediaSendFlowViewModel
import org.signal.mediasend.MediaSendRecipient
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ArbitraryRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.conversation.mutiselect.forward.SearchConfigurationProvider
import org.thoughtcrime.securesms.mediasend.v2.stories.StoryMediaPreviews
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProvider
import org.thoughtcrime.securesms.util.adapter.mapping.compose.rememberMappingEntryProvider
import org.signal.core.ui.R as CoreUiR

/**
 * View-backed wrapper around [MultiselectForwardFragment] that provides the [ViewGroup] container
 * required by [MultiselectForwardFragment.Callback.getContainer] for bottom bar inflation.
 *
 * Implements the callback interface and uses the shared [MediaSendFlowViewModel] to drive
 * the send flow forward.
 *
 * For story sends this also acts as the [SearchConfigurationProvider], swapping the default
 * destination list for the stories-only list and prepending a preview of the media being posted.
 */
class MediaSendV3ForwardFragment : Fragment(R.layout.multiselect_forward_activity), MultiselectForwardFragment.Callback, SearchConfigurationProvider {

  companion object {
    private val TAG = Log.tag(MediaSendV3ForwardFragment::class)

    private const val PREVIEW_ITEM = "preview_item"

    /** The number of selected media items rendered into the destination picker preview. */
    private const val PREVIEW_COUNT = 2

    /**
     * Fixed height of the preview row, so the list does not shift when the asynchronously rendered
     * previews arrive. Matches the tallest thumbnail in [StoryMediaPreviews] plus its vertical padding.
     */
    private val PREVIEW_HEIGHT = 260.dp
  }

  private val viewModel: MediaSendFlowViewModel by activityViewModels {
    MediaSendFlowViewModel.Factory(args = MediaSendFlowActivityContract.Args.fromIntent(requireActivity().intent))
  }

  /**
   * The rendered story previews. Backed by compose state because rendering the image editor models happens off the main
   * thread, after the picker has already been composed.
   */
  private var storyPreviews: List<Uri> by mutableStateOf(emptyList())

  private val isStory: Boolean get() = viewModel.state.value.isStory

  private val hasPreviewableMedia: Boolean get() = viewModel.state.value.selectedMedia.isNotEmpty()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val state = viewModel.state.value

    if (state.isStory && storyPreviews.isEmpty()) {
      renderStoryPreviews(state)
    }

    if (savedInstanceState == null) {
      val forwardFragment = MultiselectForwardFragment.create(
        MultiselectForwardFragmentArgs(
          title = R.string.MediaReviewFragment__send_to,
          storySendRequirements = state.storySendRequirements.toAppSendRequirements(),
          isSearchEnabled = !state.isStory,
          isViewOnce = state.isViewOnceEnabled
        )
      )

      childFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, forwardFragment)
        .commitNow()
    }
  }

  override fun onFinishForwardAction() = Unit

  override fun exitFlow() {
    requireActivity().finish()
  }

  override fun onSearchInputFocused() = Unit

  override fun setResult(bundle: Bundle) {
    val selectedRecipients: List<ContactSearchKey.RecipientSearchKey> = bundle.getParcelableArrayListCompat(MultiselectForwardFragment.RESULT_SELECTION, ContactSearchKey.RecipientSearchKey::class.java)
      ?: emptyList()

    val recipients = selectedRecipients.map { MediaSendRecipient(MediaRecipientId(it.recipientId.toLong()), it.isStory) }
    viewModel.setAdditionalRecipients(recipients)
    viewModel.performSend()
  }

  override fun getContainer(): ViewGroup {
    return requireView().findViewById(R.id.fragment_container_wrapper)
  }

  override fun getDialogBackgroundColor(): Int {
    return ContextCompat.getColor(requireContext(), CoreUiR.color.signal_colorBackground)
  }

  override fun getStorySendRequirements(): Stories.MediaTransform.SendRequirements? {
    return viewModel.getStorySendRequirements().toAppSendRequirements()
  }

  override fun getSearchConfiguration(fragmentManager: FragmentManager, contactSearchState: ContactSearchState): ContactSearchConfiguration? {
    if (!isStory) {
      return null
    }

    return ContactSearchConfiguration.build {
      query = contactSearchState.query

      addSection(
        ContactSearchConfiguration.Section.Arbitrary(setOf(PREVIEW_ITEM))
      )

      addSection(
        ContactSearchConfiguration.Section.Stories(
          groupStories = contactSearchState.groupStories,
          includeHeader = true,
          headerAction = Stories.getHeaderAction(fragmentManager)
        )
      )
    }
  }

  override fun getArbitraryRepository(): ArbitraryRepository? {
    if (!isStory) {
      return null
    }

    return object : ArbitraryRepository {
      /**
       * Reported as present as soon as there is media to preview, rather than once the previews have finished rendering.
       * The section size is only sampled while the paged data source is being built, so a size that flipped from zero to
       * one later on would never be picked up.
       */
      override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int = if (hasPreviewableMedia) 1 else 0

      override fun getData(
        section: ContactSearchConfiguration.Section.Arbitrary,
        query: String?,
        startIndex: Int,
        endIndex: Int,
        totalSearchSize: Int
      ): List<ContactSearchData.Arbitrary> = if (hasPreviewableMedia) listOf(ContactSearchData.Arbitrary(PREVIEW_ITEM)) else emptyList()

      override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> = PreviewEntryMappingModel()
    }
  }

  @Composable
  override fun getAdditionalEntries(): MappingEntryProvider<Any> {
    return rememberMappingEntryProvider {
      entry<PreviewEntryMappingModel> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
        ) {
          StoryMediaPreviews(previews = storyPreviews)
        }
      }
    }
  }

  /**
   * Renders the first [PREVIEW_COUNT] selected media into single-session blobs so the picker shows what is actually
   * being posted, edits included. Items that aren't routed through the image editor are previewed as-is.
   */
  private fun renderStoryPreviews(state: MediaSendFlowState) {
    val context = requireContext().applicationContext

    viewLifecycleOwner.lifecycleScope.launch {
      storyPreviews = withContext(Dispatchers.IO) {
        state.selectedMedia.take(PREVIEW_COUNT).map { media ->
          val editorState = state.editorStateMap[media.uri]
          if (ContentTypeUtil.isImageType(media.contentType) && editorState is EditorState.Image) {
            try {
              ImageEditorFragment.renderToSingleSessionBlob(context, editorState.model)
            } catch (e: Exception) {
              Log.w(TAG, "Failed to render story preview. Falling back to the original media.", e)
              media.uri
            }
          } else {
            media.uri
          }
        }
      }
    }
  }

  private class PreviewEntryMappingModel : MappingModel<PreviewEntryMappingModel> {
    override fun areItemsTheSame(newItem: PreviewEntryMappingModel): Boolean = true
    override fun areContentsTheSame(newItem: PreviewEntryMappingModel): Boolean = true
  }
}
