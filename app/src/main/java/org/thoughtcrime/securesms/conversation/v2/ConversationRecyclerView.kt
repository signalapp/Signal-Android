package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.loki.utilities.disableClipping
import kotlin.math.abs

class ConversationRecyclerView : RecyclerView {
    private var velocityTracker: VelocityTracker? = null

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        disableClipping()
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        Log.d("Test", "here")
        val velocityTracker = velocityTracker ?: return super.onInterceptTouchEvent(e)
        velocityTracker.computeCurrentVelocity(1000) // Specifying 1000 gives pixels per second
        val vx = velocityTracker.xVelocity
        val vy = velocityTracker.yVelocity
        Log.d("Test", "vx: $vx, vy: $vy")
        // Only allow swipes to the left; allowing swipes to the right interferes with some back gestures
        if (vx > 0) { return super.onInterceptTouchEvent(e) }
        // Return false if abs(v.x) > abs(v.y) so that only swipes that are more horizontal than vertical
        // get passed on to the message view
        if (abs(vx) > abs(vy)) {
            return false
        } else {
            return super.onInterceptTouchEvent(e)
        }
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> velocityTracker = VelocityTracker.obtain()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> velocityTracker = null
        }
        velocityTracker?.addMovement(e)
        return super.dispatchTouchEvent(e)
    }
}