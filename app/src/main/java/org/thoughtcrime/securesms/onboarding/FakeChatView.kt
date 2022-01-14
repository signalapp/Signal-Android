package org.thoughtcrime.securesms.onboarding

import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewFakeChatBinding
import org.thoughtcrime.securesms.util.disableClipping

class FakeChatView : ScrollView {
    private lateinit var binding: ViewFakeChatBinding
    // region Settings
    private val spacing = context.resources.getDimension(R.dimen.medium_spacing)
    private val startDelay: Long = 1000
    private val delayBetweenMessages: Long = 1500
    private val animationDuration: Long = 400
    // endregion

    // region Lifecycle
    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        binding = ViewFakeChatBinding.inflate(LayoutInflater.from(context), this, true)
        binding.root.disableClipping()
        isVerticalScrollBarEnabled = false
    }
    // endregion

    // region Animation
    fun startAnimating() {
        listOf( binding.bubble1, binding.bubble2, binding.bubble3, binding.bubble4, binding.bubble5 ).forEach { it.alpha = 0.0f }
        fun show(bubble: View) {
            val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
            animation.duration = animationDuration
            animation.addUpdateListener { animator ->
                bubble.alpha = animator.animatedValue as Float
            }
            animation.start()
        }
        Handler().postDelayed({
            show(binding.bubble1)
            Handler().postDelayed({
                show(binding.bubble2)
                Handler().postDelayed({
                    show(binding.bubble3)
                    smoothScrollTo(0, (binding.bubble1.height + spacing).toInt())
                    Handler().postDelayed({
                        show(binding.bubble4)
                        smoothScrollTo(0, (binding.bubble1.height + spacing).toInt() + (binding.bubble2.height + spacing).toInt())
                        Handler().postDelayed({
                            show(binding.bubble5)
                            smoothScrollTo(0, (binding.bubble1.height + spacing).toInt() + (binding.bubble2.height + spacing).toInt() + (binding.bubble3.height + spacing).toInt())
                        }, delayBetweenMessages)
                    }, delayBetweenMessages)
                }, delayBetweenMessages)
            }, delayBetweenMessages)
        }, startDelay)
    }
    // endregion
}