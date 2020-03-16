package org.thoughtcrime.securesms.loki.redesign.utilities

import android.graphics.PointF
import android.graphics.Rect
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