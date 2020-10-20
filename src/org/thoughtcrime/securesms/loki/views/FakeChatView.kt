package org.thoughtcrime.securesms.loki.views

import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Context.LAYOUT_INFLATER_SERVICE
import android.os.Handler
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlinx.android.synthetic.main.view_fake_chat.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.disableClipping

class FakeChatView : ScrollView {

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
        val inflater = context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_fake_chat, null) as LinearLayout
        contentView.disableClipping()
        addView(contentView)
        isVerticalScrollBarEnabled = false
    }
    // endregion

    // region Animation
    fun startAnimating() {
        listOf( bubble1, bubble2, bubble3, bubble4, bubble5 ).forEach { it.alpha = 0.0f }
        fun show(bubble: View) {
            val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
            animation.duration = animationDuration
            animation.addUpdateListener { animator ->
                bubble.alpha = animator.animatedValue as Float
            }
            animation.start()
        }
        Handler().postDelayed({
            show(bubble1)
            Handler().postDelayed({
                show(bubble2)
                Handler().postDelayed({
                    show(bubble3)
                    smoothScrollTo(0, (bubble1.height + spacing).toInt())
                    Handler().postDelayed({
                        show(bubble4)
                        smoothScrollTo(0, (bubble1.height + spacing).toInt() + (bubble2.height + spacing).toInt())
                        Handler().postDelayed({
                            show(bubble5)
                            smoothScrollTo(0, (bubble1.height + spacing).toInt() + (bubble2.height + spacing).toInt() + (bubble3.height + spacing).toInt())
                        }, delayBetweenMessages)
                    }, delayBetweenMessages)
                }, delayBetweenMessages)
            }, delayBetweenMessages)
        }, startDelay)
    }
    // endregion
}