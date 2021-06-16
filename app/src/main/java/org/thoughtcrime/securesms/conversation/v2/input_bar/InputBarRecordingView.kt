package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.animation.Animator
import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_input_bar_recording.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.animateSizeChange
import org.thoughtcrime.securesms.loki.utilities.toPx
import kotlin.math.roundToInt

class InputBarRecordingView : RelativeLayout {

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_input_bar_recording, this)
    }

    fun show() {
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
    }

    private fun animateDotView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
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
        animation.duration = 1000L
        animation.addUpdateListener { animator ->
            pulseView.alpha = animator.animatedValue as Float
            if (animator.animatedFraction == 1.0f) { pulse() }
        }
        animation.start()
    }
}
