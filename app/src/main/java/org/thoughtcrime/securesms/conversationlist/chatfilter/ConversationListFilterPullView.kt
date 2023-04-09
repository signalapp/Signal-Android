package org.thoughtcrime.securesms.conversationlist.chatfilter

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.doOnNextLayout
import com.google.android.material.animation.ArgbEvaluatorCompat
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.AnimationCompleteListener
import org.thoughtcrime.securesms.databinding.ConversationListFilterPullViewBinding
import org.thoughtcrime.securesms.util.VibrateUtil
import org.thoughtcrime.securesms.util.doOnEachLayout
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
    private const val INSTANCE_STATE_ROOT = "instance_state_root"
    private const val INSTANCE_STATE_STATE = "instance_state_state"
    private const val INSTANCE_STATE_SOURCE = "instance_state_source"

    private const val ANIMATE_HELP_TEXT_VELOCITY_THRESHOLD = 1f
    private const val ANIMATE_HELP_TEXT_THRESHOLD = 30
    private const val ANIMATE_HELP_TEXT_START_FRACTION = 0.35f

    private const val CANCEL_THRESHOLD = 0.92f

    private val COLOR_EVALUATOR = ArgbEvaluatorCompat.getInstance()
  }

  private val binding: ConversationListFilterPullViewBinding
  private var state: FilterPullState = FilterPullState.CLOSED
  private var source: ConversationFilterSource = ConversationFilterSource.DRAG

  var onFilterStateChanged: OnFilterStateChanged? = null
  var onCloseClicked: OnCloseClicked? = null

  init {
    inflate(context, R.layout.conversation_list_filter_pull_view, this)
    binding = ConversationListFilterPullViewBinding.bind(this)
    binding.filterText.setOnClickListener {
      onCloseClicked?.onCloseClicked()
    }

    doOnEachLayout {
      binding.filterCircle.textFieldMetrics = Pair(binding.filterText.width, binding.filterText.height)
    }
  }

  private var pillAnimator: Animator? = null
  private var pillColorAnimator: Animator? = null
  private val velocityTracker = ProgressVelocityTracker(5)
  private var animateHelpText = 0
  private var helpTextStartFraction = 0.35f

  private val pillDefaultBackgroundTint = ContextCompat.getColor(context, R.color.signal_colorSecondaryContainer)
  private val pillWillCloseBackgroundTint = ContextCompat.getColor(context, R.color.signal_colorSurface1)

  fun setPillText(@StringRes textId: Int) {
    binding.filterText.setText(textId)
  }

  override fun onSaveInstanceState(): Parcelable {
    val root = super.onSaveInstanceState()

    return bundleOf(
      INSTANCE_STATE_ROOT to root,
      INSTANCE_STATE_STATE to state.name,
      INSTANCE_STATE_SOURCE to source.name
    )
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    val bundle = state as Bundle
    val root: Parcelable? = bundle.getParcelableCompat(INSTANCE_STATE_ROOT, Parcelable::class.java)
    super.onRestoreInstanceState(root)

    val restoredState: FilterPullState = FilterPullState.valueOf(bundle.getString(INSTANCE_STATE_STATE)!!)
    val restoredSource: ConversationFilterSource = ConversationFilterSource.valueOf(bundle.getString(INSTANCE_STATE_SOURCE)!!)

    doOnNextLayout {
      when (restoredState.toLatestSettledState()) {
        FilterPullState.OPEN -> toggle(restoredSource)
        FilterPullState.CLOSED -> Unit
        else -> throw IllegalStateException("Unexpected settled state.")
      }
    }
  }

  fun onUserDrag(progress: Float) {
    binding.filterCircle.progress = progress

    if (state == FilterPullState.CLOSED && progress <= 0) {
      setState(FilterPullState.CLOSED, ConversationFilterSource.DRAG)
    } else if ((state == FilterPullState.CLOSED || state == FilterPullState.CANCELING) && progress >= 1f) {
      setState(FilterPullState.OPEN_APEX, ConversationFilterSource.DRAG)
      vibrate()
      resetHelpText()
      resetPillColor()
    } else if (state == FilterPullState.OPEN && progress >= 1f) {
      setState(FilterPullState.CLOSE_APEX, ConversationFilterSource.DRAG)
      vibrate()
      animatePillColor()
    } else if (state == FilterPullState.OPEN_APEX && progress <= CANCEL_THRESHOLD) {
      setState(FilterPullState.CANCELING, ConversationFilterSource.DRAG)
      vibrate()
    }

    if (state == FilterPullState.CANCELING) {
      binding.filterCircle.alpha = FilterLerp.getCircleCancelAlphaLerp(progress)
    } else {
      binding.filterCircle.alpha = 1f
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
      binding.helpText.alpha = max(0f, FilterLerp.getHelpTextAlphaLerp(progress, helpTextStartFraction))
      binding.helpText.translationY = FilterLerp.getPillLerp(progress)
    }

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
      open(ConversationFilterSource.DRAG)
    } else if (state == FilterPullState.CLOSE_APEX || state == FilterPullState.CANCELING) {
      close(ConversationFilterSource.DRAG)
    }
  }

  fun toggle() {
    toggle(ConversationFilterSource.OVERFLOW)
  }

  private fun toggle(source: ConversationFilterSource) {
    if (state == FilterPullState.OPEN) {
      resetHelpText()
      setState(FilterPullState.CLOSE_APEX, source)
      close(ConversationFilterSource.OVERFLOW)
    } else if (state == FilterPullState.CLOSED) {
      setState(FilterPullState.OPEN_APEX, source)
      open(ConversationFilterSource.OVERFLOW)
    }
  }

  fun isCloseable(): Boolean {
    return state == FilterPullState.OPEN
  }

  private fun open(source: ConversationFilterSource) {
    setState(FilterPullState.OPENING, source)
    animatePillIn(source)
  }

  private fun close(source: ConversationFilterSource) {
    setState(FilterPullState.CLOSING, source)
    animatePillOut(source)
  }

  private fun resetHelpText() {
    velocityTracker.clear()
    animateHelpText = 0
    helpTextStartFraction = ANIMATE_HELP_TEXT_START_FRACTION
    binding.helpText.animate().alpha(0f).setListener(object : AnimationCompleteListener() {
      override fun onAnimationEnd(animation: Animator) {
        binding.helpText.visibility = INVISIBLE
      }
    })
  }

  private fun animatePillIn(source: ConversationFilterSource) {
    binding.filterText.visibility = VISIBLE
    binding.filterText.alpha = 0f
    binding.filterText.isEnabled = true

    pillAnimator?.cancel()
    pillAnimator = ObjectAnimator.ofFloat(binding.filterText, ALPHA, 1f).apply {
      startDelay = 300
      duration = 300
      doOnEnd {
        setState(FilterPullState.OPEN, source)
      }
      start()
    }
  }

  private fun animatePillOut(source: ConversationFilterSource) {
    pillAnimator?.cancel()
    pillAnimator = ObjectAnimator.ofFloat(binding.filterText, ALPHA, 0f).apply {
      duration = 150
      doOnEnd {
        binding.filterText.visibility = GONE
        binding.filterText.isEnabled = false
        postDelayed({ setState(FilterPullState.CLOSED, source) }, 150)
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

  private fun setState(state: FilterPullState, source: ConversationFilterSource) {
    this.state = state
    this.source = source
    binding.filterCircle.state = state
    onFilterStateChanged?.newState(state, source)
  }

  private fun vibrate() {
    if (VibrateUtil.isHapticFeedbackEnabled(context)) {
      VibrateUtil.vibrateTick(context)
    }
  }

  private fun FilterPullState.toLatestSettledState(): FilterPullState {
    return when (this) {
      FilterPullState.CLOSED, FilterPullState.OPEN_APEX, FilterPullState.OPENING, FilterPullState.CANCELING -> FilterPullState.CLOSED
      FilterPullState.OPEN, FilterPullState.CLOSE_APEX, FilterPullState.CLOSING -> FilterPullState.OPEN
    }
  }

  fun interface OnFilterStateChanged {
    fun newState(state: FilterPullState, source: ConversationFilterSource)
  }

  fun interface OnCloseClicked {
    fun onCloseClicked()
  }
}
