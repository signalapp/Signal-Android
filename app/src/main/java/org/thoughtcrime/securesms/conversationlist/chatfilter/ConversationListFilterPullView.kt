package org.thoughtcrime.securesms.conversationlist.chatfilter

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import com.google.android.material.animation.ArgbEvaluatorCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.AnimationCompleteListener
import org.thoughtcrime.securesms.databinding.ConversationListFilterPullViewBinding
import org.thoughtcrime.securesms.util.VibrateUtil
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

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
    private const val ANIMATE_HELP_TEXT_VELOCITY_THRESHOLD = 1f
    private const val ANIMATE_HELP_TEXT_THRESHOLD = 30
    private const val ANIMATE_HELP_TEXT_START_FRACTION = 0.35f
    private val COLOR_EVALUATOR = ArgbEvaluatorCompat.getInstance()
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
  private var pillColorAnimator: Animator? = null
  private val velocityTracker = ProgressVelocityTracker(5)
  private var animateHelpText = 0
  private var helpTextStartFraction = 0.35f

  private val pillDefaultBackgroundTint = ContextCompat.getColor(context, R.color.signal_colorSecondaryContainer)
  private val pillWillCloseBackgroundTint = ContextCompat.getColor(context, R.color.signal_colorSurface1)

  fun onUserDrag(progress: Float) {
    binding.filterCircle.textFieldMetrics = Pair(binding.filterText.width, binding.filterText.height)
    binding.filterCircle.progress = progress

    if (state == FilterPullState.CLOSED && progress <= 0) {
      setState(FilterPullState.CLOSED)
    } else if (state == FilterPullState.CLOSED && progress >= 1f) {
      setState(FilterPullState.OPEN_APEX)
      vibrate()
      resetHelpText()
      resetPillColor()
    } else if (state == FilterPullState.OPEN && progress >= 1f) {
      setState(FilterPullState.CLOSE_APEX)
      vibrate()
      animatePillColor()
    }

    if (state == FilterPullState.CLOSED && animateHelpText < ANIMATE_HELP_TEXT_THRESHOLD) {
      velocityTracker.submitProgress(progress, System.currentTimeMillis().milliseconds)
      val velocity = velocityTracker.calculateVelocity()
      animateHelpText = if (velocity > 0f && velocity < ANIMATE_HELP_TEXT_VELOCITY_THRESHOLD) {
        min(Int.MAX_VALUE, animateHelpText + 1)
      } else {
        max(0, animateHelpText - 1)
      }

      if (animateHelpText >= ANIMATE_HELP_TEXT_THRESHOLD) {
        helpTextStartFraction = max(progress, ANIMATE_HELP_TEXT_START_FRACTION)
      }
    }

    if (animateHelpText >= ANIMATE_HELP_TEXT_THRESHOLD) {
      binding.helpText.visibility = VISIBLE
    }

    binding.helpText.alpha = max(0f, FilterLerp.getHelpTextAlphaLerp(progress, helpTextStartFraction))
    binding.helpText.translationY = FilterLerp.getPillLerp(progress)

    if (state == FilterPullState.OPEN || state == FilterPullState.OPEN_APEX || state == FilterPullState.CLOSE_APEX || state == FilterPullState.CLOSING) {
      binding.filterText.translationY = FilterLerp.getPillLerp(progress)
    } else {
      binding.filterText.translationY = 0f
    }

    if (state == FilterPullState.CLOSE_APEX) {
      binding.filterText.alpha = FilterLerp.getPillCloseApexAlphaLerp(progress)
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

  private fun resetHelpText() {
    velocityTracker.clear()
    animateHelpText = 0
    helpTextStartFraction = ANIMATE_HELP_TEXT_START_FRACTION
    binding.helpText.animate().alpha(0f).setListener(object : AnimationCompleteListener() {
      override fun onAnimationEnd(animation: Animator?) {
        binding.helpText.visibility = INVISIBLE
      }
    })
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
      duration = 150
      doOnEnd {
        binding.filterText.visibility = GONE
        binding.filterText.isEnabled = false
        postDelayed({ setState(FilterPullState.CLOSED) }, 150)
      }
      start()
    }
  }

  private fun animatePillColor() {
    pillColorAnimator?.cancel()
    pillColorAnimator = ValueAnimator.ofInt(pillDefaultBackgroundTint, pillWillCloseBackgroundTint).apply {
      addUpdateListener {
        binding.filterText.chipBackgroundColor = ColorStateList.valueOf(it.animatedValue as Int)
      }
      setEvaluator(COLOR_EVALUATOR)
      duration = 200
      start()
    }
  }

  private fun resetPillColor() {
    pillColorAnimator?.cancel()
    binding.filterText.chipBackgroundColor = ColorStateList.valueOf(pillDefaultBackgroundTint)
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
