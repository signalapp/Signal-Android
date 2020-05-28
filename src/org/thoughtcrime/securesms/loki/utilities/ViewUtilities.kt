package org.thoughtcrime.securesms.loki.utilities

import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.graphics.PointF
import android.graphics.Rect
import android.support.annotation.DimenRes
import android.view.View

fun View.contains(point: PointF): Boolean {
    return hitRect.contains(point.x.toInt(), point.y.toInt())
}

val View.hitRect: Rect
    get()  {
        val rect = Rect()
        getHitRect(rect)
        return rect
    }

fun View.animateSizeChange(@DimenRes startSizeID: Int, @DimenRes endSizeID: Int, animationDuration: Long = 250) {
    val layoutParams = this.layoutParams
    val startSize = resources.getDimension(startSizeID)
    val endSize = resources.getDimension(endSizeID)
    val animation = ValueAnimator.ofObject(FloatEvaluator(), startSize, endSize)
    animation.duration = animationDuration
    animation.addUpdateListener { animator ->
        val size = animator.animatedValue as Float
        layoutParams.width = size.toInt()
        layoutParams.height = size.toInt()
        this.layoutParams = layoutParams
    }
    animation.start()
}