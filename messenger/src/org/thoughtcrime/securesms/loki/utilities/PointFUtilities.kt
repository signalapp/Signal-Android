package org.thoughtcrime.securesms.loki.utilities

import android.graphics.PointF
import android.view.View

fun PointF.distanceTo(other: PointF): Float {
    return Math.sqrt(Math.pow(this.x.toDouble() - other.x.toDouble(), 2.toDouble()) + Math.pow(this.y.toDouble() - other.y.toDouble(), 2.toDouble())).toFloat()
}

fun PointF.isLeftOf(view: View, margin: Float = 0.0f): Boolean {
    return isContainedVerticallyIn(view, margin) && x < view.hitRect.left
}

fun PointF.isAbove(view: View, margin: Float = 0.0f): Boolean {
    return isContainedHorizontallyIn(view, margin) && y < view.hitRect.top
}

fun PointF.isRightOf(view: View, margin: Float = 0.0f): Boolean {
    return isContainedVerticallyIn(view, margin) && x > view.hitRect.right
}

fun PointF.isBelow(view: View, margin: Float = 0.0f): Boolean {
    return isContainedHorizontallyIn(view, margin) && y > view.hitRect.bottom
}

fun PointF.isContainedHorizontallyIn(view: View, margin: Float = 0.0f): Boolean {
    return x >= view.hitRect.left - margin || x <= view.hitRect.right + margin
}

fun PointF.isContainedVerticallyIn(view: View, margin: Float = 0.0f): Boolean {
    return y >= view.hitRect.top - margin || x <= view.hitRect.bottom + margin
}