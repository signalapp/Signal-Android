package org.thoughtcrime.securesms.loki

import android.content.res.Resources
import android.os.Build
import android.support.annotation.ColorRes
import kotlin.math.roundToInt

fun Resources.getColorWithID(@ColorRes id: Int, theme: Resources.Theme?): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getColor(id, theme)
    } else {
        @Suppress("DEPRECATION") getColor(id)
    }
}

fun convertToPixels(points: Int, resources: Resources): Int {
    val scale = resources.displayMetrics.density
    return (points * scale).roundToInt()
}