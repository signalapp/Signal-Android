package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class ConversationRecyclerView : RecyclerView {
    private var velocityTracker: VelocityTracker? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        return false
        /*
        val velocityTracker = velocityTracker ?: return super.onInterceptTouchEvent(e)
        velocityTracker.computeCurrentVelocity(1000) // Specifying 1000 gives pixels per second
        val vx = velocityTracker.xVelocity
        val vy = velocityTracker.yVelocity
        // Only allow swipes to the left; allowing swipes to the right interferes with some back gestures
        if (vx > 0) { return super.onInterceptTouchEvent(e) }
        // Return false if abs(v.x) > abs(v.y) so that only swipes that are more horizontal than vertical
        // get passed on to the message view
        return abs(vx) < abs(vy)
         */
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> velocityTracker = VelocityTracker.obtain()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> velocityTracker = null
        }
        velocityTracker?.addMovement(e)
        return super.onTouchEvent(e)
    }
}