package org.thoughtcrime.securesms.stories.tabs

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.ConversationListTabsBinding
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.visible
import java.text.NumberFormat

/**
 * Displays the "Chats" and "Stories" tab to a user.
 */
class ConversationListTabsFragment : Fragment(R.layout.conversation_list_tabs) {

  private val viewModel: ConversationListTabsViewModel by viewModels(ownerProducer = { requireActivity() })
  private val binding by ViewBinderDelegate(ConversationListTabsBinding::bind)
  private var shouldBeImmediate = true
  private var pillAnimator: Animator? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val iconTint = ContextCompat.getColor(requireContext(), R.color.signal_colorOnSecondaryContainer)

    binding.chatsTabIcon.addValueCallback(
      KeyPath("**"),
      LottieProperty.COLOR
    ) { iconTint }

    binding.callsTabIcon.addValueCallback(
      KeyPath("**"),
      LottieProperty.COLOR
    ) { iconTint }

    binding.storiesTabIcon.addValueCallback(
      KeyPath("**"),
      LottieProperty.COLOR
    ) { iconTint }

    view.findViewById<View>(R.id.chats_tab_touch_point).setOnClickListener {
      viewModel.onChatsSelected()
    }

    view.findViewById<View>(R.id.calls_tab_touch_point).setOnClickListener {
      viewModel.onCallsSelected()
    }

    view.findViewById<View>(R.id.stories_tab_touch_point).setOnClickListener {
      viewModel.onStoriesSelected()
    }

    updateTabsVisibility()

    viewModel.state.observe(viewLifecycleOwner) {
      update(it, shouldBeImmediate)
      shouldBeImmediate = false
    }
  }

  override fun onResume() {
    super.onResume()
    updateTabsVisibility()
  }

  private fun updateTabsVisibility() {
    listOf(
      binding.callsPill,
      binding.callsTabIcon,
      binding.callsTabContainer,
      binding.callsTabLabel,
      binding.callsUnreadIndicator,
      binding.callsTabTouchPoint
    ).forEach {
      it.visible = FeatureFlags.callsTab()
    }

    listOf(
      binding.storiesPill,
      binding.storiesTabIcon,
      binding.storiesTabContainer,
      binding.storiesTabLabel,
      binding.storiesUnreadIndicator,
      binding.storiesTabTouchPoint
    ).forEach {
      it.visible = Stories.isFeatureEnabled()
    }

    update(viewModel.stateSnapshot, true)
  }

  private fun update(state: ConversationListTabsState, immediate: Boolean) {
    binding.chatsTabIcon.isSelected = state.tab == ConversationListTab.CHATS
    binding.chatsPill.isSelected = state.tab == ConversationListTab.CHATS

    if (Stories.isFeatureEnabled()) {
      binding.storiesTabIcon.isSelected = state.tab == ConversationListTab.STORIES
      binding.storiesPill.isSelected = state.tab == ConversationListTab.STORIES
    }

    if (FeatureFlags.callsTab()) {
      binding.callsTabIcon.isSelected = state.tab == ConversationListTab.CALLS
      binding.callsPill.isSelected = state.tab == ConversationListTab.CALLS
    }

    val hasStateChange = state.tab != state.prevTab
    if (immediate) {
      binding.chatsTabIcon.pauseAnimation()
      binding.chatsTabIcon.progress = if (state.tab == ConversationListTab.CHATS) 1f else 0f

      if (Stories.isFeatureEnabled()) {
        binding.storiesTabIcon.pauseAnimation()
        binding.storiesTabIcon.progress = if (state.tab == ConversationListTab.STORIES) 1f else 0f
      }

      if (FeatureFlags.callsTab()) {
        binding.callsTabIcon.pauseAnimation()
        binding.callsTabIcon.progress = if (state.tab == ConversationListTab.CALLS) 1f else 0f
      }

      runPillAnimation(
        0,
        listOfNotNull(
          binding.chatsPill,
          binding.callsPill.takeIf { FeatureFlags.callsTab() },
          binding.storiesPill.takeIf { Stories.isFeatureEnabled() }
        )
      )
    } else if (hasStateChange) {
      runLottieAnimations(
        listOfNotNull(
          binding.chatsTabIcon,
          binding.callsTabIcon.takeIf { FeatureFlags.callsTab() },
          binding.storiesTabIcon.takeIf { Stories.isFeatureEnabled() }
        )
      )

      runPillAnimation(
        150,
        listOfNotNull(
          binding.chatsPill,
          binding.callsPill.takeIf { FeatureFlags.callsTab() },
          binding.storiesPill.takeIf { Stories.isFeatureEnabled() }
        )
      )
    }

    binding.chatsUnreadIndicator.visible = state.unreadMessagesCount > 0
    binding.chatsUnreadIndicator.text = formatCount(state.unreadMessagesCount)

    if (Stories.isFeatureEnabled()) {
      binding.storiesUnreadIndicator.visible = state.unreadStoriesCount > 0 || state.hasFailedStory
      binding.storiesUnreadIndicator.text = if (state.hasFailedStory) "!" else formatCount(state.unreadStoriesCount)
    }

    if (FeatureFlags.callsTab()) {
      binding.callsUnreadIndicator.visible = state.unreadCallsCount > 0
      binding.callsUnreadIndicator.text = formatCount(state.unreadCallsCount)
    }

    requireView().visible = state.visibilityState.isVisible()
  }

  private fun runLottieAnimations(toAnimate: List<LottieAnimationView>) {
    toAnimate.forEach {
      if (it.isSelected) {
        it.resumeAnimation()
      } else {
        if (it.isAnimating) {
          it.pauseAnimation()
        }

        it.progress = 0f
      }
    }
  }

  private fun runPillAnimation(duration: Long, toAnimate: List<ImageView>) {
    val (selected, unselected) = toAnimate.partition { it.isSelected }

    pillAnimator?.cancel()
    pillAnimator = AnimatorSet().apply {
      this.duration = duration
      interpolator = PathInterpolatorCompat.create(0.17f, 0.17f, 0f, 1f)
      playTogether(
        selected.map { view ->
          view.visibility = View.VISIBLE
          ValueAnimator.ofInt(view.paddingLeft, 0).apply {
            addUpdateListener {
              view.setPadding(it.animatedValue as Int, 0, it.animatedValue as Int, 0)
            }
          }
        }
      )
      start()
    }

    unselected.forEach {
      val smallPad = DimensionUnit.DP.toPixels(16f).toInt()
      it.setPadding(smallPad, 0, smallPad, 0)
      it.visibility = View.INVISIBLE
    }
  }

  private fun formatCount(count: Long): String {
    if (count > 99L) {
      return getString(R.string.ConversationListTabs__99p)
    }
    return NumberFormat.getInstance().format(count)
  }
}
