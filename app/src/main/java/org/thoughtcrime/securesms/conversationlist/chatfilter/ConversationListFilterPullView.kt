package org.thoughtcrime.securesms.conversationlist.chatfilter

import android.animation.Animator
import android.animation.FloatEvaluator
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ConversationListFilterPullViewBinding
import org.thoughtcrime.securesms.util.VibrateUtil

/**
 * Encapsulates the push / pull latch for enabling and disabling
 * filters into a convenient view.
 *
 * The view should retain a height of 52dp when it is released by the user, which
 * maps to a progress of 52%
 */
class ConversationListFilterPullView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  companion object {
    private val EVAL = FloatEvaluator()
  }

  private val binding: ConversationListFilterPullViewBinding
  private var state: FilterPullState = FilterPullState.CLOSED

  var onFilterStateChanged: OnFilterStateChanged? = null
  var onCloseClicked: OnCloseClicked? = null

  init {
    inflate(context, R.layout.conversation_list_filter_pull_view, this)
    binding = ConversationListFilterPullViewBinding.bind(this)
    binding.filterText.setOnClickListener {
      onCloseClicked?.onCloseClicked()
    }
  }

  private var pillAnimator: Animator? = null

  fun onUserDrag(progress: Float) {
    binding.filterCircle.textFieldMetrics = Pair(binding.filterText.width, binding.filterText.height)
    binding.filterCircle.progress = progress

    if (state == FilterPullState.CLOSED && progress <= 0) {
      setState(FilterPullState.CLOSED)
    } else if (state == FilterPullState.CLOSED && progress >= 1f) {
      setState(FilterPullState.OPEN_APEX)
      vibrate()
    } else if (state == FilterPullState.OPEN && progress >= 1f) {
      setState(FilterPullState.CLOSE_APEX)
      vibrate()
    }

    if (state == FilterPullState.OPEN || state == FilterPullState.OPEN_APEX || state == FilterPullState.CLOSE_APEX || state == FilterPullState.CLOSING) {
      binding.filterText.translationY = FilterLerp.getPillLerp(progress)
    } else {
      binding.filterText.translationY = 0f
    }
  }

  fun onUserDragFinished() {
    if (state == FilterPullState.OPEN_APEX) {
      open()
    } else if (state == FilterPullState.CLOSE_APEX) {
      close()
    }
  }

  fun toggle() {
    if (state == FilterPullState.OPEN) {
      setState(FilterPullState.CLOSE_APEX)
      close()
    } else if (state == FilterPullState.CLOSED) {
      setState(FilterPullState.OPEN_APEX)
      open()
    }
  }

  fun isCloseable(): Boolean {
    return state == FilterPullState.OPEN
  }

  private fun open() {
    setState(FilterPullState.OPENING)
    animatePillIn()
  }

  private fun close() {
    setState(FilterPullState.CLOSING)
    animatePillOut()
  }

  private fun animatePillIn() {
    binding.filterText.visibility = VISIBLE
    binding.filterText.alpha = 0f
    binding.filterText.isEnabled = true

    pillAnimator?.cancel()
    pillAnimator = ObjectAnimator.ofFloat(binding.filterText, ALPHA, 1f).apply {
      startDelay = 300
      duration = 300
      doOnEnd {
        setState(FilterPullState.OPEN)
      }
      start()
    }
  }

  private fun animatePillOut() {
    pillAnimator?.cancel()
    pillAnimator = ObjectAnimator.ofFloat(binding.filterText, ALPHA, 0f).apply {
      duration = 300
      doOnEnd {
        binding.filterText.visibility = GONE
        binding.filterText.isEnabled = false
        setState(FilterPullState.CLOSED)
      }
      start()
    }
  }

  private fun setState(state: FilterPullState) {
    this.state = state
    binding.filterCircle.state = state
    onFilterStateChanged?.newState(state)
  }

  private fun vibrate() {
    if (VibrateUtil.isHapticFeedbackEnabled(context)) {
      VibrateUtil.vibrateTick(context)
    }
  }

  interface OnFilterStateChanged {
    fun newState(state: FilterPullState)
  }

  interface OnCloseClicked {
    fun onCloseClicked()
  }
}
