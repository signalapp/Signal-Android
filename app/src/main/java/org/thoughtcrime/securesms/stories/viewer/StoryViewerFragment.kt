package org.thoughtcrime.securesms.stories.viewer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageFragment

/**
 * Fragment which manages a vertical pager fragment of stories.
 */
class StoryViewerFragment : Fragment(R.layout.stories_viewer_fragment), StoryViewerPageFragment.Callback {

  private val onPageChanged = OnPageChanged()

  private lateinit var storyPager: ViewPager2

  private val viewModel: StoryViewerViewModel by viewModels(
    factoryProducer = {
      StoryViewerViewModel.Factory(storyRecipientId, StoryViewerRepository())
    }
  )

  private val storyRecipientId: RecipientId
    get() = requireArguments().getParcelable(ARG_START_RECIPIENT_ID)!!

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    storyPager = view.findViewById(R.id.story_item_pager)

    val adapter = StoryViewerPagerAdapter(this)
    storyPager.adapter = adapter

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.setPages(state.pages)
      if (state.pages.isNotEmpty() && storyPager.currentItem != state.page) {
        storyPager.setCurrentItem(state.page, state.previousPage > -1)

        if (state.page >= state.pages.size) {
          requireActivity().onBackPressed()
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.setIsScrolling(false)
    storyPager.registerOnPageChangeCallback(onPageChanged)
  }

  override fun onPause() {
    super.onPause()
    viewModel.setIsScrolling(false)
    storyPager.unregisterOnPageChangeCallback(onPageChanged)
  }

  override fun onFinishedPosts(recipientId: RecipientId) {
    viewModel.onFinishedPosts(recipientId)
  }

  override fun onStoryHidden(recipientId: RecipientId) {
    viewModel.onRecipientHidden()
  }

  inner class OnPageChanged : ViewPager2.OnPageChangeCallback() {
    override fun onPageSelected(position: Int) {
      viewModel.setSelectedPage(position)
    }

    override fun onPageScrollStateChanged(state: Int) {
      viewModel.setIsScrolling(state == ViewPager2.SCROLL_STATE_DRAGGING)
    }
  }

  companion object {
    private const val ARG_START_RECIPIENT_ID = "start.recipient.id"

    fun create(storyRecipientId: RecipientId): Fragment {
      return StoryViewerFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_START_RECIPIENT_ID, storyRecipientId)
        }
      }
    }
  }
}
