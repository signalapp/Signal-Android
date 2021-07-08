package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.animation.FloatEvaluator
import android.animation.IntEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_input_bar_recording.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.animateSizeChange
import org.thoughtcrime.securesms.loki.utilities.disableClipping
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.util.DateUtils
import java.util.*

class InputBarRecordingView : RelativeLayout {
    private var startTimestamp = 0L
    private val snHandler = Handler(Looper.getMainLooper())
    private var dotViewAnimation: ValueAnimator? = null
    private var pulseAnimation: ValueAnimator? = null
    var delegate: InputBarRecordingViewDelegate? = null

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_input_bar_recording, this)
        inputBarMiddleContentContainer.disableClipping()
        inputBarCancelButton.setOnClickListener { hide() }
    }

    fun show() {
        startTimestamp = Date().time
        recordButtonOverlayImageView.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_microphone, context.theme))
        inputBarCancelButton.alpha = 0.0f
        inputBarMiddleContentContainer.alpha = 1.0f
        lockView.alpha = 1.0f
        isVisible = true
        alpha = 0.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            alpha = animator.animatedValue as Float
        }
        animation.start()
        animateDotView()
        pulse()
        animateLockViewUp()
        updateTimer()
    }

    fun hide() {
        alpha = 1.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            alpha = animator.animatedValue as Float
            if (animator.animatedFraction == 1.0f) {
                isVisible = false
                dotViewAnimation?.repeatCount = 0
                pulseAnimation?.removeAllUpdateListeners()
            }
        }
        animation.start()
        delegate?.handleVoiceMessageUIHidden()
    }

    private fun animateDotView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        dotViewAnimation = animation
        animation.duration = 500L
        animation.addUpdateListener { animator ->
            dotView.alpha = animator.animatedValue as Float
        }
        animation.repeatCount = ValueAnimator.INFINITE
        animation.repeatMode = ValueAnimator.REVERSE
        animation.start()
    }

    private fun pulse() {
        val collapsedSize = toPx(80.0f, resources)
        val expandedSize = toPx(104.0f, resources)
        pulseView.animateSizeChange(collapsedSize, expandedSize, 1000)
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.5, 0.0f)
        pulseAnimation = animation
        animation.duration = 1000L
        animation.addUpdateListener { animator ->
            pulseView.alpha = animator.animatedValue as Float
            if (animator.animatedFraction == 1.0f && isVisible) { pulse() }
        }
        animation.start()
    }

    private fun animateLockViewUp() {
        val startMarginBottom = toPx(32, resources)
        val endMarginBottom = toPx(72, resources)
        val layoutParams = lockView.layoutParams as LayoutParams
        layoutParams.bottomMargin = startMarginBottom
        lockView.layoutParams = layoutParams
        val animation = ValueAnimator.ofObject(IntEvaluator(), startMarginBottom, endMarginBottom)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            layoutParams.bottomMargin = animator.animatedValue as Int
            lockView.layoutParams = layoutParams
        }
        animation.start()
    }

    private fun updateTimer() {
        val duration = (Date().time - startTimestamp) / 1000L
        recordingViewDurationTextView.text = DateUtils.formatElapsedTime(duration)
        snHandler.postDelayed({ updateTimer() }, 500)
    }

    fun lock() {
        val fadeOutAnimation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        fadeOutAnimation.duration = 250L
        fadeOutAnimation.addUpdateListener { animator ->
            inputBarMiddleContentContainer.alpha = animator.animatedValue as Float
            lockView.alpha = animator.animatedValue as Float
        }
        fadeOutAnimation.start()
        val fadeInAnimation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
        fadeInAnimation.duration = 250L
        fadeInAnimation.addUpdateListener { animator ->
            inputBarCancelButton.alpha = animator.animatedValue as Float
        }
        fadeInAnimation.start()
        recordButtonOverlayImageView.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_arrow_up, context.theme))
        recordButtonOverlay.setOnClickListener { delegate?.sendVoiceMessage() }
        inputBarCancelButton.setOnClickListener { delegate?.cancelVoiceMessage() }
    }
}

interface InputBarRecordingViewDelegate {

    fun handleVoiceMessageUIHidden()
    fun sendVoiceMessage()
    fun cancelVoiceMessage()
}
